package cl.portaalerta

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.portaalerta.data.AppDatabase
import cl.portaalerta.data.AppSettings
import cl.portaalerta.data.BackupBundle
import cl.portaalerta.data.BackupCodec
import cl.portaalerta.data.BackupCrypto
import cl.portaalerta.data.Contact
import cl.portaalerta.data.ChipScan
import cl.portaalerta.data.PhotoStore
import cl.portaalerta.data.Client
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupOutcome
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed interface Screen {
    data object Home : Screen
    data class ClientDetail(val id: Long) : Screen
    data class ClientEdit(val id: Long?) : Screen
    data object NewClient : Screen
    data object Settings : Screen
    data class Scanner(val clientId: Long?) : Screen
    data object Photos : Screen
    data class ChipResult(val scanId: Long?, val tempPath: String?, val iccid: String, val clientId: Long?) : Screen
    data object Import : Screen
    data class FollowupEdit(val followupId: Long?, val clientId: Long?, val type: FollowupType) : Screen
}

data class AgendaItem(val followup: Followup, val client: Client)

/** Aviso al marcar "No le interesó": el cliente tiene fotos guardadas. */
data class PurgePrompt(val client: Client, val count: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application = app
    private val dao = AppDatabase.get(app).dao()
    val settings = AppSettings(app)

    // ---- estado de la pantalla principal (sobrevive a abrir una ficha y volver) ----
    var homeTab by mutableIntStateOf(0)
    var agendaMode by mutableIntStateOf(0)
    var calendarOffset by mutableIntStateOf(0)
    var calendarSelected by mutableLongStateOf(LocalDate.now().toEpochDay())
    var summaryOffset by mutableIntStateOf(0)
    var clientQuery by mutableStateOf("")

    var purgePrompt by mutableStateOf<PurgePrompt?>(null)

    // ---- navegación mínima (pila de pantallas) ----
    val stack = mutableStateListOf<Screen>(Screen.Home)

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(s: Screen) {
        stack[stack.lastIndex] = s
    }

    fun openClientFromNotification(id: Long) {
        stack.clear()
        stack.add(Screen.Home)
        stack.add(Screen.ClientDetail(id))
    }

    // ---- datos ----
    val clients: StateFlow<List<Client>> =
        dao.clients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agenda: StateFlow<List<AgendaItem>> =
        combine(dao.clients(), dao.openFollowups()) { cs, fs ->
            val byId = cs.associateBy { it.id }
            fs.mapNotNull { f -> byId[f.clientId]?.let { AgendaItem(f, it) } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Todos los recordatorios (abiertos y hechos) con su cliente, para el calendario. */
    val allItems: StateFlow<List<AgendaItem>> =
        combine(dao.clients(), dao.followupsFlow()) { cs, fs ->
            val byId = cs.associateBy { it.id }
            fs.mapNotNull { f -> byId[f.clientId]?.let { AgendaItem(f, it) } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allFollowups: StateFlow<List<Followup>> =
        dao.followupsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scans: StateFlow<List<ChipScan>> =
        dao.scansFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Clientes que rechazaron (sin nada abierto ni cerrado) y todavía tienen fotos guardadas. */
    val rejectedWithPhotos: StateFlow<List<Pair<Client, Int>>> =
        combine(dao.clients(), dao.followupsFlow(), dao.scansFlow()) { cs, fs, ss ->
            val photos = ss.filter { it.clientId != null && it.photoPath != null }.groupBy { it.clientId!! }
            cs.mapNotNull { c ->
                val mine = fs.filter { it.clientId == c.id }
                val rejected = mine.any { it.outcome == FollowupOutcome.REJECTED }
                val active = mine.any { it.status == FollowupStatus.OPEN || it.outcome == FollowupOutcome.CLOSED }
                val n = photos[c.id]?.size ?: 0
                if (rejected && !active && n > 0) c to n else null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun scansFor(clientId: Long): Flow<List<ChipScan>> = dao.scansFor(clientId)
    suspend fun getScan(id: Long): ChipScan? = dao.scan(id)
    suspend fun findScanByIccid(iccid: String): ChipScan? = dao.scanByIccid(iccid)

    /** Guarda la lectura. Si venía de una foto temporal, la pasa a su lugar definitivo. */
    fun saveScan(scan: ChipScan, tempPath: String?, onSaved: () -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                if (tempPath != null) PhotoStore.promote(ctx, File(tempPath)) else scan.photoPath
            }
            val s = scan.copy(photoPath = path)
            if (s.id == 0L) dao.insertScan(s) else dao.updateScan(s)
            onSaved()
        }
    }

    fun deleteScan(scan: ChipScan) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PhotoStore.delete(scan.photoPath) }
            dao.deleteScan(scan)
        }
    }

    /** Borra fotos y lecturas de chip de un cliente. */
    fun purgeClientPhotos(clientId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.scansNow(clientId).forEach { PhotoStore.delete(it.photoPath) } }
            dao.deleteScansFor(clientId)
        }
    }

    fun purgeAllRejected() {
        viewModelScope.launch {
            for ((client, _) in rejectedWithPhotos.value) {
                withContext(Dispatchers.IO) { dao.scansNow(client.id).forEach { PhotoStore.delete(it.photoPath) } }
                dao.deleteScansFor(client.id)
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { PhotoStore.cleanTmp(ctx) }
    }

    fun clientFlow(id: Long): Flow<Client?> = dao.clientFlow(id)
    fun followupsFor(clientId: Long): Flow<List<Followup>> = dao.followupsFor(clientId)
    suspend fun getClient(id: Long): Client? = dao.client(id)
    suspend fun getFollowup(id: Long): Followup? = dao.followup(id)

    fun saveClient(c: Client, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (c.id == 0L) {
                dao.insertClient(c)
            } else {
                dao.updateClient(c)
                c.id
            }
            onSaved(id)
        }
    }

    fun deleteClient(c: Client) {
        viewModelScope.launch {
            dao.followupsNow(c.id).forEach { Scheduler.cancel(ctx, it.id) }
            withContext(Dispatchers.IO) { dao.scansNow(c.id).forEach { PhotoStore.delete(it.photoPath) } }
            dao.deleteClient(c)
        }
    }

    fun saveFollowup(f: Followup, onSaved: () -> Unit) {
        viewModelScope.launch {
            val id = if (f.id == 0L) {
                dao.insertFollowup(f)
            } else {
                dao.updateFollowup(f)
                f.id
            }
            Scheduler.schedule(ctx, f.copy(id = id))
            onSaved()
        }
    }

    fun markDone(f: Followup, outcome: FollowupOutcome) {
        viewModelScope.launch {
            dao.updateFollowup(
                f.copy(status = FollowupStatus.DONE, outcome = outcome, doneAt = System.currentTimeMillis()),
            )
            Scheduler.cancel(ctx, f.id)
            if (outcome == FollowupOutcome.REJECTED) {
                val n = dao.scansNow(f.clientId).count { it.photoPath != null }
                if (n > 0) dao.client(f.clientId)?.let { purgePrompt = PurgePrompt(it, n) }
            }
        }
    }

    fun reopen(f: Followup) {
        viewModelScope.launch {
            val reopened = f.copy(status = FollowupStatus.OPEN, outcome = null, doneAt = null)
            dao.updateFollowup(reopened)
            Scheduler.schedule(ctx, reopened)
        }
    }

    fun deleteFollowup(f: Followup) {
        viewModelScope.launch {
            Scheduler.cancel(ctx, f.id)
            dao.deleteFollowup(f)
        }
    }

    /** Crea un cliente por contacto y, si se pide, su recordatorio de portabilidad con los plazos de Ajustes. */
    fun importContacts(
        contacts: List<Pair<String, String>>,
        createPortability: Boolean,
        base: LocalDate,
        onDone: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            val months = settings.months(FollowupType.PORTABILITY)
            val lead = settings.leadDays(FollowupType.PORTABILITY)
            for ((name, phone) in contacts) {
                val clientId = dao.insertClient(Client(name = name, phone = phone))
                if (createPortability) {
                    val f = Followup(
                        clientId = clientId,
                        type = FollowupType.PORTABILITY,
                        baseDate = base.toEpochDay(),
                        months = months,
                        dueDate = Dates.dueFor(base, months).toEpochDay(),
                        leadDays = lead,
                    )
                    val id = dao.insertFollowup(f)
                    Scheduler.schedule(ctx, f.copy(id = id))
                }
            }
            onDone(contacts.size)
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch { Scheduler.rescheduleAll(ctx) }
    }

    data class ImportResult(val clients: Int, val skipped: Int, val followups: Int, val chips: Int)

    /** Guarda un respaldo cifrado con la contraseña elegida en el archivo [uri]. Devuelve cuántos clientes incluye. */
    suspend fun exportBackup(uri: Uri, password: CharArray, includePhotos: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clients = dao.clientsNow()
                val followups = dao.allFollowups()
                val scans = dao.scansAll()
                val photos = HashMap<Long, ByteArray>()
                if (includePhotos) {
                    for (s in scans) {
                        val path = s.photoPath ?: continue
                        val bmp = PhotoStore.loadBitmap(ctx, path, 1280) ?: continue
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        photos[s.id] = out.toByteArray()
                    }
                }
                val payload = BackupCodec.encode(BackupBundle(clients, followups, scans, photos))
                val file = BackupCrypto.seal(password, payload)
                val stream = ctx.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo crear el archivo")
                stream.use { it.write(file) }
                clients.size
            }
        }

    /** Restaura un respaldo. Los clientes cuyo teléfono ya existe se omiten (no se duplican). */
    suspend fun importBackup(uri: Uri, password: CharArray): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
                val bundle = BackupCodec.decode(BackupCrypto.open(password, file))
                val cc = settings.countryCode
                val known = dao.clientsNow().map { Contact.waNumber(it.phone, cc) }.toMutableSet()
                val idMap = HashMap<Long, Long>()
                var skipped = 0
                for (c in bundle.clients) {
                    val key = Contact.waNumber(c.phone, cc)
                    if (key in known) {
                        skipped++
                        continue
                    }
                    idMap[c.id] = dao.insertClient(c.copy(id = 0))
                    known += key
                }
                var nf = 0
                for (f in bundle.followups) {
                    val cid = idMap[f.clientId] ?: continue
                    val id = dao.insertFollowup(f.copy(id = 0, clientId = cid))
                    nf++
                    if (f.status == FollowupStatus.OPEN) Scheduler.schedule(ctx, f.copy(id = id, clientId = cid))
                }
                var nc = 0
                for (s in bundle.scans) {
                    val cid: Long? = if (s.clientId == null) null else (idMap[s.clientId] ?: continue)
                    val path = bundle.photos[s.id]?.let { PhotoStore.saveFinal(ctx, it) }
                    dao.insertScan(s.copy(id = 0, clientId = cid, photoPath = path))
                    nc++
                }
                ImportResult(idMap.size, skipped, nf, nc)
            }
        }

    /** Respaldo en CSV (clientes + recordatorios) para compartir por WhatsApp/correo. */
    suspend fun exportCsv(): String {
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
        val cs = dao.clientsNow()
        val byId = cs.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("CLIENTES")
        sb.appendLine("nombre,telefono,compania,notas")
        cs.forEach { sb.appendLine(listOf(it.name, it.phone, it.currentCarrier, it.notes).joinToString(",") { v -> esc(v) }) }
        sb.appendLine()
        sb.appendLine("RECORDATORIOS")
        sb.appendLine("cliente,telefono,tipo,fecha_base,meses,vencimiento,aviso_dias_antes,estado,resultado,detalle")
        dao.allFollowups().forEach { f ->
            val c = byId[f.clientId] ?: return@forEach
            sb.appendLine(
                listOf(
                    c.name, c.phone, f.type.label, Dates.format(f.baseDate), f.months.toString(),
                    Dates.format(f.dueDate), f.leadDays.toString(), f.status.name, f.outcome?.label.orEmpty(), f.detail,
                ).joinToString(",") { v -> esc(v) },
            )
        }
        return sb.toString()
    }
}
