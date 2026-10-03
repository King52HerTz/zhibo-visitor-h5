package cn.xiangyu.museum

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

class JournalPhotoProvider : FileProvider()

internal data class JournalCapture(val id: String, val scope: String, val draftId: String,
    val relicId: String, val note: String, val fileName: String, val result: String = "waiting")

internal class LocalJournalCamera(private val context: Context,
    preferencesName: String = "journal_camera") {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val directory = File(context.cacheDir, "journal-capture")

    @Synchronized fun pending(): JournalCapture? {
        val raw = preferences.getString("pending", null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            JournalCapture(value.getString("id"), value.getString("scope"), value.getString("draftId"),
                value.getString("relicId"), value.getString("note"), value.getString("fileName"), value.getString("result"))
        }.getOrNull()
    }

    private fun write(capture: JournalCapture) {
        val value = JSONObject().put("id", capture.id).put("scope", capture.scope)
            .put("draftId", capture.draftId).put("relicId", capture.relicId).put("note", capture.note)
            .put("fileName", capture.fileName).put("result", capture.result)
        check(preferences.edit().putString("pending", value.toString()).commit())
    }

    @Synchronized fun begin(key: ProgressKey, draftId: String, relicId: String, note: String): JournalCapture {
        pending()?.let { discard(it.id) }
        check(directory.isDirectory || directory.mkdirs())
        val id = UUID.randomUUID().toString()
        val capture = JournalCapture(id, localObservationScope(key), draftId, relicId, note, "$id.jpg")
        val file = requireNotNull(file(capture))
        check(file.createNewFile())
        try { write(capture) } catch (error: Exception) { file.delete(); throw error }
        return capture
    }

    fun file(capture: JournalCapture): File? {
        if (!capture.fileName.matches(Regex("[a-f0-9-]{36}\\.jpg"))) return null
        val file = File(directory, capture.fileName)
        return file.takeIf { it.canonicalFile.parentFile == directory.canonicalFile }
    }

    fun uri(capture: JournalCapture): Uri = FileProvider.getUriForFile(context,
        "${context.packageName}.journal-camera", requireNotNull(file(capture)))

    @Synchronized fun finish(id: String, success: Boolean): JournalCapture? {
        val capture = pending()?.takeIf { it.id == id && it.result == "waiting" } ?: return null
        context.revokeUriPermission(uri(capture), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val ready = success && file(capture)?.let { journalPhotoReadable(it) } == true
        val result = capture.copy(result = if (ready) "ready" else "cancelled")
        write(result)
        if (!ready) file(capture)?.delete()
        return result
    }

    @Synchronized fun discard(id: String) {
        val capture = pending()?.takeIf { it.id == id } ?: return
        context.revokeUriPermission(uri(capture), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        file(capture)?.delete()
        check(preferences.edit().remove("pending").commit())
    }
}

internal fun journalPhotoReadable(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    return options.outWidth > 0 && options.outHeight > 0
}

internal fun readJournalPhoto(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val transform = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (transform.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
