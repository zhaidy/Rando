package com.personal.rando.ride

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {

    private val g = CrashDetector.G

    /** 每 20 毫秒一个读数，[until] 之前一直喂 [magnitude]；返回有没有判断为摔车 */
    private fun CrashDetector.feed(from: Long, until: Long, magnitude: (Long) -> Double): Boolean {
        var crashed = false
        var t = from
        while (t < until) {
            if (onAccel(t, magnitude(t))) crashed = true
            t += 20
        }
        return crashed
    }

    @Test
    fun `骑着骑着重重一下，之后不动了，就是摔车`() {
        val d = CrashDetector()
        d.onSpeed(0, 6.0)
        assertFalse(d.feed(0, 1_000) { g + 1 })
        assertFalse(d.onAccel(1_000, 6 * g))
        d.onSpeed(3_000, 0.2)
        // 摔倒后几秒手机还在晃，然后躺着不动
        val crashed = d.feed(1_020, 15_000) { t -> if (t < 2_500) g + 8 else g + 0.3 }
        assertTrue(crashed)
    }

    @Test
    fun `过坑：重重一下但接着骑，不算`() {
        val d = CrashDetector()
        d.onSpeed(0, 6.0)
        d.onAccel(1_000, 5 * g)
        d.onSpeed(4_000, 5.5)
        d.onSpeed(5_000, 6.0)
        assertFalse(d.feed(1_020, 15_000) { g + 0.3 })
    }

    @Test
    fun `停着的时候手机掉地上，不算`() {
        val d = CrashDetector()
        d.onSpeed(0, 0.0)
        d.onAccel(30_000, 6 * g)
        assertFalse(d.feed(30_020, 45_000) { g })
    }

    @Test
    fun `冲击后手机一直在晃（被人捡起来、接着骑），不算`() {
        val d = CrashDetector()
        d.onSpeed(0, 6.0)
        d.onAccel(1_000, 6 * g)
        d.onSpeed(3_000, 0.5)
        assertFalse(d.feed(1_020, 15_000) { t -> if ((t / 100) % 2 == 0L) g + 4 else g - 4 })
    }

    @Test
    fun `冲击不够大，不算`() {
        val d = CrashDetector()
        d.onSpeed(0, 6.0)
        d.onAccel(1_000, 3 * g)
        d.onSpeed(3_000, 0.0)
        assertFalse(d.feed(1_020, 15_000) { g })
    }
}
