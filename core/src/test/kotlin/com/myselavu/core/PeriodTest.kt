package com.myselavu.core

import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.period.PeriodCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun calendarMonthWithStartDay1() {
        val p = PeriodCalendar.periodContaining(d("2026-11-15"), 1)
        assertEquals(BudgetPeriod(d("2026-11-01"), d("2026-11-30")), p)
        assertEquals(30, p.lengthInDays)
        assertEquals(YearMonth.of(2026, 11), p.labelMonth)
    }

    @Test fun startDay28SpansTwoMonths() {
        val p = PeriodCalendar.periodContaining(d("2026-11-10"), 28)
        assertEquals(BudgetPeriod(d("2026-10-28"), d("2026-11-27")), p)
        assertEquals(YearMonth.of(2026, 11), p.labelMonth)
        assertEquals(BudgetPeriod(d("2026-11-28"), d("2026-12-27")), PeriodCalendar.periodContaining(d("2026-11-28"), 28))
    }

    @Test fun februaryWithCustomStartDay() {
        // Non-leap year 2027
        val p = PeriodCalendar.periodContaining(d("2027-02-10"), 28)
        assertEquals(BudgetPeriod(d("2027-01-28"), d("2027-02-27")), p)
        assertEquals(31, p.lengthInDays)
        val next = PeriodCalendar.nextPeriod(p, 28)
        assertEquals(BudgetPeriod(d("2027-02-28"), d("2027-03-27")), next)
        assertEquals(28, next.lengthInDays)
        // Leap year 2028, start day 1
        val feb = PeriodCalendar.periodContaining(d("2028-02-29"), 1)
        assertEquals(BudgetPeriod(d("2028-02-01"), d("2028-02-29")), feb)
        assertEquals(29, feb.lengthInDays)
        // Start day 15 across February
        val p15 = PeriodCalendar.periodContaining(d("2027-02-20"), 15)
        assertEquals(BudgetPeriod(d("2027-02-15"), d("2027-03-14")), p15)
        assertEquals(28, p15.lengthInDays)
    }

    @Test fun nextPeriodIsContiguousForAYear() {
        for (startDay in listOf(1, 5, 15, 28)) {
            var p = PeriodCalendar.periodContaining(d("2026-01-20"), startDay)
            repeat(24) {
                val next = PeriodCalendar.nextPeriod(p, startDay)
                assertEquals(p.end.plusDays(1), next.start)
                assertEquals(startDay, next.start.dayOfMonth)
                assertEquals(next, PeriodCalendar.periodContaining(next.start, startDay))
                p = next
            }
        }
    }

    @Test fun changingStartDayMakesALongTransitionPeriod() {
        val oldPeriod = BudgetPeriod(d("2026-10-28"), d("2026-11-27"))
        val next = PeriodCalendar.nextPeriod(oldPeriod, 1)
        // Would be a 3-day "month" (28–30 Nov) – instead runs to 31 Dec.
        assertEquals(BudgetPeriod(d("2026-11-28"), d("2026-12-31")), next)
        assertEquals(BudgetPeriod(d("2027-01-01"), d("2027-01-31")), PeriodCalendar.nextPeriod(next, 1))

        val p1 = BudgetPeriod(d("2026-11-01"), d("2026-11-30"))
        val t = PeriodCalendar.nextPeriod(p1, 25)
        assertEquals(BudgetPeriod(d("2026-12-01"), d("2026-12-24")), t)
        assertTrue(t.lengthInDays >= PeriodCalendar.MIN_TRANSITION_DAYS)
    }

    @Test fun daysElapsedAndLeft() {
        val p = BudgetPeriod(d("2026-11-01"), d("2026-11-30"))
        assertEquals(1, p.daysElapsed(d("2026-11-01")))
        assertEquals(30, p.daysLeftIncludingToday(d("2026-11-01")))
        assertEquals(30, p.daysElapsed(d("2026-11-30")))
        assertEquals(1, p.daysLeftIncludingToday(d("2026-11-30")))
        assertEquals(0, p.daysLeftIncludingToday(d("2026-12-05")))
        assertEquals(10, p.daysElapsed(d("2026-11-10")))
        assertEquals(21, p.daysLeftIncludingToday(d("2026-11-10")))
        assertTrue(d("2026-11-30") in p)
        assertFalse(d("2026-12-01") in p)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startDayAbove28IsRejected() {
        PeriodCalendar.periodContaining(d("2026-11-01"), 29)
    }
}
