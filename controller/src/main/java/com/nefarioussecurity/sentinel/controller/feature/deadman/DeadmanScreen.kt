package com.nefarioussecurity.sentinel.controller.feature.deadman

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.management.DeadmanUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed
import com.runtimebridge.api.ManagementProtocol
import java.util.concurrent.TimeUnit

private val SentinelGreen = Color(0xFF55D6A0)
private val SentinelAmber = Color(0xFFF9A825)
private val SentinelGrey = Color(0xFF8A8A8A)
private val SentinelCard = Color(0xFF101212)
private val SentinelInset = Color(0xFF171919)
private val SentinelBorder = Color(0xFF303232)
private val SentinelDeepRed = Color(0xFF1A0707)
private val Background = Color(0xFF0B0C0C)

private enum class IntervalUnit {
    HOURS,
    DAYS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadmanScreen(
    state: DeadmanUiState,
    message: String?,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetIntervalMs: (Long) -> Unit,
    onCheckIn: () -> Unit,
) {
    var confirmEnable by remember {
        mutableStateOf(false)
    }

    var confirmDisable by remember {
        mutableStateOf(false)
    }

    /*
     * Determine the initial display unit from the Core interval.
     *
     * IMPORTANT:
     * `unit` itself is intentionally NOT remembered with state.intervalMs
     * as a key. Once the user selects Hours or Days, that selection must
     * remain stable while Core state refreshes.
     */
    val defaultUnit =
        remember(state.intervalMs) {
            if (
                state.intervalMs > 0L &&
                state.intervalMs %
                    TimeUnit.DAYS.toMillis(1L) == 0L
            ) {
                IntervalUnit.DAYS
            } else {
                IntervalUnit.HOURS
            }
        }

    var unit by remember {
        mutableStateOf(defaultUnit)
    }

    /*
     * The displayed value may follow Core updates, but it is always
     * interpreted using the user's currently selected unit.
     */
    var valueText by
        remember(
            state.intervalMs,
            unit,
        ) {
            mutableStateOf(
                intervalValueText(
                    state.intervalMs,
                    unit,
                ),
            )
        }

    val parsedValue =
        valueText.toLongOrNull()

    val proposedIntervalMs =
        parsedValue?.let {
            intervalToMilliseconds(
                it,
                unit,
            )
        }

    val intervalValid =
        proposedIntervalMs != null &&
            proposedIntervalMs >= state.minIntervalMs &&
            proposedIntervalMs <= state.maxIntervalMs

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DEAD MAN'S SWITCH",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SentinelRed,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                    ),
            )
        },
    ) { padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 18.dp,
                        vertical = 18.dp,
                    ),
        ) {
            DeadmanHeroCard(
                state = state,
                onToggle = { enabled ->
                    if (enabled) {
                        confirmEnable = true
                    } else {
                        confirmDisable = true
                    }
                },
            )

            Spacer(Modifier.height(14.dp))

            ProtectionStatusCard(
                state = state,
            )

            Spacer(Modifier.height(14.dp))

            IntervalCard(
                state = state,
                unit = unit,
                valueText = valueText,
                intervalValid = intervalValid,
                onUnitChange = { newUnit ->
                    unit = newUnit

                    valueText =
                        intervalValueText(
                            state.intervalMs,
                            newUnit,
                        )
                },
                onValueChange = { newText ->
                    valueText =
                        newText.filter {
                            it.isDigit()
                        }

                    val parsed =
                        valueText.toLongOrNull()
                            ?: return@IntervalCard

                    val milliseconds =
                        intervalToMilliseconds(
                            parsed,
                            unit,
                        )

                    if (
                        milliseconds >= state.minIntervalMs &&
                        milliseconds <= state.maxIntervalMs
                    ) {
                        onSetIntervalMs(
                            milliseconds,
                        )
                    }
                },
            )

            Spacer(Modifier.height(14.dp))

            CheckInCard(
                state = state,
                onCheckIn = onCheckIn,
            )

            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))

                MessageCard(
                    message = message,
                )
            }

            Spacer(Modifier.height(14.dp))

            RulesCard()

            Spacer(Modifier.height(14.dp))

            CoreStatusCard()

            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmEnable) {
        AlertDialog(
            onDismissRequest = {
                confirmEnable = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SentinelRed,
                )
            },
            title = {
                Text(
                    "Arm Dead Man's Switch?",
                )
            },
            text = {
                Text(
                    "If no successful check-in occurs before the configured " +
                        "interval expires, the headless Sentinel Core can invoke " +
                        "its destructive protection.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEnable = false
                        onSetEnabled(true)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SentinelRed,
                        ),
                ) {
                    Text("ARM")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmEnable = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = {
                confirmDisable = false
            },
            title = {
                Text(
                    "Disable Dead Man's Switch?",
                )
            },
            text = {
                Text(
                    "The headless Core will stop enforcing the Dead Man expiry timer.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDisable = false
                        onSetEnabled(false)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SentinelRed,
                        ),
                ) {
                    Text("DISABLE")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDisable = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DeadmanHeroCard(
    state: DeadmanUiState,
    onToggle: (Boolean) -> Unit,
) {
    val active = state.enabled

    val statusColor =
        if (active) {
            SentinelGreen
        } else {
            SentinelRed
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                if (active) {
                    SentinelGreen.copy(alpha = 0.72f)
                } else {
                    SentinelRed.copy(alpha = 0.62f)
                },
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (active) {
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        SentinelDeepRed.copy(alpha = 0.42f),
                                        SentinelCard,
                                        SentinelCard,
                                    ),
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        SentinelCard,
                                        SentinelCard,
                                    ),
                            )
                        },
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(11.dp),
                    color =
                        statusColor.copy(alpha = 0.12f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }

                Spacer(Modifier.size(13.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Dead Man's Switch",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text =
                            if (active) {
                                "PROTECTION ACTIVE"
                            } else {
                                "PROTECTION INACTIVE"
                            },
                        color =
                            if (active) {
                                SentinelRed.copy(alpha = 0.78f)
                            } else {
                                SentinelGrey
                            },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                }

                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggle,
                    colors = sentinelSwitchColors(),
                )
            }
        }
    }
}

@Composable
private fun ProtectionStatusCard(
    state: DeadmanUiState,
) {
    val expired =
        state.state ==
            ManagementProtocol.DEADMAN_STATE_EXPIRED

    val active =
        state.state ==
            ManagementProtocol.DEADMAN_STATE_ARMED

    val invalid =
        state.state ==
            ManagementProtocol.DEADMAN_STATE_INVALID

    val statusColor =
        when {
            expired ->
                SentinelRed

            invalid ->
                SentinelAmber

            active ->
                SentinelGreen

            else ->
                SentinelRed
        }

    val statusLabel =
        when {
            expired ->
                "EXPIRED"

            invalid ->
                "INVALID"

            active ->
                "ACTIVE"

            else ->
                "INACTIVE"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(alpha = 0.48f),
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    SentinelDeepRed.copy(alpha = 0.34f),
                                    SentinelCard,
                                    SentinelCard,
                                ),
                        ),
                    ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(11.dp),
                        color =
                            statusColor.copy(alpha = 0.13f),
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = statusColor,
                            )
                        }
                    }

                    Spacer(Modifier.size(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Protection Status",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Text(
                        text = "DEAD MAN",
                        color = SentinelGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }

                Spacer(Modifier.height(13.dp))

                HorizontalDivider(
                    color =
                        SentinelRed.copy(alpha = 0.22f),
                )

                Spacer(Modifier.height(13.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text =
                                if (state.enabled) {
                                    "TIME REMAINING"
                                } else {
                                    "DEADLINE"
                                },
                            color = SentinelGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text =
                                when {
                                    expired ->
                                        "EXPIRED"

                                    state.enabled ->
                                        formatDurationWithSeconds(
                                            state.remainingMs,
                                        )

                                    else ->
                                        "Not running"
                                },
                            color =
                                if (expired) {
                                    SentinelRed
                                } else {
                                    Color.White
                                },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(11.dp)
                                .background(
                                    statusColor,
                                    CircleShape,
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntervalCard(
    state: DeadmanUiState,
    unit: IntervalUnit,
    valueText: String,
    intervalValid: Boolean,
    onUnitChange: (IntervalUnit) -> Unit,
    onValueChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(alpha = 0.42f),
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color =
                        SentinelRed.copy(alpha = 0.12f),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                            tint = SentinelRed,
                        )
                    }
                }

                Spacer(Modifier.size(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Check-in Interval",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "CHECK-IN WINDOW",
                        color =
                            SentinelRed.copy(alpha = 0.78f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    "Set how long Sentinel may go without a successful " +
                        "check-in before the Dead Man protection expires.",
                color = SentinelGrey,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(13.dp))

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected =
                        unit ==
                            IntervalUnit.HOURS,
                    onClick = {
                        onUnitChange(
                            IntervalUnit.HOURS,
                        )
                    },
                    label = {
                        Text("Hours")
                    },
                )

                FilterChip(
                    selected =
                        unit ==
                            IntervalUnit.DAYS,
                    onClick = {
                        onUnitChange(
                            IntervalUnit.DAYS,
                        )
                    },
                    label = {
                        Text("Days")
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                label = {
                    Text(
                        if (
                            unit ==
                            IntervalUnit.HOURS
                        ) {
                            "Interval in hours"
                        } else {
                            "Interval in days"
                        },
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number,
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text =
                    if (intervalValid) {
                        "Current Core interval: " +
                            formatConfiguredInterval(
                                state.intervalMs,
                            )
                    } else {
                        "Allowed range: " +
                            formatConfiguredInterval(
                                state.minIntervalMs,
                            ) +
                            " to " +
                            formatConfiguredInterval(
                                state.maxIntervalMs,
                            )
                    },
                color =
                    if (intervalValid) {
                        SentinelGreen
                    } else {
                        SentinelAmber
                    },
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CheckInCard(
    state: DeadmanUiState,
    onCheckIn: () -> Unit,
) {
    val armed =
        state.state ==
            ManagementProtocol.DEADMAN_STATE_ARMED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(alpha = 0.42f),
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Refresh,
                    contentDescription = null,
                    tint =
                        if (armed) {
                            SentinelGreen
                        } else {
                            SentinelRed
                        },
                )

                Spacer(Modifier.size(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Check-in Status",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "CHECK-IN STATUS",
                        color =
                            SentinelRed.copy(alpha = 0.78f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            HorizontalDivider(
                color = SentinelBorder,
            )

            Spacer(Modifier.height(12.dp))

            if (!state.enabled) {
                Text(
                    text =
                        "Dead Man protection is disabled.",
                    color = SentinelGrey,
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    text =
                        if (armed) {
                            "Time remaining: " +
                                formatDurationWithSeconds(
                                    state.remainingMs,
                                )
                        } else {
                            "Core state: " +
                                deadmanStateText(
                                    state.state,
                                )
                        },
                    color =
                        if (armed) {
                            SentinelGreen
                        } else {
                            SentinelAmber
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = armed,
                onClick = onCheckIn,
                shape = RoundedCornerShape(9.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SentinelRed,
                    ),
            ) {
                Icon(
                    imageVector =
                        Icons.Default.CheckCircle,
                    contentDescription = null,
                )

                Spacer(Modifier.size(8.dp))

                Text(
                    text = "CHECK IN NOW",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelInset,
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelBorder,
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
        ) {
            Text(
                text = "DEAD MAN RULES",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(9.dp))

            Text(
                text =
                    "A successful check-in resets the full interval. " +
                        "The headless Core owns the alarm and continues operating " +
                        "without the Controller installed.",
                color = SentinelGrey,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun CoreStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelInset,
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelBorder,
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment =
                Alignment.Top,
        ) {
            Icon(
                imageVector =
                    Icons.Default.Info,
                contentDescription = null,
                tint = SentinelRed,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.size(10.dp))

            Text(
                text =
                    "Countdown, interval and armed state are read directly " +
                        "from the Device Owner Core. Removing the Controller " +
                        "does not stop an armed Dead Man timer.",
                color = SentinelGrey,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF16130C),
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelAmber.copy(alpha = 0.6f),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Text(
            text = message,
            color = SentinelAmber,
            fontSize = 12.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun sentinelSwitchColors() =
    SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = SentinelGreen,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor =
            SentinelRed.copy(alpha = 0.65f),
    )

private fun intervalValueText(
    intervalMs: Long,
    unit: IntervalUnit,
): String {
    if (intervalMs <= 0L) {
        return ""
    }

    return when (unit) {
        IntervalUnit.HOURS ->
            TimeUnit.MILLISECONDS
                .toHours(intervalMs)
                .toString()

        IntervalUnit.DAYS ->
            TimeUnit.MILLISECONDS
                .toDays(intervalMs)
                .toString()
    }
}

private fun intervalToMilliseconds(
    value: Long,
    unit: IntervalUnit,
): Long =
    when (unit) {
        IntervalUnit.HOURS ->
            TimeUnit.HOURS.toMillis(value)

        IntervalUnit.DAYS ->
            TimeUnit.DAYS.toMillis(value)
    }

private fun deadmanStateText(
    state: Int,
): String =
    when (state) {
        ManagementProtocol.DEADMAN_STATE_DISABLED ->
            "DISABLED"

        ManagementProtocol.DEADMAN_STATE_INVALID ->
            "INVALID"

        ManagementProtocol.DEADMAN_STATE_EXPIRED ->
            "EXPIRED"

        ManagementProtocol.DEADMAN_STATE_ARMED ->
            "ARMED"

        else ->
            "UNKNOWN"
    }

private fun formatConfiguredInterval(
    ms: Long,
): String {
    if (ms <= 0L) {
        return "--"
    }

    val days =
        TimeUnit.MILLISECONDS.toDays(ms)

    if (
        days > 0L &&
        ms % TimeUnit.DAYS.toMillis(1L) == 0L
    ) {
        return "${days}d"
    }

    val hours =
        TimeUnit.MILLISECONDS.toHours(ms)

    return "${hours}h"
}

private fun formatDurationWithSeconds(
    ms: Long,
): String {
    val totalSeconds =
        TimeUnit.MILLISECONDS.toSeconds(
            ms.coerceAtLeast(0L),
        )

    val days =
        totalSeconds / 86_400L

    val hours =
        (totalSeconds % 86_400L) / 3_600L

    val minutes =
        (totalSeconds % 3_600L) / 60L

    val seconds =
        totalSeconds % 60L

    return if (days > 0L) {
        "%dd %02d:%02d:%02d".format(
            days,
            hours,
            minutes,
            seconds,
        )
    } else {
        "%02d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    }
}
