package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun parseAmountInput_handlesAngolanFormats() {
        assertEquals(2500.0, CurrencyUtils.parseAmountInput("2500") ?: 0.0, 0.001)
        assertEquals(2500.0, CurrencyUtils.parseAmountInput("2.500") ?: 0.0, 0.001)
        assertEquals(2500.0, CurrencyUtils.parseAmountInput("2.500 Kz") ?: 0.0, 0.001)
        assertEquals(12500.5, CurrencyUtils.parseAmountInput("12.500,50") ?: 0.0, 0.001)
        assertEquals(3000.25, CurrencyUtils.parseAmountInput("3000,25") ?: 0.0, 0.001)
        assertNull(CurrencyUtils.parseAmountInput("   "))
        assertNull(CurrencyUtils.parseAmountInput("abc"))
    }

    @Test
    fun formatKz_formatsCleanly() {
        assertEquals("2.500 Kz", CurrencyUtils.formatKz(2500.0))
        assertEquals("12.500,50 Kz", CurrencyUtils.formatKz(12500.5))
    }
}
