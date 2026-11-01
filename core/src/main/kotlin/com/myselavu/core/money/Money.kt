package com.myselavu.core.money

/**
 * An amount of Indian Rupees stored as whole paise (₹1 = 100 paise).
 * Money is NEVER stored as Float/Double anywhere in the app.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))
    operator fun unaryMinus(): Money = Money(Math.negateExact(paise))
    operator fun times(factor: Int): Money = Money(Math.multiplyExact(paise, factor.toLong()))
    operator fun times(factor: Long): Money = Money(Math.multiplyExact(paise, factor))

    /** Integer division that rounds DOWN (towards negative infinity). */
    fun divFloor(divisor: Long): Money {
        require(divisor != 0L) { "divide by zero" }
        return Money(Math.floorDiv(paise, divisor))
    }

    /** Integer division that rounds UP (towards positive infinity). */
    fun divCeil(divisor: Long): Money {
        require(divisor != 0L) { "divide by zero" }
        return Money(-Math.floorDiv(-paise, divisor))
    }

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    val isPositive: Boolean get() = paise > 0
    val isNegative: Boolean get() = paise < 0
    val isZero: Boolean get() = paise == 0L

    fun coerceAtLeastZero(): Money = if (paise < 0) ZERO else this
    fun abs(): Money = if (paise < 0) -this else this

    /** Whole rupees, rounded down. */
    val rupees: Long get() = Math.floorDiv(paise, 100L)

    override fun toString(): String = IndianFormat.format(this)

    companion object {
        val ZERO = Money(0)
        fun rupees(r: Long): Money = Money(Math.multiplyExact(r, 100L))
        fun rupees(r: Int): Money = rupees(r.toLong())
        fun paise(p: Long): Money = Money(p)
    }
}

fun Iterable<Money>.sum(): Money {
    var total = 0L
    for (m in this) total = Math.addExact(total, m.paise)
    return Money(total)
}

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money {
    var total = 0L
    for (item in this) total = Math.addExact(total, selector(item).paise)
    return Money(total)
}

fun minOf(a: Money, b: Money): Money = if (a <= b) a else b
fun maxOf(a: Money, b: Money): Money = if (a >= b) a else b
