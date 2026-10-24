package com.personal.rando.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `一公里以内用米`() {
        assertEquals("850 米", Format.distance(850))
        assertEquals("15.9 公里", Format.distance(15863))
    }

    @Test
    fun `时间向上取整到分钟`() {
        assertEquals("1 分钟", Format.duration(5))
        assertEquals("47 分钟", Format.duration(2794))
        assertEquals("1 小时", Format.duration(3600))
        assertEquals("1 小时 5 分", Format.duration(3900))
    }

    @Test
    fun `百分比四舍五入`() {
        assertEquals("90%", Format.percent(0.9))
        assertEquals("67%", Format.percent(2.0 / 3))
    }
}
