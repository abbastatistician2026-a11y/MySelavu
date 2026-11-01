package com.myselavu.core

import com.myselavu.core.money.AmountExpression
import com.myselavu.core.money.AmountExpression.Result
import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money
import com.myselavu.core.money.sum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun paiseArithmetic() {
        val a = Money.rupees(120) + Money(50)
        assertEquals(12050L, a.paise)
        assertEquals(Money(1L), Money(10) - Money(9))
        assertEquals(Money(30), Money(10) * 3)
        // 0.1 + 0.2 problem does not exist with paise
        assertEquals(Money(30), Money(10) + Money(20))
        assertEquals(Money(600), listOf(Money(100), Money(200), Money(300)).sum())
    }

    @Test fun divisionRounding() {
        assertEquals(Money(33), Money(100).divFloor(3))
        assertEquals(Money(34), Money(100).divCeil(3))
        assertEquals(Money(-34), Money(-100).divFloor(3))
    }

    @Test(expected = ArithmeticException::class)
    fun overflowIsDetected() {
        Money(Long.MAX_VALUE) + Money(1)
    }

    @Test fun indianGrouping() {
        assertEquals("₹0", IndianFormat.format(Money.ZERO))
        assertEquals("₹999", IndianFormat.format(Money.rupees(999)))
        assertEquals("₹1,000", IndianFormat.format(Money.rupees(1000)))
        assertEquals("₹1,23,456", IndianFormat.format(Money.rupees(123456)))
        assertEquals("₹12,34,567", IndianFormat.format(Money.rupees(1234567)))
        assertEquals("₹1,00,00,000", IndianFormat.format(Money.rupees(10000000)))
        assertEquals("₹1,23,456.50", IndianFormat.format(Money(12345650)))
        assertEquals("₹5.05", IndianFormat.format(Money(505)))
        assertEquals("−₹500", IndianFormat.format(Money.rupees(-500)))
        assertEquals("₹15.00", IndianFormat.format(Money.rupees(15), alwaysShowPaise = true))
        assertEquals("1,500", IndianFormat.format(Money.rupees(1500), withSymbol = false))
    }

    @Test fun parse() {
        assertEquals(Money(12000), IndianFormat.parse("120"))
        assertEquals(Money(12050), IndianFormat.parse("120.5"))
        assertEquals(Money(12005), IndianFormat.parse("120.05"))
        assertEquals(Money(12345675), IndianFormat.parse("₹1,23,456.75"))
        assertEquals(Money(50), IndianFormat.parse(".5"))
        assertNull(IndianFormat.parse("12.345"))
        assertNull(IndianFormat.parse("abc"))
        assertNull(IndianFormat.parse(""))
        assertNull(IndianFormat.parse("1.2.3"))
    }

    @Test fun keypadExpressions() {
        assertEquals(Result.Ok(Money.rupees(165)), AmountExpression.evaluate("120+45"))
        assertEquals(Result.Ok(Money(47950)), AmountExpression.evaluate("500-20.50"))
        assertEquals(Result.Ok(Money.rupees(155)), AmountExpression.evaluate("120+45−10"))
        assertEquals(Result.Ok(Money.rupees(120)), AmountExpression.evaluate("120+"))
        assertEquals(Result.Ok(Money(1550)), AmountExpression.evaluate("10.25+5.25"))
        assertEquals(Result.Empty, AmountExpression.evaluate(""))
        assertEquals(Result.Empty, AmountExpression.evaluate("+"))
        assertEquals(Result.Invalid, AmountExpression.evaluate("12++3"))
        assertEquals(Result.Invalid, AmountExpression.evaluate("12.345"))
        assertEquals(Result.Invalid, AmountExpression.evaluate("12x3"))
        assertEquals(Result.Negative, AmountExpression.evaluate("10-20"))
        assertEquals(Result.TooLarge, AmountExpression.evaluate("100000001"))
        assertTrue(AmountExpression.evaluate("99999999+99999999") is Result.TooLarge)
        assertNull(AmountExpression.valueOrNull("0-1"))
        assertEquals(Money.ZERO, AmountExpression.valueOrNull("0"))
    }
}
