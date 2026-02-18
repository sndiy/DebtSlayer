package com.hyse.debtslayer.utils

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val formatter = NumberFormat.getNumberInstance(Locale("id", "ID"))

    fun format(amount: Long): String = "Rp ${formatter.format(amount)}"

    fun ceilToThousand(amount: Long): Long {
        if (amount <= 0) return 0L
        val remainder = amount % 1000
        return if (remainder == 0L) amount else amount + (1000 - remainder)
    }
}