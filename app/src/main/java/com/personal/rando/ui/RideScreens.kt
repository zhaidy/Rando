package com.personal.rando.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.rando.rando
import com.personal.rando.ride.Ride
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE = SimpleDateFormat("M月d日 EEE HH:mm", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val rides by produceState<List<Ride>?>(null) { value = store.list() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("骑行记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        val list = rides
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "还没有记录。在地图上点橙色的 ▶ 开始记录，或者开始导航时会自动记录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TotalsCard(list) }
                items(list, key = { it.id }) { ride -> RideCard(ride) { onOpen(ride.id) } }
            }
        }
    }
}

@Composable
private fun TotalsCard(rides: List<Ride>) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = RandoGreen, contentColor = androidx.compose.ui.graphics.Color.White) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("${rides.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("次骑行", style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text(Format.distance(rides.sumOf { it.distanceMeters }.toInt()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("总距离", style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text(Format.clock(rides.sumOf { it.movingSeconds }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("总骑行时间", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RideCard(ride: Ride, onClick: () -> Unit) {
    Surface(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(ride.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                DATE.format(Date(ride.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SmallStat(Format.distance(ride.distanceMeters.toInt()), "距离")
                SmallStat(Format.clock(ride.movingSeconds), "骑行时间")
                SmallStat(Format.speed(ride.averageSpeed), "均速")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(id: Long, onBack: () -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val scope = rememberCoroutineScope()
    val ride by produceState<Ride?>(null, id) { value = store.get(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ride?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                },
            )
        },
    ) { padding ->
        val r = ride ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            TrackMap(r, Modifier.fillMaxWidth().weight(1f))
            Column(Modifier.padding(16.dp)) {
                Text(DATE.format(Date(r.startedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SmallStat(Format.distance(r.distanceMeters.toInt()), "距离")
                    SmallStat(Format.clock(r.movingSeconds), "骑行时间")
                    SmallStat("${r.ascendMeters.toInt()} 米", "爬升")
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SmallStat(Format.speed(r.averageSpeed), "均速")
                    SmallStat(Format.speed(r.maxSpeed), "最快")
                    SmallStat(Format.clock((r.endedAt - r.startedAt) / 1000), "全程用时")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = { Text("删掉就找不回来了。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        store.delete(id)
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 地图上画出这次的轨迹，镜头框住全程 */
@Composable
private fun TrackMap(ride: Ride, modifier: Modifier) {
    val mapView = rememberMapView(textureMode = true)
    val density = LocalDensity.current
    var style by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(MapLayers.STYLE_URL) { s ->
                MapLayers.install(s)
                style = s
                val track = ride.points.map { it.location }
                MapLayers.showTrack(s, track)
                if (track.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(track.map { LatLng(it.lat, it.lon) }).build()
                    val pad = with(density) { 32.dp.roundToPx() }
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad))
                }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier.height(320.dp))
}

@Composable
private fun SmallStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
