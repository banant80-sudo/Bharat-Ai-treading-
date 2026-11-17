package com.example.util

import com.example.domain.model.Exchange
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object Formatters {
    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private val inrFormat: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    private val timeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss 'IST'", Locale.US).apply {
            timeZone = istZone
        }
    }

    fun formatInr(amount: Double, showSign: Boolean = false): String {
        val prefix = when {
            showSign && amount > 0.004 -> "+₹"
            amount < -0.004 -> "-₹"
            else -> "₹"
        }
        val formatted = inrFormat.get()?.format(abs(amount)) ?: String.format(Locale.US, "%.2f", abs(amount))
        return "$prefix$formatted"
    }

    fun formatPrice(price: Double): String {
        val formatted = inrFormat.get()?.format(price) ?: String.format(Locale.US, "%.2f", price)
        return "₹$formatted"
    }

    fun formatQuantity(quantity: Double, exchange: Exchange? = null): String {
        return quantity.toInt().toString()
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
        return timeFormat.get()?.format(Date(epochMs)) ?: SimpleDateFormat("HH:mm:ss 'IST'", Locale.US).apply {
            timeZone = istZone
        }.format(Date(epochMs))
    }
}
