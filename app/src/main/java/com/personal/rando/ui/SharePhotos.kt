package com.personal.rando.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.personal.rando.ride.Ride
import com.personal.rando.ride.RidePhotos
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一张要放进分享图的照片：[at] 是按拍照时间对到轨迹上的位置，对不上是 null（只随原图发，不钉在图上）。
 * 相机拍完导进手机的，时间对不上，可以在预览里自己拖到图上的位置
 */
class SharePhoto(val uri: Uri, val takenAt: Long?, val at: LatLon?, val thumb: Bitmap) {
    /** 拖到图上 [place] 之后的这张 */
    fun movedTo(place: LatLon) = SharePhoto(uri, takenAt, place, thumb)
}

/** 从相册找骑行时拍的照片、读缩略图、导出去掉位置信息的原图 */
object SharePhotos {
    /** 自动找骑行时拍的照片要读相册；Android 14 以上只允许部分照片也行 */
    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun canRead(context: Context): Boolean =
        permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** 这几次骑行时拍的照片，按时间排；截图和 Rando 自己存的分享图不算 */
    suspend fun during(context: Context, rides: List<Ride>): List<SharePhoto> = withContext(Dispatchers.IO) {
        val window = RidePhotos.window(rides) ?: return@withContext emptyList()
        if (!canRead(context)) return@withContext emptyList()
        val found = mutableListOf<Pair<Uri, Long>>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                "${MediaStore.Images.Media.DATE_TAKEN} BETWEEN ? AND ?",
                arrayOf(window.first.toString(), window.last.toString()),
                "${MediaStore.Images.Media.DATE_TAKEN} ASC",
            )?.use { c ->
                while (c.moveToNext() && found.size < MAX_FOUND) {
                    val bucket = c.getString(2).orEmpty()
                    if (bucket.equals("Screenshots", ignoreCase = true) || bucket == "Rando") continue
                    val taken = c.getLong(1)
                    if (RidePhotos.place(taken, rides) == null) continue
                    found.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) to taken)
                }
            }
        }
        found.mapNotNull { (uri, taken) -> thumbnail(context, uri)?.let { SharePhoto(uri, taken, RidePhotos.place(taken, rides), it) } }
    }

    /** 自己从相册挑的：读拍摄时间对到轨迹上；读不出缩略图返回 null */
    suspend fun picked(context: Context, uri: Uri, rides: List<Ride>): SharePhoto? = withContext(Dispatchers.IO) {
        val thumb = thumbnail(context, uri) ?: return@withContext null
        val taken = takenAt(context, uri)
        SharePhoto(uri, taken, taken?.let { RidePhotos.place(it, rides) }, thumb)
    }

    /** 先问相册（照片选择器给的地址也有这一列），没有再看照片里的 EXIF */
    private fun takenAt(context: Context, uri: Uri): Long? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0) && c.getLong(0) > 0) return c.getLong(0)
            }
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                text?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
            }
        }.getOrNull()
    }

    /** 预览和分享图上用的小图 */
    suspend fun thumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) { decode(context, uri, THUMB_SIDE) }

    /**
     * 原图一起发：重新压一遍 JPEG 存到缓存——照片里的 GPS 位置、手机型号这些 EXIF 都不带出去
     * （原图里记着在哪拍的，在家拍的就把家发出去了）。长边缩到 [ORIGINAL_SIDE]
     */
    suspend fun stripped(context: Context, uri: Uri, file: File): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decode(context, uri, ORIGINAL_SIDE) ?: return@withContext false
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }.isSuccess
    }

    /** 读成长边不超过 [side] 的图，按 EXIF 摆正；要画到软件画布上，所以不要硬件位图 */
    private fun decode(context: Context, uri: Uri, side: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val k = side.toFloat() / max(info.size.width, info.size.height)
                if (k < 1f) decoder.setTargetSize((info.size.width * k).roundToInt().coerceAtLeast(1), (info.size.height * k).roundToInt().coerceAtLeast(1))
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val k = (side.toFloat() / max(raw.width, raw.height)).coerceAtMost(1f)
            if (degrees == 0f && k == 1f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postScale(k, k); postRotate(degrees) }, true)
        }
    }.getOrNull()

    private const val MAX_FOUND = 24
    private const val THUMB_SIDE = 360
    private const val ORIGINAL_SIDE = 2048
}
