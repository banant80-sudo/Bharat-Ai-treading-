package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.BrokerConnectionState
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberContainer
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
import com.example.util.Formatters
import com.example.util.MarketWindowStatus

@Composable
fun TopTelemetryBar(
    connectionState: BrokerConnectionState,
    isPaperMode: Boolean,
    marketWindowStatus: MarketWindowStatus,
    accountSummary: AccountSummary,
    onOpenBrokerModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "conn_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val isConnected = connectionState is BrokerConnectionState.Connected
    val statusDotColor = when (connectionState) {
        is BrokerConnectionState.Connected -> if (connectionState.isPaperSession) CyanTerminal else EmeraldProfit
        is BrokerConnectionState.Connecting -> AmberAlert
        is BrokerConnectionState.Disconnected -> TextMuted
        is BrokerConnectionState.Error -> CrimsonLoss
    }

    val statusText = when (connectionState) {
        is BrokerConnectionState.Connected -> if (connectionState.isPaperSession) "DEMO" else "LIVE"
        is BrokerConnectionState.Connecting -> "SYNC"
        is BrokerConnectionState.Disconnected -> "OFFLINE"
        is BrokerConnectionState.Error -> "ERROR"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TerminalSurface, TerminalVoid)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldContainer, CyanContainer)
                            )
                        )
                        .border(1.dp, EmeraldProfit.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Bharat AI Trading Terminal",
                        tint = EmeraldBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Bharat AI Trading",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isPaperMode) CyanContainer else AmberContainer,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPaperMode) CyanTerminal.copy(alpha = 0.4f) else AmberAlert.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = if (isPaperMode) "PAPER DEMO" else "LIVE API",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isPaperMode) CyanTerminal else AmberAlert,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "${marketWindowStatus.istTimeFormatted} • ${marketWindowStatus.statusSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (marketWindowStatus.isAnyMarketOpen) TextSecondary else AmberAlert,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenBrokerModal,
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isConnected) statusDotColor.copy(alpha = 0.55f) else TerminalBorder
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("broker_connection_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (isConnected) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Cable else Icons.Default.LinkOff,
                        contentDescription = "Broker Connection",
                        tint = statusDotColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }

        val pnlPositive = accountSummary.totalDayPnl >= 0.0
        val pnlColor by animateColorAsState(
            targetValue = if (pnlPositive) EmeraldProfit else CrimsonLoss,
            label = "pnl_color"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryMetricCard(
                label = "AVAILABLE CASH",
                value = Formatters.formatInr(accountSummary.availableCash),
                subValue = "RMS Margin",
                valueColor = TextPrimary,
                accentColor = TerminalBorder,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                label = "USED MARGIN",
                value = Formatters.formatInr(accountSummary.usedMargin),
                subValue = if (accountSummary.usedMargin > 0.0) "Micro-Split" else "Idle",
                valueColor = if (accountSummary.usedMargin > 0.0) CyanTerminal else TextSecondary,
                accentColor = TerminalBorder,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                label = "NET DAY P&L",
                value = Formatters.formatInr(accountSummary.totalDayPnl, showSign = true),
                subValue = Formatters.formatPct(accountSummary.totalDayPnlPct),
                valueColor = pnlColor,
                accentColor = if (pnlPositive) EmeraldProfit.copy(alpha = 0.45f) else CrimsonLoss.copy(alpha = 0.45f),
                containerColor = if (pnlPositive) EmeraldContainer.copy(alpha = 0.25f) else CrimsonContainer.copy(alpha = 0.25f),
                modifier = Modifier.weight(1.08f)
            )
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    label: String,
    value: String,
    subValue: String,
    valueColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = TerminalSurfaceElevated
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MonoTabularStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                style = MonoTabularStyle.copy(fontSize = 10.sp),
                color = if (label == "NET DAY P&L") valueColor else TextSecondary,
                maxLines = 1
            )
        }
    }
}
