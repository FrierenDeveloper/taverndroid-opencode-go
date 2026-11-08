package cl.portaalerta

import android.app.Application
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
import cl.portaalerta.data.Client
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupOutcome
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Screen {
    data object Home : Screen
    data class ClientDetail(val id: Long) : Screen
    data class ClientEdit(val id: Long?) : Screen
    data object Settings : Screen
    data object Import : Screen
    data class FollowupEdit(val followupId: Long?, val clientId: Long?, val type: FollowupType) : Screen
}

data class AgendaItem(val followup: Followup, val client: Client)

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

    /** Respaldo en CSV (clientes + recordatorios) para compartir por WhatsApp/correo. */
    suspend fun exportCsv(): String {
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
        val cs = dao.clientsNow()
        val byId = cs.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("CLIENTES")
        sb.appendLine("nombre,telefono,compania,plan,notas")
        cs.forEach { sb.appendLine(listOf(it.name, it.phone, it.currentCarrier, it.plan, it.notes).joinToString(",") { v -> esc(v) }) }
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
