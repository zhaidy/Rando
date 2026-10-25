package com.personal.rando.ride

import com.personal.rando.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class RideWidgetTest {
    @Test
    fun `小组件上的字`() {
        val week = PeriodTotal(0, "10/20", rides = 3, meters = 42_350.0, movingSeconds = 8_100)
        assertEquals(WidgetText("42.4", "3 次 · 2:15:00", "开始记录"), RideWidget.text(week, null))
        assertEquals("这周还没骑", RideWidget.text(week.copy(rides = 0, meters = 0.0, movingSeconds = 0), null).detail)
        assertEquals("记录中 · 8.4 公里", RideWidget.text(week, RecordingState(startedAt = 0, distanceMeters = 8_400.0)).button)
    }

    @Test
    fun `英文的小组件`() = Lang.with(english = true) {
        val week = PeriodTotal(0, "10/20", rides = 1, meters = 42_350.0, movingSeconds = 8_100)
        assertEquals(WidgetText("42.4", "1 ride · 2:15:00", "Start recording"), RideWidget.text(week, null))
        assertEquals("No rides this week", RideWidget.text(week.copy(rides = 0), null).detail)
    }

    @Test
    fun `矮到一行就换一行的布局`() {
        assertEquals(true, RideWidget.isRow(56))
        assertEquals(false, RideWidget.isRow(110))
        assertEquals(false, RideWidget.isRow(0))
    }
}
