package com.myselavu.core.debts

import com.myselavu.core.money.Money

enum class DebtDirection {
    /** "I lent ₹500 to Arun" — they owe me. */
    LENT,
    /** "I borrowed ₹1,000 from Priya" — I owe them. */
    BORROWED,
}

data class Debt(
    val direction: DebtDirection,
    val amount: Money,
    val repayments: List<Money> = emptyList(),
    /** Marked settled by hand (e.g. forgiven or rounded off). */
    val markedSettled: Boolean = false,
) {
    val repaid: Money get() = Money(repayments.sumOf { it.paise })

    /** Still to be paid back; never negative. Zero once settled. */
    val outstanding: Money
        get() = if (markedSettled) Money.ZERO else (amount - repaid).coerceAtLeastZero()

    val isSettled: Boolean get() = markedSettled || repaid >= amount

    /** Amount repaid beyond the original amount (e.g. paid back ₹1,050 for ₹1,000). */
    val overpaid: Money get() = (repaid - amount).coerceAtLeastZero()
}

data class DebtTotals(val othersOweMe: Money, val iOweOthers: Money)

object DebtMath {
    fun totals(debts: List<Debt>): DebtTotals = DebtTotals(
        othersOweMe = Money(debts.filter { it.direction == DebtDirection.LENT }.sumOf { it.outstanding.paise }),
        iOweOthers = Money(debts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.outstanding.paise }),
    )

    /** How much of a new repayment can be applied without going past the amount owed. */
    fun maxRepayment(debt: Debt): Money = debt.outstanding
}
