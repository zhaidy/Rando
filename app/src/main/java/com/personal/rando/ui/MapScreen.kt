package com.personal.rando.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import com.personal.rando.ui.glass.GlassIconButton
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberUpdatedState
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.neu
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import dev.chrisbanes.haze.hazeSource
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.rando.places.SavedPlace
import com.personal.rando.ride.Fix
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.RecordingState
import com.personal.rando.ride.RideSession
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.group.SharedRoute
import com.personal.rando.routing.Crossing
import com.personal.rando.routing.CrossingType
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.Structure
import com.personal.rando.routing.StructureKind
import com.personal.rando.routing.structures
import com.personal.rando.routing.crossings
import com.personal.rando.routing.breakdown
import com.personal.rando.search.Place
import com.personal.rando.rando
import com.personal.rando.group.TeamCode
import com.personal.rando.group.NearbyTeam
import com.personal.rando.group.NearbyLink
import com.personal.rando.places.MrtHome
import com.personal.rando.ride.AutoStart
import androidx.core.content.FileProvider
import com.personal.rando.ride.Calories
import com.personal.rando.voice.VoiceState
import com.personal.rando.voice.VoiceCommand
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import com.personal.rando.ride.RouteRecord
import com.personal.rando.nav.Geo
import com.personal.rando.places.Hazard
import com.personal.rando.ride.RideService
import com.personal.rando.ride.Ride
import com.personal.rando.ride.RideHeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** 新加坡市中心，还没定位时先看这里 */
private val SINGAPORE = LatLng(1.3521, 103.8198)

@Composable
fun MapScreen(vm: RouteViewModel = viewModel(), onOpenRides: () -> Unit, onCheckUpdate: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current ?: rememberNotices()
    val navigation by vm.session.navigation.collectAsStateWithLifecycle()
    val recording by vm.session.recording.collectAsStateWithLifecycle()
    val saved by vm.savedPlaces.places.collectAsStateWithLifecycle()
    val corrections by vm.roadCorrections.all.collectAsStateWithLifecycle()
    val fix by vm.session.fix.collectAsStateWithLifecycle()
    val group = LocalContext.current.rando.group
    val groupState by group.state.collectAsStateWithLifecycle()
    val routeOffer by group.offer.collectAsStateWithLifecycle()
    val hazards by LocalContext.current.rando.hazards.all.collectAsStateWithLifecycle()
    var hazardShown by remember { mutableStateOf<Hazard?>(null) }
    val savedRoutes by vm.savedRoutes.routes.collectAsStateWithLifecycle()
    var savingRoute by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val offlineMaps = LocalContext.current.rando.offlineMaps
    val offlineState by offlineMaps.state.collectAsStateWithLifecycle()
    var offlineDialog by remember { mutableStateOf(false) }
    val settingsStore = LocalContext.current.rando.settings
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    var settingsDialog by remember { mutableStateOf(false) }
    val photoAppContext = LocalContext.current.applicationContext
    val recognitionPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            AutoStart.apply(photoAppContext, true)
        } else {
            settingsStore.update(settingsStore.settings.value.copy(autoStart = false))
            notices.show(tr("没有「身体活动」权限，认不出在骑车", "Without Physical activity permission, rides can't be detected"), long = true)
        }
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val parking = LocalContext.current.rando.parking
    val parked by parking.bike.collectAsStateWithLifecycle()
    var parkedDialog by remember { mutableStateOf(false) }
    var mrtPlan by remember { mutableStateOf<MrtHome?>(null) }
    val mrtContext = LocalContext.current
    // 骑累了坐 MRT 回家：离家近或者最近的站就是家门口的站，直接导航回家
    val mrtHome: () -> Unit = {
        val home = vm.savedPlaces.places.value.firstOrNull { it.name == SavedPlace.HOME }
        val here = vm.session.location.value
        when {
            home == null -> vm.session.say(tr("先把家收藏起来，才知道坐到哪个站", "Save your home first so I know which station to ride to")).also {
                notices.show(tr("先把「家」收藏起来", "Save your home first"), long = true)
            }
            here == null -> notices.show(tr("还没定位到", "No location yet"))
            else -> {
                val plan = MrtHome.plan(mrtContext.rando.mrt, here, home.location)
                if (plan == null) {
                    vm.session.say(tr("离家不远，直接骑回去吧", "You're close to home, just ride back"))
                    vm.goTo(SavedPlace.HOME) { vm.session.say(it) }
                } else {
                    mrtPlan = plan
                    vm.session.say(plan.text)
                }
            }
        }
    }
    val photoContext = LocalContext.current
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            parking.photoTaken()
            notices.show(tr("车位照片存好了", "Parking photo saved"))
        }
    }
    val photoParking = {
        val uri = FileProvider.getUriForFile(photoContext, "${photoContext.packageName}.files", parking.photoFile)
        runCatching { takePhoto.launch(uri) }.onFailure { notices.show(tr("打不开相机", "Can't open the camera")) }
        Unit
    }
    val voice = LocalContext.current.rando.voice
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val sosList by group.sos.collectAsStateWithLifecycle()
    val crash by vm.session.crash.collectAsStateWithLifecycle()
    var groupDialog by remember { mutableStateOf(false) }
    val walkie = LocalContext.current.rando.walkie
    val walkieState by walkie.state.collectAsStateWithLifecycle()
    val teammates = groupState?.teammates.orEmpty()
    val rideWeather by vm.session.weather.collectAsStateWithLifecycle()
    val rideClimate by vm.session.climate.collectAsStateWithLifecycle()
    val rideAmenities by vm.session.amenities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current

    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> hasLocation = grants.values.any { it } }
    LaunchedEffect(Unit) {
        if (!hasLocation) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = rememberMapView(textureMode = true)
    // 跟着系统深色模式换地图样式；系统切换时 Activity 会重建，这里读一次就行
    val styleUrl = MapLayers.styleUrl(LocalPalette.current.dark)
    // 规划页上下两摞挡住了哪里，镜头框路线、框某一段时避开
    var plannerInsets by remember { mutableStateOf(PlannerInsets()) }
    // 规划卡片拉满看详情时，右边竖条让开
    var sheetExpanded by remember { mutableStateOf(false) }
    // 比例尺：地图中心每 1 dp 多少米，镜头一动就更新
    var metersPerDp by remember { mutableStateOf(0.0) }
    // 地图转了多少度（0 是正北朝上）
    var bearing by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(SINGAPORE).zoom(11.0).build()
            // 规划时可以两指转地图对着前方看；转了以后右边出现指北针，点一下转回正北
            m.uiSettings.isCompassEnabled = false
            m.addOnCameraMoveListener {
                bearing = m.cameraPosition.bearing.toFloat()
                metersPerDp = metersPerDp(m, density.density)
            }
            m.addOnMapLongClickListener { latLng ->
                val home = vm.state.value.pickingHome
                vm.onMapLongPress(LatLon(latLng.latitude, latLng.longitude))
                if (home) notices.show(tr("家的位置设好了", "Home is set"))
                true
            }
            m.setStyle(styleUrl) { s ->
                MapLayers.install(s)
                style = s
                metersPerDp = metersPerDp(m, density.density)
            }
            map = m
        }
    }

    LaunchedEffect(map, style, hasLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (hasLocation) enableMyLocation(context, m, s)
    }
    val navigating = navigation != null
    // 导航、记录时由 RideService 收定位（锁屏也不停），这里只管规划时的
    LocationUpdates(enabled = hasLocation && !navigating && recording == null) { vm.session.onFix(it) }

    // 导航结束的通知栏提示要这个权限；不给也能导航，只是通知栏看不到
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val startNavigation = {
        askNotifications()
        vm.startNavigation()
    }
    // 打开对讲：先要麦克风权限，顺便要「附近的设备」（离得近的队友直连，不给也能用，只走服务器）；
    // 开了以后让前台服务带上麦克风，锁屏也能听
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (walkie.hasMicPermission()) {
            walkie.turnOn()
            RideService.start(context)
        } else {
            notices.show(tr("没有麦克风权限，开不了对讲", "No microphone permission, walkie-talkie unavailable"))
        }
    }
    val toggleTalk = {
        when {
            walkieState.on -> walkie.turnOff()
            walkie.hasMicPermission() && group.hasLocalPermissions() -> {
                walkie.turnOn()
                RideService.start(context)
            }
            else -> micPermission.launch((listOf(Manifest.permission.RECORD_AUDIO) + NearbyLink.PERMISSIONS).toTypedArray())
        }
    }
    // 语音指令：听懂了就在这里做
    val session = vm.session
    // 识别服务都用不了时弹系统的语音输入框
    val voiceDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val candidates = if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        } else {
            null
        }
        voice.dialogResult(candidates)
    }
    DisposableEffect(voice) {
        voice.dialog = { intent -> runCatching { voiceDialog.launch(intent) }.isSuccess }
        voice.handler = { cmd ->
            when (cmd) {
                is VoiceCommand.GoTo -> {
                    session.say(if (cmd.place == SavedPlace.HOME) tr("好的，去${cmd.place}", "OK, heading home") else tr("好的，去${cmd.place}", "OK, heading to ${cmd.place}"))
                    vm.goTo(cmd.place) { session.say(it) }
                }
                is VoiceCommand.Loop -> {
                    session.say(tr("好的，骑一圈 ${cmd.km} 公里", "OK, a ${cmd.km} km loop"))
                    vm.startLoop(cmd.km)
                }
                is VoiceCommand.Route -> if (vm.startSavedRoute(cmd.name)) session.say(tr("好的，骑${cmd.name}", "OK, riding ${cmd.name}")) else session.say(tr("没有叫${cmd.name}的收藏路线", "No saved route called ${cmd.name}"))
                VoiceCommand.StopNavigation -> if (session.navigation.value != null) {
                    session.stopNavigation()
                    session.say(tr("导航已结束，还在记录", "Navigation ended, still recording"))
                } else {
                    session.say(tr("现在没在导航", "Not navigating right now"))
                }
                VoiceCommand.Remaining -> session.say(session.remainingText())
                is VoiceCommand.Report -> session.say(if (session.reportHazard(cmd.type)) tr("已标记${cmd.type.label}", "Marked: ${cmd.type.label}") else tr("还没定位到，标不了", "No location yet, can't mark"))
                is VoiceCommand.Walkie -> when {
                    group.state.value == null -> session.say(tr("先加入一起骑才能对讲", "Join a group ride first to use the walkie-talkie"))
                    cmd.on -> {
                        walkie.turnOn()
                        RideService.start(context)
                        session.say(tr("对讲已打开", "Walkie-talkie on"))
                    }
                    else -> {
                        walkie.turnOff()
                        session.say(tr("对讲已关闭", "Walkie-talkie off"))
                    }
                }
                VoiceCommand.Weather -> session.say(session.weatherText())
                VoiceCommand.MrtHome -> mrtHome()
                is VoiceCommand.Unknown -> session.say(if (cmd.text.isBlank()) tr("没听清，再说一次", "Didn't catch that, please say it again") else tr("没听懂：${cmd.text}", "Didn't understand: ${cmd.text}"))
            }
        }
        onDispose {
            voice.handler = null
            voice.dialog = null
        }
    }
    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen() else notices.show(tr("没有麦克风权限，用不了语音指令", "No microphone permission, voice commands unavailable"))
    }
    val listen = {
        if (voice.hasMicPermission()) voice.listen() else voicePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val leaveGroup = {
        walkie.turnOff()
        group.leave()
    }
    // 刚结束的这次骑行：弹一张卡片，问要不要原路返回
    var finished by remember { mutableStateOf<Ride?>(null) }
    // 骑完还留在队里（比如在终点等队友），要走了在小队面板上点「退出」
    val finishRide = {
        finished = finishRide(notices, vm.session)
    }
    // 又开始记录了（自动开始、进队）：上一次的卡片没用了，不然这次结束后又冒出来
    LaunchedEffect(recording != null) {
        if (recording != null) finished = null
    }
    // 「结束」按钮先问一句：骑车时手套一蹭就把导航和记录都停了
    var confirmFinish by remember { mutableStateOf(false) }
    val askFinish = { confirmFinish = true }
    // 「退出」紧挨着「分享」，点错就退队、关对讲，也先问一句
    var confirmLeave by remember { mutableStateOf(false) }
    // 一起骑就是一次骑行：进队就开始记录，前台服务让定位和连接在锁屏后也不断
    val joinGroup = { code: String, name: String ->
        groupDialog = false
        askNotifications()
        group.join(code, name)
        vm.session.startRecording()
    }
    LaunchedEffect(style, teammates) {
        val s = style ?: return@LaunchedEffect
        // 断了信号的队友不会再发位置，列表不变；隔一会儿重画一次，过时的点才会变灰
        while (true) {
            MapLayers.showTeam(s, teammates)
            delay(MapLayers.TEAM_REFRESH_MS)
        }
    }
    fun returnTrip(ride: Ride) {
        finished = null
        notices.show(tr("正在算原路返回的路线…", "Planning the way back…"))
        askNotifications()
        vm.returnTrip(ride.points.map { it.location }) { notices.show(tr("原路返回失败：$it", "Way back failed: $it"), action = tr("重试", "Retry")) { returnTrip(ride) } }
    }
    // 导航去某个地方（MRT 站、摔车的队友）；失败了提示条上可以重试
    fun navigateTo(to: LatLon, name: String) {
        askNotifications()
        vm.navigateTo(to, name) { notices.show(tr("导航失败：$it", "Navigation failed: $it"), action = tr("重试", "Retry")) { navigateTo(to, name) } }
    }
    fun followShared(offer: SharedRoute) {
        askNotifications()
        notices.show(tr("正在算${offer.name}的路线…", "Planning ${offer.name}'s route…"))
        vm.followShared(offer) { notices.show(tr("跟着骑失败：$it", "Follow failed: $it"), action = tr("重试", "Retry")) { followShared(offer) } }
    }

    // 卫星图、公园连道网络：右边「图层」里切换
    LaunchedEffect(style, settings.satellite) {
        style?.let { MapLayers.showSatellite(it, settings.satellite) }
    }
    LaunchedEffect(style, settings.showPcn) {
        val s = style ?: return@LaunchedEffect
        MapLayers.showPcnNetwork(s, if (settings.showPcn) context.rando.pcn.flatMap { it.lines } else emptyList())
    }

    // 以前骑过的路垫在底下；存了、删了记录就重画，设置里可以关
    val rideChanges by vm.session.rides.changes.collectAsStateWithLifecycle()
    LaunchedEffect(style, settings.showRidden, rideChanges) {
        val s = style ?: return@LaunchedEffect
        val pieces = if (settings.showRidden) {
            val tracks = vm.session.rides.list().map { r -> r.points.map { it.location } }
            withContext(Dispatchers.Default) { RideHeat.pieces(tracks) }
        } else emptyList()
        MapLayers.showRidden(s, pieces, faint = true)
    }

    // 记录中把骑过的轨迹画出来；按点数变化重画，不用每次重组都画
    val trackSize = recording?.track?.size ?: 0
    LaunchedEffect(style, trackSize) {
        val s = style ?: return@LaunchedEffect
        MapLayers.showTrack(s, recording?.track?.map { it.location })
    }

    // 导航时屏幕常亮，镜头跟着人走、朝前进方向转
    val view = LocalView.current
    // 骑行视图：上次导航用了它，这次开始导航就直接进
    val viewPrefs = remember { context.getSharedPreferences("planner", Context.MODE_PRIVATE) }
    var rideView by rememberSaveable { mutableStateOf(false) }
    val riding = navigating || recording != null
    // 进队也会开始记录，但这时多半还在集合点看队友、分享路线，不跳进去；队里开始导航照样进
    val autoRideView = navigating || (recording != null && groupState == null)
    LaunchedEffect(autoRideView) {
        if (autoRideView && viewPrefs.getBoolean(KEY_RIDE_VIEW, false)) rideView = true
    }
    val openRideView = { show: Boolean ->
        rideView = show
        viewPrefs.edit { putBoolean(KEY_RIDE_VIEW, show) }
    }
    // 导航、骑行视图（自由骑行时它就是码表）里屏幕常亮
    val screenOn = navigating || (rideView && recording != null)
    DisposableEffect(screenOn) {
        view.keepScreenOn = screenOn
        onDispose { view.keepScreenOn = false }
    }
    // 导航时镜头跟着人：在骑按前进方向，停着按手机朝向；拖开地图后右边出现「回到我的位置」
    val follow = rememberFollow(map, active = navigating && style != null && hasLocation, moving = fix?.moving == true, zoom = 17.0, tilt = 40.0)
    LaunchedEffect(map, style, hasLocation, navigating) {
        val m = map ?: return@LaunchedEffect
        if (style == null || !hasLocation || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        if (navigating) follow.recenter()
        m.locationComponent.apply {
            if (!navigating) {
                cameraMode = CameraMode.NONE
                renderMode = RenderMode.COMPASS
                m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0).build()))
            }
        }
    }

    // 路线变了就重画，并把镜头框住整条路线（上下留出搜索框和底部卡片的位置）。
    // 导航中画的是正在走的那条，骑偏重新规划后会换
    val route = navigation?.route ?: (state.route as? RouteState.Ready)?.route
    val via = navigation?.via ?: state.via.map { it.location }
    // 沿途补给点：导航时用导航那条路线查到的
    val amenities = if (navigating) rideAmenities else state.amenities.takeIf { state.route is RouteState.Ready }.orEmpty()
    // 没选中的几条可选路线画成灰线
    val alternatives = (state.route as? RouteState.Ready)?.takeIf { !navigating }
        ?.let { r -> r.options.filterIndexed { i, _ -> i != r.chosen }.map { it.route } }.orEmpty()
    LaunchedEffect(style, alternatives) {
        style?.let { MapLayers.showAlternatives(it, alternatives) }
    }
    LaunchedEffect(style, amenities) {
        style?.let { MapLayers.showAmenities(it, amenities.map { a -> a.amenity }) }
    }
    LaunchedEffect(style, route, state.start, state.destination, via) {
        val s = style ?: return@LaunchedEffect
        MapLayers.show(s, route, state.start?.location, state.destination?.location, via)
        val m = map ?: return@LaunchedEffect
        // 导航中重新算路时镜头还跟着人，不要跳去看全程
        if (navigating) return@LaunchedEffect
        if (route != null && route.points.size >= 2) {
            val bounds = LatLngBounds.Builder()
                .includes(route.points.map { LatLng(it.lat, it.lon) })
                .build()
            // 等卡片换成算好的样子、高度定下来，再按它实际挡住的地方框
            delay(400)
            val (top, bottom) = cameraInsets(plannerInsets, density, fallbackTop = 200, fallbackBottom = 380)
            with(density) {
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48.dp.roundToPx(), top, 72.dp.roundToPx(), bottom))
            }
        } else {
            state.destination?.let { m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.location.lat, it.location.lon))) }
        }
    }

    // 点地图上的路线，或者点卡片上的提示，指给你看那几处
    var focus by remember { mutableStateOf<Focus?>(null) }
    LaunchedEffect(route) { focus = null }
    val currentRoute by rememberUpdatedState(route)
    val currentHazards by rememberUpdatedState(hazards)
    LaunchedEffect(style, hazards) {
        style?.let { MapLayers.showHazards(it, hazards) }
    }
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        m.addOnMapClickListener { latLng ->
            // 手指点的范围大约 28 个像素，换成米
            val tolerance = m.projection.getMetersPerPixelAtLatitude(latLng.latitude) * 28
            val tapped = LatLon(latLng.latitude, latLng.longitude)
            currentHazards.minByOrNull { Geo.distance(it.location, tapped) }
                ?.takeIf { Geo.distance(it.location, tapped) <= tolerance }
                ?.let {
                    hazardShown = it
                    return@addOnMapClickListener true
                }
            val r = currentRoute ?: return@addOnMapClickListener false
            val hit = r.segmentAt(LatLon(latLng.latitude, latLng.longitude), tolerance) ?: return@addOnMapClickListener false
            focus = Focus(listOf(hit), 0)
            true
        }
    }
    val focused = focus?.let { it.items[it.index] }
    LaunchedEffect(style, focused) {
        val s = style ?: return@LaunchedEffect
        MapLayers.showFocus(s, focused?.points)
        val m = map ?: return@LaunchedEffect
        if (focused == null) return@LaunchedEffect
        with(density) {
            if (focused.points.size >= 2) {
                val bounds = LatLngBounds.Builder().includes(focused.points.map { LatLng(it.lat, it.lon) }).build()
                val (top, bottom) = cameraInsets(plannerInsets, density, fallbackTop = 240, fallbackBottom = 260)
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80.dp.roundToPx(), top, 80.dp.roundToPx(), bottom), 700)
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focused.location.lat, focused.location.lon), 17.5), 700)
            }
        }
    }
    var saving by remember { mutableStateOf<Place?>(null) }
    val focusManager = LocalFocusManager.current

    // 卫星图上的玻璃要实一些，字才看得清
    val palette = LocalPalette.current
    CompositionLocalProvider(LocalPalette provides if (settings.satellite) palette.overImagery() else palette) {
    Box(Modifier.fillMaxSize()) {
        // 地图是液态玻璃取景的来源，所以用 TextureView（SurfaceView 取不到画面）
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .then(LocalHazeState.current?.let { Modifier.hazeSource(it) } ?: Modifier),
        )

        AnimatedContent(
            targetState = when {
                riding && rideView -> Layer.RIDE_VIEW
                navigation != null -> Layer.NAVIGATION
                else -> Layer.PLANNER
            },
            transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(180))) },
            label = "layer",
        ) { layer ->
            when (layer) {
                Layer.RIDE_VIEW -> {
                    BackHandler { openRideView(false) }
                    RideView(
                        navigation, recording,
                        weather = rideWeather,
                        climate = rideClimate,
                        amenities = rideAmenities,
                        onMap = { openRideView(false) },
                        onStop = askFinish,
                        teammates = teammatesText(teammates, fix?.location),
                        walkie = walkieState.takeIf { groupState != null },
                        onToggleTalk = toggleTalk,
                        onTalkLevel = walkie::setLevel,
                        onTalkMode = walkie::setMode,
                        onVoice = listen,
                        onMrt = mrtHome,
                        listening = (voiceState as? VoiceState.Listening)?.let { it.partial.ifEmpty { tr("请说…", "Speak now…") } },
                        weightKg = settings.weightKg,
                        onReport = { type ->
                            val ok = vm.session.reportHazard(type)
                            notices.show(if (ok) tr("已标记：${type.label}", "Marked: ${type.label}") else tr("还没定位到，标不了", "No location yet, can't mark"))
                        },
                    ) { modifier, cover -> RideMap(navigation, fix, recording?.track?.map { t -> t.location }.orEmpty(), teammates, modifier, hazards, cover) }
                }
                Layer.NAVIGATION -> navigation?.let { nav ->
                    Box(Modifier.fillMaxSize()) {
                        NavigationOverlay(
                            nav,
                            onRideView = { openRideView(true) },
                            onStop = askFinish,
                        )
                        RecenterButton(follow, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
                    }
                }
                Layer.PLANNER -> PlannerFrame(
                    top = {
                        SearchBar(
                            query = state.query,
                            hint = when {
                                state.pickingStart -> tr("搜索起点", "Search start")
                                state.pickingVia -> tr("搜索途经点", "Search stop")
                                state.pickingHome -> tr("搜索家的地址", "Search home address")
                                else -> tr("去哪儿骑？", "Where to ride?")
                            },
                            results = state.results,
                            build = appBuild(),
                            onQuery = vm::onQueryChange,
                            onPick = {
                                focusManager.clearFocus()
                                val home = state.pickingHome
                                vm.choosePlace(it)
                                if (home) notices.show(tr("家设在「${it.name}」", "Home set to ${it.name}"))
                            },
                            onCheckUpdate = onCheckUpdate,
                            onSettings = { settingsDialog = true },
                            onOfflineMaps = {
                                offlineMaps.refresh()
                                offlineDialog = true
                            },
                        )
                        PrefsRow(state.prefs, vm::setPrefs)
                        (voiceState as? VoiceState.Listening)?.let { HintPill(tr("正在听：", "Listening: ") + it.partial.ifEmpty { tr("请说…", "Speak now…") }, onCancel = voice::cancel) }
                        AnimatedVisibility(
                            state.pickingStart || state.pickingVia || state.pickingHome,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                        ) {
                            val what = when {
                                state.pickingVia -> tr("途经点", "a stop")
                                state.pickingHome -> tr("家的位置", "your home")
                                else -> tr("起点", "a start")
                            }
                            HintPill(tr("长按地图选$what，或者在上面搜一个地方", "Long-press the map to pick $what, or search above"), onCancel = {
                                focusManager.clearFocus()
                                vm.cancelPicking()
                            })
                        }
                        if (state.pickingStart || state.pickingVia || state.pickingHome) BackHandler { vm.cancelPicking() }
                    },
                    bottom = {
                        // 竖条左边空着的地方放比例尺，不多占高度
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            ScaleBar(metersPerDp)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val rotated = bearing in 1f..359f
                                AnimatedVisibility(
                                    rotated,
                                    modifier = Modifier.align(Alignment.End),
                                    enter = fadeIn() + scaleIn(Motion.bouncy()),
                                    exit = fadeOut() + scaleOut(),
                                ) {
                                    GlassIconButton(onClick = {
                                        map?.let { m -> m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).build())) }
                                    }) {
                                        Icon(
                                            NorthIcon,
                                            contentDescription = tr("转回正北", "Face north"),
                                            tint = RandoRed.onGlass(),
                                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -bearing },
                                        )
                                    }
                                }
                                SideRail(
                                    showLocation = hasLocation,
                                    showRecord = recording == null,
                                    onRides = onOpenRides,
                                    onRecord = {
                                        askNotifications()
                                        vm.session.startRecording()
                                    },
                                    onLocate = { map?.let(::flyToMyLocation) },
                                    onGroup = { groupDialog = true }.takeIf { groupState == null },
                                    modifier = Modifier.align(Alignment.End),
                                    onVoice = listen,
                                    listening = voiceState is VoiceState.Listening,
                                    layers = { LayersButton(settings, settingsStore::update) },
                                )
                            }
                        }
                        AnimatedVisibility(
                            groupState != null,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it },
                            exit = fadeOut() + slideOutVertically { it },
                        ) {
                            groupState?.let { g ->
                                GroupPill(
                                    g,
                                    onShare = { shareTeamCode(context, g.code) },
                                    onLeave = { confirmLeave = true },
                                    walkie = walkieState,
                                    onToggleTalk = toggleTalk,
                                    onLevel = walkie::setLevel,
                                    onMode = walkie::setMode,
                                    onTest = walkie::test,
                                )
                            }
                        }
                        AnimatedVisibility(
                            finished != null && recording == null,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it },
                            exit = fadeOut() + slideOutVertically { it },
                        ) {
                            finished?.let { ride ->
                                val record by produceState<String?>(null, ride) {
                                    value = RouteRecord.of(ride, vm.session.rides.list())?.text
                                }
                                FinishedCard(
                                    ride, onReturn = { returnTrip(ride) }, onDismiss = { finished = null }, record = record,
                                    onShare = rememberRideShare(ride, record),
                                    kcal = Calories.of(ride, settings.weightKg),
                                    onParkPhoto = photoParking,
                                )
                            }
                        }
                        AnimatedVisibility(
                            recording != null,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it },
                            exit = fadeOut() + slideOutVertically { it },
                        ) {
                            recording?.let { RecordingPill(it, onRideView = { openRideView(true) }, onStop = askFinish) }
                        }
                    },
                    sheet = { sheetModifier ->
                        AnimatedContent(
                            targetState = focus,
                            modifier = sheetModifier,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically(Motion.bouncy()) { it / 3 })
                                    .togetherWith(fadeOut(tween(140)))
                                    .using(SizeTransform(clip = false) { _, _ -> Motion.smooth() })
                            },
                            contentKey = { it == null },
                            label = "sheet",
                        ) { f ->
                            if (f != null) {
                                BackHandler { focus = null }
                                val item = f.items[f.index]
                                val marked = corrections.any { it.overlaps(item.points) }
                                SegmentCard(
                                    info = item,
                                    index = f.index,
                                    count = f.items.size,
                                    onNext = { focus = f.copy(index = (f.index + 1) % f.items.size) },
                                    onStreetView = { openStreetView(context, item.location) },
                                    onClose = { focus = null },
                                    markLabel = item.markLabel(marked),
                                    onMark = {
                                        if (marked) vm.roadCorrections.removeCovering(item.points) else vm.roadCorrections.add(item.points)
                                        focus = null
                                        vm.retry()
                                        notices.show(if (marked) tr("已撤销，重新算路", "Undone, rerouting") else tr("记下了，这里按马路算；开「不上马路」会绕开", "Noted, counted as road now; \"No roads\" will avoid it"), long = true)
                                    },
                                )
                            } else {
                                PlannerSheet(
                                    state = state,
                                    saved = saved,
                                    savedRoutes = savedRoutes,
                                    parked = parked,
                                    here = fix?.location,
                                    onExpandedChange = { sheetExpanded = it },
                                    actions = remember(vm) {
                                        PlannerActions(
                                            onChooseSaved = vm::chooseSaved,
                                            onToggleSave = { place, savedHere ->
                                                if (savedHere != null) vm.savedPlaces.remove(savedHere) else saving = place
                                            },
                                            onClear = vm::clearRoute,
                                            onPickStart = vm::startPickingStart,
                                            onUseMyLocation = vm::useMyLocationAsStart,
                                            onAddVia = vm::startPickingVia,
                                            onRemoveVia = vm::removeVia,
                                            onLoop = vm::chooseLoop,
                                            onAnotherLoop = vm::anotherLoop,
                                            onRetry = vm::retry,
                                            onStart = startNavigation,
                                            onShowOnMap = { target ->
                                                val s = vm.state.value
                                                val items = when (target) {
                                                    NoteTarget.AMENITIES -> s.amenities.targets()
                                                    NoteTarget.COVERED -> s.covered.coveredTargets()
                                                    else -> (s.route as? RouteState.Ready)?.route?.targets(target).orEmpty()
                                                }
                                                if (items.isNotEmpty()) focus = Focus(items, 0)
                                            },
                                            onPace = vm::setPace,
                                            onChooseOption = vm::chooseOption,
                                            onSaveRoute = { savingRoute = true },
                                            onChooseRoute = vm::chooseSavedRoute,
                                            onRemoveRoute = vm.savedRoutes::remove,
                                            onParked = { parkedDialog = true },
                                            onMrtHome = mrtHome,
                                        )
                                    },
                                )
                            }
                        }
                    },
                    onInsets = { plannerInsets = it },
                    sheetExpanded = sheetExpanded && focus == null,
                )
            }
        }
    }
    }
    parked?.takeIf { parkedDialog }?.let { bike ->
        ParkedDialog(
            bike,
            here = fix?.location,
            onShow = {
                parkedDialog = false
                style?.let { MapLayers.showFocus(it, listOf(bike.location)) }
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(bike.location.lat, bike.location.lon), 17.5), 700)
            },
            onWalk = {
                parkedDialog = false
                // 走路过去交给谷歌地图（有步行导航）
                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${bike.location.lat},${bike.location.lon}&travelmode=walking")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            },
            onPhoto = photoParking,
            onClear = {
                parking.clear()
                parkedDialog = false
                style?.let { MapLayers.showFocus(it, null) }
            },
            onDismiss = { parkedDialog = false },
        )
    }
    mrtPlan?.let { plan ->
        MrtHomeDialog(
            plan,
            onGo = {
                mrtPlan = null
                navigateTo(plan.boardExit.location, tr("${plan.board.label}站", "${plan.board.label} station"))
            },
            onDismiss = { mrtPlan = null },
        )
    }
    if (settingsDialog) {
        val voiceOptions by context.rando.session.voiceOptions.collectAsStateWithLifecycle()
        val saved by vm.savedPlaces.places.collectAsStateWithLifecycle()
        val here by vm.session.location.collectAsStateWithLifecycle()
        val home = saved.firstOrNull { it.name == SavedPlace.HOME }
        SettingsDialog(
            settings,
            home = home?.location,
            here = here,
            onHomeHere = {
                here?.let { vm.savedPlaces.save(SavedPlace(SavedPlace.HOME, it)) }
                    ?: notices.show(tr("还没定位到", "No location yet"))
            },
            onPickHome = {
                settingsDialog = false
                vm.startPickingHome()
            },
            onRemoveHome = { home?.let(vm.savedPlaces::remove) },
            onSave = {
                settingsStore.update(it)
                Lang.apply(it.language, context.resources.configuration.locales[0])
                context.rando.session.languageChanged()
                settingsDialog = false
                if (it.autoStart && !AutoStart.hasRecognitionPermission(context) && Build.VERSION.SDK_INT >= 29) {
                    recognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    AutoStart.apply(context, it.autoStart)
                }
            },
            onDismiss = { settingsDialog = false },
            voices = voiceOptions,
            onPreviewVoice = context.rando.session::previewVoice,
            onSystemVoices = {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { notices.show(tr("打不开系统的文字转语音设置", "Can't open system text-to-speech settings")) }
            },
            backgroundLocation = AutoStart.hasBackgroundLocation(context),
            onAskBackgroundLocation = {
                if (Build.VERSION.SDK_INT >= 29) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
        )
    }
    if (offlineDialog) {
        OfflineMapsDialog(
            offlineState,
            onDownload = offlineMaps::download,
            onCancel = offlineMaps::cancel,
            onDelete = offlineMaps::delete,
            onDismiss = { offlineDialog = false },
        )
    }
    if (savingRoute) {
        val suggested = state.destination?.name?.takeUnless(::isMapPlaceName)
            ?: state.loop?.let { tr("骑一圈 ${it.targetMeters / 1000} 公里", "${it.targetMeters / 1000} km loop") }.orEmpty()
        SaveRouteDialog(
            suggested,
            onSave = { name ->
                vm.saveRoute(name)
                savingRoute = false
                notices.show(tr("已收藏「$name」", "Saved \"$name\""))
            },
            onDismiss = { savingRoute = false },
        )
    }
    hazardShown?.let { h ->
        HazardDialog(
            h,
            onDelete = {
                vm.session.removeHazard(h.id)
                hazardShown = null
            },
            onDismiss = { hazardShown = null },
        )
    }
    if (confirmLeave) {
        RandoDialog(
            onDismissRequest = { confirmLeave = false },
            shape = GlassShapes.Sheet,
            containerColor = DialogColor,
            title = { Text(tr("退出这个队伍？", "Leave the team?")) },
            text = {
                Text(
                    tr("对讲会关掉，队友也看不到你的位置了。", "The walkie-talkie turns off and your team can no longer see you. ") +
                        if (recording != null) tr("记录不受影响，还会接着记。", "Your ride keeps recording.") else "",
                    color = InkSoft,
                )
            },
            confirmButton = {
                DialogButton(danger = true, onClick = {
                    confirmLeave = false
                    leaveGroup()
                }) { Text(tr("退出", "Leave"), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { DialogButton(onClick = { confirmLeave = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
    if (confirmFinish) {
        FinishDialog(
            navigating = navigating,
            recording = recording != null,
            onFinish = {
                confirmFinish = false
                finishRide()
            },
            onStopNavigation = {
                confirmFinish = false
                vm.session.stopNavigation()
            },
            onDismiss = { confirmFinish = false },
        )
    }
    crash?.let { alert -> CrashDialog(alert, onOk = vm.session::imOk, onSendNow = vm.session::sendSos) }
    sosList.firstOrNull()?.takeIf { crash == null }?.let { alert ->
        SosDialog(
            alert,
            me = fix?.location,
            onGo = {
                group.dismissSos(alert.id)
                navigateTo(alert.location, tr("${alert.name}的位置", "${alert.name}'s location"))
            },
            onDismiss = { group.dismissSos(alert.id) },
        )
    }
    routeOffer?.let { offer ->
        SharedRouteDialog(
            offer,
            navigating = navigating,
            onFollow = {
                group.answerOffer()
                followShared(offer)
            },
            onDecline = group::answerOffer,
        )
    }
    if (groupDialog) {
        // 打开就找一下附近的队伍，不用每次输入队伍码；找不到、没定位就当没有
        val nearby by produceState<List<NearbyTeam>?>(null, group.configured) {
            val me = vm.session.location.value
            value = if (me == null || !group.configured) emptyList() else runCatching { group.nearby(me) }.getOrDefault(emptyList())
        }
        GroupDialog(
            configured = group.configured,
            name = group.myName,
            onCreate = { name -> joinGroup(TeamCode.generate(), name) },
            onJoin = { code, name -> joinGroup(code, name) },
            onDismiss = { groupDialog = false },
            nearby = nearby,
        )
    }
    saving?.let { place ->
        SavePlaceDialog(
            place,
            onSave = {
                vm.savedPlaces.save(it)
                saving = null
            },
            onDismiss = { saving = null },
        )
    }
}

/**
 * 镜头框东西时上下要留多少像素：量到了就按上面那摞、底部卡片实际挡住的地方再多留一点，
 * 还没量到（刚打开）就用估计的 dp
 */
private fun cameraInsets(insets: PlannerInsets, density: Density, fallbackTop: Int, fallbackBottom: Int): Pair<Int, Int> = with(density) {
    if (!insets.measured) return fallbackTop.dp.roundToPx() to fallbackBottom.dp.roundToPx()
    val margin = 24.dp.roundToPx()
    (insets.top + margin) to (insets.height - insets.sheetTop + margin)
}

/** 地图上叠的是哪一层 */
private enum class Layer { PLANNER, NAVIGATION, RIDE_VIEW }

/** 正在指给你看的几处，看到第几处 */
private data class Focus(val items: List<SegmentInfo>, val index: Int)

/**
 * 用 Google 地图看街景。用 Maps 链接而不是 google.streetview:，
 * 这样点上正好没有街景（桥底下、公园里）时会找附近最近的一张，而不是退回卫星图。
 * 没装 Google 地图就开网页版
 */
private fun openStreetView(context: Context, at: LatLon) {
    val url = Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${at.lat},${at.lon}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.google.android.apps.maps"))
    } catch (_: ActivityNotFoundException) {
        // 没装 Google 地图就用浏览器开；浏览器也没有就提示一下，不要闪退
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, tr("没有能打开实景的 App", "No app can open Street View"), Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 点了「结束」先确认一下。导航中还可以只停导航、接着记录；
 * 「继续骑」放在最显眼的位置，误触了点它就行
 */
@Composable
private fun FinishDialog(
    navigating: Boolean,
    recording: Boolean,
    onFinish: () -> Unit,
    onStopNavigation: () -> Unit,
    onDismiss: () -> Unit,
) {
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        title = { Text(if (recording) tr("结束这次骑行？", "End this ride?") else tr("结束导航？", "End navigation?")) },
        text = {
            Text(
                when {
                    recording && navigating -> tr("导航和记录一起停，记录会保存下来。只想停导航的话可以接着记录。", "Stops navigation and recording; the ride will be saved. To stop only navigation, keep recording.")
                    recording -> tr("记录会保存下来。", "The ride will be saved.")
                    else -> tr("不再提示怎么走。", "Turn-by-turn directions will stop.")
                },
                color = InkSoft,
            )
        },
        // 一个一行、占满宽：骑着车点，按钮大一点、分得清楚
        stacked = true,
        confirmButton = {
            DialogButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr("继续骑", "Keep riding"), fontSize = 17.sp) }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recording && navigating) {
                    DialogButton(onClick = onStopNavigation, modifier = Modifier.fillMaxWidth().neu(GlassShapes.Button)) { Text(tr("只停导航", "Stop navigation only")) }
                }
                DialogButton(onClick = onFinish, modifier = Modifier.fillMaxWidth(), danger = true, primary = true) { Text(tr("结束", "End")) }
            }
        },
    )
}

/** 收藏时起个名字；家、公司一点就行 */
@Composable
private fun SavePlaceDialog(place: Place, onSave: (SavedPlace) -> Unit, onDismiss: () -> Unit) {
    // 长按地图选的点没有名字，不拿「地图上的点」当默认名
    var name by remember { mutableStateOf(if (place.detail.isEmpty() && isMapPlaceName(place.name)) "" else place.name) }
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        title = { Text(tr("收藏这个地方", "Save this place")) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SavedPlace.HOME, SavedPlace.WORK).forEach { preset ->
                        FilterChip(selected = name == preset, onClick = { name = preset }, label = { Text(SavedPlace.label(preset)) })
                    }
                }
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("名字", "Name")) }, singleLine = true)
            }
        },
        confirmButton = {
            DialogButton(enabled = name.isNotBlank(), onClick = { onSave(SavedPlace(name.trim(), place.location)) }) { Text(tr("收藏", "Save")) }
        },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** 地图上显示我的位置（蓝点）；有上次的位置就先把镜头移过去 */
@SuppressLint("MissingPermission")
private fun enableMyLocation(context: Context, map: MapLibreMap, style: Style) {
    val component = map.locationComponent
    if (component.isLocationComponentActivated) return
    component.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.COMPASS
    flyToMyLocation(map)
}

/** 规划路线要用的位置：GPS 和网络定位都听，哪个先到用哪个 */
@SuppressLint("MissingPermission")
@Composable
private fun LocationUpdates(enabled: Boolean, onFix: (Fix) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val manager = context.getSystemService(LocationManager::class.java)
        if (!enabled || manager == null) return@DisposableEffect onDispose {}
        val listener = Fix.listener(onFix)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
            .forEach { provider ->
                // 只给了大概位置时 Android 12 以下不让听 GPS，跳过它只用网络定位
                runCatching {
                    manager.getLastKnownLocation(provider)?.let { onFix(Fix.of(it)) }
                    manager.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper())
                }
            }
        onDispose { manager.removeUpdates(listener) }
    }
}

/** 地图中心每 1 dp 代表多少米（比例尺用） */
private fun metersPerDp(map: MapLibreMap, density: Float): Double =
    map.projection.getMetersPerPixelAtLatitude(map.cameraPosition.target?.latitude ?: SINGAPORE.latitude) * density

private fun flyToMyLocation(map: MapLibreMap) {
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) return
    val location = component.lastKnownLocation ?: return
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
}

/** 用微信、Telegram 之类把队伍码发给队友 */
private fun shareTeamCode(context: Context, code: String) {
    val text = tr("一起骑车！打开 Rando，点右边的「一起骑」，输入队伍码 $code", "Let's ride together! Open Rando, tap Group ride on the right and enter team code $code")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, tr("分享队伍码", "Share team code")))
}

/** 结束这次骑行：导航和记录一起停，轨迹够长就存下来 */
private fun finishRide(notices: Notices, session: RideSession): Ride? {
    session.stopNavigation()
    val ride = session.stopRecording()
    if (ride == null) notices.show(tr("骑得太短，没有保存记录", "Ride too short, not saved"))
    return ride
}

@Composable
private fun appBuild(): Long {
    val context = LocalContext.current
    return remember {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }
}

private const val KEY_RIDE_VIEW = "ride_view"
