package com.example.util

import com.example.domain.model.Exchange
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun formatInr(amount: Double, showSign: Boolean = false): String {
        val prefix = when {
            showSign && amount > 0.004 -> "+₹"
            amount < -0.004 -> "-₹"
            else -> "₹"
        }
        return "$prefix${inrFormat.format(abs(amount))}"
    }

    fun formatPrice(price: Double): String {
        return "₹${inrFormat.format(price)}"
    }

    fun formatQuantity(quantity: Double, exchange: Exchange): String {
        return if (exchange == Exchange.CRYPTO) {
            if (quantity >= 1.0) {
                String.format(Locale.US, "%.4f", quantity).trimEnd('0').trimEnd('.')
            } else {
                String.format(Locale.US, "%.6f", quantity).trimEnd('0').trimEnd('.')
            }
        } else {
            quantity.toInt().toString()
        }
    }

    fun formatPct(pct: Double, showSign: Boolean = true): String {
        val sign = when {
            showSign && pct > 0.0001 -> "+"
            pct < -0.0001 -> "-"
            else -> ""
        }
        return "$sign${String.format(Locale.US, "%.2f", abs(pct))}%"
    }

    fun formatTime(epochMs: Long = System.currentTimeMillis()): String {
        return timeFormat.format(Date(epochMs))
    }
}
