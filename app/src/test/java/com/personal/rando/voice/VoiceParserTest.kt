package com.personal.rando.voice

import com.personal.rando.places.HazardType
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceParserTest {

    private fun p(s: String) = VoiceParser.parse(s)

    @Test
    fun `去哪儿`() {
        assertEquals(VoiceCommand.GoTo("家"), p("导航回家"))
        assertEquals(VoiceCommand.GoTo("家"), p("回家。"))
        assertEquals(VoiceCommand.GoTo("公司"), p("带我去公司"))
        assertEquals(VoiceCommand.GoTo("Funan"), p("去 Funan"))
        assertEquals(VoiceCommand.GoTo("滨海湾花园"), p("导航到滨海湾花园"))
        assertEquals(VoiceCommand.GoTo("东海岸公园"), p("导航东海岸公园"))
    }

    @Test
    fun `骑一圈和收藏的路线`() {
        assertEquals(VoiceCommand.Loop(20), p("骑一圈二十公里"))
        assertEquals(VoiceCommand.Loop(25), p("一圈 25 公里"))
        assertEquals(VoiceCommand.Loop(10), p("骑一圈十公里"))
        assertEquals(VoiceCommand.Loop(20), p("骑一圈二十公里吧"))
        assertEquals(VoiceCommand.Route("东海岸一圈"), p("骑东海岸一圈"))
        assertEquals(VoiceCommand.Route("上班"), p("骑上班那条"))
        assertEquals(VoiceCommand.Loop(20), p("骑二十公里"))
    }

    @Test
    fun `导航中`() {
        assertEquals(VoiceCommand.StopNavigation, p("结束导航"))
        assertEquals(VoiceCommand.StopNavigation, p("停止"))
        assertEquals(VoiceCommand.Remaining, p("还有多远"))
        assertEquals(VoiceCommand.Remaining, p("还要多久"))
        assertEquals(VoiceCommand.Report(HazardType.CONSTRUCTION), p("这里在施工"))
        assertEquals(VoiceCommand.Report(HazardType.CLOSED), p("前面封路了"))
        assertEquals(VoiceCommand.Report(HazardType.BAD_SURFACE), p("这里有个坑"))
        assertEquals(VoiceCommand.Walkie(true), p("打开对讲"))
        assertEquals(VoiceCommand.Walkie(false), p("关掉对讲"))
        assertEquals(VoiceCommand.Walkie(false), p("不要对讲了"))
        assertEquals(VoiceCommand.Walkie(false), p("别开对讲"))
        // 对讲连不上不是封路
        assertEquals(VoiceCommand.Walkie(true), p("对讲不通"))
        assertEquals(VoiceCommand.StopNavigation, p("关闭导航"))
        assertEquals(VoiceCommand.StopNavigation, p("关掉导航"))
        assertEquals(VoiceCommand.Weather, p("会不会下雨"))
        assertEquals(VoiceCommand.MrtHome, p("坐地铁回家"))
        assertEquals(VoiceCommand.MrtHome, p("搭MRT回家"))
    }

    @Test
    fun `听不懂的`() {
        assertEquals(VoiceCommand.Unknown("今天吃什么"), p("今天吃什么"))
        assertEquals(VoiceCommand.Unknown("导航"), p("导航"))
    }

    @Test
    fun `中文数字`() {
        assertEquals(20, VoiceParser.number("二十"))
        assertEquals(25, VoiceParser.number("二十五"))
        assertEquals(10, VoiceParser.number("十"))
        assertEquals(15, VoiceParser.number("十五"))
        assertEquals(100, VoiceParser.number("一百"))
        assertEquals(2, VoiceParser.number("两"))
    }

    @Test
    fun `英文说法`() {
        assertEquals(VoiceCommand.GoTo("家"), p("Navigate home"))
        assertEquals(VoiceCommand.GoTo("家"), p("take me home."))
        assertEquals(VoiceCommand.GoTo("家"), p("Home"))
        assertEquals(VoiceCommand.GoTo("Funan"), p("Take me to Funan"))
        assertEquals(VoiceCommand.GoTo("公司"), p("take me to work"))
        assertEquals(VoiceCommand.GoTo("公司"), p("navigate to the office"))
        assertEquals(VoiceCommand.GoTo("家"), p("directions to my home"))
        assertEquals(VoiceCommand.GoTo("Gardens by the Bay"), p("navigate to Gardens by the Bay"))
        assertEquals(VoiceCommand.Loop(20), p("ride a 20 km loop"))
        assertEquals(VoiceCommand.Loop(25), p("Loop twenty-five kilometres"))
        assertEquals(VoiceCommand.Route("East Coast"), p("Ride the East Coast route"))
        assertEquals(VoiceCommand.StopNavigation, p("Stop navigation"))
        assertEquals(VoiceCommand.StopNavigation, p("stop"))
        assertEquals(VoiceCommand.Remaining, p("How far is it?"))
        assertEquals(VoiceCommand.Remaining, p("how much longer"))
        assertEquals(VoiceCommand.Report(HazardType.CONSTRUCTION), p("There's construction here"))
        assertEquals(VoiceCommand.Report(HazardType.CLOSED), p("road closed ahead"))
        assertEquals(VoiceCommand.Report(HazardType.BAD_SURFACE), p("big pothole here"))
        assertEquals(VoiceCommand.Walkie(true), p("turn on the walkie"))
        assertEquals(VoiceCommand.Walkie(false), p("turn off walkie talkie"))
        assertEquals(VoiceCommand.Weather, p("is it going to rain"))
        assertEquals(VoiceCommand.MrtHome, p("take the MRT home"))
        assertEquals(VoiceCommand.MrtHome, p("take the train home"))
        assertEquals(VoiceCommand.Weather, p("is it raining"))
        assertEquals(VoiceCommand.Unknown("what's for lunch"), p("what's for lunch"))
    }

    @Test
    fun `英文数字`() {
        assertEquals(20, VoiceParser.englishNumber("twenty"))
        assertEquals(25, VoiceParser.englishNumber("twenty five"))
        assertEquals(100, VoiceParser.englishNumber("a hundred"))
        assertEquals(15, VoiceParser.englishNumber("15"))
    }
}
