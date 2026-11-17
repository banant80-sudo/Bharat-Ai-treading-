package com.example.util

import com.example.domain.model.Exchange
import com.example.domain.model.RiskConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class ChargesBreakdown(
    val brokerage: Double,
    val sttAndTxn: Double,
    val gstAndSebi: Double,
    val totalCharges: Double
)

data class SegmentChargeDetail(
    val segmentName: String,
    val buyValue: Double,
    val sellValue: Double,
    val buyBrokerage: Double,
    val sellBrokerage: Double,
    val dpCharge: Double,
    val stt: Double,
    val exchangeTxnCharge: Double,
    val stampDuty: Double,
    val gst: Double,
    val totalCharges: Double
) {
    val grossProfit: Double get() = sellValue - buyValue
    val netProfitAfterCharges: Double get() = grossProfit - totalCharges
    val totalChargesFormatted: String get() = String.format(Locale.US, "%.2f", totalCharges)
}

data class MarketWindowStatus(
    val istTimeFormatted: String,
    val isNseBseOpen: Boolean,
    val isMcxOpen: Boolean,
    val isCryptoOpen: Boolean,
    val isOpeningVolatilityWindow: Boolean,
    val isAnyMarketOpen: Boolean,
    val statusSummary: String
)

object AngelOneChargeCalculator {
    /**
     * 1. Equity Delivery (e.g. Buy 5000, Sell 5100):
     * brok_buy = max(5, min(20, 0.001 * buy))
     * brok_sell = max(5, min(20, 0.001 * sell))
     * dp_charge = 20
     * stt = round(0.001 * buy) + round(0.001 * sell)
     * exch = (buy + sell) * 0.000030699
     * stamp = round(0.00015 * buy)
     * gst = 0.18 * (brok_buy + brok_sell + dp_charge + exch)
     */
    fun calculateEquityDeliveryCharges(buyValue: Double = 5000.0, sellValue: Double = 5100.0): SegmentChargeDetail {
        val brokBuy = max(5.0, min(20.0, 0.001 * buyValue))
        val brokSell = max(5.0, min(20.0, 0.001 * sellValue))
        val dpCharge = 20.0
        val stt = round(0.001 * buyValue) + round(0.001 * sellValue)
        val exch = (buyValue + sellValue) * 0.000030699
        val stamp = round(0.00015 * buyValue)
        val gst = 0.18 * (brokBuy + brokSell + dpCharge + exch)
        val total = brokBuy + brokSell + dpCharge + stt + exch + stamp + gst
        return SegmentChargeDetail(
            segmentName = "Equity Delivery",
            buyValue = buyValue,
            sellValue = sellValue,
            buyBrokerage = brokBuy,
            sellBrokerage = brokSell,
            dpCharge = dpCharge,
            stt = stt,
            exchangeTxnCharge = exch,
            stampDuty = stamp,
            gst = gst,
            totalCharges = total
        )
    }

    /**
     * 2. Equity Intraday (e.g. Buy 5000, Sell 5100):
     * brok_buy = max(5, min(20, 0.001 * buy))
     * brok_sell = max(5, min(20, 0.001 * sell))
     * stt = round(0.00025 * sell)
     * exch = (buy + sell) * 0.000030699
     * stamp = round(0.00003 * buy)
     * gst = 0.18 * (brok_buy + brok_sell + exch)
     */
    fun calculateEquityIntradayCharges(buyValue: Double = 5000.0, sellValue: Double = 5100.0): SegmentChargeDetail {
        val brokBuy = max(5.0, min(20.0, 0.001 * buyValue))
        val brokSell = max(5.0, min(20.0, 0.001 * sellValue))
        val stt = round(0.00025 * sellValue)
        val exch = (buyValue + sellValue) * 0.000030699
        val stamp = round(0.00003 * buyValue)
        val gst = 0.18 * (brokBuy + brokSell + exch)
        val total = brokBuy + brokSell + stt + exch + stamp + gst
        return SegmentChargeDetail(
            segmentName = "Equity Intraday",
            buyValue = buyValue,
            sellValue = sellValue,
            buyBrokerage = brokBuy,
            sellBrokerage = brokSell,
            dpCharge = 0.0,
            stt = stt,
            exchangeTxnCharge = exch,
            stampDuty = stamp,
            gst = gst,
            totalCharges = total
        )
    }

    /**
     * 3. Options Trading (e.g. Buy 5000, Sell 5100):
     * brok_buy = 20, brok_sell = 20
     * stt = round(0.0015 * sell)
     * exch = (buy + sell) * 0.000355299
     * stamp = round(0.00003 * buy)
     * gst = 0.18 * (brok_buy + brok_sell + exch)
     */
    fun calculateOptionsCharges(buyValue: Double = 5000.0, sellValue: Double = 5100.0): SegmentChargeDetail {
        val brokBuy = 20.0
        val brokSell = 20.0
        val stt = round(0.0015 * sellValue)
        val exch = (buyValue + sellValue) * 0.000355299
        val stamp = round(0.00003 * buyValue)
        val gst = 0.18 * (brokBuy + brokSell + exch)
        val total = brokBuy + brokSell + stt + exch + stamp + gst
        return SegmentChargeDetail(
            segmentName = "Options Trading",
            buyValue = buyValue,
            sellValue = sellValue,
            buyBrokerage = brokBuy,
            sellBrokerage = brokSell,
            dpCharge = 0.0,
            stt = stt,
            exchangeTxnCharge = exch,
            stampDuty = stamp,
            gst = gst,
            totalCharges = total
        )
    }

    fun formatBenchmarkSummary(buyValue: Double = 5000.0, sellValue: Double = 5100.0): String {
        val del = calculateEquityDeliveryCharges(buyValue, sellValue)
        val intra = calculateEquityIntradayCharges(buyValue, sellValue)
        val opt = calculateOptionsCharges(buyValue, sellValue)
        return String.format(
            Locale.US,
            "Delivery: %.2f, Intraday: %.2f, Options: %.2f",
            del.totalCharges,
            intra.totalCharges,
            opt.totalCharges
        )
    }

    /**
     * Estimates two-way (entry + square-off) intraday charges:
     * - NSE/BSE/MCX: Angel One SmartAPI brokerage min(₹20, 0.03%) per leg + STT/CTT + Exchange Txn + 18% GST
     * - CRYPTO: Typical 0.10% spot/perp maker-taker round-trip fee + 18% GST on fee
     */
    fun estimateRoundTripCharges(investedCapital: Double, exchange: Exchange): ChargesBreakdown {
        if (investedCapital <= 0.0) {
            return ChargesBreakdown(0.0, 0.0, 0.0, 0.0)
        }
        if (exchange == Exchange.CRYPTO) {
            val twoWayFee = investedCapital * 0.0020 // 0.10% per leg
            val gst = twoWayFee * 0.18
            val total = Math.round((twoWayFee + gst) * 100.0) / 100.0
            return ChargesBreakdown(
                brokerage = Math.round(twoWayFee * 100.0) / 100.0,
                sttAndTxn = 0.0,
                gstAndSebi = Math.round(gst * 100.0) / 100.0,
                totalCharges = total
            )
        }

        val legBrokerage = min(20.0, investedCapital * 0.0003)
        val totalBrokerage = legBrokerage * 2.0
        val turnover = investedCapital * 2.0
        val sttRate = if (exchange == Exchange.MCX) 0.00018 else 0.00025
        val sttAndTxn = turnover * sttRate + (investedCapital * 0.00003)
        val gstAndSebi = (totalBrokerage + sttAndTxn * 0.15) * 0.18 + (turnover * 0.000001)
        val total = Math.round((totalBrokerage + sttAndTxn + gstAndSebi) * 100.0) / 100.0
        return ChargesBreakdown(
            brokerage = Math.round(totalBrokerage * 100.0) / 100.0,
            sttAndTxn = Math.round(sttAndTxn * 100.0) / 100.0,
            gstAndSebi = Math.round(gstAndSebi * 100.0) / 100.0,
            totalCharges = total
        )
    }
}

object IstMarketScheduler {
    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val istClockFormat = SimpleDateFormat("HH:mm:ss 'IST'", Locale.US).apply {
        timeZone = istZone
    }

    const val NSE_OPEN_MINUTE = 9 * 60 + 15                 // 09:15 AM IST
    const val OPENING_VOLATILITY_END_MINUTE = 9 * 60 + 30   // 09:30 AM IST
    const val NSE_CLOSE_MINUTE = 15 * 60 + 15               // 03:15 PM IST
    const val MCX_OPEN_MINUTE = 9 * 60 + 0                  // 09:00 AM IST
    const val MCX_CLOSE_MINUTE = 23 * 60 + 30               // 11:30 PM IST

    fun evaluate(config: RiskConfig, nowMillis: Long = System.currentTimeMillis()): MarketWindowStatus {
        val cal = Calendar.getInstance(istZone).apply { timeInMillis = nowMillis }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekday = dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY

        val nseOpen = if (!config.enforceMarketHours) {
            true
        } else {
            isWeekday && minuteOfDay in NSE_OPEN_MINUTE until NSE_CLOSE_MINUTE
        }

        val isOpeningVolWindow = isWeekday && minuteOfDay in NSE_OPEN_MINUTE until OPENING_VOLATILITY_END_MINUTE

        val mcxOpen = if (!config.enforceMarketHours) {
            true
        } else {
            config.mcxExtendedHoursEnabled &&
                isWeekday &&
                minuteOfDay in MCX_OPEN_MINUTE until MCX_CLOSE_MINUTE
        }

        // Crypto markets trade 24/7 globally
        val cryptoOpen = true

        val summary = when {
            !config.enforceMarketHours -> "24/7 Demo + Crypto Active"
            !isWeekday -> "NSE/MCX Weekend • Crypto 24/7 Open"
            config.skipOpeningVolatilityEnabled && isOpeningVolWindow -> "Opening Vol Guard (09:15–09:30 IST)"
            nseOpen && mcxOpen -> "NSE/BSE, MCX & Crypto 24/7 Open"
            nseOpen -> "NSE/BSE (09:15–15:15) & Crypto Open"
            mcxOpen -> "MCX (–23:30 IST) & Crypto Open"
            else -> "NSE/BSE Closed • Crypto 24/7 Open"
        }

        return MarketWindowStatus(
            istTimeFormatted = istClockFormat.format(cal.time),
            isNseBseOpen = nseOpen,
            isMcxOpen = mcxOpen,
            isCryptoOpen = cryptoOpen,
            isOpeningVolatilityWindow = isOpeningVolWindow,
            isAnyMarketOpen = nseOpen || mcxOpen || cryptoOpen,
            statusSummary = summary
        )
    }

    fun isExchangeOpen(exchange: Exchange, status: MarketWindowStatus): Boolean {
        return when (exchange) {
            Exchange.NSE, Exchange.BSE -> status.isNseBseOpen
            Exchange.MCX -> status.isMcxOpen
            Exchange.CRYPTO -> status.isCryptoOpen
        }
    }

    fun isEntryAllowed(exchange: Exchange, config: RiskConfig, status: MarketWindowStatus): Boolean {
        if (!isExchangeOpen(exchange, status)) return false
        if ((exchange == Exchange.NSE || exchange == Exchange.BSE) &&
            config.enforceMarketHours &&
            config.skipOpeningVolatilityEnabled &&
            status.isOpeningVolatilityWindow
        ) {
            return false
        }
        return true
    }
}
