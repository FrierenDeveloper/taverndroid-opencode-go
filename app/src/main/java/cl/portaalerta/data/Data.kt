package cl.portaalerta.data

import android.content.Context
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Tipos de recordatorio. Los plazos por defecto se pueden cambiar al crear cada uno. */
enum class FollowupType(val label: String, val defaultMonths: Int, val defaultLeadDays: Int) {
    PORTABILITY("Portabilidad a WOM", 4, 7),
    INSURANCE("Seguro del equipo", 1, 3),
}

enum class FollowupStatus { OPEN, DONE }

/** Resultado al marcar un recordatorio como hecho; alimenta el resumen del mes. */
enum class FollowupOutcome(val label: String, val hint: String) {
    CLOSED("Cerrado", "Se portó o contrató"),
    REJECTED("No le interesó", "Lo descartó"),
    NO_ANSWER("Sin respuesta", "No contestó"),
}

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val currentCarrier: String = "",
    val plan: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Un recordatorio asociado a un cliente.
 * baseDate y dueDate son "epoch day" (días desde 1970-01-01); dueDate = baseDate + months.
 * La alarma suena [leadDays] días antes de dueDate y también el día de dueDate.
 */
@Entity(
    tableName = "followups",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clientId")],
)
data class Followup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val type: FollowupType,
    val baseDate: Long,
    val months: Int,
    val dueDate: Long,
    val leadDays: Int,
    val detail: String = "",
    val status: FollowupStatus = FollowupStatus.OPEN,
    val outcome: FollowupOutcome? = null,
    /** Momento (epoch millis) en que se marcó como hecho. */
    val doneAt: Long? = null,
)

/** Chip leído con el lector. La foto vive solo en el almacenamiento privado de la app. */
@Entity(
    tableName = "chip_scans",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clientId")],
)
data class ChipScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val iccid: String,
    val photoPath: String? = null,
    val clientId: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface AppDao {
    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE")
    fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE")
    suspend fun clientsNow(): List<Client>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun client(id: Long): Client?

    @Query("SELECT * FROM clients WHERE id = :id")
    fun clientFlow(id: Long): Flow<Client?>

    @Insert
    suspend fun insertClient(c: Client): Long

    @Update
    suspend fun updateClient(c: Client)

    @Delete
    suspend fun deleteClient(c: Client)

    @Query("SELECT * FROM followups WHERE clientId = :clientId ORDER BY CASE status WHEN 'OPEN' THEN 0 ELSE 1 END, dueDate")
    fun followupsFor(clientId: Long): Flow<List<Followup>>

    @Query("SELECT * FROM followups WHERE clientId = :clientId")
    suspend fun followupsNow(clientId: Long): List<Followup>

    @Query("SELECT * FROM followups WHERE status = 'OPEN' ORDER BY dueDate")
    fun openFollowups(): Flow<List<Followup>>

    @Query("SELECT * FROM followups WHERE status = 'OPEN'")
    suspend fun openFollowupsNow(): List<Followup>

    @Query("SELECT * FROM followups ORDER BY dueDate")
    fun followupsFlow(): Flow<List<Followup>>

    @Query("SELECT * FROM followups ORDER BY dueDate")
    suspend fun allFollowups(): List<Followup>

    @Query("SELECT * FROM chip_scans ORDER BY createdAt DESC")
    fun scansFlow(): Flow<List<ChipScan>>

    @Query("SELECT * FROM chip_scans")
    suspend fun scansAll(): List<ChipScan>

    @Query("SELECT * FROM chip_scans WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun scansFor(clientId: Long): Flow<List<ChipScan>>

    @Query("SELECT * FROM chip_scans WHERE clientId = :clientId")
    suspend fun scansNow(clientId: Long): List<ChipScan>

    @Query("SELECT * FROM chip_scans WHERE id = :id")
    suspend fun scan(id: Long): ChipScan?

    @Query("SELECT * FROM chip_scans WHERE iccid = :iccid LIMIT 1")
    suspend fun scanByIccid(iccid: String): ChipScan?

    @Insert
    suspend fun insertScan(s: ChipScan): Long

    @Update
    suspend fun updateScan(s: ChipScan)

    @Delete
    suspend fun deleteScan(s: ChipScan)

    @Query("DELETE FROM chip_scans WHERE clientId = :clientId")
    suspend fun deleteScansFor(clientId: Long)

    @Query("SELECT * FROM followups WHERE id = :id")
    suspend fun followup(id: Long): Followup?

    @Insert
    suspend fun insertFollowup(f: Followup): Long

    @Update
    suspend fun updateFollowup(f: Followup)

    @Delete
    suspend fun deleteFollowup(f: Followup)
}

@Database(entities = [Client::class, Followup::class, ChipScan::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Base nueva y cifrada. La anterior (sin cifrar, solo de pruebas) se elimina. */
        private const val DB_NAME = "portaalerta_secure.db"

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(ctx: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            ctx.deleteDatabase("portaalerta.db")
            // Si la clave se perdió, lo que hubiera en la base ya no se puede leer: se parte de cero.
            val secret = SecureKeys.secret(ctx, "db") { ctx.deleteDatabase(DB_NAME) }
            val passphrase = Base64.encodeToString(secret, Base64.NO_WRAP).toByteArray()
            return Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
