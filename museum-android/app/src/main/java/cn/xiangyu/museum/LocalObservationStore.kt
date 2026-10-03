package cn.xiangyu.museum

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class LocalObservation(val id: String, val relicId: String, val note: String,
                            val photoName: String?, val createdAt: Long)

fun localObservationScope(key: ProgressKey): String = MessageDigest.getInstance("SHA-256")
    .digest("${key.endpoint}\u0000${key.username}\u0000${key.audience.apiValue}".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

class SqliteLocalObservationStore(context: Context, name: String = "museum_observations.db",
                                  private val photoRoot: File = File(context.filesDir, "journal-photos")) : AutoCloseable {
    private val captureRoot = File(context.cacheDir, "journal-capture").canonicalFile
    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE observations(scope TEXT NOT NULL,id TEXT NOT NULL,relic_id TEXT NOT NULL," +
                "note TEXT NOT NULL,photo_name TEXT,created_at INTEGER NOT NULL,PRIMARY KEY(scope,id))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun validId(id: String) = runCatching { UUID.fromString(id).toString().equals(id, ignoreCase = true) }.getOrDefault(false)
    private fun read(db: SQLiteDatabase, key: ProgressKey, id: String? = null): List<LocalObservation> {
        val scope = localObservationScope(key)
        val where = if (id == null) "scope=?" else "scope=? AND id=?"
        val args = if (id == null) arrayOf(scope) else arrayOf(scope, id)
        return db.rawQuery("SELECT id,relic_id,note,photo_name,created_at FROM observations WHERE $where ORDER BY created_at DESC,id DESC", args).use {
            buildList {
                while (it.moveToNext()) add(LocalObservation(it.getString(0), it.getString(1), it.getString(2),
                    if (it.isNull(3)) null else it.getString(3), it.getLong(4)))
            }
        }
    }

    suspend fun load(key: ProgressKey): List<LocalObservation> = withContext(Dispatchers.IO) {
        synchronized(helper) { read(helper.readableDatabase, key) }
    }

    private fun ownedPhoto(key: ProgressKey, name: String): File {
        val root = photoRoot.canonicalFile
        val directory = File(root, localObservationScope(key)).canonicalFile
        require(directory.parentFile == root)
        return File(directory, name).canonicalFile.also { require(it.parentFile == directory) }
    }

    fun photo(key: ProgressKey, record: LocalObservation): File? {
        val name = record.photoName ?: return null
        if (!validId(record.id) || name != "${record.id}.jpg") return null
        return runCatching { ownedPhoto(key, name).takeIf { it.isFile } }.getOrNull()
    }

    suspend fun save(key: ProgressKey, id: String, relicId: String, note: String,
                     capture: File? = null): LocalObservation = withContext(Dispatchers.IO) {
        require(validId(id))
        require(FirstChapter.relics.any { it.id == relicId })
        val text = note.trim()
        require(text.length in 1..600)
        synchronized(helper) {
            val db = helper.writableDatabase
            var copied: File? = null
            db.beginTransaction()
            try {
                val existing = read(db, key, id).firstOrNull()
                val result = if (existing != null) existing else {
                    if (capture != null) {
                        val source = capture.canonicalFile
                        require(source.parentFile == captureRoot && source.isFile &&
                            source.extension.lowercase() in setOf("jpg", "jpeg"))
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(source.path, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == "image/jpeg")
                        val destination = ownedPhoto(key, "$id.jpg")
                        check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                        require(!destination.exists())
                        copied = destination
                        source.copyTo(destination)
                    }
                    val record = LocalObservation(id, relicId, text, copied?.name, System.currentTimeMillis())
                    db.insertOrThrow("observations", null, ContentValues().apply {
                        put("scope", localObservationScope(key)); put("id", record.id)
                        put("relic_id", record.relicId); put("note", record.note)
                        put("photo_name", record.photoName); put("created_at", record.createdAt)
                    })
                    record
                }
                db.setTransactionSuccessful()
                result
            } catch (error: Exception) {
                copied?.delete()
                throw error
            } finally { db.endTransaction() }
        }
    }

    suspend fun delete(key: ProgressKey, id: String): Boolean = withContext(Dispatchers.IO) {
        require(validId(id))
        synchronized(helper) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val record = read(db, key, id).firstOrNull()
                val deleted = if (record == null) false else {
                    check(db.delete("observations", "scope=? AND id=?", arrayOf(localObservationScope(key), id)) == 1)
                    photo(key, record)?.let { check(it.delete()) }
                    true
                }
                db.setTransactionSuccessful()
                deleted
            } finally { db.endTransaction() }
        }
    }

    override fun close() = synchronized(helper) { helper.close() }
}
