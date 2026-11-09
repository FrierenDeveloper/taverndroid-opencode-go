@file:OptIn(ExperimentalMaterial3Api::class)

package cl.portaalerta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.portaalerta.MainViewModel
import cl.portaalerta.Screen
import cl.portaalerta.data.ChipScan
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Iccid
import cl.portaalerta.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ---------------------------------------------------------------- piezas comunes

@Composable
fun PhotoThumb(path: String?, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) { PhotoStore.loadBitmap(path, 480)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, contentDescription = "Foto del chip", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Abre el selector de fotos del sistema (sin permisos), copia la elegida a la carpeta privada y la analiza. */
@Composable
private fun rememberGalleryPicker(vm: MainViewModel, replaceScreen: Boolean, clientId: Long?): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { PhotoStore.copyToTemp(ctx, uri) }
                val iccid = file?.let { ChipReader.readFile(it) }
                if (file != null && iccid == null) {
                    Toast.makeText(ctx, "No pude leer el número. Puedes escribirlo a mano.", Toast.LENGTH_LONG).show()
                }
                val next = Screen.ChipResult(null, file?.path, iccid.orEmpty(), clientId)
                if (replaceScreen) vm.replaceTop(next) else vm.push(next)
            }
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

// ---------------------------------------------------------------- pestaña "Chips"

/** Pantalla "Fotos y chips guardados" (se abre desde Ajustes): lista, purga y lector sin cliente. */
@Composable
fun PhotosScreen(vm: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Fotos y chips") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding -> ChipsTab(vm, padding) }
}

@Composable
private fun ChipsTab(vm: MainViewModel, padding: PaddingValues) {
    val scans by vm.scans.collectAsState()
    val clients by vm.clients.collectAsState()
    val rejected by vm.rejectedWithPhotos.collectAsState()
    val pickFromGallery = rememberGalleryPicker(vm, replaceScreen = false, clientId = null)
    var confirmPurge by remember { mutableStateOf(false) }
    val names = remember(clients) { clients.associate { it.id to it.name } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lector de chips", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Apunta la cámara al código de barras o al número del chip y se lee solo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.push(Screen.Scanner(null)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Leer chip con la cámara")
                    }
                    OutlinedButton(onClick = pickFromGallery, modifier = Modifier.fillMaxWidth()) {
                        Text("Elegir foto de la galería")
                    }
                    Text(
                        "Las fotos se guardan solo en este teléfono, en la carpeta privada de la app. " +
                            "No aparecen en la galería ni en Google Fotos, y no se incluyen en los respaldos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (rejected.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fotos de clientes que te rechazaron", style = MaterialTheme.typography.titleMedium)
                        Text(
                            rejected.joinToString("\n") { (c, n) -> "• ${c.name}: $n ${if (n == 1) "foto" else "fotos"}" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { confirmPurge = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        ) { Text("Purgar estas fotos") }
                    }
                }
            }
        }
        item {
            Text(
                if (scans.isEmpty()) "Aún no has guardado lecturas." else "Lecturas guardadas (${scans.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        items(scans, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth().clickable { vm.push(Screen.ChipResult(s.id, null, s.iccid, s.clientId)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoThumb(s.photoPath, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Iccid.pretty(s.iccid), style = MaterialTheme.typography.titleSmall)
                        Text(
                            (s.clientId?.let { names[it] } ?: "Sin cliente") + " · " +
                                Dates.format(Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("Purgar fotos") },
            text = { Text("Se borrarán para siempre las fotos y lecturas de chip de los ${rejected.size} clientes que te rechazaron.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeAllRejected()
                    confirmPurge = false
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- lector en vivo

@Composable
fun ScannerScreen(vm: MainViewModel, clientId: Long?) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var message by remember { mutableStateOf("Apunta al código de barras o al número del chip") }
    val handled = remember { AtomicBoolean(false) }
    val pickFromGallery = rememberGalleryPicker(vm, replaceScreen = true, clientId = clientId)

    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    fun accept(iccid: String) {
        if (!handled.compareAndSet(false, true)) return
        message = "¡Leído! Guardando foto…"
        val file = PhotoStore.newTempFile(ctx)
        val cap = capture
        if (cap == null) {
            vm.replaceTop(Screen.ChipResult(null, null, iccid, clientId))
            return
        }
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    vm.replaceTop(Screen.ChipResult(null, file.path, iccid, clientId))
                }

                override fun onError(exception: ImageCaptureException) {
                    vm.replaceTop(Screen.ChipResult(null, null, iccid, clientId))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Leer chip") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            if (granted) {
                CameraPreview(onBound = { cam, cap -> camera = cam; capture = cap }, onRead = { accept(it) })
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.9f).height(150.dp)
                        .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)),
                )
            } else {
                Text(
                    "Necesito permiso de cámara para leer el chip.\nTambién puedes elegir una foto o escribir el número.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(message, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { torch = !torch }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (torch) "Linterna: sí" else "Linterna")
                    }
                    FilledTonalButton(onClick = pickFromGallery, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Galería")
                    }
                    FilledTonalButton(
                        onClick = { vm.replaceTop(Screen.ChipResult(null, null, "", clientId)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Escribir") }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onBound: (Camera, ImageCapture) -> Unit, onRead: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnRead by rememberUpdatedState(onRead)
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        ChipAnalyzer { value -> ContextCompat.getMainExecutor(ctx).execute { currentOnRead(value) } }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            provider.unbindAll()
            val cam = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
            onBound(cam, imageCapture)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { future.get().unbindAll() }
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

// ---------------------------------------------------------------- resultado / edición

@Composable
fun ChipResultScreen(vm: MainViewModel, screen: Screen.ChipResult) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val clients by vm.clients.collectAsState()

    var existing by remember { mutableStateOf<ChipScan?>(null) }
    var ready by remember { mutableStateOf(screen.scanId == null) }
    LaunchedEffect(screen.scanId) {
        if (screen.scanId != null) {
            existing = vm.getScan(screen.scanId)
            ready = true
        }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var iccid by remember { mutableStateOf(existing?.iccid ?: screen.iccid) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var clientId by remember { mutableStateOf(existing?.clientId ?: screen.clientId) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<ChipScan?>(null) }
    val saved = remember { AtomicBoolean(false) }
    val photoPath = existing?.photoPath ?: screen.tempPath

    // Una foto temporal que no se guardó se borra al salir.
    DisposableEffect(screen.tempPath) {
        onDispose { if (screen.tempPath != null && !saved.get()) PhotoStore.delete(screen.tempPath) }
    }
    LaunchedEffect(iccid) {
        duplicate = if (iccid.length >= 15) vm.findScanByIccid(iccid)?.takeIf { it.id != existing?.id } else null
    }

    val valid = Iccid.isValid(iccid)
    val clientName = clients.firstOrNull { it.id == clientId }?.name

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(if (existing == null) "Chip leído" else "Chip guardado") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (photoPath != null) PhotoThumb(photoPath, 220.dp, Modifier.fillMaxWidth())

            OutlinedTextField(
                value = iccid,
                onValueChange = { iccid = it.filter { c -> c.isDigit() }.take(20) },
                label = { Text("Número del chip (ICCID)") },
                supportingText = {
                    Text(
                        when {
                            iccid.isEmpty() -> "Escríbelo o vuelve a leerlo"
                            valid -> "Número válido ✓  ${Iccid.pretty(iccid)}"
                            else -> "No pasa la validación: revisa los dígitos (19 o 20, parte con 89)"
                        },
                    )
                },
                isError = iccid.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            duplicate?.let {
                Text(
                    "Este chip ya fue guardado el ${Dates.format(Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())}.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(iccid))
                    Toast.makeText(ctx, "Número copiado", Toast.LENGTH_SHORT).show()
                },
                enabled = iccid.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copiar número") }

            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(clientName ?: "Asociar a un cliente (opcional)")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Sin cliente") }, onClick = { clientId = null; menuOpen = false })
                    clients.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { clientId = c.id; menuOpen = false })
                    }
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Nota") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Button(
                enabled = iccid.isNotEmpty(),
                onClick = {
                    saved.set(true)
                    val scan = (existing ?: ChipScan(iccid = iccid)).copy(iccid = iccid, clientId = clientId, note = note.trim())
                    vm.saveScan(scan, screen.tempPath) { vm.pop() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar lectura y foto", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar") },
            text = { Text("Se borrarán la foto y el número de este chip.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { vm.deleteScan(it) }
                    vm.pop()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
