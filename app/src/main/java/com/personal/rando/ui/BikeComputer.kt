package com.personal.rando.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.RecordingState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CLOCK = SimpleDateFormat("HH:mm", Locale.CHINA)

/**
 * 码表：白底黑字大号数字，太阳底下也看得清。
 * 中间是当前速度，下面六格是这次骑行的数据；导航中顶部还有下一个路口
 */
@Composable
fun BikeComputer(
    recording: RecordingState,
    navigation: NavigationState?,
    onMap: () -> Unit,
    onStop: () -> Unit,
) {
    // 全程用时和右上角的钟每秒走一下，不等定位
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val average = if (recording.movingSeconds > 0) recording.distanceMeters / recording.movingSeconds else 0.0
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("码表", style = MaterialTheme.typography.titleMedium, color = Color.Gray, modifier = Modifier.weight(1f))
            Text(CLOCK.format(Date(now)), style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        }
        navigation?.let { NavBanner(it, Modifier.fillMaxWidth().padding(top = 8.dp)) }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format(Locale.US, "%.1f", recording.currentSpeed * 3.6),
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Text("公里/时", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Field(Format.distance(recording.distanceMeters.toInt()), "距离", Modifier.weight(1f))
            Field(Format.clock(recording.movingSeconds), "骑行时间", Modifier.weight(1f))
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Field(Format.speed(average), "均速", Modifier.weight(1f))
            Field(Format.speed(recording.maxSpeed), "最快", Modifier.weight(1f))
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Field("${recording.ascendMeters.toInt()} 米", "爬升", Modifier.weight(1f))
            Field(Format.clock(((now - recording.startedAt) / 1000).coerceAtLeast(0)), "全程用时", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) { Text("地图") }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = RandoOrange),
            ) { Text("结束") }
        }
    }
}

@Composable
private fun Field(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}
