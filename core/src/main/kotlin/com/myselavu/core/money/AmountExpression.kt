package com.myselavu.core.money

/**
 * The tiny calculator behind the amount keypad. Supports "+" and "−" only,
 * e.g. "120+45" = ₹165, "500-20.50" = ₹479.50.
 *
 * A trailing operator is ignored while typing ("120+" shows ₹120).
 */
object AmountExpression {

    sealed interface Result {
        data class Ok(val amount: Money) : Result
        data object Empty : Result
        data object Invalid : Result
        data object Negative : Result
        data object TooLarge : Result
    }

    /** Biggest single expense the keypad accepts: ₹1 crore. */
    val MAX = Money.rupees(1_00_00_000L)

    fun evaluate(input: String): Result {
        val text = input
            .replace('−', '-') // unicode minus from the keypad
            .replace('–', '-')
            .replace(" ", "")
            .replace(",", "")
            .removePrefix(IndianFormat.RUPEE)
        if (text.isEmpty()) return Result.Empty

        // Drop one trailing operator so "120+" evaluates while typing.
        val trimmed = if (text.last() == '+' || text.last() == '-') text.dropLast(1) else text
        if (trimmed.isEmpty()) return Result.Empty

        var total = 0L
        var sign = 1L
        var i = 0
        var expectNumber = true
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (expectNumber) {
                if (c == '-' && i == 0) { sign = -1; i++; continue }
                val start = i
                while (i < trimmed.length && (trimmed[i].isDigit() || trimmed[i] == '.')) i++
                if (start == i) return Result.Invalid
                val number = IndianFormat.parse(trimmed.substring(start, i)) ?: return Result.Invalid
                if (number > MAX) return Result.TooLarge
                total += sign * number.paise
                if (kotlin.math.abs(total) > MAX.paise * 10) return Result.TooLarge
                expectNumber = false
            } else {
                sign = when (c) {
                    '+' -> 1
                    '-' -> -1
                    else -> return Result.Invalid
                }
                i++
                expectNumber = true
            }
        }
        if (expectNumber) return Result.Invalid
        if (total < 0) return Result.Negative
        if (total > MAX.paise) return Result.TooLarge
        return Result.Ok(Money(total))
    }

    /** Convenience: the amount, or null when the expression can't be saved. */
    fun valueOrNull(input: String): Money? = (evaluate(input) as? Result.Ok)?.amount
}
