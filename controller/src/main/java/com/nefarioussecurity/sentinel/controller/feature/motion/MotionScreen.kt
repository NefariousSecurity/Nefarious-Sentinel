package com.nefarioussecurity.sentinel.controller.feature.motion

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.management.MotionUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed
import com.runtimebridge.api.ManagementProtocol
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SentinelGreen = Color(0xFF55D6A0)
private val SentinelAmber = Color(0xFFF9A825)
private val SentinelGrey = Color(0xFF8A8A8A)
private val SentinelCard = Color(0xFF101212)
private val SentinelInset = Color(0xFF171919)
private val SentinelBorder = Color(0xFF303232)
private val SentinelDeepRed = Color(0xFF1A0707)
private val Background = Color.Black

private const val LOW_SENSITIVITY = 0
private const val MEDIUM_SENSITIVITY = 50
private const val HIGH_SENSITIVITY = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionScreen(
    state: MotionUiState,
    message: String?,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetAction: (Int) -> Unit,
    onSetSensitivity: (Int) -> Unit,
) {
    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    val coroutineScope =
        rememberCoroutineScope()

    Scaffold(
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SNATCH PROTECTION",
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
                    .padding(horizontal = 18.dp)
                    .padding(
                        top = 4.dp,
                        bottom = 26.dp,
                    ),
        ) {
            MotionHeroCard(
                state = state,
                onToggle = onSetEnabled,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            ProtectionStatusCard(
                state = state,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            ActionCard(
                state = state,
                onActionSelected = onSetAction,
                onRebootUnavailable = {
                    coroutineScope.launch {
                        snackbarHostState
                            .currentSnackbarData
                            ?.dismiss()

                        snackbarHostState
                            .showSnackbar(
                                message =
                                    "Reboot requires Hardened mode. " +
                                        "Device Owner provisioning is required.",
                            )
                    }
                },
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            SensitivityCard(
                state = state,
                onSensitivityChanged = onSetSensitivity,
            )

            if (!message.isNullOrBlank()) {
                Spacer(
                    modifier =
                        Modifier.height(14.dp),
                )

                MessageCard(
                    message = message,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            OperationalStatusCard(
                state = state,
            )

            Spacer(
                modifier =
                    Modifier.height(22.dp),
            )
        }
    }
}

@Composable
private fun MotionHeroCard(
    state: MotionUiState,
    onToggle: (Boolean) -> Unit,
) {
    val statusColor =
        operationalColor(
            state,
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(16.dp),
        border = null,
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Black,
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
                                    SentinelDeepRed.copy(
                                        alpha = 0.92f,
                                    ),
                                    Color(0xFF0B0505),
                                    Color.Black,
                                ),
                        ),
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 18.dp,
                    ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(
                            width = 88.dp,
                            height = 96.dp,
                        ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Outlined.DirectionsRun,
                        contentDescription = null,
                        modifier =
                            Modifier.size(88.dp),
                        tint = SentinelRed,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.size(14.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            if (state.enabled) {
                                "Snatch Protection armed"
                            } else {
                                "Snatch Protection disabled"
                            },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(7.dp),
                    )

                    StatusBadge(
                        text =
                            operationalLabel(
                                state,
                            ),
                        color =
                            statusColor,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(9.dp),
                    )

                    Text(
                        text =
                            when (state.action) {
                                ManagementProtocol
                                    .MOTION_ACTION_REBOOT ->
                                    "Detect sudden device movement and " +
                                        "request an immediate reboot."

                                else ->
                                    "Detect sudden device movement and " +
                                        "immediately lock the screen."
                            },
                        color = SentinelGrey,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.size(10.dp),
                )

                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggle,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SentinelGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor =
                                SentinelRed.copy(
                                    alpha = 0.65f,
                                ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
) {
    Surface(
        shape =
            RoundedCornerShape(7.dp),
        color =
            color.copy(
                alpha = 0.08f,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha = 0.75f,
                ),
            ),
    ) {
        Text(
            text = text,
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 4.dp,
                ),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        )
    }
}

@Composable
private fun ProtectionStatusCard(
    state: MotionUiState,
) {
    val operational =
        state.operationalState ==
            ManagementProtocol.MOTION_OPERATIONAL_ACTIVE

    val statusColor =
        operationalColor(
            state,
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(
                    alpha = 0.48f,
                ),
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
                                    SentinelDeepRed.copy(
                                        alpha = 0.34f,
                                    ),
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
                        modifier =
                            Modifier.size(42.dp),
                        shape =
                            RoundedCornerShape(11.dp),
                        color =
                            statusColor.copy(
                                alpha = 0.13f,
                            ),
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Security,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(24.dp),
                                tint = statusColor,
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.size(12.dp),
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Protection Status",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(
                            modifier =
                                Modifier.height(3.dp),
                        )

                        Text(
                            text =
                                operationalLabel(
                                    state,
                                ),
                            color = statusColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Text(
                        text = "MOTION GUARD",
                        color = SentinelGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(13.dp),
                )

                HorizontalDivider(
                    color =
                        SentinelRed.copy(
                            alpha = 0.22f,
                        ),
                )

                MotionStatusRow(
                    title = "Movement detection",
                    description =
                        operationalDescription(
                            state,
                        ),
                    active = operational,
                    warning =
                        state.enabled &&
                            !operational,
                )

                HorizontalDivider(
                    color =
                        SentinelBorder.copy(
                            alpha = 0.7f,
                        ),
                )

                MotionStatusRow(
                    title = "Response action",
                    description =
                        when (state.action) {
                            ManagementProtocol
                                .MOTION_ACTION_REBOOT ->
                                "Reboot to Before First Unlock"

                            else ->
                                "Lock screen"
                        },
                    active = operational,
                )

                HorizontalDivider(
                    color =
                        SentinelBorder.copy(
                            alpha = 0.7f,
                        ),
                )

                MotionStatusRow(
                    title = "Sensitivity",
                    description =
                        "${state.sensitivity} / ${state.maxSensitivity}",
                    active = operational,
                )
            }
        }
    }
}

@Composable
private fun MotionStatusRow(
    title: String,
    description: String,
    active: Boolean,
    warning: Boolean = false,
) {
    val indicatorColor =
        when {
            warning ->
                SentinelAmber

            active ->
                SentinelGreen

            else ->
                SentinelGrey
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 11.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp),
            )

            Text(
                text = description,
                color = SentinelGrey,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        Spacer(
            modifier =
                Modifier.size(10.dp),
        )

        Surface(
            shape =
                RoundedCornerShape(16.dp),
            color =
                indicatorColor.copy(
                    alpha = 0.09f,
                ),
            border =
                BorderStroke(
                    1.dp,
                    indicatorColor.copy(
                        alpha = 0.34f,
                    ),
                ),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 5.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(
                                indicatorColor,
                                CircleShape,
                            ),
                )

                Spacer(
                    modifier =
                        Modifier.size(6.dp),
                )

                Text(
                    text =
                        when {
                            warning ->
                                "WAIT"

                            active ->
                                "ON"

                            else ->
                                "OFF"
                        },
                    color = indicatorColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    state: MotionUiState,
    onActionSelected: (Int) -> Unit,
    onRebootUnavailable: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(
                    alpha = 0.42f,
                ),
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
                    modifier =
                        Modifier.size(40.dp),
                    shape =
                        RoundedCornerShape(10.dp),
                    color =
                        SentinelRed.copy(
                            alpha = 0.12f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                if (
                                    state.action ==
                                    ManagementProtocol.MOTION_ACTION_REBOOT
                                ) {
                                    Icons.Default.RestartAlt
                                } else {
                                    Icons.Default.Lock
                                },
                            contentDescription = null,
                            modifier =
                                Modifier.size(23.dp),
                            tint = SentinelRed,
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(12.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = "Response Action",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp),
                    )

                    Text(
                        text = "TRIGGER RESPONSE",
                        color =
                            SentinelRed.copy(
                                alpha = 0.78f,
                            ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            Text(
                text =
                    "Choose what Snatch Protection should do when a " +
                        "snatch candidate is detected.",
                color = SentinelGrey,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected =
                        state.action ==
                            ManagementProtocol.MOTION_ACTION_LOCK,
                    onClick = {
                        onActionSelected(
                            ManagementProtocol.MOTION_ACTION_LOCK,
                        )
                    },
                    label = {
                        Text("Lock")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                Icons.Default.Lock,
                            contentDescription = null,
                            modifier =
                                Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.weight(1f),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor =
                                SentinelRed.copy(
                                    alpha = 0.18f,
                                ),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = SentinelRed,
                        ),
                )

                FilterChip(
                    selected =
                        state.action ==
                            ManagementProtocol.MOTION_ACTION_REBOOT,
                    onClick = {
                        if (state.rebootAvailable) {
                            onActionSelected(
                                ManagementProtocol.MOTION_ACTION_REBOOT,
                            )
                        } else {
                            onRebootUnavailable()
                        }
                    },
                    label = {
                        Text("Reboot")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier =
                                Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.weight(1f),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor =
                                SentinelRed.copy(
                                    alpha = 0.18f,
                                ),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = SentinelRed,
                        ),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(9.dp),
                color = SentinelInset,
                border =
                    BorderStroke(
                        1.dp,
                        SentinelBorder,
                    ),
            ) {
                Text(
                    text =
                        when {
                            state.action ==
                                ManagementProtocol.MOTION_ACTION_REBOOT &&
                                state.rebootAvailable ->
                                "Reboot returns the device to the Before " +
                                    "First Unlock state. No data wipe is performed."

                            state.action ==
                                ManagementProtocol.MOTION_ACTION_REBOOT &&
                                !state.rebootAvailable ->
                                "Reboot is selected but is not currently " +
                                    "available to the headless Core."

                            !state.rebootAvailable ->
                                "Lock immediately secures the screen. " +
                                    "Reboot response is unavailable on this device."

                            else ->
                                "Lock immediately secures the screen but " +
                                    "leaves the device in its current post-unlock state."
                        },
                    modifier =
                        Modifier.padding(11.dp),
                    color =
                        if (
                            state.action ==
                                ManagementProtocol.MOTION_ACTION_REBOOT &&
                            !state.rebootAvailable
                        ) {
                            SentinelAmber
                        } else {
                            SentinelGrey
                        },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun SensitivityCard(
    state: MotionUiState,
    onSensitivityChanged: (Int) -> Unit,
) {
    var sliderValue by
        remember(
            state.sensitivity,
        ) {
            mutableFloatStateOf(
                state.sensitivity.toFloat(),
            )
        }

    val min =
        state.minSensitivity

    val max =
        state.maxSensitivity
            .coerceAtLeast(min + 1)

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(
                    alpha = 0.42f,
                ),
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
                    modifier =
                        Modifier.size(40.dp),
                    shape =
                        RoundedCornerShape(10.dp),
                    color =
                        SentinelRed.copy(
                            alpha = 0.12f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Speed,
                            contentDescription = null,
                            modifier =
                                Modifier.size(23.dp),
                            tint = SentinelRed,
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(12.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = "Sensitivity",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp),
                    )

                    Text(
                        text = "MOTION THRESHOLD",
                        color =
                            SentinelRed.copy(
                                alpha = 0.78f,
                            ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }

                Text(
                    text =
                        state.sensitivity.toString(),
                    color = SentinelRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                PresetChip(
                    text = "Low",
                    selected =
                        state.sensitivity ==
                            LOW_SENSITIVITY,
                    onClick = {
                        onSensitivityChanged(
                            LOW_SENSITIVITY.coerceIn(
                                min,
                                max,
                            ),
                        )
                    },
                    modifier =
                        Modifier.weight(1f),
                )

                PresetChip(
                    text = "Medium",
                    selected =
                        state.sensitivity ==
                            MEDIUM_SENSITIVITY,
                    onClick = {
                        onSensitivityChanged(
                            MEDIUM_SENSITIVITY.coerceIn(
                                min,
                                max,
                            ),
                        )
                    },
                    modifier =
                        Modifier.weight(1f),
                )

                PresetChip(
                    text = "High",
                    selected =
                        state.sensitivity ==
                            HIGH_SENSITIVITY,
                    onClick = {
                        onSensitivityChanged(
                            HIGH_SENSITIVITY.coerceIn(
                                min,
                                max,
                            ),
                        )
                    },
                    modifier =
                        Modifier.weight(1f),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp),
            )

            Slider(
                value =
                    sliderValue.coerceIn(
                        min.toFloat(),
                        max.toFloat(),
                    ),
                onValueChange = {
                    sliderValue = it
                },
                onValueChangeFinished = {
                    onSensitivityChanged(
                        sliderValue
                            .roundToInt()
                            .coerceIn(
                                min,
                                max,
                            ),
                    )
                },
                valueRange =
                    min.toFloat()..
                        max.toFloat(),
                steps =
                    (max - min - 1)
                        .coerceAtLeast(0),
                colors =
                    SliderDefaults.colors(
                        thumbColor = SentinelRed,
                        activeTrackColor = SentinelRed,
                        inactiveTrackColor = SentinelBorder,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Less sensitive",
                    color = SentinelGrey,
                    fontSize = 12.sp,
                )

                Text(
                    text = "More sensitive",
                    color = SentinelGrey,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(text)
        },
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor =
                    SentinelRed.copy(
                        alpha = 0.18f,
                    ),
                selectedLabelColor = Color.White,
            ),
    )
}

@Composable
private fun OperationalStatusCard(
    state: MotionUiState,
) {
    val color =
        operationalColor(
            state,
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelInset,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha = 0.45f,
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier =
                Modifier.padding(15.dp),
            verticalAlignment =
                Alignment.Top,
        ) {
            Icon(
                imageVector =
                    Icons.Default.Info,
                contentDescription = null,
                tint = color,
                modifier =
                    Modifier.size(20.dp),
            )

            Spacer(
                modifier =
                    Modifier.size(10.dp),
            )

            Column {
                Text(
                    text =
                        "CORE SENSOR STATUS",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp),
                )

                Text(
                    text =
                        operationalDescription(
                            state,
                        ),
                    color = SentinelGrey,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF16130C),
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelAmber.copy(
                    alpha = 0.6f,
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Text(
            text = message,
            color = SentinelAmber,
            fontSize = 12.sp,
            modifier =
                Modifier.padding(14.dp),
        )
    }
}

private fun operationalColor(
    state: MotionUiState,
): Color =
    when (
        state.operationalState
    ) {
        ManagementProtocol
            .MOTION_OPERATIONAL_ACTIVE ->
            SentinelGreen

        ManagementProtocol
            .MOTION_OPERATIONAL_STARTING,
        ManagementProtocol
            .MOTION_OPERATIONAL_SENSOR_PERMISSION_REQUIRED,
        ManagementProtocol
            .MOTION_OPERATIONAL_ACCELEROMETER_UNAVAILABLE ->
            SentinelAmber

        ManagementProtocol
            .MOTION_OPERATIONAL_FAILED ->
            SentinelRed

        else ->
            SentinelRed
    }

private fun operationalLabel(
    state: MotionUiState,
): String =
    when (
        state.operationalState
    ) {
        ManagementProtocol
            .MOTION_OPERATIONAL_DISABLED ->
            "DISARMED"

        ManagementProtocol
            .MOTION_OPERATIONAL_STARTING ->
            "STARTING"

        ManagementProtocol
            .MOTION_OPERATIONAL_ACTIVE ->
            "ACTIVE"

        ManagementProtocol
            .MOTION_OPERATIONAL_SENSOR_PERMISSION_REQUIRED ->
            "PERMISSION"

        ManagementProtocol
            .MOTION_OPERATIONAL_ACCELEROMETER_UNAVAILABLE ->
            "NO SENSOR"

        ManagementProtocol
            .MOTION_OPERATIONAL_FAILED ->
            "FAILED"

        else ->
            "UNKNOWN"
    }

private fun operationalDescription(
    state: MotionUiState,
): String =
    when (
        state.operationalState
    ) {
        ManagementProtocol
            .MOTION_OPERATIONAL_DISABLED ->
            "Motion monitoring is disabled."

        ManagementProtocol
            .MOTION_OPERATIONAL_STARTING ->
            "The Core is starting motion monitoring."

        ManagementProtocol
            .MOTION_OPERATIONAL_ACTIVE ->
            "Accelerometer monitoring is active and ready to detect sudden movement."

        ManagementProtocol
            .MOTION_OPERATIONAL_SENSOR_PERMISSION_REQUIRED ->
            "Motion Protection requires Sensors permission before monitoring can start."

        ManagementProtocol
            .MOTION_OPERATIONAL_ACCELEROMETER_UNAVAILABLE ->
            "No usable accelerometer is available to the Core."

        ManagementProtocol
            .MOTION_OPERATIONAL_FAILED ->
            "The Core reported a motion-monitoring failure."

        else ->
            "Motion operational state is unavailable."
    }
