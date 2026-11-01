package com.myselavu.core.money

/**
 * Formats money the Indian way: ₹1,23,456 (lakh/crore grouping).
 * Paise are shown only when they are not zero, unless [alwaysShowPaise] is set.
 */
object IndianFormat {
    const val RUPEE = "₹"
    private const val MINUS = "−" // proper minus sign, reads better than a hyphen

    fun format(
        money: Money,
        alwaysShowPaise: Boolean = false,
        withSymbol: Boolean = true,
    ): String {
        val negative = money.paise < 0
        // Work with the absolute value as unsigned-safe pieces (Long.MIN_VALUE is never a real amount).
        val abs = if (negative) -money.paise else money.paise
        val rupees = abs / 100
        val paise = abs % 100
        val sb = StringBuilder()
        if (negative) sb.append(MINUS)
        if (withSymbol) sb.append(RUPEE)
        sb.append(groupIndian(rupees))
        if (alwaysShowPaise || paise != 0L) {
            sb.append('.')
            if (paise < 10) sb.append('0')
            sb.append(paise)
        }
        return sb.toString()
    }

    /** Hidden amount for the privacy mode: ₹•••• */
    fun hidden(): String = "$RUPEE••••"

    /** 1234567 -> "12,34,567" */
    fun groupIndian(value: Long): String {
        require(value >= 0)
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /**
     * Parses a plain amount typed by a person: "120", "120.5", "1,20,000.75", "₹ 99".
     * Returns null for anything that isn't a valid non-negative amount with at most 2 decimals.
     */
    fun parse(text: String): Money? {
        val cleaned = text.trim().removePrefix(RUPEE).replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val dot = cleaned.indexOf('.')
        val wholePart = if (dot >= 0) cleaned.substring(0, dot) else cleaned
        val fracPart = if (dot >= 0) cleaned.substring(dot + 1) else ""
        if (wholePart.isEmpty() && fracPart.isEmpty()) return null
        if (!wholePart.all { it in '0'..'9' } || !fracPart.all { it in '0'..'9' }) return null
        if (fracPart.length > 2) return null
        if (wholePart.length > 13) return null // > ₹1 lakh crore is not a real expense
        val rupees = if (wholePart.isEmpty()) 0L else wholePart.toLong()
        val paise = when (fracPart.length) {
            0 -> 0L
            1 -> fracPart.toLong() * 10
            else -> fracPart.toLong()
        }
        return Money(rupees * 100 + paise)
    }
}
