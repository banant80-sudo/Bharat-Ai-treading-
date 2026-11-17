package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BasketExitScope
import com.example.domain.model.Exchange
import com.example.domain.model.MarketInstrument
import com.example.domain.model.RiskConfig
import com.example.domain.model.StopLossMode
import com.example.domain.model.TargetProfitMode
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTerminal
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TerminalVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.AngelOneChargeCalculator
import com.example.util.Formatters
import com.example.util.MarketWindowStatus
import java.util.Locale
import kotlin.math.floor

@Composable
fun RiskConfigScreen(
    riskConfig: RiskConfig,
    instruments: List<MarketInstrument>,
    marketWindowStatus: MarketWindowStatus,
    lastSavedAt: String? = null,
    onUpdateRiskConfig: (RiskConfig) -> Unit,
    onSaveRiskConfig: (RiskConfig) -> Unit = onUpdateRiskConfig,
    onActivateMorningEveningSchedule: (Boolean) -> Unit = {},
    onResetPaperAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var smallAmtText by remember { mutableStateOf(riskConfig.smallAmountPerTrade.toInt().toString()) }
    var tpInrText by remember { mutableStateOf(riskConfig.targetProfitInr.toInt().toString()) }
    var tpPctText by remember { mutableStateOf(String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)) }
    var slPctText by remember { mutableStateOf(String.format(Locale.US, "%.2f", riskConfig.stopLossPct)) }
    var slInrText by remember { mutableStateOf(riskConfig.stopLossInr.toInt().toString()) }
    var circuitInrText by remember { mutableStateOf(riskConfig.dailyMaxLossLimitInr.toInt().toString()) }
    var capitalText by remember { mutableStateOf(riskConfig.maxCapitalPerTrade.toInt().toString()) }

    LaunchedEffect(riskConfig) {
        if (abs((smallAmtText.toDoubleOrNull() ?: -1.0) - riskConfig.smallAmountPerTrade) > 0.5) {
            smallAmtText = riskConfig.smallAmountPerTrade.toInt().toString()
        }
        if (abs((tpInrText.toDoubleOrNull() ?: -1.0) - riskConfig.targetProfitInr) > 0.5) {
            tpInrText = riskConfig.targetProfitInr.toInt().toString()
        }
        if (abs((tpPctText.toDoubleOrNull() ?: -1.0) - riskConfig.targetProfitPct) > 0.009) {
            tpPctText = String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)
        }
        if (abs((slPctText.toDoubleOrNull() ?: -1.0) - riskConfig.stopLossPct) > 0.009) {
            slPctText = String.format(Locale.US, "%.2f", riskConfig.stopLossPct)
        }
        if (abs((slInrText.toDoubleOrNull() ?: -1.0) - riskConfig.stopLossInr) > 0.5) {
            slInrText = riskConfig.stopLossInr.toInt().toString()
        }
        if (abs((circuitInrText.toDoubleOrNull() ?: -1.0) - riskConfig.dailyMaxLossLimitInr) > 0.5) {
            circuitInrText = riskConfig.dailyMaxLossLimitInr.toInt().toString()
        }
        if (abs((capitalText.toDoubleOrNull() ?: -1.0) - riskConfig.maxCapitalPerTrade) > 0.5) {
            capitalText = riskConfig.maxCapitalPerTrade.toInt().toString()
        }
    }

    fun commitAndSaveAllRules() {
        val nextSmallAmt = (smallAmtText.toDoubleOrNull() ?: riskConfig.smallAmountPerTrade).coerceIn(50.0, 50000.0)
        val nextCap = (capitalText.toDoubleOrNull() ?: riskConfig.maxCapitalPerTrade).coerceIn(5000.0, 500000.0)
        val nextTpInr = (tpInrText.toDoubleOrNull() ?: riskConfig.targetProfitInr).coerceIn(30.0, 1000.0)
        val nextTpPct = (tpPctText.toDoubleOrNull() ?: riskConfig.targetProfitPct).coerceIn(0.25, 15.0)
        val nextSlPct = (slPctText.toDoubleOrNull() ?: riskConfig.stopLossPct).coerceIn(0.20, 15.0)
        val nextSlInr = (slInrText.toDoubleOrNull() ?: riskConfig.stopLossInr).coerceIn(25.0, 15000.0)
        val nextCircuitInr = (circuitInrText.toDoubleOrNull() ?: riskConfig.dailyMaxLossLimitInr).coerceIn(300.0, 50000.0)

        smallAmtText = nextSmallAmt.toInt().toString()
        capitalText = nextCap.toInt().toString()
        tpInrText = nextTpInr.toInt().toString()
        tpPctText = String.format(Locale.US, "%.2f", nextTpPct)
        slPctText = String.format(Locale.US, "%.2f", nextSlPct)
        slInrText = nextSlInr.toInt().toString()
        circuitInrText = nextCircuitInr.toInt().toString()

        onSaveRiskConfig(
            riskConfig.copy(
                smallAmountPerTrade = nextSmallAmt,
                maxCapitalPerTrade = nextCap,
                targetProfitInr = nextTpInr,
                targetProfitPct = nextTpPct,
                stopLossPct = nextSlPct,
                stopLossInr = nextSlInr,
                dailyMaxLossLimitInr = nextCircuitInr
            )
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EmeraldProfit,
        unfocusedBorderColor = TerminalBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = EmeraldProfit,
        unfocusedLabelColor = TextSecondary
    )

    val effectiveBudget = riskConfig.effectiveCapitalPerTrade
    val eligibleInstrumentsCount = instruments.count {
        floor(effectiveBudget / it.ltp) >= 1.0
    }
    val estimatedCharges = AngelOneChargeCalculator.estimateRoundTripCharges(effectiveBudget, Exchange.NSE)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "risk_save_status_banner") {
            Surface(
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EmeraldProfit.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Settings synced",
                            tint = EmeraldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (lastSavedAt != null) "Saved ✓ ($lastSavedAt)" else "Risk Rules Auto-Save Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldBright,
                                modifier = Modifier.testTag("risk_saved_status_text")
                            )
                            Text(
                                text = "Toggles & presets save instantly. Tap Save to commit typed inputs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Button(
                        onClick = { commitAndSaveAllRules() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldProfit,
                            contentColor = TerminalVoid
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_risk_rules_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save rules",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(5.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // 1. Small-Amount Auto-Split Mode (Invest Across All Affordable Stocks)
        item(key = "small_amount_split_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (riskConfig.smallAmountAutoSplitEnabled) CyanTerminal.copy(alpha = 0.55f) else TerminalBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = CyanTerminal,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Small-Amount Auto-Split Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Divides available margin into ₹50–₹50,000 allocations across affordable NSE, BSE & MCX equities and commodities.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = riskConfig.smallAmountAutoSplitEnabled,
                            onCheckedChange = { checked ->
                                onUpdateRiskConfig(riskConfig.copy(smallAmountAutoSplitEnabled = checked))
                            },
                            modifier = Modifier.testTag("small_amount_split_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TerminalVoid,
                                checkedTrackColor = CyanTerminal
                            )
                        )
                    }

                    if (riskConfig.smallAmountAutoSplitEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(500.0, 2000.0, 5000.0, 10000.0, 25000.0).forEach { preset ->
                                FilterChip(
                                    selected = riskConfig.smallAmountPerTrade.toInt() == preset.toInt(),
                                    onClick = {
                                        onUpdateRiskConfig(riskConfig.copy(smallAmountPerTrade = preset))
                                    },
                                    label = { Text(Formatters.formatInr(preset).removeSuffix(".00")) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanContainer,
                                        selectedLabelColor = CyanTerminal,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("small_amount_preset_${preset.toInt()}")
                                )
                            }
                        }

                        Slider(
                            value = riskConfig.smallAmountPerTrade.toFloat().coerceIn(50f, 50000f),
                            onValueChange = { valAmt ->
                                val rounded = (Math.round(valAmt / 50.0) * 50.0).coerceIn(50.0, 50000.0)
                                onUpdateRiskConfig(riskConfig.copy(smallAmountPerTrade = rounded))
                            },
                            valueRange = 50f..50000f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanTerminal,
                                activeTrackColor = CyanTerminal
                            ),
                            modifier = Modifier.testTag("small_amount_slider")
                        )

                        OutlinedTextField(
                            value = smallAmtText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                smallAmtText = digits
                                digits.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 50.0) {
                                        onUpdateRiskConfig(
                                            riskConfig.copy(smallAmountPerTrade = parsed.coerceIn(50.0, 50000.0))
                                        )
                                    }
                                }
                            },
                            label = { Text("Allocation Per Trade (₹50 – ₹50,000)") },
                            prefix = { Text("₹", color = CyanTerminal) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("small_amount_input")
                        )

                        Surface(
                            color = TerminalSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TerminalBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$eligibleInstrumentsCount / ${instruments.size} scanner instruments eligible at ${Formatters.formatInr(effectiveBudget)}/trade (Equities and commodities qualify at ≥1 share/lot).",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanTerminal,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = capitalText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                capitalText = digits
                                digits.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 5000.0) {
                                        onUpdateRiskConfig(riskConfig.copy(maxCapitalPerTrade = parsed))
                                    }
                                }
                            },
                            label = { Text("Max Capital Per Trade (₹)") },
                            prefix = { Text("₹", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("max_capital_input")
                        )
                    }
                }
            }
        }

        // 2. Smart Capital-Proportional Basket Target (Auto 0.8% or Custom ₹30–₹1,000)
        item(key = "target_profit_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EmeraldProfit.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart Basket Target (Auto-Drop)",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldBright
                            )
                            Text(
                                text = when {
                                    riskConfig.targetProfitMode == TargetProfitMode.PERCENTAGE ->
                                        "Scales with active basket capital (${String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)}%) for faster net drops."
                                    riskConfig.basketExitScope == BasketExitScope.COMBINED_BASKET ->
                                        "Squares off BUY or SELL basket when combined net P&L hits +₹${riskConfig.targetProfitInr.toInt()}."
                                    else ->
                                        "Monitors each position individually and drops at +₹${riskConfig.targetProfitInr.toInt()}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = if (riskConfig.targetProfitMode == TargetProfitMode.FIXED_INR) {
                                    "+${Formatters.formatInr(riskConfig.targetProfitInr)}"
                                } else {
                                    "+${String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)}%"
                                },
                                style = MonoTabularStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = EmeraldBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Combined Basket vs Per-Stock Scope Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasketExitScope.entries.forEach { scope ->
                            FilterChip(
                                selected = riskConfig.basketExitScope == scope,
                                onClick = { onUpdateRiskConfig(riskConfig.copy(basketExitScope = scope)) },
                                label = { Text(scope.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanContainer,
                                    selectedLabelColor = CyanTerminal,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("basket_scope_${scope.name.lowercase()}")
                            )
                        }
                    }

                    // Target Mode Toggle: Auto-Smart % (0.8%) vs Custom Fixed ₹ (₹30–₹1,000)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetProfitMode.entries.forEach { mode ->
                            FilterChip(
                                selected = riskConfig.targetProfitMode == mode,
                                onClick = { onUpdateRiskConfig(riskConfig.copy(targetProfitMode = mode)) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldContainer,
                                    selectedLabelColor = EmeraldBright,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("tp_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    if (riskConfig.targetProfitMode == TargetProfitMode.PERCENTAGE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5, 0.8, 1.2, 2.0).forEach { pctPreset ->
                                FilterChip(
                                    selected = kotlin.math.abs(riskConfig.targetProfitPct - pctPreset) < 0.01,
                                    onClick = { onUpdateRiskConfig(riskConfig.copy(targetProfitPct = pctPreset)) },
                                    label = { Text("${String.format(Locale.US, "%.1f", pctPreset)}%") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldBright,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("tp_pct_preset_${(pctPreset * 10).toInt()}")
                                )
                            }
                        }

                        Slider(
                            value = riskConfig.targetProfitPct.toFloat().coerceIn(0.25f, 5.0f),
                            onValueChange = { valPct ->
                                val rounded = Math.round(valPct * 20.0) / 20.0
                                onUpdateRiskConfig(riskConfig.copy(targetProfitPct = rounded))
                            },
                            valueRange = 0.25f..5.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldBright,
                                activeTrackColor = EmeraldProfit
                            ),
                            modifier = Modifier.testTag("target_profit_slider")
                        )

                        OutlinedTextField(
                            value = tpPctText,
                            onValueChange = { raw ->
                                tpPctText = raw
                                raw.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 0.25) {
                                        onUpdateRiskConfig(riskConfig.copy(targetProfitPct = parsed.coerceIn(0.25, 15.0)))
                                    }
                                }
                            },
                            label = { Text("Auto-Smart Basket Target % (Default 0.80%)") },
                            suffix = { Text("%", color = EmeraldBright) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("target_profit_input")
                        )

                        val factor = riskConfig.targetProfitPct / 100.0
                        Surface(
                            color = TerminalSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldProfit.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "PROPORTIONAL BASKET TARGET PREVIEW (${String.format(Locale.US, "%.2f", riskConfig.targetProfitPct)}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = "₹2k → +₹${(2000 * factor).toInt()} • ₹5k → +₹${(5000 * factor).toInt()} • ₹10k → +₹${(10000 * factor).toInt()} • ₹40k → +₹${(40000 * factor).toInt()}",
                                    style = MonoTabularStyle.copy(fontSize = 11.sp),
                                    color = TextPrimary
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50.0, 100.0, 150.0, 300.0).forEach { preset ->
                                FilterChip(
                                    selected = riskConfig.targetProfitInr.toInt() == preset.toInt(),
                                    onClick = { onUpdateRiskConfig(riskConfig.copy(targetProfitInr = preset)) },
                                    label = { Text("+₹${preset.toInt()}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldBright,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("tp_inr_preset_${preset.toInt()}")
                                )
                            }
                        }

                        Slider(
                            value = riskConfig.targetProfitInr.toFloat().coerceIn(30f, 1000f),
                            onValueChange = { valInr ->
                                val rounded = (Math.round(valInr / 10.0) * 10.0).coerceIn(30.0, 1000.0)
                                onUpdateRiskConfig(riskConfig.copy(targetProfitInr = rounded))
                            },
                            valueRange = 30f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldBright,
                                activeTrackColor = EmeraldProfit
                            ),
                            modifier = Modifier.testTag("target_profit_inr_slider")
                        )

                        OutlinedTextField(
                            value = tpInrText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                tpInrText = digits
                                digits.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 30.0) {
                                        onUpdateRiskConfig(riskConfig.copy(targetProfitInr = parsed.coerceIn(30.0, 1000.0)))
                                    }
                                }
                            },
                            label = { Text("Custom Fixed Basket Target (₹30 – ₹1,000)") },
                            prefix = { Text("₹", color = EmeraldBright) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("target_profit_inr_input")
                        )
                    }

                    // Zero-Wait Instant Net Auto-Book (+₹15 Trade / +₹25 Basket)
                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldProfit.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ZERO-WAIT INSTANT AUTO-BOOK (NET AFTER CHARGES)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldBright
                            )
                            Text(
                                text = "Single-Trade Auto-Book (+₹${riskConfig.singleTradeInstantBookInr.toInt()} Net)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(10.0, 15.0, 25.0, 50.0).forEach { tradeAmt ->
                                    FilterChip(
                                        selected = riskConfig.singleTradeInstantBookInr.toInt() == tradeAmt.toInt(),
                                        onClick = { onUpdateRiskConfig(riskConfig.copy(singleTradeInstantBookInr = tradeAmt)) },
                                        label = { Text("+₹${tradeAmt.toInt()}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldContainer,
                                            selectedLabelColor = EmeraldBright,
                                            containerColor = TerminalSurface,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("zero_wait_trade_${tradeAmt.toInt()}")
                                    )
                                }
                            }
                            Text(
                                text = "Basket / Master Auto-Book (+₹${riskConfig.basketInstantBookInr.toInt()} Net)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(25.0, 50.0, 100.0).forEach { basketAmt ->
                                    FilterChip(
                                        selected = riskConfig.basketInstantBookInr.toInt() == basketAmt.toInt(),
                                        onClick = { onUpdateRiskConfig(riskConfig.copy(basketInstantBookInr = basketAmt)) },
                                        label = { Text("+₹${basketAmt.toInt()}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanContainer,
                                            selectedLabelColor = CyanTerminal,
                                            containerColor = TerminalSurface,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("zero_wait_basket_${basketAmt.toInt()}")
                                    )
                                }
                            }
                        }
                    }

                    // Estimated Brokerage & Taxes Breakdown alongside active target
                    val targetNet = riskConfig.computeBasketTargetInr(effectiveBudget)
                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ESTIMATED BROKERAGE & TAXES (PER ${Formatters.formatInr(effectiveBudget)} TRADE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Brokerage + STT + GST: ~${Formatters.formatInr(estimatedCharges.totalCharges)}",
                                    style = MonoTabularStyle.copy(fontSize = 12.sp),
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Gross Needed: +${Formatters.formatInr(targetNet + estimatedCharges.totalCharges)}",
                                    style = MonoTabularStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                    color = EmeraldBright
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Mandatory Stop-Loss Protection
        item(key = "stop_loss_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mandatory Stop-Loss Protection",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonBright
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", riskConfig.stopLossPct)}% per-trade SL + strict -₹${riskConfig.basketMaxLossInr.toInt()} per-basket loss cap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(color = CrimsonContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = if (riskConfig.stopLossMode == StopLossMode.FIXED_INR) {
                                    "-${Formatters.formatInr(riskConfig.stopLossInr)}"
                                } else {
                                    "-${String.format(Locale.US, "%.2f", riskConfig.stopLossPct)}%"
                                },
                                style = MonoTabularStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = CrimsonBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StopLossMode.entries.forEach { mode ->
                            FilterChip(
                                selected = riskConfig.stopLossMode == mode,
                                onClick = { onUpdateRiskConfig(riskConfig.copy(stopLossMode = mode)) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonContainer,
                                    selectedLabelColor = CrimsonBright,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("sl_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    if (riskConfig.stopLossMode == StopLossMode.PERCENTAGE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.50, 0.75, 1.00, 1.50).forEach { slPreset ->
                                FilterChip(
                                    selected = kotlin.math.abs(riskConfig.stopLossPct - slPreset) < 0.01,
                                    onClick = { onUpdateRiskConfig(riskConfig.copy(stopLossPct = slPreset)) },
                                    label = { Text("-${String.format(Locale.US, "%.2f", slPreset)}%") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonContainer,
                                        selectedLabelColor = CrimsonBright,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("sl_pct_preset_${(slPreset * 100).toInt()}")
                                )
                            }
                        }

                        Slider(
                            value = riskConfig.stopLossPct.toFloat().coerceIn(0.20f, 6.0f),
                            onValueChange = { valPct ->
                                val rounded = Math.round(valPct * 20.0) / 20.0
                                onUpdateRiskConfig(riskConfig.copy(stopLossPct = rounded.coerceIn(0.20, 15.0)))
                            },
                            valueRange = 0.20f..6.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonBright,
                                activeTrackColor = CrimsonLoss
                            ),
                            modifier = Modifier.testTag("stop_loss_slider")
                        )

                        OutlinedTextField(
                            value = slPctText,
                            onValueChange = { raw ->
                                slPctText = raw
                                raw.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 0.20) {
                                        onUpdateRiskConfig(riskConfig.copy(stopLossPct = parsed.coerceIn(0.20, 15.0)))
                                    }
                                }
                            },
                            label = { Text("Exact Per-Trade Stop-Loss % (Default 0.75%, Min 0.20%)") },
                            suffix = { Text("%", color = CrimsonBright) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stop_loss_input")
                        )
                    } else {
                        Slider(
                            value = riskConfig.stopLossInr.toFloat().coerceIn(25f, 1000f),
                            onValueChange = { valInr ->
                                val rounded = (Math.round(valInr / 25.0) * 25.0).coerceIn(25.0, 1000.0)
                                onUpdateRiskConfig(riskConfig.copy(stopLossInr = rounded))
                            },
                            valueRange = 25f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonBright,
                                activeTrackColor = CrimsonLoss
                            ),
                            modifier = Modifier.testTag("stop_loss_inr_slider")
                        )

                        OutlinedTextField(
                            value = slInrText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                slInrText = digits
                                digits.toDoubleOrNull()?.let { parsed ->
                                    if (parsed >= 25.0) {
                                        onUpdateRiskConfig(riskConfig.copy(stopLossInr = parsed))
                                    }
                                }
                            },
                            label = { Text("Fixed Stop-Loss Cap (₹, Min ₹25)") },
                            prefix = { Text("₹", color = CrimsonBright) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stop_loss_inr_input")
                        )
                    }

                    // Strict Per-Basket Max Loss Cap (-₹80 default)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PER-BASKET MAX LOSS CAP (CUT LOSING BASKET FAST)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonBright
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(40.0, 80.0, 120.0, 150.0).forEach { cap ->
                                FilterChip(
                                    selected = kotlin.math.abs(riskConfig.basketMaxLossInr - cap) < 0.5,
                                    onClick = { onUpdateRiskConfig(riskConfig.copy(basketMaxLossInr = cap)) },
                                    label = { Text("-₹${cap.toInt()}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonContainer,
                                        selectedLabelColor = CrimsonBright,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("basket_sl_cap_${cap.toInt()}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3b. Daily Max Loss Circuit Breaker (-₹1,500)
        item(key = "daily_circuit_breaker_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Max Loss Circuit Breaker",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonBright
                            )
                            Text(
                                text = "Auto-squares off all trades and locks Auto-Trading for the day if Total Net Day P&L hits this cap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(color = CrimsonContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "-${Formatters.formatInr(riskConfig.dailyMaxLossLimitInr)}",
                                style = MonoTabularStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = CrimsonBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(750.0, 1500.0, 3000.0, 5000.0).forEach { preset ->
                            FilterChip(
                                selected = riskConfig.dailyMaxLossLimitInr.toInt() == preset.toInt(),
                                onClick = {
                                    onUpdateRiskConfig(riskConfig.copy(dailyMaxLossLimitInr = preset))
                                },
                                label = { Text("-${Formatters.formatInr(preset).removeSuffix(".00")}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonContainer,
                                    selectedLabelColor = CrimsonBright,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("circuit_breaker_preset_${preset.toInt()}")
                            )
                        }
                    }

                    Slider(
                        value = riskConfig.dailyMaxLossLimitInr.toFloat().coerceIn(300f, 15000f),
                        onValueChange = { valInr ->
                            val rounded = (Math.round(valInr / 100.0) * 100.0).coerceIn(300.0, 50000.0)
                            onUpdateRiskConfig(riskConfig.copy(dailyMaxLossLimitInr = rounded))
                        },
                        valueRange = 300f..15000f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonBright,
                            activeTrackColor = CrimsonLoss
                        ),
                        modifier = Modifier.testTag("circuit_breaker_slider")
                    )

                    OutlinedTextField(
                        value = circuitInrText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            circuitInrText = digits
                            digits.toDoubleOrNull()?.let { parsed ->
                                if (parsed >= 300.0) {
                                    onUpdateRiskConfig(
                                        riskConfig.copy(dailyMaxLossLimitInr = parsed.coerceIn(300.0, 50000.0))
                                    )
                                }
                            }
                        },
                        label = { Text("Daily Max Loss Cap (₹, Default ₹1,500)") },
                        prefix = { Text("-₹", color = CrimsonBright) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("circuit_breaker_input")
                    )
                }
            }
        }

        // 3c. High-Probability Profit-Lock & Strategy Confirmation Mode
        item(key = "high_prob_profit_lock_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (riskConfig.highProbabilityProfitLockEnabled) EmeraldProfit.copy(alpha = 0.5f) else TerminalBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "High-Probability Profit-Lock & High-Water Guard",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "BE +₹3 net charge shield, +₹20 → +₹10 SL lock & ≥${riskConfig.minAiConfidence}% Local AI gate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = riskConfig.highProbabilityProfitLockEnabled,
                            onCheckedChange = { checked ->
                                onUpdateRiskConfig(riskConfig.copy(highProbabilityProfitLockEnabled = checked))
                            },
                            modifier = Modifier.testTag("high_prob_profit_lock_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TerminalVoid,
                                checkedTrackColor = EmeraldProfit
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(95, 97, 99).forEach { conf ->
                            FilterChip(
                                selected = riskConfig.minAiConfidence == conf,
                                onClick = {
                                    onUpdateRiskConfig(riskConfig.copy(minAiConfidence = conf))
                                },
                                label = { Text("Min $conf% AI") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldContainer,
                                    selectedLabelColor = EmeraldBright,
                                    containerColor = TerminalSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("min_ai_conf_$conf")
                            )
                        }
                    }

                    Surface(
                        color = TerminalSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TerminalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Break-Even +₹3 Charge Shield: Once a trade hits +₹5 net, SL shifts to Gross ≥ RoundTripCharges + ₹3.00 so exits never lose money to fees.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldBright
                            )
                            Text(
                                text = "• Instant 99% 3-Factor Local Gate: Evaluates every tick in <1ms; invests immediately when VWAP/EMA/RSI + Vol Surge + Fee-Clearance hit ≥${riskConfig.minAiConfidence}%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    SchedulerToggleRow(
                        title = "Single-Direction AI Trend Mode (One Side Only)",
                        subtitle = "OFF (Default): Synchronized Pair (1 BUY + 1 SELL together, waits until both exit). ON: One side only.",
                        checked = riskConfig.singleDirectionOnly,
                        onCheckedChange = { enabled ->
                            onUpdateRiskConfig(riskConfig.copy(singleDirectionOnly = enabled))
                        },
                        testTag = "single_direction_mode_switch"
                    )

                    SchedulerToggleRow(
                        title = "Allow SELL Auto-Trading (OFF / ON)",
                        subtitle = if (riskConfig.allowSellAutoTrade) {
                            "ON: Bot can auto-invest in SELL signals (Dual-Basket BUY + SELL active)."
                        } else {
                            "OFF (HOLD - Default): 100% BUY-Only auto-trading. SELL signals are strictly blocked & held."
                        },
                        checked = riskConfig.allowSellAutoTrade,
                        onCheckedChange = { enabled ->
                            onUpdateRiskConfig(riskConfig.copy(allowSellAutoTrade = enabled))
                        },
                        testTag = "risk_allow_sell_switch"
                    )

                    Surface(
                        color = AmberContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmberAlert.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("risk_disclaimer_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Market risk disclaimer",
                                tint = AmberAlert,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Risk Disclaimer: Strict capital-protection filters minimize downside risk, but no algorithm can guarantee zero loss in live financial markets due to slippage, volatility, and market gaps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // 4. Market Hours Active Window Scheduler & Continuous Re-Entry Loop
        item(key = "market_hours_scheduler_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = AmberAlert,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Market Hours Active Window",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${marketWindowStatus.istTimeFormatted} • ${marketWindowStatus.statusSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (marketWindowStatus.isAnyMarketOpen) EmeraldBright else AmberAlert
                                )
                            }
                        }
                    }

                    val isMorningEveningPreset = riskConfig.enforceMarketHours &&
                        riskConfig.autoStartAtMarketOpen &&
                        !riskConfig.mcxExtendedHoursEnabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isMorningEveningPreset,
                            onClick = { onActivateMorningEveningSchedule(true) },
                            label = { Text("Morning–Evening (09:15–15:15 IST)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldBright,
                                containerColor = TerminalSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("preset_morning_evening_schedule")
                        )
                        FilterChip(
                            selected = !riskConfig.enforceMarketHours,
                            onClick = { onActivateMorningEveningSchedule(false) },
                            label = { Text("24/7 Demo Mode") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanContainer,
                                selectedLabelColor = CyanTerminal,
                                containerColor = TerminalSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("preset_24_7_demo_mode")
                        )
                    }

                    SchedulerToggleRow(
                        title = "Enforce IST Schedule (09:15 AM – 03:15 PM)",
                        subtitle = "Turn OFF for 24/7 Paper Demo testing outside Indian market hours.",
                        checked = riskConfig.enforceMarketHours,
                        onCheckedChange = {
                            onUpdateRiskConfig(riskConfig.copy(enforceMarketHours = it))
                        },
                        testTag = "enforce_market_hours_switch"
                    )

                    SchedulerToggleRow(
                        title = "Skip Opening Volatility (09:15 AM – 09:30 AM IST)",
                        subtitle = "Wait for the first 15 minutes of erratic opening swings to settle before entering NSE/BSE trades.",
                        checked = riskConfig.skipOpeningVolatilityEnabled,
                        onCheckedChange = {
                            onUpdateRiskConfig(riskConfig.copy(skipOpeningVolatilityEnabled = it))
                        },
                        testTag = "skip_opening_volatility_switch"
                    )

                    SchedulerToggleRow(
                        title = "MCX Extended Hours (up to 11:30 PM IST)",
                        subtitle = "Keep MCX commodities active after 03:15 PM NSE/BSE close.",
                        checked = riskConfig.mcxExtendedHoursEnabled,
                        onCheckedChange = {
                            onUpdateRiskConfig(riskConfig.copy(mcxExtendedHoursEnabled = it))
                        },
                        testTag = "mcx_extended_switch"
                    )

                    SchedulerToggleRow(
                        title = "Auto-Start at 09:15 AM & Square-Off at Close",
                        subtitle = "Automatically loops all day and squares off positions before close.",
                        checked = riskConfig.autoStartAtMarketOpen,
                        onCheckedChange = {
                            onUpdateRiskConfig(riskConfig.copy(autoStartAtMarketOpen = it))
                        },
                        testTag = "auto_start_open_switch"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyanTerminal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Max Concurrent Positions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (riskConfig.maxOpenPositions <= 2) {
                                "${riskConfig.maxOpenPositions} max (Top 1–2 AI)"
                            } else {
                                "${riskConfig.maxOpenPositions} (${riskConfig.maxPositionsPerBasket}B+${riskConfig.maxPositionsPerBasket}S)"
                            },
                            style = MonoTabularStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                    Slider(
                        value = riskConfig.maxOpenPositions.toFloat().coerceIn(1f, 20f),
                        onValueChange = {
                            val clamped = it.toInt().coerceIn(1, 20)
                            onUpdateRiskConfig(riskConfig.copy(maxOpenPositions = clamped))
                        },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanTerminal,
                            activeTrackColor = CyanTerminal
                        ),
                        modifier = Modifier.testTag("max_positions_slider")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (riskConfig.reentryCooldownSec == 0) {
                                "Post-Exit Re-Entry Delay: 0s (Instant Re-Entry)"
                            } else {
                                "Post-Exit Re-Entry Cooldown (${riskConfig.reentryCooldownSec}s)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 15, 20, 30, 45).forEach { sec ->
                                FilterChip(
                                    selected = riskConfig.reentryCooldownSec == sec,
                                    onClick = { onUpdateRiskConfig(riskConfig.copy(reentryCooldownSec = sec)) },
                                    label = { Text(if (sec == 0) "0s (Instant)" else "${sec}s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanContainer,
                                        selectedLabelColor = CyanTerminal,
                                        containerColor = TerminalSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("cooldown_preset_$sec")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Paper Trading (Demo Mode) Sandbox Controls
        item(key = "paper_sandbox_card") {
            Surface(
                color = TerminalSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyanTerminal.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Paper Trading (Demo Mode)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Test multi-stock auto-split & smart basket auto-drop safely with virtual funds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = riskConfig.isPaperMode,
                            onCheckedChange = { checked ->
                                onUpdateRiskConfig(riskConfig.copy(isPaperMode = checked))
                            },
                            modifier = Modifier.testTag("paper_mode_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TerminalVoid,
                                checkedTrackColor = CyanTerminal
                            )
                        )
                    }

                    if (riskConfig.isPaperMode) {
                        OutlinedButton(
                            onClick = onResetPaperAccount,
                            border = BorderStroke(1.dp, CyanTerminal.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanTerminal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_paper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Reset Virtual ₹5,00,000 Portfolio")
                        }
                    }
                }
            }
        }

        item(key = "save_all_risk_rules_bottom") {
            Button(
                onClick = { commitAndSaveAllRules() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldProfit,
                    contentColor = TerminalVoid
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_risk_rules_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (lastSavedAt != null) "Save & Apply Risk Rules (Last: $lastSavedAt)" else "Save & Apply All Risk Rules",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        item(key = "risk_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SchedulerToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = TerminalVoid,
                checkedTrackColor = EmeraldProfit
            )
        )
    }
}
