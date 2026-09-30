package com.minimaldesigner.arise.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Progress photos as files in app-private storage (filesDir/photos). Every photo is
 * stored upright (EXIF orientation applied), at most 2048 px on its long side, with a
 * 480 px thumbnail for the grid.
 */
class PhotoStore(context: Context) {
    private val resolver = context.contentResolver
    val dir: File = File(context.filesDir, "photos").apply { mkdirs() }

    data class Saved(val file: String, val thumb: String)

    fun file(name: String) = File(dir, name)

    /** Copies the image at [uri] into the store as `<id>.jpg` + `<id>_t.jpg`. */
    suspend fun save(uri: Uri, id: String): Saved = saveFrom(id) { resolver.openInputStream(uri) }

    /** The same, from a file (a photo unpacked from a backup or 75 Hard zip). */
    suspend fun save(file: File, id: String): Saved = saveFrom(id) { file.inputStream() }

    /** [open] must return a fresh stream each call: the image is read three times. */
    private suspend fun saveFrom(id: String, open: () -> InputStream?): Saved = withContext(Dispatchers.IO) {
        val full = decodeUpright(open, MAX_SIDE)
        val thumb = scaleDown(full, THUMB_SIDE)
        val saved = Saved("$id.jpg", "${id}_t.jpg")
        write(full, file(saved.file))
        write(thumb, file(saved.thumb))
        if (thumb !== full) thumb.recycle()
        full.recycle()
        saved
    }

    fun delete(vararg names: String) {
        names.forEach { file(it).delete() }
    }

    private fun decodeUpright(open: () -> InputStream?, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("That file isn't a photo ARISE can read.")

        // Decode at a power-of-two size no smaller than needed, then scale exactly.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = open().use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("That file isn't a photo ARISE can read.")

        val orientation = runCatching {
            open().use { s -> s?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val upright = rotate(raw, orientation)
        return scaleDown(upright, maxSide).also { if (it !== upright) upright.recycle() }
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxSide) return src
        val f = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
    }

    private fun write(bmp: Bitmap, to: File) {
        val tmp = File(to.path + ".part")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(to)) throw IOException("Couldn't save the photo.")
    }

    companion object {
        const val MAX_SIDE = 2048
        const val THUMB_SIDE = 480
    }
}
