package cl.portaalerta

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cl.portaalerta.data.AppSettings
import cl.portaalerta.ui.AppRoot
import cl.portaalerta.ui.LockScreen
import cl.portaalerta.ui.PortaTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var leftAt = 0L

    private val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            leftAt = SystemClock.elapsedRealtime()
            vm.locked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Scheduler.scheduleDigest(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PortaTheme {
                AskNotificationPermission()
                if (vm.locked) LockScreen(onUnlock = ::requestUnlock) else AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyLockPolicy()
    }

    override fun onStop() {
        super.onStop()
        if (!vm.locked) leftAt = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Bloqueo activo: se pide el PIN/patrón/huella del teléfono al abrir y tras 1 minuto fuera de la app. */
    private fun applyLockPolicy() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val enabled = AppSettings(this).lockEnabled && keyguard?.isDeviceSecure == true
        if (enabled) {
            // También oculta la app en "recientes" y bloquea las capturas de pantalla.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            if (!vm.locked && SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MS) vm.locked = true
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            vm.locked = false
        }
    }

    private fun requestUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = keyguard?.createConfirmDeviceCredentialIntent("PortaAlerta", "Confirma que eres tú para ver tus clientes")
        if (intent == null) {
            vm.locked = false
        } else {
            unlock.launch(intent)
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val id = i.getLongExtra(EXTRA_CLIENT_ID, -1L)
        if (id >= 0) {
            vm.openClientFromNotification(id)
            i.removeExtra(EXTRA_CLIENT_ID)
        }
    }

    private companion object {
        const val LOCK_AFTER_MS = 60_000L
    }
}

@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
