package cl.portaalerta.data

import android.content.Context
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

/** Tipos de recordatorio. Los plazos por defecto se pueden cambiar al crear cada uno. */
enum class FollowupType(val label: String, val defaultMonths: Int, val defaultLeadDays: Int) {
    PORTABILITY("Portabilidad a WOM", 4, 7),
    INSURANCE("Seguro del equipo", 1, 3),
}

enum class FollowupStatus { OPEN, DONE }

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

    @Query("SELECT * FROM followups WHERE clientId = :clientId ORDER BY status, dueDate")
    fun followupsFor(clientId: Long): Flow<List<Followup>>

    @Query("SELECT * FROM followups WHERE clientId = :clientId")
    suspend fun followupsNow(clientId: Long): List<Followup>

    @Query("SELECT * FROM followups WHERE status = 'OPEN' ORDER BY dueDate")
    fun openFollowups(): Flow<List<Followup>>

    @Query("SELECT * FROM followups WHERE status = 'OPEN'")
    suspend fun openFollowupsNow(): List<Followup>

    @Query("SELECT * FROM followups ORDER BY dueDate")
    suspend fun allFollowups(): List<Followup>

    @Query("SELECT * FROM followups WHERE id = :id")
    suspend fun followup(id: Long): Followup?

    @Insert
    suspend fun insertFollowup(f: Followup): Long

    @Update
    suspend fun updateFollowup(f: Followup)

    @Delete
    suspend fun deleteFollowup(f: Followup)
}

@Database(entities = [Client::class, Followup::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "portaalerta.db",
                ).build().also { instance = it }
            }
    }
}
