package cl.portaalerta

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.portaalerta.data.AppDatabase
import cl.portaalerta.data.AppSettings
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.Templates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

const val EXTRA_FOLLOWUP_ID = "followup_id"
const val EXTRA_CLIENT_ID = "client_id"
const val EXTRA_DIGEST = "digest"

/** Programa dos alarmas por recordatorio: "aviso previo" (leadDays antes) y "día del vencimiento". */
object Scheduler {
    private const val KIND_LEAD = 0
    private const val KIND_DUE = 1

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(ctx: Context, followupId: Long, kind: Int, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_FOLLOWUP_ID, followupId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, (followupId * 2 + kind).toInt(), intent, flags)
    }

    fun cancel(ctx: Context, followupId: Long) {
        val am = alarmManager(ctx)
        for (kind in listOf(KIND_LEAD, KIND_DUE)) {
            pendingIntent(ctx, followupId, kind, create = false)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
    }

    fun schedule(ctx: Context, f: Followup) {
        cancel(ctx, f.id)
        if (f.status != FollowupStatus.OPEN) return
        val hour = AppSettings(ctx).notifyHour
        val due = LocalDate.ofEpochDay(f.dueDate)
        if (f.leadDays > 0) {
            setAt(ctx, f.id, KIND_LEAD, millis(due.minusDays(f.leadDays.toLong()), hour))
        }
        setAt(ctx, f.id, KIND_DUE, millis(due, hour))
    }

    suspend fun rescheduleAll(ctx: Context) {
        AppDatabase.get(ctx).dao().openFollowupsNow().forEach { schedule(ctx, it) }
        scheduleDigest(ctx)
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(ctx).canScheduleExactAlarms()

    private fun millis(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setAt(ctx: Context, followupId: Long, kind: Int, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val pi = pendingIntent(ctx, followupId, kind, create = true) ?: return
        arm(ctx, pi, atMillis)
    }

    private fun arm(ctx: Context, pi: PendingIntent, atMillis: Long) {
        val am = alarmManager(ctx)
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    /** Aviso diario (30 min después de la hora de alarma) si quedan recordatorios atrasados sin actualizar. */
    fun scheduleDigest(ctx: Context) {
        val hour = AppSettings(ctx).notifyHour
        var at = LocalDate.now().atTime(hour, 30)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_DIGEST, true)
        val pi = PendingIntent.getBroadcast(
            ctx, DIGEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        arm(ctx, pi, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    private const val DIGEST_CODE = Int.MAX_VALUE - 1
}

object Notifier {
    private const val CHANNEL = "recordatorios"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Recordatorios de clientes", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Resumen diario: cuántos recordatorios siguen abiertos después de su fecha. */
    suspend fun notifyDigest(ctx: Context) {
        val today = LocalDate.now().toEpochDay()
        val overdue = AppDatabase.get(ctx).dao().openFollowupsNow().count { it.dueDate < today }
        if (overdue == 0 || !canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (overdue == 1) "Tienes 1 recordatorio atrasado" else "Tienes $overdue recordatorios atrasados"
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText("Márcalos como hechos o cambia la fecha para dejar el estado al día.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(0, notification)
    }

    suspend fun notifyFollowup(ctx: Context, followupId: Long) {
        val dao = AppDatabase.get(ctx).dao()
        val f = dao.followup(followupId) ?: return
        if (f.status != FollowupStatus.OPEN) return
        val client = dao.client(f.clientId) ?: return
        if (!canNotify(ctx)) return
        ensureChannel(ctx)

        val settings = AppSettings(ctx)
        val days = Dates.daysUntil(f.dueDate)
        val whenText = when {
            days > 0 -> "Disponible en $days días (${Dates.format(f.dueDate)})"
            days == 0L -> "¡Es hoy!"
            else -> "Vencido ${Dates.relative(days)}"
        }
        val message = Templates.messageFor(f, client, settings)
        val immutableUpdate = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val base = (f.id * 10).toInt()

        val open = PendingIntent.getActivity(
            ctx, base,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_CLIENT_ID, client.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            immutableUpdate,
        )
        val whatsapp = PendingIntent.getActivity(
            ctx, base + 1,
            Contact.whatsappIntent(client.phone, message, settings.countryCode),
            immutableUpdate,
        )
        val call = PendingIntent.getActivity(ctx, base + 2, Contact.dialIntent(client.phone), immutableUpdate)

        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("${client.name} · ${f.type.label}")
            .setContentText(whenText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$whenText\n${f.detail}".trim()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "WhatsApp", whatsapp)
            .addAction(0, "Llamar", call)
            .build()

        NotificationManagerCompat.from(ctx).notify(f.id.toInt(), notification)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_FOLLOWUP_ID, -1L)
        val digest = intent.getBooleanExtra(EXTRA_DIGEST, false)
        if (id < 0 && !digest) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (digest) {
                    Notifier.notifyDigest(app)
                    Scheduler.scheduleDigest(app)
                } else {
                    Notifier.notifyFollowup(app, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-programa las alarmas tras reiniciar el teléfono, actualizar la app o cambiar la hora/zona. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
