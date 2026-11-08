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
import java.time.ZoneId

const val EXTRA_FOLLOWUP_ID = "followup_id"
const val EXTRA_CLIENT_ID = "client_id"

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
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(ctx).canScheduleExactAlarms()

    private fun millis(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setAt(ctx: Context, followupId: Long, kind: Int, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val am = alarmManager(ctx)
        val pi = pendingIntent(ctx, followupId, kind, create = true) ?: return
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

    suspend fun notifyFollowup(ctx: Context, followupId: Long) {
        val dao = AppDatabase.get(ctx).dao()
        val f = dao.followup(followupId) ?: return
        if (f.status != FollowupStatus.OPEN) return
        val client = dao.client(f.clientId) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
        if (id < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Notifier.notifyFollowup(app, id)
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
