package com.personal.rando.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.rando.ride.Fix
import com.personal.rando.ride.NavigationState
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.SafetyScore
import com.personal.rando.search.Place
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
fun MapScreen(vm: RouteViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val navigation by vm.session.navigation.collectAsStateWithLifecycle()
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
    val mapView = rememberMapView()

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
    // 导航时由 RideService 收定位（锁屏也不停），这里只管规划时的
    LocationUpdates(enabled = hasLocation && !navigating) { vm.session.onFix(it) }

    // 导航结束的通知栏提示要这个权限；不给也能导航，只是通知栏看不到
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startNavigation = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.startNavigation()
    }

    // 导航时屏幕常亮，镜头跟着人走、朝前进方向转
    val view = LocalView.current
    DisposableEffect(navigating) {
        view.keepScreenOn = navigating
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(map, style, hasLocation, navigating) {
        val m = map ?: return@LaunchedEffect
        if (style == null || !hasLocation || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        m.locationComponent.apply {
            if (navigating) {
                renderMode = RenderMode.GPS
                cameraMode = CameraMode.TRACKING_GPS
                zoomWhileTracking(17.0)
                tiltWhileTracking(40.0)
            } else {
                cameraMode = CameraMode.NONE
                renderMode = RenderMode.COMPASS
                m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).tilt(0.0).build()))
            }
        }
    }

    // 路线变了就重画，并把镜头框住整条路线（上下留出搜索框和底部卡片的位置）。
    // 导航中画的是正在走的那条，骑偏重新规划后会换
    val route = navigation?.route ?: (state.route as? RouteState.Ready)?.route
    LaunchedEffect(style, route, state.start, state.destination) {
        val s = style ?: return@LaunchedEffect
        MapLayers.show(s, route?.points, state.start?.location, state.destination?.location)
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
                        bounds, 48.dp.roundToPx(), 200.dp.roundToPx(), 48.dp.roundToPx(), 300.dp.roundToPx(),
                    )
                )
            }
        } else {
            state.destination?.let { m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.location.lat, it.location.lon))) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        navigation?.let {
            NavigationOverlay(it, onStop = vm.session::stopNavigation)
            return@Box
        }

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SearchBox(state, vm)
            Spacer(Modifier.size(8.dp))
            ProfileChips(state.profile, vm::setProfile)
            if (state.pickingStart) {
                Spacer(Modifier.size(8.dp))
                Banner("长按地图选起点，或者在上面搜一个地方")
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (hasLocation) {
                SmallFloatingActionButton(
                    onClick = { map?.let(::flyToMyLocation) },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "回到我的位置", tint = RandoGreen)
                }
                Spacer(Modifier.size(8.dp))
            }
            PlannerCard(state, vm, startNavigation)
        }
    }
}

@Composable
private fun SearchBox(state: PlannerState, vm: RouteViewModel) {
    val focus = LocalFocusManager.current
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
        Column {
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (state.pickingStart) "搜索起点" else "去哪儿骑？") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (state.results.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.results) { place ->
                        PlaceRow(place) {
                            focus.clearFocus()
                            vm.choosePlace(place)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (place.detail.isNotEmpty()) {
            Text(
                place.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileChips(selected: BikeProfile, onSelect: (BikeProfile) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BikeProfile.entries.forEach { profile ->
            FilterChip(
                selected = profile == selected,
                onClick = { onSelect(profile) },
                label = { Text(profile.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun Banner(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = RandoOrange, contentColor = Color.White) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlannerCard(state: PlannerState, vm: RouteViewModel, onStartNavigation: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            val destination = state.destination
            if (destination == null) {
                Text("搜索目的地，或者长按地图选一个点", style = MaterialTheme.typography.bodyLarge)
                StartRow(state, vm)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    destination.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = vm::clearRoute) {
                    Icon(Icons.Default.Close, contentDescription = "取消路线")
                }
            }
            StartRow(state, vm)
            Spacer(Modifier.size(8.dp))
            when (val r = state.route) {
                RouteState.Idle -> Unit
                RouteState.WaitingForLocation -> Text("正在定位，定位到就开始算路…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RouteState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在找一条好骑的路…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is RouteState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("算路失败：${r.message}", Modifier.weight(1f), color = RandoRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = vm::retry) { Text("重试") }
                }
                is RouteState.Ready -> {
                    RouteSummary(r)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = onStartNavigation, modifier = Modifier.fillMaxWidth()) {
                        Text("开始导航")
                    }
                }
            }
        }
    }
}

@Composable
private fun StartRow(state: PlannerState, vm: RouteViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val start = state.start
        Text(
            "起点：${start?.name ?: "我的位置"}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (start != null) {
            TextButton(onClick = vm::useMyLocationAsStart) { Text("用我的位置") }
        } else if (!state.pickingStart) {
            TextButton(onClick = vm::startPickingStart) { Text("换起点") }
        }
    }
}

@Composable
private fun RouteSummary(ready: RouteState.Ready) {
    val route = ready.route
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Stat(Format.distance(route.distanceMeters), "距离")
        Stat(Format.duration(route.durationSeconds), "预计")
        Stat("${route.ascendMeters} 米", "爬升")
    }
    val safety = ready.safety ?: return
    Spacer(Modifier.size(10.dp))
    val color = when {
        safety.score >= SafetyScore.QUIET -> RandoGreen
        safety.score >= 60 -> RandoAmber
        else -> RandoRed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(8.dp), color = color, contentColor = Color.White) {
            Text(
                "安全 ${safety.score}",
                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${safety.label} · ${Format.percent(safety.quietShare)} 走自行车道或安静小路",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** MapView 要跟着 Activity 的生命周期走，否则地图不渲染或者泄漏 */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
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

@Composable
private fun NavigationOverlay(navigation: NavigationState, onStop: () -> Unit) {
    val progress = navigation.progress
    Column(Modifier.fillMaxSize()) {
        Surface(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = RandoGreen,
            contentColor = Color.White,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    progress == null -> NavText("等待 GPS 定位…")
                    progress.arrived -> NavText("已到达目的地")
                    navigation.rerouting || progress.offRoute -> NavText("已偏离路线，正在重新规划…")
                    else -> {
                        val next = progress.next
                        Text(next?.turn?.arrow ?: "⚑", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                Format.distance(progress.distanceToNext.toInt()),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(next?.text ?: "到达终点", style = MaterialTheme.typography.titleMedium)
                            val following = progress.following
                            if (following != null && progress.distanceNextToFollowing <= 100) {
                                Text("然后${following.text}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Stat(Format.distance(progress.remainingMeters.toInt()), "剩余")
                        Stat(Format.duration(progress.remainingSeconds), "还要")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(onClick = onStop) { Text("结束") }
            }
        }
    }
}

@Composable
private fun NavText(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}
