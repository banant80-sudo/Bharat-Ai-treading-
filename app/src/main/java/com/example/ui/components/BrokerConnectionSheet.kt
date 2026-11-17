package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.smartapi.TOTPGenerator
import com.example.domain.model.BrokerConnectionState
import com.example.domain.model.BrokerCredentials
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTerminal
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerConnectionSheet(
    credentials: BrokerCredentials,
    connectionState: BrokerConnectionState,
    isPaperMode: Boolean,
    availableCashInr: Double = 0.0,
    hasSavedBrokerLink: Boolean = false,
    onCredentialsChange: (BrokerCredentials) -> Unit,
    onPaperModeToggle: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncFunds: () -> Unit = {},
    onClearSavedLink: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMpin by remember { mutableStateOf(false) }
    var totpCountdown by remember { mutableIntStateOf(TOTPGenerator.secondsRemaining()) }
    var liveTotpCode by remember(credentials.totpSecret) {
        mutableStateOf(TOTPGenerator.generateTotp(credentials.totpSecret))
    }

    LaunchedEffect(credentials.totpSecret) {
        while (true) {
            totpCountdown = TOTPGenerator.secondsRemaining()
            liveTotpCode = TOTPGenerator.generateTotp(credentials.totpSecret)
            delay(1000L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TerminalSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Angel One SmartAPI",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Configure broker credentials & session mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close SmartAPI sheet",
                        tint = TextSecondary
                    )
                }
            }

            // Live Connection Status Banner
            val (badgeColor, badgeBg, statusLabel, statusDetail) = when (connectionState) {
                is BrokerConnectionState.Connected -> listOf(
                    if (connectionState.isPaperSession) CyanTerminal else EmeraldProfit,
                    if (connectionState.isPaperSession) CyanContainer else EmeraldContainer,
                    if (connectionState.isPaperSession) "PAPER SANDBOX ACTIVE" else "SMARTAPI CONNECTED",
                    "Client: ${connectionState.clientId} • Session: ${connectionState.jwtTokenPreview}"
                )
                is BrokerConnectionState.Connecting -> listOf(
                    AmberAlert,
                    AmberContainer,
                    "AUTHENTICATING…",
                    "Contacting apiconnect.angelone.in SmartAPI v1"
                )
                is BrokerConnectionState.Error -> listOf(
                    CrimsonLoss,
                    CrimsonContainer,
                    "CONNECTION FAILED",
                    connectionState.message
                )
                BrokerConnectionState.Disconnected -> listOf(
                    TextMuted,
                    TerminalSurfaceElevated,
                    "DISCONNECTED",
                    "Connect to enable order execution"
                )
            }

            Surface(
                color = (badgeBg as androidx.compose.ui.graphics.Color).copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, (badgeColor as androidx.compose.ui.graphics.Color).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusLabel as String,
                            style = MaterialTheme.typography.labelLarge,
                            color = badgeColor
                        )
                        Text(
                            text = statusDetail as String,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // One-Time Link & Angel One Fund Sync Guide
            Surface(
                color = if (hasSavedBrokerLink) EmeraldContainer.copy(alpha = 0.35f) else AmberContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasSavedBrokerLink) EmeraldProfit.copy(alpha = 0.55f) else AmberAlert.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("broker_routing_note")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                imageVector = if (hasSavedBrokerLink) Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (hasSavedBrokerLink) EmeraldProfit else AmberAlert,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (hasSavedBrokerLink) {
                                    "One-Time Link Saved • Auto-ReLogin Daily"
                                } else {
                                    "One-Time Angel One Link & Fund Setup"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hasSavedBrokerLink) EmeraldProfit else AmberAlert
                            )
                        }
                        if (hasSavedBrokerLink) {
                            TextButton(
                                onClick = onClearSavedLink,
                                modifier = Modifier.testTag("clear_saved_broker_button")
                            ) {
                                Text("Unlink", color = CrimsonLoss, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(
                        text = "• Ek baar API Key, Client ID, MPIN & TOTP Secret save karein — app roz khud TOTP generate karke login kar lega (bar-bar link nahi karna).\n• Live Mode ON karte hi /getRMS aapka Angel One fund (${com.example.util.Formatters.formatInr(availableCashInr)}) sync karke Intraday orders place karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }

            // Paper Trading (Demo Mode) Toggle
            Surface(
                color = TerminalSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TerminalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Paper Trading (Demo Mode)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Test auto-buy & auto-drop safely with virtual ₹5,00,000",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isPaperMode,
                        onCheckedChange = onPaperModeToggle,
                        modifier = Modifier.testTag("paper_mode_switch_modal"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TerminalVoid,
                            checkedTrackColor = CyanTerminal
                        )
                    )
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldProfit,
                unfocusedBorderColor = TerminalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = EmeraldProfit,
                unfocusedLabelColor = TextSecondary,
                cursorColor = EmeraldProfit
            )

            OutlinedTextField(
                value = credentials.apiKey,
                onValueChange = { onCredentialsChange(credentials.copy(apiKey = it)) },
                label = { Text("SmartAPI Key (X-PrivateKey)") },
                placeholder = { Text("e.g. aBcDeFgH") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_api_key")
            )

            OutlinedTextField(
                value = credentials.clientId,
                onValueChange = { onCredentialsChange(credentials.copy(clientId = it)) },
                label = { Text("Angel One Client ID") },
                placeholder = { Text("e.g. A123456") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_client_id")
            )

            OutlinedTextField(
                value = credentials.mpin,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= 6) {
                        onCredentialsChange(credentials.copy(mpin = value))
                    }
                },
                label = { Text("MPIN (4-Digit PIN)") },
                placeholder = { Text("••••") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { showMpin = !showMpin }) {
                        Icon(
                            imageVector = if (showMpin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle MPIN visibility",
                            tint = TextSecondary
                        )
                    }
                },
                visualTransformation = if (showMpin) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_mpin")
            )

            OutlinedTextField(
                value = credentials.totpSecret,
                onValueChange = { onCredentialsChange(credentials.copy(totpSecret = it)) },
                label = { Text("TOTP Secret (Base32 Key)") },
                placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
                leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_totp_secret")
            )

            // Live RFC 6238 TOTP Preview helper
            if (liveTotpCode != null) {
                Surface(
                    color = TerminalSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldProfit.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = EmeraldProfit,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Live Generated TOTP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "${liveTotpCode} (${totpCountdown}s)",
                                style = MonoTabularStyle.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = EmeraldProfit
                            )
                        }
                        LinearProgressIndicator(
                            progress = { totpCountdown / 30f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = EmeraldProfit,
                            trackColor = TerminalBorder
                        )
                    }
                }
            }

            // Connect / Disconnect Action Row
            val isConnected = connectionState is BrokerConnectionState.Connected
            val isConnecting = connectionState is BrokerConnectionState.Connecting

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        border = BorderStroke(1.dp, CrimsonLoss),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLoss),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("broker_disconnect_button")
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaperMode) CyanTerminal else EmeraldProfit,
                        contentColor = TerminalVoid
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("broker_connect_button")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TerminalVoid,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = when {
                                isConnected -> "Reconnect & Save Link"
                                isPaperMode -> "Connect Paper Sandbox"
                                else -> "Link One-Time & Connect Live"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = onSyncFunds,
                    border = BorderStroke(1.dp, CyanTerminal.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanTerminal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_angel_one_funds_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (isPaperMode) {
                            "Refresh Sandbox Balance (${com.example.util.Formatters.formatInr(availableCashInr)})"
                        } else {
                            "Sync Live Angel One Fund (${com.example.util.Formatters.formatInr(availableCashInr)})"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
