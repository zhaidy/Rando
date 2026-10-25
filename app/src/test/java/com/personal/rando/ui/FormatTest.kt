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
    fun `胶囊里的距离不带空格`() {
        assertEquals("850米", Format.shortDistance(850))
        assertEquals("15.9公里", Format.shortDistance(15863))
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

    @Test
    fun `码表时间`() {
        assertEquals("0:05", Format.clock(5))
        assertEquals("12:34", Format.clock(754))
        assertEquals("1:02:03", Format.clock(3723))
    }

    @Test
    fun `速度换成公里每小时`() {
        assertEquals("18.0 公里/时", Format.speed(5.0))
    }

    @Test
    fun `占比不到 1% 写小于 1%`() {
        assertEquals("<1%", Format.share(0.003))
        assertEquals("0%", Format.share(0.0))
        assertEquals("4%", Format.share(0.04))
    }
}
