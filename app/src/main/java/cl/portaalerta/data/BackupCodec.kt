package cl.portaalerta.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Contenido de un respaldo: clientes, recordatorios, lecturas de chip y (opcional) sus fotos en JPEG. */
data class BackupBundle(
    val clients: List<Client>,
    val followups: List<Followup>,
    val scans: List<ChipScan>,
    val photos: Map<Long, ByteArray>,
)

/** Convierte el contenido a un ZIP en memoria (data.json + fotos) y de vuelta. Se cifra aparte con [BackupCrypto]. */
object BackupCodec {
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

    fun encode(b: BackupBundle): ByteArray {
        val root = JSONObject().put("v", 1)
        root.put("clients", JSONArray().also { arr ->
            b.clients.forEach { c ->
                arr.put(
                    JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone)
                        .put("carrier", c.currentCarrier).put("notes", c.notes).put("createdAt", c.createdAt),
                )
            }
        })
        root.put("followups", JSONArray().also { arr ->
            b.followups.forEach { f ->
                arr.put(
                    JSONObject().put("id", f.id).put("clientId", f.clientId).put("type", f.type.name)
                        .put("baseDate", f.baseDate).put("months", f.months).put("dueDate", f.dueDate)
                        .put("leadDays", f.leadDays).put("detail", f.detail).put("status", f.status.name)
                        .put("outcome", f.outcome?.name ?: JSONObject.NULL).put("doneAt", f.doneAt ?: JSONObject.NULL),
                )
            }
        })
        root.put("scans", JSONArray().also { arr ->
            b.scans.forEach { s ->
                arr.put(
                    JSONObject().put("id", s.id).put("iccid", s.iccid).put("clientId", s.clientId ?: JSONObject.NULL)
                        .put("note", s.note).put("createdAt", s.createdAt).put("hasPhoto", b.photos.containsKey(s.id)),
                )
            }
        })
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            b.photos.forEach { (scanId, jpeg) ->
                zip.putNextEntry(ZipEntry("photos/$scanId.jpg"))
                zip.write(jpeg)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun decode(zipBytes: ByteArray): BackupBundle {
        var json: String? = null
        val photos = HashMap<Long, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_TOTAL_BYTES) throw InvalidBackupException("Respaldo demasiado grande o dañado")
                    buf.write(chunk, 0, n)
                }
                val name = entry.name
                if (name == "data.json") {
                    json = buf.toString(Charsets.UTF_8.name())
                } else if (name.startsWith("photos/") && name.endsWith(".jpg")) {
                    name.removePrefix("photos/").removeSuffix(".jpg").toLongOrNull()?.let { photos[it] = buf.toByteArray() }
                }
            }
        }
        val root = JSONObject(json ?: throw InvalidBackupException("Respaldo sin datos"))
        val clients = root.getJSONArray("clients").objects().map {
            Client(
                id = it.getLong("id"), name = it.getString("name"), phone = it.getString("phone"),
                currentCarrier = it.optString("carrier", ""), notes = it.optString("notes", ""),
                createdAt = it.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        val followups = root.getJSONArray("followups").objects().mapNotNull {
            val type = runCatching { FollowupType.valueOf(it.getString("type")) }.getOrNull() ?: return@mapNotNull null
            val status = runCatching { FollowupStatus.valueOf(it.getString("status")) }.getOrNull() ?: return@mapNotNull null
            Followup(
                id = it.getLong("id"), clientId = it.getLong("clientId"), type = type,
                baseDate = it.getLong("baseDate"), months = it.getInt("months"), dueDate = it.getLong("dueDate"),
                leadDays = it.getInt("leadDays"), detail = it.optString("detail", ""), status = status,
                outcome = if (it.isNull("outcome")) null else runCatching { FollowupOutcome.valueOf(it.getString("outcome")) }.getOrNull(),
                doneAt = if (it.isNull("doneAt")) null else it.getLong("doneAt"),
            )
        }
        val scans = root.getJSONArray("scans").objects().map {
            ChipScan(
                id = it.getLong("id"), iccid = it.getString("iccid"),
                clientId = if (it.isNull("clientId")) null else it.getLong("clientId"),
                note = it.optString("note", ""), createdAt = it.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        return BackupBundle(clients, followups, scans, photos)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
