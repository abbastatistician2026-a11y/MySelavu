package com.myselavu.core.recurring

import java.time.LocalDate

/** How often a recurring payment (recharge, OTT, gym, SIP, EMI…) is due. */
sealed interface Frequency {
    val monthsBetween: Int

    data object Monthly : Frequency { override val monthsBetween = 1 }
    data class EveryNMonths(val n: Int) : Frequency {
        init { require(n in 1..120) { "n must be 1..120" } }
        override val monthsBetween = n
    }
    data object Yearly : Frequency { override val monthsBetween = 12 }
}

/**
 * Due dates are always computed from the original [anchor] date, never by adding to the previous
 * due date. So a payment due on the 31st is due on 28/29 Feb but back on 31 Mar (not "stuck" on the 28th).
 */
data class RecurringSchedule(val anchor: LocalDate, val frequency: Frequency) {

    /** The k-th due date (k = 0 is the anchor). */
    fun occurrence(k: Int): LocalDate {
        require(k >= 0)
        return anchor.plusMonths(k.toLong() * frequency.monthsBetween)
    }

    /** First due date on or after [date]. */
    fun nextOnOrAfter(date: LocalDate): LocalDate {
        if (!date.isAfter(anchor)) return anchor
        val monthsDiff = (date.year - anchor.year) * 12 + (date.monthValue - anchor.monthValue)
        var k = maxOf(0, monthsDiff / frequency.monthsBetween - 1)
        while (occurrence(k).isBefore(date)) k++
        return occurrence(k)
    }

    /** First due date strictly after [date] (used after "Mark paid" or "Skip"). */
    fun nextAfter(date: LocalDate): LocalDate = nextOnOrAfter(date.plusDays(1))

    /** All due dates in [from]..[to] inclusive. */
    fun occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = nextOnOrAfter(from)
        while (!d.isAfter(to)) {
            out.add(d)
            d = nextAfter(d)
        }
        return out
    }

    /** Cost per month in paise, for the "Subscriptions: ₹X per month" total (rounded up). */
    fun monthlyCostPaise(amountPaise: Long): Long {
        val n = frequency.monthsBetween.toLong()
        return -Math.floorDiv(-amountPaise, n)
    }
}

/** Due dates for fixed monthly expenses ("Rent, due on the 5th"). */
object FixedDueDate {
    /**
     * The date within [periodStart]..[periodEnd] whose day-of-month matches [dueDay]
     * (clamped to the month's length, so "31" means 30 Nov / 28 Feb). Null if none falls inside.
     */
    fun inPeriod(dueDay: Int, periodStart: LocalDate, periodEnd: LocalDate): LocalDate? {
        require(dueDay in 1..31)
        var month = java.time.YearMonth.from(periodStart)
        val last = java.time.YearMonth.from(periodEnd)
        while (!month.isAfter(last)) {
            val date = month.atDay(minOf(dueDay, month.lengthOfMonth()))
            if (!date.isBefore(periodStart) && !date.isAfter(periodEnd)) return date
            month = month.plusMonths(1)
        }
        return null
    }

    /** The day before the due date, when the reminder fires. */
    fun reminderDate(due: LocalDate): LocalDate = due.minusDays(1)
}
