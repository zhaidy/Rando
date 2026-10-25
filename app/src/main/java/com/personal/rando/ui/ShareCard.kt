package com.personal.rando.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.places.SavedPlace
import com.personal.rando.rando
import com.personal.rando.ride.Ride
import com.personal.rando.ride.RidePhotos
import com.personal.rando.ride.RideStats
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
import kotlin.math.abs
import kotlin.math.cos

/**
 * 骑完的分享图：9:16（1080 × 1920），正好铺满 Instagram 快拍。
 * 地图铺满上大半张、四周渐隐到底色，轨迹橙到黄带光晕；下面是标题、大号距离和其余数据。
 * 快拍顶上（头像、进度条）和底下（回复栏）会被盖住，字和数都放在中间
 */
object ShareCard {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val MAP_HEIGHT = 1440

    /** 深色（默认，快拍里更显眼）还是浅色；预览里能切 */
    @Volatile var dark: Boolean = true

    /**
     * 画好的分享图：先给人看一眼，再分享或者存到相册。
     * [privacy] 是预览里开关的名字：收藏了家是「隐藏家附近」，没收藏是剪掉起点终点
     */
    class Card(
        val bitmap: Bitmap,
        val name: String,
        val chooserTitle: String,
        val privacy: String,
        private val track: List<Pair<PointF, LatLon>> = emptyList(),
        private val pins: List<Pair<Uri, PointF>> = emptyList(),
    ) {
        /** 图上 [p]（分享图的像素）最近的轨迹点：照片拖到哪，就钉在那附近画出来的路上；一段路都没画返回 null */
        fun nearest(p: PointF): LatLon? = track.minByOrNull { (q, _) -> (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y) }?.second

        /** [p] 按在哪张照片的小卡片上 */
        fun pinAt(p: PointF): Uri? = pins.lastOrNull { (_, c) -> abs(c.x - p.x) <= PhotoPins.SIZE / 2 && abs(c.y - p.y) <= PhotoPins.SIZE / 2 }?.first
    }

    /** 照片钉在图上的样子：小图和它拍的地方（像素）；画完 [center] 是小卡片摆在哪 */
    class Pin(val photo: Bitmap, val at: PointF) {
        var center: PointF? = null
            internal set
    }

    /** 一次骑行的分享图；[hide] 打开时家附近的轨迹不画（见 [SharePrivacy]），[photos] 钉在拍的地方 */
    suspend fun card(context: Context, ride: Ride, record: String?, hide: Boolean = true, photos: List<SharePhoto> = emptyList()): Card =
        render(context, "rando-${ride.id}", tr("分享骑行", "Share ride"), listOf(ride), hide, photos) { map, tracks, pins -> draw(map, tracks.first(), ride, record, pins) }

    /** 同一天骑的几次：轨迹画在一张图上，数据加起来；以前哪天的不写「今天」 */
    suspend fun dayCard(context: Context, rides: List<Ride>, hide: Boolean = true, photos: List<SharePhoto> = emptyList()): Card? {
        if (rides.isEmpty()) return null
        val today = RideStats.today(rides.take(1), System.currentTimeMillis()).isNotEmpty()
        val share = if (today) tr("分享今天的骑行", "Share today's rides") else tr("分享这天的骑行", "Share this day's rides")
        return render(context, "rando-day-${rides.first().id}", share, rides, hide, photos) { map, tracks, pins -> drawDay(map, tracks, rides, pins, today) }
    }

    /** 分享图的隐私圈：收藏了家才有；没有的话隐私靠剪掉起点终点 */
    fun privacyZone(context: Context): SharePrivacy.Zone? {
        val home = context.rando.savedPlaces.places.value.firstOrNull { it.name == SavedPlace.HOME }?.location ?: return null
        return SharePrivacy.zone(home, SharePrivacy.seed(context))
    }

    /** 拍地图、画图；每次骑行可能被隐私圈断成几段，所以是 骑行 → 段 → 点 */
    private suspend fun render(
        context: Context,
        name: String,
        chooserTitle: String,
        rides: List<Ride>,
        hide: Boolean,
        photos: List<SharePhoto>,
        paint: (Bitmap?, List<List<List<PointF>>>, List<Pin>) -> Bitmap,
    ): Card {
        val home = context.rando.savedPlaces.places.value.firstOrNull { it.name == SavedPlace.HOME }?.location
        val zone = if (hide) privacyZone(context) else null
        val tracks = rides.map { r -> r.points.map { it.location } }
        val rideTracks = tracks.map { if (hide) SharePrivacy.hide(it, zone) else listOf(it) }
        val pieces = rideTracks.flatten()
        // 隐藏掉的那段路上拍的不钉，不然照片把家标出来了
        val pinned = photos.filter { p -> p.at != null && !(hide && SharePrivacy.hides(p.at, tracks, zone)) }.take(RidePhotos.MAX)
        // 地图只框住画出来的那些，不框到家
        val framed = pieces.flatten()
        // 选照片时一张张重画，地图还是那张就不再拍
        val theme = dark
        val snapshot = lastSnapshot?.takeIf { it.first == (framed to theme) }?.second
            ?: withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshot(context, framed, theme) }?.also { lastSnapshot = (framed to theme) to it }
        val (bitmap, trackPx, pins) = withContext(Dispatchers.Default) {
            // 照片的点和轨迹一起缩放，没地图时也对得上
            val places = pinned.map { listOf(it.at!!) }
            val allPx = snapshot?.second?.let { project -> (pieces + places).map { it.map(project) } }
                ?: fitAll(pieces + places, WIDTH.toFloat(), TRACK_HEIGHT - TRACK_TOP).map { t -> t.map { PointF(it.x, it.y + TRACK_TOP) } }
            val piecesPx = allPx.subList(0, pieces.size)
            var from = 0
            val ridesPx = rideTracks.map { r -> piecesPx.subList(from, from + r.size).also { from += r.size } }
            val pins = pinned.mapIndexed { i, p -> Pin(p.thumb, allPx[pieces.size + i].first()) }
            Triple(paint(snapshot?.first, ridesPx, pins), piecesPx.flatten(), pins)
        }
        return Card(
            bitmap, name, chooserTitle, if (home != null) tr("隐藏家附近", "Hide near home") else tr("隐藏起点终点附近", "Hide start and finish"),
            track = trackPx.zip(pieces.flatten()),
            pins = pinned.zip(pins).mapNotNull { (p, pin) -> pin.center?.let { p.uri to it } },
        )
    }

    /**
     * 存到缓存，弹出系统分享；[originals] 是要一起发的照片：分享图排第一张，后面跟着去掉位置信息的原图，
     * 朋友圈、小红书收到的就是一组图
     */
    suspend fun send(context: Context, card: Card, originals: List<SharePhoto> = emptyList()) {
        val files = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val cardFile = File(dir, "${card.name}.png").also { f -> f.outputStream().use { card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            // 上次发的照片删掉，缓存里不留一堆
            val photoDir = File(dir, "photos").apply { deleteRecursively(); mkdirs() }
            listOf(cardFile) + originals.mapIndexedNotNull { i, p ->
                File(photoDir, "${card.name}-${i + 1}.jpg").takeIf { SharePhotos.stripped(context, p.uri, it) }
            }
        }
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    /** 上次拍的地图和它框住的点 */
    private var lastSnapshot: Pair<Pair<List<LatLon>, Boolean>, Pair<Bitmap, (LatLon) -> PointF>>? = null

    /** 地图快照：框住整条轨迹；拍不到（没网）返回 null，就画在纯色底上 */
    private suspend fun snapshot(context: Context, track: List<LatLon>, dark: Boolean): Pair<Bitmap, (LatLon) -> PointF>? {
        if (track.size < 2) return null
        val bounds = LatLngBounds.Builder().includes(track.map { LatLng(it.lat, it.lon) }).build()
        // 四周留点边，下面多留一些：轨迹放在图的上半部分，下面渐隐的地方写字
        val padLat = (bounds.latitudeSpan * 0.18).coerceAtLeast(0.002)
        val padLon = (bounds.longitudeSpan * 0.18).coerceAtLeast(0.002)
        val span = TRACK_HEIGHT - TRACK_TOP
        val region = LatLngBounds.from(
            bounds.latitudeNorth + padLat + bounds.latitudeSpan * TRACK_TOP / span, bounds.longitudeEast + padLon,
            bounds.latitudeSouth - padLat - bounds.latitudeSpan * (MAP_HEIGHT - TRACK_HEIGHT) / span, bounds.longitudeWest - padLon,
        )
        val options = MapSnapshotter.Options(WIDTH, MAP_HEIGHT)
            .withStyleBuilder(Style.Builder().fromUri(if (dark) MapLayers.DARK_STYLE_URL else MapLayers.STYLE_URL))
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
    fun draw(map: Bitmap?, trackPx: List<List<PointF>>, ride: Ride, record: String?, pins: List<Pin> = emptyList()): Bitmap =
        drawCard(
            map, listOf(trackPx), ride.title, DATE.format(Date(ride.startedAt)), record,
            ride.distanceMeters, ride.movingSeconds, ride.ascendMeters, pins,
        )

    /** 同一天几次骑行合在一张图上：标题写骑了几次，下面是合计 */
    fun drawDay(map: Bitmap?, tracksPx: List<List<List<PointF>>>, rides: List<Ride>, pins: List<Pin> = emptyList(), today: Boolean = true): Bitmap {
        val first = rides.first()
        val last = rides.last()
        val title = when {
            rides.size == 1 && today -> tr("今天${first.title}", "Today: ${first.title}")
            rides.size == 1 -> first.title
            today -> tr("今天骑了 ${rides.size} 次", "${rides.size} rides today")
            else -> tr("这天骑了 ${rides.size} 次", "${rides.size} rides that day")
        }
        val time = if (rides.size == 1) {
            "${CLOCK.format(Date(first.startedAt))}–${CLOCK.format(Date(first.endedAt))}"
        } else {
            // 「早上 · 傍晚」；两次都在早上就写几点到几点
            rides.map { timeOfDay(it.startedAt) }.takeIf { it.distinct().size == it.size && it.size <= 4 }?.joinToString(" · ")
                ?: "${CLOCK.format(Date(first.startedAt))}–${CLOCK.format(Date(last.endedAt))}"
        }
        val note = if (pins.isEmpty()) time else time + tr(" · 拍了 ${pins.size} 张", " · ${pins.size} photo${if (pins.size == 1) "" else "s"}")
        return drawCard(
            map, tracksPx, title, DAY.format(Date(first.startedAt)), note,
            rides.sumOf { it.distanceMeters }, rides.sumOf { it.movingSeconds }, rides.sumOf { it.ascendMeters }, pins,
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
        pins: List<Pin> = emptyList(),
    ): Bitmap {
        val dark = dark
        val bg = if (dark) Color.parseColor("#0E1116") else Color.parseColor("#F3F1EC")
        val ink = if (dark) Color.WHITE else Color.parseColor("#1C2330")
        val soft = if (dark) Color.parseColor("#AEB6C2") else Color.parseColor("#5E6878")
        val green = if (dark) Color.parseColor("#6FE3A0") else Color.parseColor("#1B7F3B")
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        val w = WIDTH.toFloat()
        val mapH = MAP_HEIGHT.toFloat()
        if (map != null) {
            // 地图压淡一些，突出轨迹
            val faded = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(if (dark) 0.35f else 0.4f) })
                alpha = if (dark) 200 else 235
            }
            c.drawBitmap(map, null, RectF(0f, 0f, w, mapH), faded)
        }
        // 四周和上下渐隐到底色：地图像是融在图里
        val clear = bg and 0x00FFFFFF
        c.drawRect(0f, 0f, w, mapH, Paint().apply {
            shader = RadialGradient(w / 2, mapH * 0.42f, w * 0.85f, intArrayOf(clear, clear, bg), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 0f, w, 360f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 360f, bg, clear, Shader.TileMode.CLAMP) })
        c.drawRect(0f, mapH - 520f, w, mapH, Paint().apply { shader = LinearGradient(0f, mapH - 520f, 0f, mapH, clear, bg, Shader.TileMode.CLAMP) })
        tracksPx.forEach { drawTrack(c, it, bg) }
        drawPins(c, pins, tracksPx.flatten().flatten())

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val heavy = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 900, false) else bold
        val left = 72f
        // 顶上一行小字：快拍的头像那条下面一点
        c.drawText("RANDO", left, 290f, text(32f, ink, bold).apply { letterSpacing = 0.25f })
        c.drawText(date, WIDTH - 72f, 290f, text(32f, soft, bold).apply { textAlign = Paint.Align.RIGHT })

        var y = 1180f
        c.drawText(title, left, y, text(68f, ink, heavy).also { fitWidth(it, title, WIDTH - 2 * left) })
        if (note != null) {
            y += 64f
            c.drawText(note, left, y, text(36f, green, bold).also { fitWidth(it, note, WIDTH - 2 * left) })
        }
        // 大号距离：橙到黄的渐变字
        val distance = Format.distance(meters.toInt())
        val number = distance.substringBefore(' ')
        val unit = distance.substringAfter(' ', "")
        y += 250f
        val big = text(250f, ink, heavy).apply {
            letterSpacing = -0.04f
            shader = LinearGradient(left, 0f, left + measureText(number), 0f, Color.parseColor("#FF6A1A"), Color.parseColor("#FFB020"), Shader.TileMode.CLAMP)
        }
        c.drawText(number, left, y, big)
        c.drawText(" $unit", left + big.measureText(number), y, text(58f, ink, bold))

        // 其余三项一排，中间细线隔开
        y += 120f
        val stats = listOf(
            Format.clock(movingSeconds) to tr("骑行时间", "Moving time"),
            tr("${ascendMeters.toInt()} 米", "${ascendMeters.toInt()} m") to tr("爬升", "Climb"),
            Format.speed(if (movingSeconds > 0) meters / movingSeconds else 0.0) to tr("均速", "Avg speed"),
        )
        val value = text(64f, ink, heavy)
        val small = text(30f, ink, bold)
        val caption = text(30f, soft, bold)
        val col = (WIDTH - 2 * left) / stats.size
        val rule = Paint().apply { color = if (dark) Color.argb(46, 255, 255, 255) else Color.argb(38, 28, 35, 48); strokeWidth = 3f }
        stats.forEachIndexed { i, (v, label) ->
            val x = left + i * col + if (i == 0) 0f else 32f
            val n = v.substringBefore(' ')
            val u = v.substringAfter(' ', "")
            c.drawText(n, x, y, value)
            if (u.isNotEmpty()) c.drawText(" $u", x + value.measureText(n), y, small)
            c.drawText(label, x, y + 48f, caption)
            if (i > 0) c.drawLine(left + i * col, y - 60f, left + i * col, y + 56f, rule)
        }

        // 最底下：Rando 和地图版权，快拍的回复栏会盖住也没关系
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(left, HEIGHT - 120f, left + 28f, HEIGHT - 92f, Color.parseColor("#3E949F"), Color.parseColor("#17707C"), Shader.TileMode.CLAMP)
        }
        c.drawCircle(left + 14f, HEIGHT - 106f, 14f, dot)
        c.drawText("Rando", left + 40f, HEIGHT - 92f, text(44f, ink, heavy))
        if (map != null) c.drawText(tr("地图 © OpenStreetMap", "Map © OpenStreetMap"), WIDTH - 72f, HEIGHT - 96f, text(22f, soft).apply { textAlign = Paint.Align.RIGHT })
        return out
    }

    /** 字太长就缩小，放得进 [max] 宽 */
    private fun fitWidth(p: Paint, s: String, max: Float) {
        while (p.measureText(s) > max && p.textSize > 20f) p.textSize -= 2f
    }

    /** 一次骑行：几段线（橙到黄，外面一圈光晕），起点绿点、终点橙点（中间断开的地方不加点） */
    private fun drawTrack(c: Canvas, pieces: List<List<PointF>>, bg: Int) {
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
        val all = drawn.flatten()
        val first = all.first()
        val last = all.last()
        c.drawPath(path, Paint(stroke).apply {
            color = Color.parseColor("#FF8A3D"); alpha = 120; strokeWidth = 44f
            maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawPath(path, Paint(stroke).apply { color = bg; strokeWidth = 24f })
        c.drawPath(path, Paint(stroke).apply {
            strokeWidth = 13f
            shader = LinearGradient(first.x, first.y, last.x, last.y, Color.parseColor("#FF6A1A"), Color.parseColor("#FFC83D"), Shader.TileMode.CLAMP)
        })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        fun pin(p: PointF, color: String) {
            c.drawCircle(p.x, p.y, 22f, dot.apply { this.color = Color.WHITE })
            c.drawCircle(p.x, p.y, 15f, dot.apply { this.color = Color.parseColor(color) })
        }
        pin(drawn.first().first(), "#22B07D")
        pin(drawn.last().last(), "#FF6A1A")
    }

    /** 照片做成带白边的小卡片，一根细线连到拍的地方 */
    private fun drawPins(c: Canvas, pins: List<Pin>, track: List<PointF>) {
        if (pins.isEmpty()) return
        // 照片只摆在顶上那行字下面、标题上面那块里
        val centers = PhotoPins.layout(pins.map { it.at.x to it.at.y - TRACK_TOP }, WIDTH.toFloat(), TRACK_HEIGHT - TRACK_TOP, track.map { it.x to it.y - TRACK_TOP })
            .map { (x, y) -> x to y + TRACK_TOP }
        pins.forEachIndexed { i, p -> p.center = PointF(centers[i].first, centers[i].second) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        pins.forEachIndexed { i, p -> c.drawLine(p.at.x, p.at.y, centers[i].first, centers[i].second, line) }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        pins.forEach { p ->
            c.drawCircle(p.at.x, p.at.y, 12f, dot.apply { color = Color.WHITE })
            c.drawCircle(p.at.x, p.at.y, 7f, dot.apply { color = Color.parseColor("#1F2A24") })
        }
        val size = PhotoPins.SIZE
        val border = 8f
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(18f, 0f, 8f, Color.argb(56, 31, 42, 36))
        }
        val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        pins.forEachIndexed { i, p ->
            val (cx, cy) = centers[i]
            c.save()
            c.rotate(PhotoPins.tilt(i), cx, cy)
            c.drawRoundRect(RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), 20f, 20f, frame)
            val inner = RectF(cx - size / 2 + border, cy - size / 2 + border, cx + size / 2 - border, cy + size / 2 - border)
            c.clipPath(Path().apply { addRoundRect(inner, 14f, 14f, Path.Direction.CW) })
            // 居中裁成正方形
            val b = p.photo
            val side = minOf(b.width, b.height)
            val src = android.graphics.Rect((b.width - side) / 2, (b.height - side) / 2, (b.width + side) / 2, (b.height + side) / 2)
            c.drawBitmap(b, src, inner, image)
            c.restore()
        }
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
    /** 轨迹画在地图上面这么高的一块里，下面渐隐的地方留给字 */
    private const val TRACK_HEIGHT = 1060f
    /** 轨迹、照片从这儿往下画，上面留给快拍的头像和那行小字 */
    private const val TRACK_TOP = 340f
}
