package com.personal.rando.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeammatesLineTest {

    @Test
    fun `写名字，隐藏时只说几位`() {
        assertNull(ShareCard.teammatesLine(emptyList(), hide = false))
        assertEquals("和 爱妃、小王 一起骑", ShareCard.teammatesLine(listOf("爱妃", "小王"), hide = false))
        assertEquals("和 2 位队友一起骑", ShareCard.teammatesLine(listOf("爱妃", "小王"), hide = true))
        assertEquals("和 甲、乙、丙 等 5 人一起骑", ShareCard.teammatesLine(listOf("甲", "乙", "丙", "丁", "戊"), hide = false))
    }
}
