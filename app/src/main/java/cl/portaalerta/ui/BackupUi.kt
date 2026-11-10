package cl.portaalerta.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.data.InvalidBackupException
import cl.portaalerta.data.WrongPasswordException
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class BackupDialog { EXPORT, IMPORT }

/**
 * Respaldo cifrado con contraseña: sirve para pasar los clientes a un teléfono nuevo.
 * El archivo se guarda donde la persona elija (Drive, Descargas, un pendrive...) y solo se abre
 * con la contraseña que puso; sin ella nadie puede leerlo.
 */
@Composable
fun EncryptedBackupSection(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var includePhotos by remember { mutableStateOf(true) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun clearPassword() {
        password = ""
        confirm = ""
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            clearPassword()
        } else {
            busy = true
            scope.launch {
                val chars = password.toCharArray()
                val result = vm.exportBackup(uri, chars, includePhotos)
                chars.fill('\u0000')
                clearPassword()
                busy = false
                message = result.fold(
                    onSuccess = { "Respaldo guardado con $it clientes.\n\nGuarda bien tu contraseña: sin ella nadie puede abrirlo, ni siquiera tú." },
                    onFailure = { "No se pudo guardar el respaldo: ${it.message}" },
                )
            }
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = uri
            dialog = BackupDialog.IMPORT
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Respaldo cifrado: para pasar tus clientes a un teléfono nuevo. Lo proteges con una contraseña que tú eliges.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { clearPassword(); dialog = BackupDialog.EXPORT }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar respaldo cifrado")
        }
        OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Restaurar desde un respaldo")
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.padding(4.dp))
                Text("Trabajando…")
            }
        }
    }

    when (dialog) {
        BackupDialog.EXPORT -> {
            val tooShort = password.length < 8
            val mismatch = password != confirm
            AlertDialog(
                onDismissRequest = { dialog = null; clearPassword() },
                title = { Text("Contraseña del respaldo") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Si la olvidas, nadie puede recuperar el respaldo. Usa una frase larga (12 o más caracteres) y guárdala en un lugar seguro.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            supportingText = { Text(if (password.isEmpty()) "Mínimo 8 caracteres" else if (tooShort) "Muy corta" else if (password.length < 12) "Mejor con 12 o más" else "Buena longitud ✓") },
                        )
                        OutlinedTextField(
                            confirm, { confirm = it }, label = { Text("Repite la contraseña") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = confirm.isNotEmpty() && mismatch,
                            supportingText = { if (confirm.isNotEmpty() && mismatch) Text("No coinciden") },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(includePhotos, { includePhotos = it })
                            Text("Incluir fotos de chips (se reducen de tamaño)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !tooShort && !mismatch,
                        onClick = {
                            dialog = null
                            createFile.launch("PortaAlerta-respaldo-${LocalDate.now()}.pabk")
                        },
                    ) { Text("Elegir dónde guardar") }
                },
                dismissButton = { TextButton(onClick = { dialog = null; clearPassword() }) { Text("Cancelar") } },
            )
        }

        BackupDialog.IMPORT -> AlertDialog(
            onDismissRequest = { dialog = null; pendingImport = null; clearPassword() },
            title = { Text("Restaurar respaldo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Escribe la contraseña de ese respaldo. Los clientes cuyo teléfono ya existe aquí no se duplican.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.isNotEmpty(),
                    onClick = {
                        val uri = pendingImport
                        dialog = null
                        pendingImport = null
                        if (uri != null) {
                            busy = true
                            scope.launch {
                                val chars = password.toCharArray()
                                val result = vm.importBackup(uri, chars)
                                chars.fill('\u0000')
                                clearPassword()
                                busy = false
                                message = result.fold(
                                    onSuccess = {
                                        "Listo: se restauraron ${it.clients} clientes, ${it.followups} recordatorios y ${it.chips} chips." +
                                            if (it.skipped > 0) "\n${it.skipped} clientes ya existían y se omitieron." else ""
                                    },
                                    onFailure = {
                                        when (it) {
                                            is WrongPasswordException -> "Contraseña incorrecta o archivo dañado."
                                            is InvalidBackupException -> it.message ?: "Archivo no válido."
                                            else -> "No se pudo restaurar el respaldo."
                                        }
                                    },
                                )
                            }
                        }
                    },
                ) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { dialog = null; pendingImport = null; clearPassword() }) { Text("Cancelar") } },
        )

        null -> {}
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Respaldo") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } },
        )
    }
}
