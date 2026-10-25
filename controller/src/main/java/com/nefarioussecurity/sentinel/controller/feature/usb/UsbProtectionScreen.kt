package com.nefarioussecurity.sentinel.controller.feature.usb

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.management.UsbUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed
import com.runtimebridge.api.ManagementProtocol

private val SentinelGreen =
    Color(0xFF55D6A0)

private val SentinelAmber =
    Color(0xFFF9A825)

private val SentinelGrey =
    Color(0xFF8A8A8A)

private val SentinelCard =
    Color(0xFF101212)

private val SentinelInset =
    Color(0xFF171919)

private val SentinelBorder =
    Color(0xFF303232)

private val SentinelDeepRed =
    Color(0xFF1A0707)

private val Background =
    Color(0xFF0B0C0C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbProtectionScreen(
    state: UsbUiState,
    message: String?,
    onMessageConsumed: () -> Unit,
    onBack: () -> Unit,
    onSetProtection: (Boolean) -> Unit,
    onSetStrict: (Boolean) -> Unit,
    onStartMaintenance: () -> Unit,
    onExtendMaintenance: () -> Unit,
    onCancelMaintenance: () -> Unit,
) {
    var confirmProtection by remember {
        mutableStateOf(false)
    }

    var confirmStrict by remember {
        mutableStateOf(false)
    }

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    LaunchedEffect(
        message,
    ) {
        if (!message.isNullOrBlank()) {
            snackbarHostState
                .currentSnackbarData
                ?.dismiss()

            snackbarHostState.showSnackbar(
                message = message,
            )

            onMessageConsumed()
        }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(
                hostState =
                    snackbarHostState,
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "USB PROTECTION",
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
            ProtectionSummaryCard(
                state = state,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            UsbTesterCard(
                state = state,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            ProtectionSettingCard(
                title =
                    "USB DATA-HOST PROTECTION",
                subtitle =
                    "PRIMARY DEFENCE",
                description =
                    "Detect data-capable USB hosts and invoke Sentinel protection.",
                icon =
                    Icons.Default.Security,
                enabled =
                    state.protectionEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        confirmProtection = true
                    } else {
                        onSetProtection(false)
                    }
                },
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            ProtectionSettingCard(
                title =
                    "STRICT USB",
                subtitle =
                    "EXPANDED WIRED PROTECTION",
                description =
                    if (state.protectionEnabled) {
                        "Treat a wider range of wired USB events as protection candidates."
                    } else {
                        "USB Data-Host Protection must be active before Strict USB can be armed."
                    },
                icon =
                    Icons.Default.Usb,
                enabled =
                    state.strictModeEnabled,
                controlEnabled =
                    state.protectionEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        confirmStrict = true
                    } else {
                        onSetStrict(false)
                    }
                },
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            MaintenanceCard(
                state = state,
                onStart = onStartMaintenance,
                onExtend = onExtendMaintenance,
                onCancel = onCancelMaintenance,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            SafetyCard()

            Spacer(
                modifier =
                    Modifier.height(28.dp),
            )
        }
    }

    if (confirmProtection) {
        AlertDialog(
            onDismissRequest = {
                confirmProtection = false
            },
            icon = {
                Icon(
                    imageVector =
                        Icons.Default.Security,
                    contentDescription = null,
                    tint = SentinelRed,
                )
            },
            title = {
                Text(
                    "Enable USB Protection?",
                )
            },
            text = {
                Text(
                    "Disconnect all USB cables before enabling protection. " +
                        "A data-capable USB connection can trigger Sentinel's " +
                        "configured destructive protection.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmProtection = false

                        onSetProtection(true)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SentinelRed,
                        ),
                ) {
                    Text(
                        "ENABLE",
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmProtection = false
                    },
                ) {
                    Text(
                        "Cancel",
                    )
                }
            },
        )
    }

    if (confirmStrict) {
        AlertDialog(
            onDismissRequest = {
                confirmStrict = false
            },
            icon = {
                Icon(
                    imageVector =
                        Icons.Default.Security,
                    contentDescription = null,
                    tint = SentinelRed,
                )
            },
            title = {
                Text(
                    "Enable Strict USB?",
                )
            },
            text = {
                Text(
                    "Strict USB expands protection beyond confirmed " +
                        "data-capable hosts. Disconnect wired USB before enabling it.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStrict = false

                        onSetStrict(true)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SentinelRed,
                        ),
                ) {
                    Text(
                        "ENABLE STRICT USB",
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmStrict = false
                    },
                ) {
                    Text(
                        "Cancel",
                    )
                }
            },
        )
    }
}

@Composable
private fun ProtectionSummaryCard(
    state: UsbUiState,
) {
    val active =
        state.protectionEnabled

    val statusColor =
        if (active) {
            SentinelGreen
        } else {
            SentinelRed
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                statusColor.copy(
                    alpha =
                        if (active) {
                            0.72f
                        } else {
                            0.62f
                        },
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
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
                                        SentinelDeepRed.copy(
                                            alpha = 0.42f,
                                        ),
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
                        .padding(
                            horizontal = 16.dp,
                            vertical = 16.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier.size(46.dp),
                    shape =
                        RoundedCornerShape(11.dp),
                    color =
                        statusColor.copy(
                            alpha = 0.12f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Usb,
                            contentDescription = null,
                            tint = statusColor,
                            modifier =
                                Modifier.size(27.dp),
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(13.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            "USB Protection",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            18.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp),
                    )

                    Text(
                        text =
                            if (active) {
                                "PROTECTION ACTIVE"
                            } else {
                                "PROTECTION INACTIVE"
                            },
                        color =
                            if (active) {
                                SentinelRed.copy(
                                    alpha = 0.78f,
                                )
                            } else {
                                SentinelGrey
                            },
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.4.sp,
                    )
                }

                StatusPill(
                    text =
                        if (active) {
                            "ARMED"
                        } else {
                            "OFF"
                        },
                    color =
                        statusColor,
                )
            }

            if (state.strictModeEnabled) {
                Text(
                    text =
                        "STRICT",
                    color =
                        SentinelGreen,
                    fontSize =
                        8.sp,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomEnd,
                            )
                            .padding(
                                end = 16.dp,
                                bottom = 7.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun UsbTesterCard(
    state: UsbUiState,
) {
    val connection =
        connectionDescription(
            state.connectionState,
        )

    val color =
        when (
            state.connectionState
        ) {
            ManagementProtocol
                .USB_CONNECTION_DISCONNECTED ->
                SentinelGrey

            ManagementProtocol
                .USB_CONNECTION_PENDING ->
                SentinelAmber

            ManagementProtocol
                .USB_CONNECTION_CHARGE_ONLY ->
                SentinelGreen

            ManagementProtocol
                .USB_CONNECTION_DATA_CAPABLE ->
                SentinelRed

            else ->
                SentinelGrey
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha = 0.62f,
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier.size(44.dp),
                shape =
                    RoundedCornerShape(11.dp),
                color =
                    color.copy(
                        alpha = 0.11f,
                    ),
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = color,
                        modifier =
                            Modifier.size(25.dp),
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.size(13.dp),
            )

            Column(
                modifier =
                    Modifier.weight(1f),
            ) {
                Text(
                    text =
                        "USB TESTER",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        14.sp,
                    letterSpacing =
                        1.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp),
                )

                Text(
                    text =
                        connection.first,
                    color =
                        color,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        13.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(3.dp),
                )

                Text(
                    text =
                        connection.second,
                    color =
                        SentinelGrey,
                    fontSize =
                        12.sp,
                    lineHeight =
                        16.sp,
                )
            }

            StatusDot(
                color = color,
            )
        }
    }
}

@Composable
private fun ProtectionSettingCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    controlEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val statusColor =
        if (enabled) {
            SentinelGreen
        } else {
            SentinelRed
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                statusColor.copy(
                    alpha =
                        if (enabled) {
                            0.72f
                        } else {
                            0.62f
                        },
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (enabled) {
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        SentinelDeepRed.copy(
                                            alpha = 0.42f,
                                        ),
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
                        .padding(
                            horizontal = 15.dp,
                            vertical = 14.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier.size(44.dp),
                    shape =
                        RoundedCornerShape(11.dp),
                    color =
                        statusColor.copy(
                            alpha =
                                if (enabled) {
                                    0.13f
                                } else {
                                    0.11f
                                },
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier =
                                Modifier.size(25.dp),
                            tint =
                                statusColor,
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(13.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            14.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp),
                    )

                    Text(
                        text = subtitle,
                        color =
                            if (enabled) {
                                SentinelRed.copy(
                                    alpha = 0.78f,
                                )
                            } else {
                                SentinelGrey.copy(
                                    alpha = 0.72f,
                                )
                            },
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.3.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp),
                    )

                    Text(
                        text = description,
                        color =
                            SentinelGrey,
                        fontSize =
                            12.sp,
                        lineHeight =
                            16.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.size(10.dp),
                )

                Switch(
                    checked =
                        enabled,
                    onCheckedChange =
                        onCheckedChange,
                    enabled =
                        controlEnabled,
                    colors =
                        sentinelSwitchColors(),
                )
            }
        }
    }
}

@Composable
private fun MaintenanceCard(
    state: UsbUiState,
    onStart: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
) {
    val active =
        state.maintenanceActive

    val color =
        if (active) {
            SentinelAmber
        } else {
            SentinelRed
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha = 0.62f,
                ),
            ),
        shape =
            RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier.size(44.dp),
                    shape =
                        RoundedCornerShape(11.dp),
                    color =
                        color.copy(
                            alpha = 0.11f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Build,
                            contentDescription = null,
                            tint = color,
                            modifier =
                                Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(13.dp),
                )

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            "BACKUP / MAINTENANCE",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            14.sp,
                    )

                    Text(
                        text =
                            if (active) {
                                "TEMPORARY BYPASS ACTIVE"
                            } else {
                                "TRUSTED MAINTENANCE WINDOW"
                            },
                        color =
                            if (active) {
                                SentinelAmber
                            } else {
                                SentinelGrey
                            },
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.2.sp,
                    )
                }

                StatusPill(
                    text =
                        if (active) {
                            "ACTIVE"
                        } else {
                            "OFF"
                        },
                    color =
                        color,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(13.dp),
            )

            HorizontalDivider(
                color =
                    SentinelBorder,
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp),
            )

            Text(
                text =
                    "Maintenance temporarily suspends USB Protection and " +
                        "USB / Wireless Debugging tamper reactions. " +
                        "Disable USB and Wireless Debugging before this window " +
                        "ends. If either remains enabled while its Tamper " +
                        "Proofing switch is ON, Sentinel will trigger an " +
                        "emergency wipe when protection resumes.",
                color =
                    if (active) {
                        SentinelAmber
                    } else {
                        SentinelGrey
                    },
                fontSize =
                    12.sp,
                lineHeight =
                    17.sp,
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            if (active) {
                Text(
                    text =
                        formatDuration(
                            state.maintenanceRemainingMs,
                        ),
                    color =
                        SentinelAmber,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        26.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp),
                )

                Text(
                    text =
                        "Extensions used: " +
                            "${state.maintenanceExtendsUsed} / " +
                            "${state.maintenanceMaxExtends}",
                    color =
                        SentinelGrey,
                    fontSize =
                        12.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(14.dp),
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick =
                            onExtend,
                        enabled =
                            state.maintenanceExtendsUsed <
                                state.maintenanceMaxExtends,
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            "+30 MIN",
                        )
                    }

                    Button(
                        onClick =
                            onCancel,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = SentinelRed,
                            ),
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            "CANCEL",
                        )
                    }
                }
            } else {
                Text(
                    text =
                        "Temporarily suppress USB-triggered protection while " +
                            "performing a trusted backup or maintenance task.",
                    color =
                        SentinelGrey,
                    fontSize =
                        12.sp,
                    lineHeight =
                        17.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(14.dp),
                )

                Button(
                    onClick =
                        onStart,
                    enabled =
                        state.protectionEnabled,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SentinelRed,
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "START 30-MINUTE MAINTENANCE",
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyCard() {
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
                SentinelBorder,
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
                tint =
                    SentinelRed,
                modifier =
                    Modifier.size(20.dp),
            )

            Spacer(
                modifier =
                    Modifier.size(10.dp),
            )

            Text(
                text =
                    "The headless Core is authoritative. Sentinel reads the " +
                        "current protection state back from Core after every operation.",
                color =
                    SentinelGrey,
                fontSize =
                    12.sp,
                lineHeight =
                    17.sp,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Surface(
        shape =
            RoundedCornerShape(20.dp),
        color =
            color.copy(
                alpha = 0.08f,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha = 0.32f,
                ),
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 5.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .background(
                            color,
                            CircleShape,
                        ),
            )

            Spacer(
                modifier =
                    Modifier.size(5.dp),
            )

            Text(
                text =
                    text,
                color =
                    color,
                fontSize =
                    8.sp,
                fontWeight =
                    FontWeight.Bold,
                letterSpacing =
                    0.7.sp,
            )
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .background(
                    color,
                    CircleShape,
                ),
    )
}

@Composable
private fun sentinelSwitchColors() =
    SwitchDefaults.colors(
        checkedThumbColor =
            Color.White,
        checkedTrackColor =
            SentinelGreen,
        uncheckedThumbColor =
            Color.White,
        uncheckedTrackColor =
            SentinelRed.copy(
                alpha = 0.65f,
            ),
        disabledUncheckedThumbColor =
            SentinelGrey,
        disabledUncheckedTrackColor =
            SentinelBorder,
    )

private fun connectionDescription(
    state: Int,
): Pair<String, String> =
    when (state) {
        ManagementProtocol
            .USB_CONNECTION_DISCONNECTED ->
            "DISCONNECTED" to
                "No wired USB connection detected."

        ManagementProtocol
            .USB_CONNECTION_PENDING ->
            "CLASSIFYING" to
                "Sentinel is determining the USB connection type."

        ManagementProtocol
            .USB_CONNECTION_CHARGE_ONLY ->
            "CHARGE ONLY" to
                "No data-capable USB host is currently detected."

        ManagementProtocol
            .USB_CONNECTION_DATA_CAPABLE ->
            "DATA CAPABLE" to
                "A data-capable USB host has been detected."

        else ->
            "UNKNOWN" to
                "USB connection state is unavailable."
    }

private fun formatDuration(
    milliseconds: Long,
): String {
    val totalSeconds =
        milliseconds
            .coerceAtLeast(0L) /
            1_000L

    val hours =
        totalSeconds /
            3_600L

    val minutes =
        (
            totalSeconds %
                3_600L
            ) /
            60L

    val seconds =
        totalSeconds %
            60L

    return if (hours > 0L) {
        "%02d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    } else {
        "%02d:%02d".format(
            minutes,
            seconds,
        )
    }
}
