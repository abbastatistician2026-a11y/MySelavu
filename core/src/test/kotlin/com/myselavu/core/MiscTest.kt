package com.myselavu.core

import com.myselavu.core.debts.Debt
import com.myselavu.core.debts.DebtDirection
import com.myselavu.core.debts.DebtMath
import com.myselavu.core.goals.GoalMath
import com.myselavu.core.goals.SavingsGoal
import com.myselavu.core.goals.SavingsPot
import com.myselavu.core.insight.CategorySuggestions
import com.myselavu.core.insight.MonthChange
import com.myselavu.core.insight.NoSpendStreak
import com.myselavu.core.insight.UnusualExpense
import com.myselavu.core.money.Money
import com.myselavu.core.recurring.FixedDueDate
import com.myselavu.core.recurring.Frequency
import com.myselavu.core.recurring.RecurringSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MiscTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun r(x: Long) = Money.rupees(x)

    // ---- Recurring due dates ----

    @Test fun monthlyFrom31st() {
        val s = RecurringSchedule(d("2027-01-31"), Frequency.Monthly)
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31"),
            (0..4).map { s.occurrence(it).toString() },
        )
        assertEquals(d("2027-02-28"), s.nextAfter(d("2027-01-31")))
        assertEquals(d("2027-03-31"), s.nextAfter(d("2027-02-28")))
        assertEquals(d("2027-03-31"), s.nextOnOrAfter(d("2027-03-01")))
    }

    @Test fun leapYear29thFebruary() {
        val s = RecurringSchedule(d("2028-01-29"), Frequency.Monthly)
        assertEquals(d("2028-02-29"), s.nextAfter(d("2028-01-29")))
        val yearly = RecurringSchedule(d("2028-02-29"), Frequency.Yearly)
        assertEquals(d("2029-02-28"), yearly.nextAfter(d("2028-02-29")))
        assertEquals(d("2032-02-29"), yearly.occurrence(4))
    }

    @Test fun everyNMonths() {
        val s = RecurringSchedule(d("2026-11-15"), Frequency.EveryNMonths(3))
        assertEquals(d("2026-11-15"), s.nextOnOrAfter(d("2026-01-01")))
        assertEquals(d("2027-02-15"), s.nextAfter(d("2026-11-15")))
        assertEquals(d("2027-05-15"), s.nextOnOrAfter(d("2027-02-16")))
        assertEquals(listOf(d("2027-02-15"), d("2027-05-15")), s.occurrencesBetween(d("2027-01-01"), d("2027-06-30")))
        assertEquals(33400L, s.monthlyCostPaise(100200L)) // ₹1,002 every 3 months ≈ ₹334/month
    }

    @Test fun yearly() {
        val s = RecurringSchedule(d("2026-03-10"), Frequency.Yearly)
        assertEquals(d("2027-03-10"), s.nextOnOrAfter(d("2026-03-11")))
        assertEquals(d("2027-03-10"), s.nextOnOrAfter(d("2027-03-10")))
        assertEquals(10000L, s.monthlyCostPaise(120000L))
    }

    @Test fun nextOnOrAfterFarInFuture() {
        val s = RecurringSchedule(d("2020-01-31"), Frequency.Monthly)
        assertEquals(d("2030-02-28"), s.nextOnOrAfter(d("2030-02-01")))
    }

    @Test fun fixedDueDayInPeriod() {
        assertEquals(d("2026-11-05"), FixedDueDate.inPeriod(5, d("2026-11-01"), d("2026-11-30")))
        assertEquals(d("2026-11-30"), FixedDueDate.inPeriod(31, d("2026-11-01"), d("2026-11-30")))
        assertEquals(d("2027-02-28"), FixedDueDate.inPeriod(31, d("2027-02-01"), d("2027-02-28")))
        // Start-day-28 period: 28 Oct – 27 Nov; due on the 5th → 5 Nov; due on the 30th → 30 Oct
        assertEquals(d("2026-11-05"), FixedDueDate.inPeriod(5, d("2026-10-28"), d("2026-11-27")))
        assertEquals(d("2026-10-30"), FixedDueDate.inPeriod(30, d("2026-10-28"), d("2026-11-27")))
        assertEquals(d("2026-11-04"), FixedDueDate.reminderDate(d("2026-11-05")))
    }

    // ---- Goals ----

    @Test fun goalPerMonth() {
        val goal = SavingsGoal(target = r(20000), saved = r(5000), deadline = d("2027-03-31"))
        // Nov, Dec, Jan, Feb, Mar = 5 months; 15000 / 5 = 3000
        assertEquals(5, GoalMath.monthsLeft(d("2026-11-01"), d("2027-03-15")))
        assertEquals(r(3000), GoalMath.perMonthNeeded(goal, d("2026-11-20")))
        assertEquals(0.25, goal.progress, 1e-9)
    }

    @Test fun goalPerMonthRoundsUpToRupee() {
        val goal = SavingsGoal(target = r(10000), saved = Money.ZERO, deadline = d("2027-01-10"))
        // 3 months: 10000/3 = 3333.33 → ₹3,334
        assertEquals(r(3334), GoalMath.perMonthNeeded(goal, d("2026-11-01")))
    }

    @Test fun goalEdgeCases() {
        assertNull(GoalMath.perMonthNeeded(SavingsGoal(r(100), r(0)), d("2026-11-01")))
        assertEquals(Money.ZERO, GoalMath.perMonthNeeded(SavingsGoal(r(100), r(150), d("2027-01-01")), d("2026-11-01")))
        assertTrue(SavingsGoal(r(100), r(100)).isReached)
        assertFalse(SavingsGoal(r(100), r(99)).isReached)
        // Deadline already passed: everything needed now
        assertEquals(r(500), GoalMath.perMonthNeeded(SavingsGoal(r(1000), r(500), d("2026-01-01")), d("2026-11-01")))
        assertEquals(1.0, SavingsGoal(r(100), r(500)).progress, 1e-9)
        assertEquals(r(250), SavingsPot.total(listOf(r(300), r(-100), r(50))))
    }

    // ---- Lent & borrowed ----

    @Test fun partialRepayments() {
        val lent = Debt(DebtDirection.LENT, r(500), listOf(r(200), r(100)))
        assertEquals(r(200), lent.outstanding)
        assertFalse(lent.isSettled)
        val paid = lent.copy(repayments = lent.repayments + r(200))
        assertEquals(Money.ZERO, paid.outstanding)
        assertTrue(paid.isSettled)
        val over = Debt(DebtDirection.BORROWED, r(1000), listOf(r(1050)))
        assertEquals(Money.ZERO, over.outstanding)
        assertEquals(r(50), over.overpaid)
        val forgiven = Debt(DebtDirection.LENT, r(300), listOf(r(100)), markedSettled = true)
        assertEquals(Money.ZERO, forgiven.outstanding)
        assertTrue(forgiven.isSettled)
    }

    @Test fun debtTotals() {
        val totals = DebtMath.totals(listOf(
            Debt(DebtDirection.LENT, r(500), listOf(r(100))),
            Debt(DebtDirection.LENT, r(200)),
            Debt(DebtDirection.BORROWED, r(1000), listOf(r(400))),
            Debt(DebtDirection.BORROWED, r(700), markedSettled = true),
        ))
        assertEquals(r(600), totals.othersOweMe)
        assertEquals(r(600), totals.iOweOthers)
        assertEquals(r(400), DebtMath.maxRepayment(Debt(DebtDirection.LENT, r(500), listOf(r(100)))))
    }

    // ---- Unusual expenses ----

    @Test fun unusualExpense() {
        val food = listOf(100L, 120L, 80L, 150L, 90L).map(::r) // median 100
        assertTrue(UnusualExpense.isUnusual(r(301), food))
        assertFalse(UnusualExpense.isUnusual(r(300), food))
        assertFalse(UnusualExpense.isUnusual(r(5000), food.take(4))) // not enough history
        assertEquals(r(110), UnusualExpense.median(listOf(r(100), r(120))))
        assertNull(UnusualExpense.median(emptyList()))
    }

    // ---- No-spend streak ----

    @Test fun noSpendStreak() {
        val spend = setOf(d("2026-11-01"), d("2026-11-05"))
        assertEquals(3, NoSpendStreak.current(spend, d("2026-11-08"), d("2026-10-01")))
        assertEquals(0, NoSpendStreak.current(spend, d("2026-11-05"), d("2026-10-01")))
        // Tracking started on 3 Nov: only 3..4 count
        assertEquals(2, NoSpendStreak.current(spend, d("2026-11-04"), d("2026-11-03")))
        assertEquals(3, NoSpendStreak.countNoSpendDays(spend, d("2026-11-01"), d("2026-11-05")))
    }

    // ---- Suggestions ----

    @Test fun categorySuggestionsPreferTimeOfDayAndRecency() {
        val now = LocalDateTime.parse("2026-11-20T08:30")
        val history = listOf(
            CategorySuggestions.Use(2, LocalDateTime.parse("2026-11-19T08:15")), // tea in the morning, recent
            CategorySuggestions.Use(2, LocalDateTime.parse("2026-11-18T08:40")),
            CategorySuggestions.Use(1, LocalDateTime.parse("2026-10-01T20:00")),
            CategorySuggestions.Use(1, LocalDateTime.parse("2026-10-02T20:00")),
            CategorySuggestions.Use(1, LocalDateTime.parse("2026-10-03T20:00")),
        )
        assertEquals(listOf(2L, 1L, 3L), CategorySuggestions.rank(history, now, listOf(1L, 2L, 3L)))
        // No history: keep the given order
        assertEquals(listOf(1L, 2L, 3L), CategorySuggestions.rank(emptyList(), now, listOf(1L, 2L, 3L)))
    }

    @Test fun monthChange() {
        assertEquals(-18, MonthChange.percent(r(1000), r(820)))
        assertEquals(25, MonthChange.percent(r(800), r(1000)))
        assertNull(MonthChange.percent(Money.ZERO, r(100)))
    }
}
