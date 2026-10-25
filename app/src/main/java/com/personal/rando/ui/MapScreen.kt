package com.personal.rando.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import dev.chrisbanes.haze.hazeSource
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.location.LocationListener
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
    val navigation by vm.session.navigation.collectAsStateWithLifecycle()
    val recording by vm.session.recording.collectAsStateWithLifecycle()
    val saved by vm.savedPlaces.places.collectAsStateWithLifecycle()
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
            Toast.makeText(photoAppContext, "没有「身体活动」权限，认不出在骑车", Toast.LENGTH_LONG).show()
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
            home == null -> vm.session.say("先把家收藏起来，才知道坐到哪个站").also {
                Toast.makeText(mrtContext, "先把「家」收藏起来", Toast.LENGTH_LONG).show()
            }
            here == null -> Toast.makeText(mrtContext, "还没定位到", Toast.LENGTH_SHORT).show()
            else -> {
                val plan = MrtHome.plan(mrtContext.rando.mrt, here, home.location)
                if (plan == null) {
                    vm.session.say("离家不远，直接骑回去吧")
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
            Toast.makeText(photoContext, "车位照片存好了", Toast.LENGTH_SHORT).show()
        }
    }
    val photoParking = {
        val uri = FileProvider.getUriForFile(photoContext, "${photoContext.packageName}.files", parking.photoFile)
        runCatching { takePhoto.launch(uri) }.onFailure { Toast.makeText(photoContext, "打不开相机", Toast.LENGTH_SHORT).show() }
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

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(SINGAPORE).zoom(11.0).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.addOnMapLongClickListener { latLng ->
                vm.onMapLongPress(LatLon(latLng.latitude, latLng.longitude))
                true
            }
            m.setStyle(MapLayers.STYLE_URL) { s ->
                MapLayers.install(s)
                style = s
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
    // 打开对讲：先要麦克风权限；开了以后让前台服务带上麦克风，锁屏也能听
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            walkie.turnOn()
            RideService.start(context)
        } else {
            Toast.makeText(context, "没有麦克风权限，开不了对讲", Toast.LENGTH_SHORT).show()
        }
    }
    val toggleTalk = {
        when {
            walkieState.on -> walkie.turnOff()
            walkie.hasMicPermission() -> {
                walkie.turnOn()
                RideService.start(context)
            }
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // 语音指令：听懂了就在这里做
    val session = vm.session
    DisposableEffect(voice) {
        voice.handler = { cmd ->
            when (cmd) {
                is VoiceCommand.GoTo -> {
                    session.say("好的，去${cmd.place}")
                    vm.goTo(cmd.place) { session.say(it) }
                }
                is VoiceCommand.Loop -> {
                    session.say("好的，骑一圈 ${cmd.km} 公里")
                    vm.startLoop(cmd.km)
                }
                is VoiceCommand.Route -> if (vm.startSavedRoute(cmd.name)) session.say("好的，骑${cmd.name}") else session.say("没有叫${cmd.name}的收藏路线")
                VoiceCommand.StopNavigation -> if (session.navigation.value != null) {
                    session.stopNavigation()
                    session.say("导航已结束，还在记录")
                } else {
                    session.say("现在没在导航")
                }
                VoiceCommand.Remaining -> session.say(session.remainingText())
                is VoiceCommand.Report -> session.say(if (session.reportHazard(cmd.type)) "已标记${cmd.type.label}" else "还没定位到，标不了")
                is VoiceCommand.Walkie -> when {
                    group.state.value == null -> session.say("先加入一起骑才能对讲")
                    cmd.on -> {
                        walkie.turnOn()
                        RideService.start(context)
                        session.say("对讲已打开")
                    }
                    else -> {
                        walkie.turnOff()
                        session.say("对讲已关闭")
                    }
                }
                VoiceCommand.Weather -> session.say(session.weatherText())
                VoiceCommand.MrtHome -> mrtHome()
                is VoiceCommand.Unknown -> session.say(if (cmd.text.isBlank()) "没听清，再说一次" else "没听懂：${cmd.text}")
            }
        }
        onDispose { voice.handler = null }
    }
    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.listen() else Toast.makeText(context, "没有麦克风权限，用不了语音指令", Toast.LENGTH_SHORT).show()
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
    val finishRide = {
        leaveGroup()
        finished = finishRide(context, vm.session)
    }
    // 一起骑就是一次骑行：进队就开始记录，前台服务让定位和连接在锁屏后也不断
    val joinGroup = { code: String, name: String ->
        groupDialog = false
        askNotifications()
        group.join(code, name)
        vm.session.startRecording()
    }
    LaunchedEffect(style, teammates) {
        style?.let { MapLayers.showTeam(it, teammates) }
    }
    val returnTrip = { ride: Ride ->
        finished = null
        Toast.makeText(context, "正在算原路返回的路线…", Toast.LENGTH_SHORT).show()
        askNotifications()
        vm.returnTrip(ride.points.map { it.location }) { Toast.makeText(context, "原路返回失败：$it", Toast.LENGTH_LONG).show() }
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
    LaunchedEffect(riding) {
        if (riding && viewPrefs.getBoolean(KEY_RIDE_VIEW, false)) rideView = true
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
            with(density) {
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds, 48.dp.roundToPx(), 200.dp.roundToPx(), 48.dp.roundToPx(), 380.dp.roundToPx(),
                    )
                )
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
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80.dp.roundToPx(), 240.dp.roundToPx(), 80.dp.roundToPx(), 260.dp.roundToPx()),
                    700,
                )
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focused.location.lat, focused.location.lon), 17.5), 700)
            }
        }
    }
    var saving by remember { mutableStateOf<Place?>(null) }
    val focusManager = LocalFocusManager.current

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
                        onStop = finishRide,
                        teammates = teammatesText(teammates, fix?.location),
                        walkie = walkieText(walkieState).takeIf { groupState != null },
                        onVoice = listen,
                        onMrt = mrtHome,
                        listening = (voiceState as? VoiceState.Listening)?.let { it.partial.ifEmpty { "请说…" } },
                        weightKg = settings.weightKg,
                        onReport = { type ->
                            val ok = vm.session.reportHazard(type)
                            Toast.makeText(context, if (ok) "已标记：${type.label}" else "还没定位到，标不了", Toast.LENGTH_SHORT).show()
                        },
                    ) { RideMap(navigation, fix, recording?.track?.map { t -> t.location }.orEmpty(), teammates, it, hazards) }
                }
                Layer.NAVIGATION -> navigation?.let { nav ->
                    Box(Modifier.fillMaxSize()) {
                        NavigationOverlay(
                            nav,
                            onRideView = { openRideView(true) },
                            onStop = finishRide,
                        )
                        RecenterButton(follow, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
                    }
                }
                Layer.PLANNER -> Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchBar(
                            query = state.query,
                            hint = when {
                                state.pickingStart -> "搜索起点"
                                state.pickingVia -> "搜索途经点"
                                else -> "去哪儿骑？"
                            },
                            results = state.results,
                            build = appBuild(),
                            onQuery = vm::onQueryChange,
                            onPick = {
                                focusManager.clearFocus()
                                vm.choosePlace(it)
                            },
                            onOpenRides = onOpenRides,
                            onCheckUpdate = onCheckUpdate,
                            onSettings = { settingsDialog = true },
                            onMrtHome = mrtHome,
                            onOfflineMaps = {
                                offlineMaps.refresh()
                                offlineDialog = true
                            },
                        )
                        PrefsRow(state.prefs, vm::setPrefs)
                        (voiceState as? VoiceState.Listening)?.let { HintPill("正在听：${it.partial.ifEmpty { "请说…" }}", onCancel = voice::cancel) }
                        AnimatedVisibility(
                            state.pickingStart || state.pickingVia,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                        ) {
                            HintPill("长按地图选${if (state.pickingVia) "途经点" else "起点"}，或者在上面搜一个地方", onCancel = {
                                focusManager.clearFocus()
                                vm.cancelPicking()
                            })
                        }
                    }
                    if (state.pickingStart || state.pickingVia) BackHandler { vm.cancelPicking() }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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
                        )
                        AnimatedVisibility(
                            groupState != null,
                            enter = fadeIn() + slideInVertically(Motion.bouncy()) { it },
                            exit = fadeOut() + slideOutVertically { it },
                        ) {
                            groupState?.let { g ->
                                GroupPill(
                                    g,
                                    onShare = { shareTeamCode(context, g.code) },
                                    onLeave = leaveGroup,
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
                                    onShare = { scope.launch { ShareCard.share(context, ride, record) } },
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
                            recording?.let { RecordingPill(it, onRideView = { openRideView(true) }, onStop = finishRide) }
                        }
                        AnimatedContent(
                            targetState = focus,
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
                                SegmentCard(
                                    info = f.items[f.index],
                                    index = f.index,
                                    count = f.items.size,
                                    onNext = { focus = f.copy(index = (f.index + 1) % f.items.size) },
                                    onStreetView = { openStreetView(context, f.items[f.index].location) },
                                    onClose = { focus = null },
                                )
                            } else {
                                PlannerSheet(
                                    state = state,
                                    saved = saved,
                                    savedRoutes = savedRoutes,
                                    parked = parked,
                                    here = fix?.location,
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
                                                val items = if (target == NoteTarget.AMENITIES) {
                                                    s.amenities.targets()
                                                } else {
                                                    (s.route as? RouteState.Ready)?.route?.targets(target).orEmpty()
                                                }
                                                if (items.isNotEmpty()) focus = Focus(items, 0)
                                            },
                                            onPace = vm::setPace,
                                            onSaveRoute = { savingRoute = true },
                                            onChooseRoute = vm::chooseSavedRoute,
                                            onRemoveRoute = vm.savedRoutes::remove,
                                            onParked = { parkedDialog = true },
                                        )
                                    },
                                )
                            }
                        }
                    }
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
                askNotifications()
                vm.navigateTo(plan.boardExit.location, "${plan.board.zh}站") { Toast.makeText(context, "导航失败：$it", Toast.LENGTH_LONG).show() }
            },
            onDismiss = { mrtPlan = null },
        )
    }
    if (settingsDialog) {
        SettingsDialog(
            settings,
            onSave = {
                settingsStore.update(it)
                settingsDialog = false
                if (it.autoStart && !AutoStart.hasRecognitionPermission(context) && Build.VERSION.SDK_INT >= 29) {
                    recognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    AutoStart.apply(context, it.autoStart)
                }
            },
            onDismiss = { settingsDialog = false },
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
        val suggested = state.destination?.name?.takeUnless { it.startsWith("地图上") }
            ?: state.loop?.let { "骑一圈 ${it.targetMeters / 1000} 公里" }.orEmpty()
        SaveRouteDialog(
            suggested,
            onSave = { name ->
                vm.saveRoute(name)
                savingRoute = false
                Toast.makeText(context, "已收藏「$name」", Toast.LENGTH_SHORT).show()
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
    crash?.let { alert -> CrashDialog(alert, onOk = vm.session::imOk, onSendNow = vm.session::sendSos) }
    sosList.firstOrNull()?.takeIf { crash == null }?.let { alert ->
        SosDialog(
            alert,
            me = fix?.location,
            onGo = {
                group.dismissSos(alert.id)
                askNotifications()
                vm.navigateTo(alert.location, "${alert.name}的位置") { Toast.makeText(context, "导航失败：$it", Toast.LENGTH_LONG).show() }
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
                askNotifications()
                Toast.makeText(context, "正在算${offer.name}的路线…", Toast.LENGTH_SHORT).show()
                vm.followShared(offer) { Toast.makeText(context, "跟着骑失败：$it", Toast.LENGTH_LONG).show() }
            },
            onDecline = group::answerOffer,
        )
    }
    if (groupDialog) {
        GroupDialog(
            configured = group.configured,
            name = group.myName,
            onCreate = { name -> joinGroup(TeamCode.generate(), name) },
            onJoin = { code, name -> joinGroup(code, name) },
            onDismiss = { groupDialog = false },
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
        context.startActivity(Intent(Intent.ACTION_VIEW, url))
    }
}

/** 收藏时起个名字；家、公司一点就行 */
@Composable
private fun SavePlaceDialog(place: Place, onSave: (SavedPlace) -> Unit, onDismiss: () -> Unit) {
    // 长按地图选的点没有名字，不拿「地图上的点」当默认名
    var name by remember { mutableStateOf(if (place.detail.isEmpty() && place.name.startsWith("地图上")) "" else place.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("收藏这个地方") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SavedPlace.HOME, SavedPlace.WORK).forEach { preset ->
                        FilterChip(selected = name == preset, onClick = { name = preset }, label = { Text(preset) })
                    }
                }
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(SavedPlace(name.trim(), place.location)) }) { Text("收藏") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
        val listener = LocationListener { onFix(Fix.of(it)) }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
            .forEach { provider ->
                manager.getLastKnownLocation(provider)?.let { onFix(Fix.of(it)) }
                manager.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper())
            }
        onDispose { manager.removeUpdates(listener) }
    }
}

private fun flyToMyLocation(map: MapLibreMap) {
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) return
    val location = component.lastKnownLocation ?: return
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
}

/** 用微信、Telegram 之类把队伍码发给队友 */
private fun shareTeamCode(context: Context, code: String) {
    val text = "一起骑车！打开 Rando，点右边的「一起骑」，输入队伍码 $code"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "分享队伍码"))
}

/** 结束这次骑行：导航和记录一起停，轨迹够长就存下来 */
private fun finishRide(context: Context, session: RideSession): Ride? {
    session.stopNavigation()
    val ride = session.stopRecording()
    if (ride == null) Toast.makeText(context, "骑得太短，没有保存记录", Toast.LENGTH_SHORT).show()
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
