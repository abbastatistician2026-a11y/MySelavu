package com.myselavu.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock
import com.myselavu.core.backup.BackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** What a backup file contains, shown before restoring it. */
data class BackupPreview(
    val createdAt: Long,
    val expenseCount: Int,
    val monthCount: Int,
    val json: JSONObject,
)

/**
 * Full backup and restore. The backup is ONE small file (compressed, optionally locked with a
 * password) that you save wherever you like through Android's own "Save" screen, for example a
 * Google Drive folder. The app itself never uses the internet.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    companion object {
        const val FORMAT = 1
        private val SKIP = setOf("android_metadata", "sqlite_sequence", "room_master_table")
    }

    private fun tableNames(): List<String> {
        val out = ArrayList<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                if (name !in SKIP && !name.startsWith("sqlite_")) out += name
            }
        }
        return out
    }

    /** Column name → declared SQL type ("INTEGER", "TEXT", "REAL", "BLOB"). */
    private fun columns(table: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val typeIdx = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) out[c.getString(nameIdx)] = c.getString(typeIdx).uppercase()
        }
        return out
    }

    /** Writes the backup file. Returns its size in bytes. */
    suspend fun createBackup(out: OutputStream, password: String?): Int = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "MySelavu")
        root.put("format", FORMAT)
        root.put("dbVersion", db.openHelper.readableDatabase.version)
        root.put("createdAt", clock.millis())
        root.put("settings", JSONObject(settings.exportForBackup()))
        val tables = JSONObject()
        for (table in tableNames()) {
            val rows = JSONArray()
            db.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val v: Any = when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP)
                            else -> c.getString(i)
                        }
                        row.put(c.getColumnName(i), v)
                    }
                    rows.put(row)
                }
            }
            tables.put(table, rows)
        }
        root.put("tables", tables)
        val bytes = BackupCodec.encode(root.toString(), password?.takeIf { it.isNotEmpty() }?.toCharArray())
        out.write(bytes)
        out.flush()
        settings.setLastBackupAt(clock.millis())
        bytes.size
    }

    fun isEncrypted(bytes: ByteArray) = BackupCodec.isEncrypted(bytes)

    /** Reads and checks a backup file. Throws BackupCodec exceptions on wrong password / bad file. */
    suspend fun readBackup(bytes: ByteArray, password: String?): BackupPreview = withContext(Dispatchers.Default) {
        val json = try {
            JSONObject(BackupCodec.decode(bytes, password?.toCharArray()))
        } catch (e: org.json.JSONException) {
            throw BackupCodec.NotABackupException()
        }
        if (json.optString("app") != "MySelavu" || !json.has("tables")) throw BackupCodec.NotABackupException()
        // A backup from a newer app version can't be restored by an older app.
        if (json.optInt("dbVersion", 1) > db.openHelper.readableDatabase.version) throw BackupCodec.NotABackupException()
        val tables = json.getJSONObject("tables")
        BackupPreview(
            createdAt = json.optLong("createdAt"),
            expenseCount = tables.optJSONArray("expense")?.length() ?: 0,
            monthCount = tables.optJSONArray("budget_period")?.length() ?: 0,
            json = json,
        )
    }

    suspend fun readAll(input: InputStream): ByteArray = withContext(Dispatchers.IO) { input.use { it.readBytes() } }

    /** Replaces ALL current data with the backup's data. */
    suspend fun restore(preview: BackupPreview) {
        val json = preview.json
        val tables = json.getJSONObject("tables")
        db.withTransaction {
            val sdb = db.openHelper.writableDatabase
            sdb.execSQL("PRAGMA defer_foreign_keys = ON")
            for (table in tableNames()) {
                sdb.delete(table, null, null)
                val rows = tables.optJSONArray(table) ?: continue
                val cols = columns(table)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val cv = ContentValues()
                    for ((name, type) in cols) {
                        if (!row.has(name) || row.isNull(name)) { cv.putNull(name); continue }
                        when {
                            type.contains("INT") -> cv.put(name, row.getLong(name))
                            type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> cv.put(name, row.getDouble(name))
                            type.contains("BLOB") -> cv.put(name, Base64.decode(row.getString(name), Base64.NO_WRAP))
                            else -> cv.put(name, row.getString(name))
                        }
                    }
                    sdb.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
        val s = json.optJSONObject("settings")
        val map = HashMap<String, Any?>()
        s?.keys()?.forEach { k -> map[k] = s.get(k) }
        settings.importFromBackup(map)
    }

    /** Size of the app's data on the phone (database files). */
    fun dataSizeBytes(): Long {
        val f = context.getDatabasePath(AppDatabase.NAME)
        return listOf(f, java.io.File(f.path + "-wal"), java.io.File(f.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
    }

    /** Shrinks the database file after deleting data. */
    suspend fun compact() = withContext(Dispatchers.IO) {
        runCatching {
            val sdb = db.openHelper.writableDatabase
            sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            sdb.execSQL("VACUUM")
        }
    }
}
