package com.myselavu.core.budget

import com.myselavu.core.money.Money
import java.time.LocalDate

/** Numbers for one category in one month. */
data class CategoryStatus(
    val categoryId: Long,
    val type: CategoryType,
    val allocation: Money,
    val carryIn: Money,
    val movedIn: Money,
    val movedOut: Money,
    val spent: Money,
) {
    /** Allocation + money carried in + money moved in − money moved out. */
    val available: Money get() = allocation + carryIn + movedIn - movedOut
    /** Can go negative when overspent. */
    val remaining: Money get() = available - spent
    val isOverspent: Boolean get() = spent > available
}

/** Everything the dashboard shows for a month, computed in one place. */
data class PeriodSummary(
    val snapshot: PeriodSnapshot,
    val activity: PeriodActivity,
    val categories: List<CategoryStatus>,
    val today: LocalDate,
) {
    val salary: Money get() = snapshot.salary
    val extraIncome: Money get() = activity.extraIncome
    val totalFixed: Money get() = snapshot.totalFixed
    val totalAllocated: Money get() = snapshot.totalAllocated
    val carriedIn: Money get() = snapshot.totalCarryIn
    val totalSpent: Money get() = activity.totalSpent

    /** "Money left" = salary + extra income (+ money carried in) − fixed expenses − actual spending. */
    val moneyLeft: Money get() = salary + extraIncome + carriedIn - totalFixed - totalSpent

    /** "Unplanned money" = salary + extra income − fixed expenses − total category allocations. */
    val unplannedMoney: Money get() = salary + extraIncome - totalFixed - totalAllocated

    val spendingCategories: List<CategoryStatus> get() = categories.filter { it.type == CategoryType.SPENDING }

    val spendingAvailable: Money get() = Money(spendingCategories.sumOf { it.available.paise })
    val spendingSpent: Money get() = Money(spendingCategories.sumOf { it.spent.paise })
    val spendingRemaining: Money get() = spendingAvailable - spendingSpent

    val daysLeftIncludingToday: Int get() = snapshot.period.daysLeftIncludingToday(today)

    /** "Safe to spend today" = remaining spending budget ÷ days left (never below zero). */
    val safeToSpendToday: Money
        get() {
            val days = daysLeftIncludingToday
            if (days <= 0) return Money.ZERO
            return spendingRemaining.coerceAtLeastZero().divFloor(days.toLong())
        }

    fun category(id: Long): CategoryStatus? = categories.firstOrNull { it.categoryId == id }

    companion object {
        fun compute(snapshot: PeriodSnapshot, activity: PeriodActivity, today: LocalDate): PeriodSummary {
            val movedIn = HashMap<Long, Long>()
            val movedOut = HashMap<Long, Long>()
            for (t in activity.transfers) {
                movedIn[t.toCategoryId] = (movedIn[t.toCategoryId] ?: 0L) + t.amount.paise
                movedOut[t.fromCategoryId] = (movedOut[t.fromCategoryId] ?: 0L) + t.amount.paise
            }
            val statuses = snapshot.categories.map { c ->
                CategoryStatus(
                    categoryId = c.categoryId,
                    type = c.type,
                    allocation = c.allocation,
                    carryIn = c.carryIn,
                    movedIn = Money(movedIn[c.categoryId] ?: 0L),
                    movedOut = Money(movedOut[c.categoryId] ?: 0L),
                    spent = activity.spent(c.categoryId),
                )
            }
            return PeriodSummary(snapshot, activity, statuses, today)
        }
    }
}
