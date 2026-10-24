package com.personal.rando.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.personal.rando.ride.Ride
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos

/**
 * 骑完的分享图：上面是地图和轨迹，下面是距离、时间、爬升、均速。
 * 1080 × 1350（4:5，朋友圈、小红书都不裁）
 */
object ShareCard {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    const val MAP_HEIGHT = 900

    /** 拍地图、画图、存到缓存，弹出系统分享 */
    suspend fun share(context: Context, ride: Ride, record: String?) {
        val track = ride.points.map { it.location }
        val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshot(context, track) }
        val bitmap = withContext(Dispatchers.Default) {
            val trackPx = snapshot?.second?.let { project -> track.map(project) } ?: fit(track, WIDTH.toFloat(), MAP_HEIGHT.toFloat())
            draw(snapshot?.first, trackPx, ride, record)
        }
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                File(dir, "rando-${ride.id}.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "分享骑行").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 地图快照：框住整条轨迹；拍不到（没网）返回 null，就画在纯色底上 */
    private suspend fun snapshot(context: Context, track: List<LatLon>): Pair<Bitmap, (LatLon) -> PointF>? {
        if (track.size < 2) return null
        val bounds = LatLngBounds.Builder().includes(track.map { LatLng(it.lat, it.lon) }).build()
        // 四周留点边：轨迹不贴着图的边
        val padLat = (bounds.latitudeSpan * 0.18).coerceAtLeast(0.002)
        val padLon = (bounds.longitudeSpan * 0.18).coerceAtLeast(0.002)
        val region = LatLngBounds.from(
            bounds.latitudeNorth + padLat, bounds.longitudeEast + padLon,
            bounds.latitudeSouth - padLat, bounds.longitudeWest - padLon,
        )
        val options = MapSnapshotter.Options(WIDTH, MAP_HEIGHT)
            .withStyleBuilder(Style.Builder().fromUri(MapLayers.STYLE_URL))
            .withRegion(region)
            .withPixelRatio(1f)
            .withLogo(false)
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val snapshotter = MapSnapshotter(context, options)
                cont.invokeOnCancellation { snapshotter.cancel() }
                snapshotter.start(
                    { s -> if (cont.isActive) cont.resume(s.bitmap to { p: LatLon -> s.pixelForLatLng(LatLng(p.lat, p.lon)) }) },
                    { if (cont.isActive) cont.resume(null) },
                )
            }
        }
    }

    /** 没有地图时把轨迹缩放到 [w]×[h] 里，四周留白 */
    fun fit(track: List<LatLon>, w: Float, h: Float): List<PointF> {
        if (track.isEmpty()) return emptyList()
        val k = cos(Math.toRadians(track.first().lat))
        val xs = track.map { it.lon * k }
        val ys = track.map { it.lat }
        val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-6)
        val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-6)
        val margin = 0.12f
        val scale = minOf(w * (1 - 2 * margin) / spanX, h * (1 - 2 * margin) / spanY)
        val ox = (w - spanX * scale) / 2
        val oy = (h - spanY * scale) / 2
        return track.indices.map { i -> PointF((ox + (xs[i] - xs.min()) * scale).toFloat(), (oy + (ys.max() - ys[i]) * scale).toFloat()) }
    }

    /** 画整张图 */
    fun draw(map: Bitmap?, trackPx: List<PointF>, ride: Ride, record: String?): Bitmap {
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.parseColor("#F2F0EB"))
        if (map != null) {
            c.drawBitmap(map, null, RectF(0f, 0f, WIDTH.toFloat(), MAP_HEIGHT.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawRect(0f, 0f, WIDTH.toFloat(), MAP_HEIGHT.toFloat(), Paint().apply { color = Color.parseColor("#E4EDE6") })
        }
        // 地图底下渐变到卡片的底色，接得自然
        c.drawRect(
            0f, MAP_HEIGHT - 120f, WIDTH.toFloat(), MAP_HEIGHT.toFloat(),
            Paint().apply {
                shader = LinearGradient(0f, MAP_HEIGHT - 120f, 0f, MAP_HEIGHT.toFloat(), Color.TRANSPARENT, Color.parseColor("#F2F0EB"), Shader.TileMode.CLAMP)
            },
        )
        drawTrack(c, trackPx)

        val ink = Color.parseColor("#1F2A24")
        val soft = Color.parseColor("#6B756F")
        val green = Color.parseColor("#2E7D4F")
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val left = 72f
        var y = MAP_HEIGHT + 40f
        c.drawText(ride.title, left, y, text(56f, ink, bold))
        c.drawText(DATE.format(Date(ride.startedAt)), WIDTH - 72f, y, text(32f, soft).apply { textAlign = Paint.Align.RIGHT })
        if (record != null) {
            y += 62f
            c.drawText(record, left, y, text(34f, green, bold))
        }
        y += 140f
        val stats = listOf(
            Format.distance(ride.distanceMeters.toInt()) to "距离",
            Format.clock(ride.movingSeconds) to "骑行时间",
            "${ride.ascendMeters.toInt()} 米" to "爬升",
            Format.speed(ride.averageSpeed) to "均速",
        )
        val col = (WIDTH - 2 * left) / stats.size
        stats.forEachIndexed { i, (value, label) ->
            val x = left + col * i
            val number = value.substringBefore(' ')
            val unit = value.substringAfter(' ', "")
            val big = text(64f, ink, bold)
            c.drawText(number, x, y, big)
            if (unit.isNotEmpty()) c.drawText(" $unit", x + big.measureText(number), y, text(28f, ink))
            c.drawText(label, x, y + 50f, text(28f, soft))
        }
        c.drawText("Rando", WIDTH - 72f, HEIGHT - 48f, text(34f, green, bold).apply { textAlign = Paint.Align.RIGHT })
        return out
    }

    private fun drawTrack(c: Canvas, pts: List<PointF>) {
        if (pts.size < 2) return
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, stroke.apply { color = Color.WHITE; strokeWidth = 16f })
        c.drawPath(path, stroke.apply { color = Color.parseColor("#E0672B"); strokeWidth = 9f })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        fun pin(p: PointF, color: String) {
            c.drawCircle(p.x, p.y, 16f, dot.apply { this.color = Color.WHITE })
            c.drawCircle(p.x, p.y, 11f, dot.apply { this.color = Color.parseColor(color) })
        }
        pin(pts.first(), "#2E7D4F")
        pin(pts.last(), "#E0672B")
    }

    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
    }

    private val DATE = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
    private const val SNAPSHOT_TIMEOUT_MS = 15_000L
}
