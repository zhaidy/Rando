package com.personal.rando.ui

import android.content.ContentValues
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
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.places.SavedPlace
import com.personal.rando.rando
import com.personal.rando.ride.Ride
import com.personal.rando.ride.SharePrivacy
import com.personal.rando.ride.timeOfDay
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

    /**
     * 画好的分享图：先给人看一眼，再分享或者存到相册。
     * [privacy] 是预览里开关的名字：收藏了家是「隐藏家附近」，没收藏是剪掉起点终点
     */
    class Card(val bitmap: Bitmap, val name: String, val chooserTitle: String, val privacy: String)

    /** 一次骑行的分享图；[hide] 打开时家附近的轨迹不画（见 [SharePrivacy]） */
    suspend fun card(context: Context, ride: Ride, record: String?, hide: Boolean = true): Card =
        render(context, "rando-${ride.id}", tr("分享骑行", "Share ride"), listOf(ride), hide) { map, tracks -> draw(map, tracks.first(), ride, record) }

    /** 今天骑的几次：轨迹画在一张图上，数据加起来 */
    suspend fun dayCard(context: Context, rides: List<Ride>, hide: Boolean = true): Card? {
        if (rides.isEmpty()) return null
        return render(context, "rando-day-${rides.first().id}", tr("分享今天的骑行", "Share today's rides"), rides, hide) { map, tracks -> drawDay(map, tracks, rides) }
    }

    /** 拍地图、画图；每次骑行可能被隐私圈断成几段，所以是 骑行 → 段 → 点 */
    private suspend fun render(
        context: Context,
        name: String,
        chooserTitle: String,
        rides: List<Ride>,
        hide: Boolean,
        paint: (Bitmap?, List<List<List<PointF>>>) -> Bitmap,
    ): Card {
        val home = context.rando.savedPlaces.places.value.firstOrNull { it.name == SavedPlace.HOME }?.location
        val zone = if (hide && home != null) SharePrivacy.zone(home, SharePrivacy.seed(context)) else null
        val rideTracks = rides.map { r ->
            val track = r.points.map { it.location }
            if (hide) SharePrivacy.hide(track, zone) else listOf(track)
        }
        val pieces = rideTracks.flatten()
        // 地图只框住画出来的那些，不框到家
        val snapshot = withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshot(context, pieces.flatten()) }
        val bitmap = withContext(Dispatchers.Default) {
            val piecesPx = snapshot?.second?.let { project -> pieces.map { it.map(project) } } ?: fitAll(pieces, WIDTH.toFloat(), MAP_HEIGHT.toFloat())
            var from = 0
            val ridesPx = rideTracks.map { r -> piecesPx.subList(from, from + r.size).also { from += r.size } }
            paint(snapshot?.first, ridesPx)
        }
        return Card(bitmap, name, chooserTitle, if (home != null) tr("隐藏家附近", "Hide near home") else tr("隐藏起点终点附近", "Hide start and finish"))
    }

    /** 存到缓存，弹出系统分享 */
    suspend fun send(context: Context, card: Card) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                File(dir, "${card.name}.png").also { f -> f.outputStream().use { card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, card.chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * 存到相册的 Pictures/Rando 里；存好返回 true。
     * Android 10 以上不用权限，9 以下要先拿到「存储」权限
     */
    suspend fun save(context: Context, card: Card): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${card.name}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rando")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                try {
                    resolver.openOutputStream(uri)?.use { card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error(tr("打不开 $uri", "Can't open $uri"))
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Rando").apply { mkdirs() }
                val f = File(dir, fileName)
                f.outputStream().use { card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(context, arrayOf(f.path), arrayOf("image/png"), null)
            }
            true
        }.getOrDefault(false)
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
            // 地图自带的版权字太大，盖在地图上；改成卡片底下一行小字
            .withAttribution(false)
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

    /** 几条轨迹一起缩放（同一个比例），再按原来的条数分开 */
    fun fitAll(tracks: List<List<LatLon>>, w: Float, h: Float): List<List<PointF>> {
        val all = fit(tracks.flatten(), w, h)
        var from = 0
        return tracks.map { t -> all.subList(from, from + t.size).also { from += t.size } }
    }

    /** 一次骑行的整张图；[trackPx] 是这次骑行要画的几段 */
    fun draw(map: Bitmap?, trackPx: List<List<PointF>>, ride: Ride, record: String?): Bitmap =
        drawCard(
            map, listOf(trackPx), ride.title, DATE.format(Date(ride.startedAt)), record,
            ride.distanceMeters, ride.movingSeconds, ride.ascendMeters,
        )

    /** 今天几次骑行合在一张图上：标题写骑了几次，下面是合计 */
    fun drawDay(map: Bitmap?, tracksPx: List<List<List<PointF>>>, rides: List<Ride>): Bitmap {
        val first = rides.first()
        val last = rides.last()
        val title = if (rides.size == 1) tr("今天${first.title}", "Today: ${first.title}") else tr("今天骑了 ${rides.size} 次", "${rides.size} rides today")
        val time = if (rides.size == 1) {
            "${CLOCK.format(Date(first.startedAt))}–${CLOCK.format(Date(first.endedAt))}"
        } else {
            // 「早上 · 傍晚」；两次都在早上就写几点到几点
            rides.map { timeOfDay(it.startedAt) }.takeIf { it.distinct().size == it.size && it.size <= 4 }?.joinToString(" · ")
                ?: "${CLOCK.format(Date(first.startedAt))}–${CLOCK.format(Date(last.endedAt))}"
        }
        return drawCard(
            map, tracksPx, title, DAY.format(Date(first.startedAt)), time,
            rides.sumOf { it.distanceMeters }, rides.sumOf { it.movingSeconds }, rides.sumOf { it.ascendMeters },
        )
    }

    /** 画整张图；[note] 是标题下面绿色的那行（和以前比、几点骑的） */
    private fun drawCard(
        map: Bitmap?,
        tracksPx: List<List<List<PointF>>>,
        title: String,
        date: String,
        note: String?,
        meters: Double,
        movingSeconds: Long,
        ascendMeters: Double,
    ): Bitmap {
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
        tracksPx.forEach { drawTrack(c, it) }

        val ink = Color.parseColor("#1F2A24")
        val soft = Color.parseColor("#6B756F")
        val green = Color.parseColor("#2E7D4F")
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val left = 72f
        var y = MAP_HEIGHT + 40f
        c.drawText(title, left, y, text(56f, ink, bold))
        c.drawText(date, WIDTH - 72f, y, text(32f, soft).apply { textAlign = Paint.Align.RIGHT })
        if (note != null) {
            y += 62f
            c.drawText(note, left, y, text(34f, green, bold))
        }
        y += 140f
        val stats = listOf(
            Format.distance(meters.toInt()) to tr("距离", "Distance"),
            Format.clock(movingSeconds) to tr("骑行时间", "Moving time"),
            tr("${ascendMeters.toInt()} 米", "${ascendMeters.toInt()} m") to tr("爬升", "Climb"),
            Format.speed(if (movingSeconds > 0) meters / movingSeconds else 0.0) to tr("均速", "Avg speed"),
        )
        val big = text(64f, ink, bold)
        val small = text(28f, ink)
        val caption = text(28f, soft)
        // 按实际宽度排：第一个贴左边、最后一个贴右边，中间空隙一样大；超过一小时的时间也不会压到旁边
        val widths = stats.map { (value, label) ->
            val number = value.substringBefore(' ')
            val unit = value.substringAfter(' ', "")
            maxOf(big.measureText(number) + if (unit.isEmpty()) 0f else small.measureText(" $unit"), caption.measureText(label))
        }
        val gap = ((WIDTH - 2 * left) - widths.sum()) / (stats.size - 1)
        var x = left
        stats.forEachIndexed { i, (value, label) ->
            val number = value.substringBefore(' ')
            val unit = value.substringAfter(' ', "")
            c.drawText(number, x, y, big)
            if (unit.isNotEmpty()) c.drawText(" $unit", x + big.measureText(number), y, small)
            c.drawText(label, x, y + 50f, caption)
            x += widths[i] + gap
        }
        if (map != null) c.drawText(tr("地图 © OpenStreetMap", "Map © OpenStreetMap"), left, HEIGHT - 52f, text(16f, Color.parseColor("#C6CAC7")))
        c.drawText("Rando", WIDTH - 72f, HEIGHT - 48f, text(34f, green, bold).apply { textAlign = Paint.Align.RIGHT })
        return out
    }

    /** 一次骑行：几段线，起点终点各一个圆点（中间断开的地方不加点） */
    private fun drawTrack(c: Canvas, pieces: List<List<PointF>>) {
        val drawn = pieces.filter { it.size >= 2 }
        if (drawn.isEmpty()) return
        val path = Path().apply {
            drawn.forEach { pts ->
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
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
        pin(drawn.first().first(), "#2E7D4F")
        pin(drawn.last().last(), "#E0672B")
    }

    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
    }

    // 每次按现在的语言新建：切了语言日期也跟着换
    private val DATE get() = SimpleDateFormat(tr("yyyy年M月d日 HH:mm", "d MMM yyyy, HH:mm"), Lang.locale)
    private val DAY get() = SimpleDateFormat(tr("yyyy年M月d日 EEE", "EEE d MMM yyyy"), Lang.locale)
    private val CLOCK get() = SimpleDateFormat("HH:mm", Lang.locale)
    private const val SNAPSHOT_TIMEOUT_MS = 15_000L
}
