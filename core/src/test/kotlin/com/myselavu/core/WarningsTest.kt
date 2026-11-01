package com.myselavu.core

import com.myselavu.core.money.Money
import com.myselavu.core.warning.SpendingPattern
import com.myselavu.core.warning.WarningLevel
import com.myselavu.core.warning.WarningLevel.GREEN
import com.myselavu.core.warning.WarningLevel.RED
import com.myselavu.core.warning.WarningLevel.YELLOW
import com.myselavu.core.warning.WarningReason
import com.myselavu.core.warning.Warnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningsTest {
    private fun r(x: Long) = Money.rupees(x)
    private fun level(spent: Long, alloc: Long, elapsed: Int, days: Int = 30, curve: Double? = null): WarningLevel =
        Warnings.evaluate(r(spent), r(alloc), elapsed, days, curve).level

    @Test fun onTrackIsGreen() {
        // Day 15 of 30, half spent
        assertEquals(GREEN, level(500, 1000, 15))
        assertEquals(GREEN, level(0, 1000, 1))
    }

    @Test fun greenBoundaryAtTimePlusFivePercent() {
        // Day 15/30: timePct 0.5. used 0.55 → projected 1.10 > 1.0 → not green; 1.10 is not > 1.10 → YELLOW
        assertEquals(YELLOW, level(550, 1000, 15))
        // used 0.50 exactly: projected 1000 ≤ 1000 → GREEN
        assertEquals(GREEN, level(500, 1000, 15))
        // used 0.501 → projected 1002 > 1000 → YELLOW
        assertEquals(YELLOW, Warnings.evaluate(Money(50100), r(1000), 15, 30).level)
    }

    @Test fun redWhenProjectionOverTenPercent() {
        // Day 15/30, used 0.56 → projected 1120 > 1100
        val res = Warnings.evaluate(r(560), r(1000), 15, 30)
        assertEquals(RED, res.level)
        assertEquals(WarningReason.PROJECTED_OVER, res.reason)
        assertEquals(r(1120), res.projected)
    }

    @Test fun redWhenNinetyPercentUsedWithMoreThanThreeDaysLeft() {
        // Use a learned curve so projection stays low, isolating the 90% rule.
        // Day 26/30 → 4 days left after today. usedPct 0.90.
        val res = Warnings.evaluate(r(900), r(1000), 26, 30, expectedFraction = 0.95)
        assertEquals(RED, res.level)
        assertEquals(WarningReason.MOSTLY_USED_EARLY, res.reason)
        // Day 27/30 → exactly 3 days left: not "more than 3" → falls through to green/yellow
        val res2 = Warnings.evaluate(r(900), r(1000), 27, 30, expectedFraction = 0.95)
        assertTrue(res2.level != RED)
    }

    @Test fun overspentIsAlwaysRed() {
        val res = Warnings.evaluate(r(1001), r(1000), 30, 30)
        assertEquals(RED, res.level)
        assertEquals(WarningReason.OVERSPENT, res.reason)
        assertEquals(r(-1), res.remaining)
        assertEquals(Money.ZERO, res.perDayLeft)
    }

    @Test fun lastDayExactlySpentIsGreen() {
        assertEquals(GREEN, level(1000, 1000, 30))
    }

    @Test fun zeroAllocation() {
        assertEquals(GREEN, level(0, 0, 10))
        assertEquals(RED, level(1, 0, 10))
    }

    @Test fun perDayMessageNumbers() {
        // ₹340 left over 20 days (day 11 of 30) → ₹17/day
        val res = Warnings.evaluate(r(660), r(1000), 11, 30)
        assertEquals(20, res.daysLeft)
        assertEquals(r(17), res.perDayLeft)
        assertEquals(r(1800), res.projected)
        // ₹341 over 20 days = ₹17.05 → shown as whole rupees
        assertEquals(r(17), Warnings.evaluate(r(659), r(1000), 11, 30).perDayLeft)
        assertEquals(RED, res.level)
    }

    @Test fun projectionUsesLinearRate() {
        val res = Warnings.evaluate(r(300), r(3000), 10, 30)
        assertEquals(r(900), res.projected)
        assertEquals(GREEN, res.level)
    }

    @Test fun worstLevel() {
        assertEquals(RED, Warnings.worst(listOf(GREEN, RED, YELLOW)))
        assertEquals(GREEN, Warnings.worst(emptyList()))
    }

    // ---- Pattern learning ----

    private fun month(days: Int, vararg spends: Pair<Int, Long>): SpendingPattern.ClosedMonth {
        val list = MutableList(days) { Money.ZERO }
        for ((day, rupees) in spends) list[day - 1] = r(rupees)
        return SpendingPattern.ClosedMonth(list)
    }

    @Test fun fallsBackToLinearWithLessThanTwoMonths() {
        assertNull(SpendingPattern.expectedFraction(emptyList(), 10, 30))
        assertNull(SpendingPattern.expectedFraction(listOf(month(30, 1 to 100)), 10, 30))
        // A month with no spending at all doesn't count
        assertNull(SpendingPattern.expectedFraction(listOf(month(30, 1 to 100), month(31)), 10, 30))
    }

    @Test fun learnsFrontLoadedSpending() {
        // Groceries bought at the start of each month: 80% on day 1, 20% on day 20
        val history = listOf(month(30, 1 to 800, 20 to 200), month(31, 1 to 800, 20 to 200))
        val f = SpendingPattern.expectedFraction(history, 5, 30)
        assertNotNull(f)
        assertEquals(0.8, f!!, 0.01)
        // Spending 75% by day 5 is normal for this category → GREEN, while linear would say RED
        assertEquals(GREEN, level(750, 1000, 5, curve = f))
        assertEquals(RED, level(750, 1000, 5))
    }

    @Test fun learnedCurveNeverDropsBelowHalfOfLinear() {
        // Everything spent on the last day historically → curve ~0 early on
        val history = listOf(month(30, 30 to 1000), month(30, 30 to 1000))
        val f = SpendingPattern.expectedFraction(history, 10, 30)!!
        assertEquals(10.0 / 30 * SpendingPattern.FLOOR_OF_LINEAR, f, 1e-9)
    }

    @Test fun cumulativeShareInterpolates() {
        val m = month(10, 1 to 100, 10 to 100)
        assertEquals(0.5, m.cumulativeShareAtDay(1), 1e-9)
        assertEquals(1.0, m.cumulativeShareAtDay(10), 1e-9)
        assertEquals(0.5, m.cumulativeShareAt(0.5), 1e-9)
        assertEquals(1.0, m.cumulativeShareAt(1.0), 1e-9)
        assertEquals(0.25, m.cumulativeShareAt(0.05), 1e-9)
    }
}
