package com.myselavu.core.period

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A "budget month": from [start] to [end] (both inclusive).
 * With start day 1 this is a calendar month; with start day 28 it runs from the 28th
 * to the 27th of the next month.
 *
 * Periods are identified by their [start] date. All "effective from" rules compare
 * against period start dates, so they keep working even if the start day is changed.
 */
data class BudgetPeriod(val start: LocalDate, val end: LocalDate) : Comparable<BudgetPeriod> {
    init {
        require(!end.isBefore(start)) { "Period end $end is before start $start" }
    }

    val lengthInDays: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Days from the start up to and including [today] (1..length). Clamped to the period. */
    fun daysElapsed(today: LocalDate): Int = when {
        today.isBefore(start) -> 0
        today.isAfter(end) -> lengthInDays
        else -> (ChronoUnit.DAYS.between(start, today) + 1).toInt()
    }

    /** Days remaining INCLUDING today (so on the last day this is 1). */
    fun daysLeftIncludingToday(today: LocalDate): Int = lengthInDays - daysElapsed(today) + if (today in this) 1 else 0

    /** Days remaining AFTER today. */
    fun daysLeftAfterToday(today: LocalDate): Int = lengthInDays - daysElapsed(today)

    /**
     * The month this period is named after: the calendar month holding most of its days
     * (ties go to the later month). 1st–30th Nov → November; 28 Oct–27 Nov → November.
     */
    val labelMonth: YearMonth
        get() {
            val counts = LinkedHashMap<YearMonth, Int>()
            var d = start
            while (!d.isAfter(end)) {
                val ym = YearMonth.from(d)
                counts[ym] = (counts[ym] ?: 0) + 1
                d = d.plusDays(1)
            }
            var best: YearMonth = YearMonth.from(start)
            var bestCount = -1
            for ((ym, c) in counts) {
                if (c >= bestCount) { best = ym; bestCount = c }
            }
            return best
        }

    fun allDates(): List<LocalDate> {
        val out = ArrayList<LocalDate>(lengthInDays)
        var d = start
        while (!d.isAfter(end)) { out.add(d); d = d.plusDays(1) }
        return out
    }

    override fun compareTo(other: BudgetPeriod): Int = start.compareTo(other.start)
}

object PeriodCalendar {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    /** A changed start day never produces a period shorter than this. */
    const val MIN_TRANSITION_DAYS = 15

    fun requireValidStartDay(startDay: Int) {
        require(startDay in MIN_START_DAY..MAX_START_DAY) { "Start day must be 1..28, was $startDay" }
    }

    /** The regular period (for this start day) that contains [date]. */
    fun periodContaining(date: LocalDate, startDay: Int): BudgetPeriod {
        requireValidStartDay(startDay)
        val start = if (date.dayOfMonth >= startDay) {
            date.withDayOfMonth(startDay)
        } else {
            date.minusMonths(1).withDayOfMonth(startDay)
        }
        return BudgetPeriod(start, start.plusMonths(1).minusDays(1))
    }

    /**
     * The period that follows [previous]. Normally just "the next month".
     * If the start day was changed, the next period starts the day after [previous] ends and
     * runs until the day before the new start day, while being at least [MIN_TRANSITION_DAYS] long.
     */
    fun nextPeriod(previous: BudgetPeriod, startDay: Int): BudgetPeriod {
        requireValidStartDay(startDay)
        val start = previous.end.plusDays(1)
        if (start.dayOfMonth == startDay) {
            return BudgetPeriod(start, start.plusMonths(1).minusDays(1))
        }
        // Transition: find the first boundary (a date whose day == startDay) far enough away.
        val earliest = start.plusDays(MIN_TRANSITION_DAYS.toLong())
        var boundary = if (earliest.dayOfMonth <= startDay) {
            earliest.withDayOfMonth(startDay)
        } else {
            earliest.plusMonths(1).withDayOfMonth(startDay)
        }
        if (boundary.isBefore(earliest)) boundary = boundary.plusMonths(1)
        return BudgetPeriod(start, boundary.minusDays(1))
    }
}
