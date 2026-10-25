package com.personal.rando.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import com.personal.rando.ride.Ride
import com.personal.rando.ride.RidePhotos
import com.personal.rando.ride.SharePrivacy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.personal.rando.i18n.tr
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 分享图先预览：画好了弹出来看一眼，下面「存到相册」「分享」。
 * 返回的函数传进去分享的是哪几次骑行、怎么画图（参数是要不要隐藏家附近、钉哪些照片），调用就开始画、弹出预览；
 * 预览里切开关、选照片会重新画一张。骑行时拍的照片自动找出来，默认选几张（家附近拍的不选）
 */
@Composable
fun rememberSharePreview(): (rides: List<Ride>, make: suspend (hide: Boolean, photos: List<SharePhoto>) -> ShareCard.Card?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<ShareCard.Card?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var saved by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf<(suspend (Boolean, List<SharePhoto>) -> ShareCard.Card?)?>(null) }
    // 每次打开都默认隐藏，免得上次关掉忘了开
    var hide by remember { mutableStateOf(true) }
    var privacyLabel by remember { mutableStateOf(tr("隐藏家附近", "Hide near home")) }
    var rides by remember { mutableStateOf(emptyList<Ride>()) }
    // 找到的和自己挑的照片；选上的按选的先后
    var photos by remember { mutableStateOf(emptyList<SharePhoto>()) }
    var chosen by remember { mutableStateOf(emptyList<SharePhoto>()) }
    var finding by remember { mutableStateOf(false) }
    var canRead by remember { mutableStateOf(false) }
    var withOriginals by remember { mutableStateOf(true) }

    fun draw() {
        val m = make ?: return
        job?.cancel()
        card = null
        saved = false
        // 图上按拍的先后排
        val pins = chosen.sortedBy { it.takenAt ?: Long.MAX_VALUE }
        job = scope.launch {
            val c = m(hide, pins)
            if (c == null) open = false else { card = c; privacyLabel = c.privacy }
        }
    }

    /** 找骑行时拍的照片，默认按时间挑几张；家附近（或者没收藏家时起点终点附近）拍的不挑 */
    fun find() {
        canRead = SharePhotos.canRead(context)
        if (!canRead) return
        val forRides = rides
        scope.launch {
            finding = true
            val found = SharePhotos.during(context, forRides)
            finding = false
            if (rides !== forRides) return@launch
            val zone = ShareCard.privacyZone(context)
            val tracks = forRides.map { r -> r.points.map { it.location } }
            val ok = found.filter { p -> p.at != null && !SharePrivacy.hides(p.at, tracks, zone) }
            // 自己已经挑的留着，找到的排在前面
            photos = found + photos.filter { p -> found.none { it.uri == p.uri } }
            if (chosen.isEmpty() && ok.isNotEmpty()) {
                chosen = RidePhotos.pick(ok.size).map { ok[it] }
                draw()
            }
        }
    }

    fun toggle(p: SharePhoto) {
        chosen = when {
            chosen.any { it.uri == p.uri } -> chosen.filter { it.uri != p.uri }
            chosen.size >= RidePhotos.MAX -> {
                Toast.makeText(context, tr("最多选 ${RidePhotos.MAX} 张", "Up to ${RidePhotos.MAX} photos"), Toast.LENGTH_SHORT).show()
                return
            }
            else -> chosen + p
        }
        draw()
    }

    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) find() else Toast.makeText(context, tr("没有相册权限，可以点 + 自己挑", "No photo access — tap + to pick"), Toast.LENGTH_SHORT).show()
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(RidePhotos.MAX)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val forRides = rides
        scope.launch {
            val picked = uris.mapNotNull { SharePhotos.picked(context, it, forRides) }
            if (rides !== forRides) return@launch
            val fresh = picked.filter { p -> photos.none { it.uri == p.uri } }
            photos = photos + fresh
            // 挑了就是想放上去：选满为止
            chosen = (chosen + picked.filter { p -> chosen.none { it.uri == p.uri } }).take(RidePhotos.MAX)
            draw()
        }
    }

    fun save(c: ShareCard.Card) = scope.launch {
        val ok = ShareCard.save(context, c)
        saved = ok
        Toast.makeText(context, if (ok) tr("存到相册了（Pictures/Rando）", "Saved to Photos (Pictures/Rando)") else tr("没存上", "Couldn't save"), Toast.LENGTH_SHORT).show()
    }
    // Android 9 以下存相册要「存储」权限
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val c = card
        if (granted && c != null) save(c) else if (!granted) Toast.makeText(context, tr("没有存储权限，存不了", "No storage permission, can't save"), Toast.LENGTH_SHORT).show()
    }

    if (open) {
        Dialog(onDismissRequest = { open = false; job?.cancel() }) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ShareCard.WIDTH.toFloat() / ShareCard.HEIGHT)
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val c = card
                        if (c == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = RandoGreen, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(tr("正在画图…", "Drawing…"), color = InkSoft, fontSize = 14.sp)
                            }
                        } else {
                            val image = remember(c) { c.bitmap.asImageBitmap() }
                            Image(image, contentDescription = tr("分享图预览", "Share image preview"), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PhotoRow(
                        photos = photos,
                        chosen = chosen,
                        finding = finding,
                        canRead = canRead,
                        onToggle = ::toggle,
                        onAsk = { askPhotos.launch(SharePhotos.permissions) },
                        onPick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(privacyLabel, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
                        Switch(
                            checked = hide,
                            onCheckedChange = { hide = it; draw() },
                            colors = SwitchDefaults.colors(checkedTrackColor = RandoGreen),
                        )
                    }
                    if (chosen.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("照片原图一起发", "Also send the photos"), color = Ink, fontSize = 15.sp)
                                Text(tr("分享图排第一张；照片里的位置信息会去掉", "Card first; location data is removed from photos"), color = InkSoft, fontSize = 12.sp)
                            }
                            Switch(
                                checked = withOriginals,
                                onCheckedChange = { withOriginals = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = RandoGreen),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val c = card
                        GlassButton(
                            onClick = {
                                if (c == null || saved) return@GlassButton
                                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save(c)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (saved) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = RandoGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (saved) tr("已存到相册", "Saved to Photos") else tr("存到相册", "Save to Photos"), color = if (c == null) InkSoft else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GlassButton(
                            onClick = { if (c != null) scope.launch { ShareCard.send(context, c, if (withOriginals) chosen.sortedBy { it.takenAt ?: Long.MAX_VALUE } else emptyList()) } },
                            modifier = Modifier.weight(1f),
                            tone = GlassTone.Tinted(if (c == null) Color(0xFF9FB8A8) else RandoGreen),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("分享", "Share"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    return { r, m ->
        make = m
        rides = r
        hide = true
        photos = emptyList()
        chosen = emptyList()
        withOriginals = true
        open = true
        draw()
        find()
    }
}

/** 预览里的一排照片：点一下选上 / 取消，最后一格从相册再挑；没给相册权限时可以点着去给 */
@Composable
private fun PhotoRow(
    photos: List<SharePhoto>,
    chosen: List<SharePhoto>,
    finding: Boolean,
    canRead: Boolean,
    onToggle: (SharePhoto) -> Unit,
    onAsk: () -> Unit,
    onPick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("照片", "Photos"), Modifier.weight(1f), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val unplaced = chosen.count { it.at == null }
            val status = when {
                finding -> tr("正在找照片…", "Looking for photos…")
                !canRead -> null
                photos.isEmpty() -> tr("骑行时没拍照片", "No photos during the ride")
                unplaced > 0 -> tr("$unplaced 张不是骑行时拍的，不放在图上", "$unplaced not taken on the ride, not on the card")
                else -> tr("找到 ${photos.size} 张 · 选了 ${chosen.size} 张", "${photos.size} found · ${chosen.size} chosen")
            }
            if (status != null) Text(status, color = InkSoft, fontSize = 12.sp)
            else Text(
                tr("自动找骑行时拍的", "Find ride photos"),
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAsk).padding(horizontal = 6.dp, vertical = 4.dp),
                color = RandoGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { p ->
                val on = chosen.any { it.uri == p.uri }
                val image = remember(p) { p.thumb.asImageBitmap() }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.5.dp, if (on) RandoGreen else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onToggle(p) },
                ) {
                    Image(image, contentDescription = if (on) tr("已选的照片", "Chosen photo") else tr("没选的照片", "Photo, not chosen"), modifier = Modifier.fillMaxSize().alpha(if (on) 1f else 0.55f), contentScale = ContentScale.Crop)
                    if (on) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(CircleShape).background(RandoGreen),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                    }
                }
            }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, InkSoft.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = tr("从相册挑照片", "Pick photos"), tint = InkSoft, modifier = Modifier.size(24.dp)) }
        }
    }
}
