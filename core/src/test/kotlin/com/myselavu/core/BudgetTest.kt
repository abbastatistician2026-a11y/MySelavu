package com.myselavu.core

import com.myselavu.core.budget.BudgetRules
import com.myselavu.core.budget.CategoryRule
import com.myselavu.core.budget.CategoryTransfer
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.FixedExpenseRule
import com.myselavu.core.budget.PeriodActivity
import com.myselavu.core.budget.PeriodSummary
import com.myselavu.core.budget.Rollover
import com.myselavu.core.budget.Snapshotter
import com.myselavu.core.budget.UnspentMode
import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.period.PeriodCalendar
import com.myselavu.core.rules.ApplyFrom
import com.myselavu.core.rules.Effective
import com.myselavu.core.rules.EffectiveFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun r(x: Long) = Money.rupees(x)

    private val FOOD = 1L
    private val TRAVEL = 2L
    private val SAVINGS = 3L
    private val RENT = 10L
    private val FAMILY = 11L

    private val nov = BudgetPeriod(d("2026-11-01"), d("2026-11-30"))
    private val dec = BudgetPeriod(d("2026-12-01"), d("2026-12-31"))

    private fun baseRules() = BudgetRules(
        salary = listOf(Effective(d("2026-11-01"), r(50000))),
        fixedExpenses = mapOf(
            RENT to listOf(Effective(d("2026-11-01"), FixedExpenseRule("Rent", r(12000), 5))),
            FAMILY to listOf(Effective(d("2026-11-01"), FixedExpenseRule("Family fund", r(8000)))),
        ),
        categoryRules = mapOf(
            FOOD to listOf(Effective(d("2026-11-01"), CategoryRule(r(6000)))),
            TRAVEL to listOf(Effective(d("2026-11-01"), CategoryRule(r(3000)))),
            SAVINGS to listOf(Effective(d("2026-11-01"), CategoryRule(r(10000)))),
        ),
        categoryTypes = mapOf(FOOD to CategoryType.SPENDING, TRAVEL to CategoryType.SPENDING, SAVINGS to CategoryType.SAVINGS),
    )

    @Test fun effectiveFromLookup() {
        val rows = listOf(
            Effective(d("2026-01-01"), "a"),
            Effective(d("2026-04-01"), "b"),
            Effective(d("2026-08-01"), "c"),
        )
        assertNull(EffectiveFrom.valueFor(rows, d("2025-12-01")))
        assertEquals("a", EffectiveFrom.valueFor(rows, d("2026-01-01")))
        assertEquals("a", EffectiveFrom.valueFor(rows, d("2026-03-01")))
        assertEquals("b", EffectiveFrom.valueFor(rows, d("2026-04-01")))
        assertEquals("c", EffectiveFrom.valueFor(rows, d("2027-01-01")))
        // Same date edited twice in a month: the later edit (higher sequence) wins, regardless of list order
        val sameDay = listOf(Effective(d("2026-11-01"), "second", 2), Effective(d("2026-11-01"), "first", 1))
        assertEquals("second", EffectiveFrom.valueFor(sameDay, d("2026-11-01")))
    }

    @Test fun effectiveDateForEdits() {
        assertEquals(d("2026-11-01"), EffectiveFrom.effectiveDateFor(ApplyFrom.THIS_PERIOD, nov))
        assertEquals(d("2026-12-01"), EffectiveFrom.effectiveDateFor(ApplyFrom.NEXT_PERIOD, nov))
    }

    @Test fun fromNextMonthEditLeavesCurrentAndPastUnchanged() {
        val oct = BudgetPeriod(d("2026-10-01"), d("2026-10-31"))
        val rules0 = baseRules().copy(salary = listOf(Effective(d("2026-10-01"), r(45000)), Effective(d("2026-11-01"), r(50000))))
        val octSnap = Snapshotter.snapshot(oct, rules0)
        val novSnap = Snapshotter.snapshot(nov, rules0)

        // During November: salary raised "from next month", rent changed "from next month", Food archived from next month.
        val eff = EffectiveFrom.effectiveDateFor(ApplyFrom.NEXT_PERIOD, nov)
        val rules1 = rules0.copy(
            salary = rules0.salary + Effective(eff, r(60000)),
            fixedExpenses = rules0.fixedExpenses + (RENT to rules0.fixedExpenses.getValue(RENT) + Effective(eff, FixedExpenseRule("Rent", r(13000), 5))),
            categoryRules = rules0.categoryRules + (FOOD to rules0.categoryRules.getValue(FOOD) + Effective(eff, CategoryRule(r(6000), active = false))),
        )
        assertEquals(octSnap, Snapshotter.snapshot(oct, rules1))
        assertEquals(novSnap, Snapshotter.snapshot(nov, rules1))
        val decSnap = Snapshotter.snapshot(dec, rules1)
        assertEquals(r(60000), decSnap.salary)
        assertEquals(r(13000), decSnap.fixedExpenses.first { it.fixedExpenseId == RENT }.amount)
        assertNull(decSnap.category(FOOD))
    }

    @Test fun thisMonthEditChangesCurrentMonthOnly() {
        val rules = baseRules().let { it.copy(salary = it.salary + Effective(EffectiveFrom.effectiveDateFor(ApplyFrom.THIS_PERIOD, dec), r(55000))) }
        assertEquals(r(50000), Snapshotter.snapshot(nov, rules).salary)
        assertEquals(r(55000), Snapshotter.snapshot(dec, rules).salary)
    }

    @Test fun deletedFixedExpenseDisappearsFromThenOn() {
        val rules = baseRules().let {
            it.copy(fixedExpenses = it.fixedExpenses + (FAMILY to it.fixedExpenses.getValue(FAMILY) + Effective(d("2026-12-01"), FixedExpenseRule("Family fund", r(8000), active = false))))
        }
        assertEquals(2, Snapshotter.snapshot(nov, rules).fixedExpenses.size)
        assertEquals(1, Snapshotter.snapshot(dec, rules).fixedExpenses.size)
    }

    @Test fun dashboardNumbers() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val activity = PeriodActivity(
            spentByCategory = mapOf(FOOD to r(2000), TRAVEL to r(500)),
            extraIncome = r(1000),
            transfers = listOf(CategoryTransfer(TRAVEL, FOOD, r(500))),
        )
        val s = PeriodSummary.compute(snap, activity, d("2026-11-11"))
        // Money left = 50000 + 1000 − 20000 − 2500
        assertEquals(r(28500), s.moneyLeft)
        // Unplanned = 50000 + 1000 − 20000 − 19000
        assertEquals(r(12000), s.unplannedMoney)
        assertEquals(r(6500), s.category(FOOD)!!.available)
        assertEquals(r(2500), s.category(TRAVEL)!!.available)
        assertEquals(r(4500), s.category(FOOD)!!.remaining)
        // Spending remaining = (6500−2000) + (2500−500) = 6500 over 20 days left (11th..30th)
        assertEquals(20, s.daysLeftIncludingToday)
        assertEquals(r(325), s.safeToSpendToday)
    }

    @Test fun overspendingGoesNegative() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val s = PeriodSummary.compute(snap, PeriodActivity(mapOf(TRAVEL to r(3500))), d("2026-11-20"))
        assertEquals(r(-500), s.category(TRAVEL)!!.remaining)
        assertTrue(s.category(TRAVEL)!!.isOverspent)
    }

    @Test fun safeToSpendNeverNegative() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val s = PeriodSummary.compute(snap, PeriodActivity(mapOf(TRAVEL to r(30000))), d("2026-11-20"))
        assertEquals(Money.ZERO, s.safeToSpendToday)
    }

    // ---- Unspent-money modes ----

    private val novActivity = PeriodActivity(spentByCategory = mapOf(FOOD to r(5000), TRAVEL to r(1000), SAVINGS to r(0)))

    @Test fun moveToSavingsMovesEverythingLeft() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val res = Rollover.close(snap, novActivity, UnspentMode.MOVE_TO_SAVINGS)
        // 50000 − 20000 − 6000 = 24000
        assertEquals(r(24000), res.moneyLeft)
        assertEquals(r(24000), res.savingsDeposit)
        assertTrue(res.carryToNext.isEmpty())
    }

    @Test fun resetCarriesNothingButKeepsSavingsCategory() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val res = Rollover.close(snap, novActivity, UnspentMode.RESET)
        assertTrue(res.carryToNext.isEmpty())
        assertEquals(r(10000), res.savingsDeposit) // the SAVINGS category's money is never lost
    }

    @Test fun carryForwardAddsToSameCategoryNextMonth() {
        val rules = baseRules()
        val snap = Snapshotter.snapshot(nov, rules)
        val res = Rollover.close(snap, novActivity, UnspentMode.CARRY_FORWARD)
        assertEquals(mapOf(FOOD to r(1000), TRAVEL to r(2000)), res.carryToNext)
        assertEquals(r(10000), res.savingsDeposit)
        val decSnap = Snapshotter.snapshot(dec, rules, res.carryToNext)
        assertEquals(r(1000), decSnap.category(FOOD)!!.carryIn)
        val decSummary = PeriodSummary.compute(decSnap, PeriodActivity.EMPTY, d("2026-12-01"))
        assertEquals(r(7000), decSummary.category(FOOD)!!.available)
    }

    @Test fun carryForwardOnlyCarriesPositiveAndNeverMoreThanIsLeft() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val overspentFood = PeriodActivity(spentByCategory = mapOf(FOOD to r(7000), TRAVEL to r(1000)))
        val res = Rollover.close(snap, overspentFood, UnspentMode.CARRY_FORWARD)
        assertEquals(mapOf(TRAVEL to r(2000)), res.carryToNext)

        // Huge overspend in Food: little real money left, so carries shrink.
        val huge = PeriodActivity(spentByCategory = mapOf(FOOD to r(29000), TRAVEL to r(0)))
        // money left = 50000 − 20000 − 29000 = 1000; savings category wants 10000 → gets 1000; nothing left to carry
        val res2 = Rollover.close(snap, huge, UnspentMode.CARRY_FORWARD)
        assertEquals(r(1000), res2.moneyLeft)
        assertEquals(r(1000), res2.savingsDeposit)
        assertTrue(res2.carryToNext.isEmpty())
    }

    @Test fun scaleDownIsProportional() {
        val out = Rollover.scaleDown(mapOf(1L to Money(300), 2L to Money(100)), Money(200))
        assertEquals(mapOf(1L to Money(150), 2L to Money(50)), out)
        assertTrue(out.values.sumOf { it.paise } <= 200)
    }

    @Test fun overspentMonthMovesNothingToSavings() {
        val snap = Snapshotter.snapshot(nov, baseRules())
        val res = Rollover.close(snap, PeriodActivity(mapOf(FOOD to r(40000))), UnspentMode.MOVE_TO_SAVINGS)
        assertEquals(r(-10000), res.moneyLeft)
        assertEquals(Money.ZERO, res.savingsDeposit)
    }

    // ---- Catch-up across skipped months ----

    @Test fun firstUseCreatesCurrentPeriod() {
        val plan = Rollover.catchUp(null, d("2026-11-15"), 1, baseRules(), UnspentMode.MOVE_TO_SAVINGS, { PeriodActivity.EMPTY })
        assertEquals(1, plan.created.size)
        assertEquals(nov, plan.created[0].period)
        assertTrue(plan.closed.isEmpty())
        val again = Rollover.catchUp(plan.created[0], d("2026-11-30"), 1, baseRules(), UnspentMode.MOVE_TO_SAVINGS, { PeriodActivity.EMPTY })
        assertTrue(again.isEmpty)
    }

    @Test fun rolloverAcrossThreeSkippedMonths() {
        val rules = baseRules().let {
            // salary raised from January
            it.copy(salary = it.salary + Effective(d("2027-01-01"), r(52000)))
        }
        val novSnap = Snapshotter.snapshot(nov, rules)
        val activities = mapOf(nov.start to PeriodActivity(mapOf(FOOD to r(4000), TRAVEL to r(3000))))
        // App not opened from mid-November until 10 March 2027: Nov, Dec, Jan, Feb must close.
        val plan = Rollover.catchUp(novSnap, d("2027-03-10"), 1, rules, UnspentMode.CARRY_FORWARD,
            { p -> activities[p.start] ?: PeriodActivity.EMPTY })
        assertEquals(listOf("2026-11-01", "2026-12-01", "2027-01-01", "2027-02-01"), plan.closed.map { it.period.start.toString() })
        assertEquals(listOf("2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01"), plan.created.map { it.period.start.toString() })
        // Periods are contiguous
        plan.created.zipWithNext().forEach { (a, b) -> assertEquals(a.period.end.plusDays(1), b.period.start) }
        // Food carried forward month after month: Nov leftover 2000 → Dec available 8000 → Jan 14000 → Feb 20000 → Mar 26000
        assertEquals(r(2000), plan.created[0].category(FOOD)!!.carryIn)
        assertEquals(r(8000), plan.created[1].category(FOOD)!!.carryIn)
        assertEquals(r(14000), plan.created[2].category(FOOD)!!.carryIn)
        assertEquals(r(20000), plan.created[3].category(FOOD)!!.carryIn)
        // Travel was fully used in November so nothing carried into December
        assertEquals(Money.ZERO, plan.created[0].category(TRAVEL)!!.carryIn)
        // Salary rule applied to the right months
        assertEquals(r(50000), plan.created[0].salary)
        assertEquals(r(52000), plan.created[1].salary)
        // Savings category money went to the pot every month
        assertTrue(plan.closed.all { it.savingsDeposit == r(10000) })
    }

    @Test fun rolloverAcrossSkippedMonthsMoveToSavings() {
        val novSnap = Snapshotter.snapshot(nov, baseRules())
        val plan = Rollover.catchUp(novSnap, d("2027-02-02"), 1, baseRules(), UnspentMode.MOVE_TO_SAVINGS, { PeriodActivity.EMPTY })
        assertEquals(3, plan.closed.size)
        // Nothing spent: 50000 − 20000 = 30000 each month
        assertTrue(plan.closed.all { it.savingsDeposit == r(30000) })
        assertTrue(plan.created.all { it.totalCarryIn == Money.ZERO })
    }

    @Test fun rolloverWithCustomStartDayThroughFebruary() {
        val rules = baseRules().copy(salary = listOf(Effective(d("2026-01-01"), r(40000))),
            categoryRules = mapOf(FOOD to listOf(Effective(d("2026-01-01"), CategoryRule(r(6000))))),
            fixedExpenses = emptyMap())
        val start = Snapshotter.snapshot(PeriodCalendar.periodContaining(d("2027-01-10"), 28), rules)
        assertEquals(d("2026-12-28"), start.period.start)
        val plan = Rollover.catchUp(start, d("2027-03-30"), 28, rules, UnspentMode.RESET, { PeriodActivity.EMPTY })
        assertEquals(
            listOf("2027-01-28..2027-02-27", "2027-02-28..2027-03-27", "2027-03-28..2027-04-27"),
            plan.created.map { "${it.period.start}..${it.period.end}" },
        )
    }

    @Test fun startDayChangeDuringCatchUp() {
        val novSnap = Snapshotter.snapshot(BudgetPeriod(d("2026-10-28"), d("2026-11-27")), baseRules())
        val plan = Rollover.catchUp(novSnap, d("2027-01-05"), 1, baseRules(), UnspentMode.RESET, { PeriodActivity.EMPTY })
        assertEquals(listOf("2026-11-28..2026-12-31", "2027-01-01..2027-01-31"),
            plan.created.map { "${it.period.start}..${it.period.end}" })
    }
}
