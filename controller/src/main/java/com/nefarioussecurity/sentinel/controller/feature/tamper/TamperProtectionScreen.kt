package com.nefarioussecurity.sentinel.controller.feature.tamper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.management.TamperUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed
import com.runtimebridge.api.ManagementProtocol

private val SentinelGreen =
    Color(0xFF55D6A0)

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
fun TamperProtectionScreen(
    state: TamperUiState,
    authorityMode: Int,
    maintenanceActive: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSetUsbDebuggingProtection: (Boolean) -> Unit,
    onSetWirelessDebuggingProtection: (Boolean) -> Unit,
) {
    Scaffold(
        containerColor =
            Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            "TAMPER PROOFING",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.4.sp,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick =
                            onBack,
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                "Back",
                            tint =
                                SentinelRed,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            Color.Black,
                    ),
            )
        },
    ) {
        padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding,
                    )
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal =
                            18.dp,
                        vertical =
                            18.dp,
                    ),
        ) {
            TamperSummaryCard(
                state =
                    state,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            ProtectionSettingCard(
                title =
                    "USB DEBUGGING PROTECTION",
                subtitle =
                    "ADB TAMPER DEFENCE",
                description =
                    "Treat enabling USB debugging after protection is armed as a tamper event.",
                icon =
                    Icons.Default.Usb,
                enabled =
                    state.usbDebuggingProtectionEnabled,
                onCheckedChange =
                    onSetUsbDebuggingProtection,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            ProtectionSettingCard(
                title =
                    "WIRELESS DEBUGGING PROTECTION",
                subtitle =
                    "WIRELESS ADB TAMPER DEFENCE",
                description =
                    "Treat enabling Wireless Debugging after protection is armed as a tamper event.",
                icon =
                    Icons.Default.Security,
                enabled =
                    state.wirelessDebuggingProtectionEnabled,
                onCheckedChange =
                    onSetWirelessDebuggingProtection,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            MessageCard(
                message =
                    tamperProtectionStatusMessage(
                        state =
                            state,
                        authorityMode =
                            authorityMode,
                        maintenanceActive =
                            maintenanceActive,
                    ),
            )

            if (!message.isNullOrBlank()) {
                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp,
                    ),
                )

                MessageCard(
                    message =
                        message,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            SafetyCard()

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp,
                    ),
            )
        }
    }
}

private fun tamperProtectionStatusMessage(
    state: TamperUiState,
    authorityMode: Int,
    maintenanceActive: Boolean,
): String {
    val usbEnabled =
        state.usbDebuggingProtectionEnabled

    val wirelessEnabled =
        state.wirelessDebuggingProtectionEnabled

    val anyEnabled =
        usbEnabled ||
            wirelessEnabled

    val bothEnabled =
        usbEnabled &&
            wirelessEnabled

    if (
        authorityMode ==
        ManagementProtocol.AUTHORITY_NONE
    ) {
        return "Device administration is not active. " +
            "Debugging protection enforcement is unavailable."
    }

    if (!anyEnabled) {
        return when (
            authorityMode
        ) {
            ManagementProtocol.AUTHORITY_DEVICE_OWNER ->
                "Hardened mode: debugging protection is currently disabled."

            ManagementProtocol.AUTHORITY_DEVICE_ADMIN ->
                "Standard mode: debugging protection is currently disabled."

            else ->
                "Debugging protection is currently disabled."
        }
    }

    if (maintenanceActive) {
        return when (
            authorityMode
        ) {
            ManagementProtocol.AUTHORITY_DEVICE_OWNER ->
                if (bothEnabled) {
                    "USB Maintenance Mode active. Debugging tamper reactions are temporarily suspended, " +
                        "and Hardened mode's preventive debugging restriction is temporarily lifted. " +
                        "Protection resumes automatically when Maintenance Mode ends."
                } else {
                    "USB Maintenance Mode active. Debugging tamper reactions are temporarily suspended. " +
                        "Protection resumes automatically when Maintenance Mode ends."
                }

            ManagementProtocol.AUTHORITY_DEVICE_ADMIN ->
                "USB Maintenance Mode active. Debugging tamper reactions are temporarily suspended. " +
                    "Protection resumes automatically when Maintenance Mode ends."

            else ->
                "USB Maintenance Mode active. Debugging tamper reactions are temporarily suspended."
        }
    }

    return when (
        authorityMode
    ) {
        ManagementProtocol.AUTHORITY_DEVICE_OWNER ->
            when {
                bothEnabled ->
                    "Hardened mode: preventive debugging protection active. " +
                        "Android's debugging restriction is enforced while both protections remain enabled."

                usbEnabled ->
                    "Hardened mode: reactive USB debugging protection active. " +
                        "Preventive debugging protection requires both USB and Wireless Debugging Protection to be enabled."

                else ->
                    "Hardened mode: reactive Wireless Debugging protection active. " +
                        "Preventive debugging protection requires both USB and Wireless Debugging Protection to be enabled."
            }

        ManagementProtocol.AUTHORITY_DEVICE_ADMIN ->
            when {
                bothEnabled ->
                    "Standard mode: reactive USB and Wireless Debugging protection active. " +
                        "Sentinel detects and reacts if protected debugging is enabled."

                usbEnabled ->
                    "Standard mode: reactive USB debugging protection active. " +
                        "Sentinel detects and reacts if USB debugging is enabled."

                else ->
                    "Standard mode: reactive Wireless Debugging protection active. " +
                        "Sentinel detects and reacts if Wireless Debugging is enabled."
            }

        else ->
            "Debugging protection state is unavailable."
    }
}

@Composable
private fun TamperSummaryCard(
    state: TamperUiState,
) {
    val active =
        state.usbDebuggingProtectionEnabled ||
            state.wirelessDebuggingProtectionEnabled

    val color =
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
                containerColor =
                    SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha =
                        if (active) {
                            0.72f
                        } else {
                            0.62f
                        },
                ),
            ),
        shape =
            RoundedCornerShape(
                14.dp,
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
                                        SentinelDeepRed.copy(
                                            alpha =
                                                0.42f,
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
                            horizontal =
                                16.dp,
                            vertical =
                                16.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier.size(
                            46.dp,
                        ),
                    shape =
                        RoundedCornerShape(
                            11.dp,
                        ),
                    color =
                        color.copy(
                            alpha =
                                0.12f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Security,
                            contentDescription =
                                null,
                            tint =
                                color,
                            modifier =
                                Modifier.size(
                                    27.dp,
                                ),
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(
                            13.dp,
                        ),
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                ) {
                    Text(
                        text =
                            "Tamper Proofing",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            18.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp,
                            ),
                    )

                    Text(
                        text =
                            if (active) {
                                "DEBUGGING PROTECTION ACTIVE"
                            } else {
                                "DEBUGGING PROTECTION INACTIVE"
                            },
                        color =
                            if (active) {
                                SentinelRed.copy(
                                    alpha =
                                        0.78f,
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
                            "ACTIVE"
                        } else {
                            "OFF"
                        },
                    color =
                        color,
                )
            }
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
    onCheckedChange: (Boolean) -> Unit,
) {
    val color =
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
                containerColor =
                    SentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha =
                        if (enabled) {
                            0.72f
                        } else {
                            0.62f
                        },
                ),
            ),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
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
                                            alpha =
                                                0.42f,
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
                            horizontal =
                                15.dp,
                            vertical =
                                14.dp,
                        ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier =
                        Modifier.size(
                            44.dp,
                        ),
                    shape =
                        RoundedCornerShape(
                            11.dp,
                        ),
                    color =
                        color.copy(
                            alpha =
                                0.12f,
                        ),
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                icon,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    25.dp,
                                ),
                            tint =
                                color,
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(
                            13.dp,
                        ),
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                ) {
                    Text(
                        text =
                            title,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            14.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                2.dp,
                            ),
                    )

                    Text(
                        text =
                            subtitle,
                        color =
                            if (enabled) {
                                SentinelRed.copy(
                                    alpha =
                                        0.78f,
                                )
                            } else {
                                SentinelGrey.copy(
                                    alpha =
                                        0.72f,
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
                            Modifier.height(
                                5.dp,
                            ),
                    )

                    Text(
                        text =
                            description,
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
                        Modifier.size(
                            10.dp,
                    ),
                )

                Switch(
                    checked =
                        enabled,
                    onCheckedChange =
                        onCheckedChange,
                    colors =
                        sentinelSwitchColors(),
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
                    Color(
                        0xFF16130C,
                    ),
            ),
        border =
            BorderStroke(
                1.dp,
                Color(0xFFF9A825).copy(
                    alpha =
                        0.6f,
                ),
            ),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
    ) {
        Text(
            text =
                message,
            color =
                Color(0xFFF9A825),
            fontSize =
                12.sp,
            modifier =
                Modifier.padding(
                    14.dp,
                ),
        )
    }
}

@Composable
private fun SafetyCard() {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    SentinelInset,
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelBorder,
            ),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    15.dp,
                ),
            verticalAlignment =
                Alignment.Top,
        ) {
            Icon(
                imageVector =
                    Icons.Default.Info,
                contentDescription =
                    null,
                tint =
                    SentinelRed,
                modifier =
                    Modifier.size(
                        20.dp,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.size(
                        10.dp,
                    ),
            )

            Text(
                text =
                    "Both protections default to OFF. Sentinel refuses to arm a " +
                        "protection while its corresponding debugging interface is already enabled.",
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
            RoundedCornerShape(
                20.dp,
            ),
        color =
            color.copy(
                alpha =
                    0.08f,
            ),
        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha =
                        0.32f,
                ),
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal =
                        8.dp,
                    vertical =
                        5.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(
                            7.dp,
                        )
                        .background(
                            color,
                            CircleShape,
                        ),
            )

            Spacer(
                modifier =
                    Modifier.size(
                        5.dp,
                    ),
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
                alpha =
                    0.65f,
            ),
    )
