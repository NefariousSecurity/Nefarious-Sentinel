package com.nefarioussecurity.sentinel.controller.feature.sms

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.BuildConfig
import com.nefarioussecurity.sentinel.controller.management.SmsUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed

private val SentinelGreen = Color(0xFF55D6A0)
private val SentinelAmber = Color(0xFFF9A825)
private val SentinelGrey = Color(0xFF8A8A8A)
private val SentinelCard = Color(0xFF101212)
private val SentinelInset = Color(0xFF171919)
private val SentinelBorder = Color(0xFF303232)
private val SentinelDeepRed = Color(0xFF1A0707)
private val SentinelRedLight = Color(0xFFFF6B6B)
private val Background = Color(0xFF0B0C0C)

/**
 * Polished Controller UI for Core-owned SMS Wipe.
 *
 * Matching, permission state, configuration and destructive enforcement stay
 * inside the headless Sentinel Core. The trigger keyword is intentionally
 * write-only over IPC and is never read back into this Controller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsWipeScreen(
    state: SmsUiState,
    adminActive: Boolean,
    deviceOwner: Boolean,
    message: String?,
    onBack: () -> Unit,
    onGrantPermission: () -> Unit,
    onSetSender: (String) -> Unit,
    onSetKeyword: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSimulate: (String, String) -> Unit,
) {
    var senderInput by
        remember(
            state.sender,
        ) {
            mutableStateOf(
                state.sender,
            )
        }

    var keywordInput by remember {
        mutableStateOf("")
    }

    var simSender by remember {
        mutableStateOf("")
    }

    var simBody by remember {
        mutableStateOf("")
    }

    var simulationRequested by remember {
        mutableStateOf(false)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            SmsTopBar(
                onBack = onBack,
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
                    )
                    .padding(
                        top = 4.dp,
                        bottom = 26.dp,
                    ),
        ) {
            SmsHeroCard(
                enabled =
                    state.enabled,
                armed =
                    state.armed,
                onToggle = {
                    requested ->

                    simulationRequested =
                        false

                    onSetEnabled(
                        requested,
                    )
                },
            )

            Spacer(
                Modifier.height(
                    14.dp,
                ),
            )

            ProtectionStatusCard(
                enabled =
                    state.enabled,
                armed =
                    state.armed,
                adminActive =
                    adminActive,
                deviceOwner =
                    deviceOwner,
                hasSmsPermission =
                    state.permissionGranted,
                senderConfigured =
                    state.senderConfigured,
                keywordConfigured =
                    state.keywordConfigured,
            )

            if (
                !message.isNullOrBlank() &&
                !simulationRequested
            ) {
                Spacer(
                    Modifier.height(
                        14.dp,
                    ),
                )

                OperationMessageCard(
                    message =
                        message,
                )
            }

            if (!state.permissionGranted) {
                Spacer(
                    Modifier.height(
                        14.dp,
                    ),
                )

                PermissionCard(
                    deviceOwner =
                        deviceOwner,
                    onGrant = {
                        simulationRequested =
                            false

                        onGrantPermission()
                    },
                )
            }

            Spacer(
                Modifier.height(
                    14.dp,
                ),
            )

            SenderCard(
                senderInput =
                    senderInput,
                onSenderChange = {
                    value ->

                    senderInput =
                        value.take(
                            state.senderMaxLength,
                        )
                },
                onSave = {
                    simulationRequested =
                        false

                    onSetSender(
                        senderInput,
                    )
                },
            )

            Spacer(
                Modifier.height(
                    14.dp,
                ),
            )

            KeywordCard(
                keywordInput =
                    keywordInput,
                configured =
                    state.keywordConfigured,
                minLength =
                    state.keywordMinLength,
                maxLength =
                    state.keywordMaxLength,
                onKeywordChange = {
                    value ->

                    keywordInput =
                        value.take(
                            state.keywordMaxLength,
                        )
                },
                onSave = {
                    simulationRequested =
                        false

                    val submitted =
                        keywordInput

                    keywordInput =
                        ""

                    onSetKeyword(
                        submitted,
                    )
                },
            )

            Spacer(
                Modifier.height(
                    14.dp,
                ),
            )

            RulesCard()

            if (BuildConfig.DEBUG) {
                Spacer(
                    Modifier.height(
                        14.dp,
                    ),
                )

                SimulationCard(
                    simSender =
                        simSender,
                    simBody =
                        simBody,
                    resultText =
                        if (simulationRequested) {
                            message
                        } else {
                            null
                        },
                    onSenderChange = {
                        simSender =
                            it.take(
                                state.senderMaxLength,
                            )
                    },
                    onBodyChange = {
                        simBody =
                            it
                    },
                    onSimulate = {
                        simulationRequested =
                            true

                        onSimulate(
                            simSender,
                            simBody,
                        )
                    },
                )
            }

            Spacer(
                Modifier.height(
                    22.dp,
                ),
            )

            SentinelFooter()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsTopBar(
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text =
                    "SMS WIPE",
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
}

@Composable
private fun SmsHeroCard(
    enabled: Boolean,
    armed: Boolean,
    onToggle: (Boolean) -> Unit,
) {

    val statusColor =
        when {
            armed ->
                SentinelGreen

            enabled ->
                SentinelAmber

            else ->
                SentinelRed
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(
                            width = 88.dp,
                            height = 96.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = SentinelRed,
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            when {
                                armed ->
                                    "SMS Wipe armed"

                                enabled ->
                                    "SMS Wipe not ready"

                                else ->
                                    "SMS Wipe disabled"
                            },
                        color =
                            MaterialTheme.colorScheme.onSurface,
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(7.dp))

                    StatusBadge(
                        text =
                            when {
                                armed ->
                                    "ARMED"

                                enabled ->
                                    "NOT READY"

                                else ->
                                    "DISARMED"
                            },
                        color = statusColor,
                    )

                    Spacer(Modifier.height(9.dp))

                    Text(
                        text =
                            "Trigger the configured destructive action " +
                                "when an authorised SMS matches the saved " +
                                "sender and keyword.",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(Modifier.width(10.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SentinelRed,
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
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.08f),
        border =
            BorderStroke(
                1.dp,
                color.copy(alpha = 0.75f),
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
    enabled: Boolean,
    armed: Boolean,
    adminActive: Boolean,
    deviceOwner: Boolean,
    hasSmsPermission: Boolean,
    senderConfigured: Boolean,
    keywordConfigured: Boolean,
) {
    val prerequisitesReady =
        adminActive &&
            hasSmsPermission &&
            senderConfigured &&
            keywordConfigured

    /*
     * Core's armed state is authoritative when SMS Wipe is enabled. When the
     * feature is disabled, the individual prerequisite snapshot can still
     * truthfully show that the configuration is READY to arm.
     */
    val ready =
        if (enabled) {
            armed
        } else {
            prerequisitesReady
        }

    val statusColor =
        when {
            armed ->
                SentinelGreen

            ready ->
                SentinelGreen

            enabled ->
                SentinelAmber

            else ->
                SentinelRed
        }

    val statusText =
        when {
            armed ->
                "ARMED"

            ready ->
                "READY"

            else ->
                "NOT READY"
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
                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(
                                    RoundedCornerShape(
                                        11.dp,
                                    ),
                                )
                                .background(
                                    statusColor.copy(
                                        alpha = 0.13f,
                                    ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Security,
                            contentDescription = null,
                            modifier =
                                Modifier.size(24.dp),
                            tint = statusColor,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Protection Status",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurface,
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Text(
                        text = "REMOTE GUARD",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                                .copy(alpha = 0.7f),
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }

                Spacer(Modifier.height(13.dp))

                HorizontalDivider(
                    color =
                        SentinelRed.copy(alpha = 0.22f),
                )

                SmsStatusRow(
                    title = "Wipe authority",
                    description =
                        when {
                            deviceOwner ->
                                "Hardened Device Owner authority active"

                            adminActive ->
                                "Standard Device Admin authority active"

                            else ->
                                "Device administration must be activated"
                        },
                    active = adminActive,
                    criticalWhenInactive = true,
                )

                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme
                            .outlineVariant
                            .copy(alpha = 0.34f),
                )

                SmsStatusRow(
                    title = "SMS permission",
                    description =
                        if (hasSmsPermission) {
                            "Incoming SMS receiver permission granted"
                        } else {
                            "Incoming SMS receiver permission required"
                        },
                    active = hasSmsPermission,
                    criticalWhenInactive = true,
                )

                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme
                            .outlineVariant
                            .copy(alpha = 0.34f),
                )

                SmsStatusRow(
                    title = "Authorised sender",
                    description =
                        if (senderConfigured) {
                            "Sender number configured"
                        } else {
                            "No sender number configured"
                        },
                    active = senderConfigured,
                )

                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme
                            .outlineVariant
                            .copy(alpha = 0.34f),
                )

                SmsStatusRow(
                    title = "Trigger keyword",
                    description =
                        if (keywordConfigured) {
                            "Keyword configured"
                        } else {
                            "No valid keyword configured"
                        },
                    active = keywordConfigured,
                )
            }
        }
    }
}

@Composable
private fun SmsStatusRow(
    title: String,
    description: String,
    active: Boolean,
    criticalWhenInactive: Boolean = false,
) {
    val indicatorColor =
        when {
            active ->
                SentinelGreen

            criticalWhenInactive ->
                SentinelRed

            else ->
                SentinelGrey
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color =
                    MaterialTheme.colorScheme.onSurface,
                style =
                    MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = description,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color =
                indicatorColor.copy(alpha = 0.09f),
            border =
                BorderStroke(
                    1.dp,
                    indicatorColor.copy(alpha = 0.34f),
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
                            .clip(CircleShape)
                            .background(indicatorColor),
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text =
                        if (active) {
                            "OK"
                        } else {
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
private fun PermissionCard(
    deviceOwner: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = SentinelDeepRed,
        border =
            BorderStroke(
                1.dp,
                SentinelRed.copy(alpha = 0.45f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = SentinelRedLight,
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "SMS permission required",
                        color = SentinelRedLight,
                        style =
                            MaterialTheme.typography
                                .titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text =
                            if (deviceOwner) {
                                "The headless Core requires RECEIVE_SMS before Sentinel can " +
                                    "receive and evaluate trigger messages. Hardened mode " +
                                    "can grant the permission through Device Owner policy."
                            } else {
                                "The headless Core requires RECEIVE_SMS before Sentinel can " +
                                    "receive and evaluate trigger messages. Android will ask " +
                                    "you to grant the permission directly to the Core."
                            },
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SentinelRed,
                    ),
            ) {
                Text(
                    text = "Grant SMS Permission",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SenderCard(
    senderInput: String,
    onSenderChange: (String) -> Unit,
    onSave: () -> Unit,
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
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(
                                RoundedCornerShape(10.dp),
                            )
                            .background(
                                SentinelRed.copy(
                                    alpha = 0.12f,
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                        tint = SentinelRed,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Authorised Sender",
                        color =
                            MaterialTheme.colorScheme
                                .onSurface,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "TRUSTED NUMBER",
                        color =
                            SentinelRed.copy(alpha = 0.78f),
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.7.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    "Only messages received from the configured " +
                        "sender are eligible for the SMS Wipe trigger.",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = senderInput,
                onValueChange = onSenderChange,
                label = {
                    Text(
                        text = "Sender number",
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                    ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = senderInput.isNotBlank(),
                shape = RoundedCornerShape(9.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SentinelRed,
                    ),
            ) {
                Text(
                    text = "Save Sender",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OperationMessageCard(
    message: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = SentinelInset,
        border =
            BorderStroke(
                1.dp,
                SentinelBorder,
            ),
    ) {
        Text(
            text = message,
            modifier =
                Modifier.padding(
                    12.dp,
                ),
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            style =
                MaterialTheme.typography.bodySmall,
            lineHeight = 17.sp,
        )
    }
}


@Composable
private fun KeywordCard(
    keywordInput: String,
    configured: Boolean,
    minLength: Int,
    maxLength: Int,
    onKeywordChange: (String) -> Unit,
    onSave: () -> Unit,
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
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(
                                RoundedCornerShape(10.dp),
                            )
                            .background(
                                SentinelRed.copy(
                                    alpha = 0.12f,
                                ),
                            ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Lock,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                23.dp,
                            ),
                        tint =
                            SentinelRed,
                    )
                }

                Spacer(
                    Modifier.width(
                        12.dp,
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
                            "Trigger Keyword",
                        color =
                            MaterialTheme.colorScheme
                                .onSurface,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Spacer(
                        Modifier.height(
                            2.dp,
                        ),
                    )

                    Text(
                        text =
                            if (configured) {
                                "CONFIGURED • WRITE ONLY"
                            } else {
                                "MATCH PHRASE"
                            },
                        color =
                            if (configured) {
                                SentinelGreen
                            } else {
                                SentinelRed.copy(
                                    alpha = 0.78f,
                                )
                            },
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            9.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.7.sp,
                    )
                }
            }

            Spacer(
                Modifier.height(
                    12.dp,
                ),
            )

            Text(
                text =
                    if (configured) {
                        "A valid trigger keyword is configured in Core. " +
                            "For security it is not readable through the " +
                            "Controller. Enter a new value below only when " +
                            "you want to replace it."
                    } else {
                        "A message must satisfy the configured keyword " +
                            "rules before the SMS Wipe trigger can match."
                    },
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall,
                lineHeight =
                    17.sp,
            )

            Spacer(
                Modifier.height(
                    12.dp,
                ),
            )

            OutlinedTextField(
                value =
                    keywordInput,
                onValueChange =
                    onKeywordChange,
                label = {
                    Text(
                        text =
                            if (configured) {
                                "New keyword"
                            } else {
                                "Keyword"
                            },
                    )
                },
                singleLine =
                    true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Spacer(
                Modifier.height(
                    5.dp,
                ),
            )

            Text(
                text =
                    "$minLength to $maxLength characters",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                style =
                    MaterialTheme.typography.bodySmall,
            )

            Spacer(
                Modifier.height(
                    10.dp,
                ),
            )

            Button(
                onClick =
                    onSave,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    keywordInput.length in
                        minLength..maxLength,
                shape =
                    RoundedCornerShape(
                        9.dp,
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            SentinelRed,
                    ),
            ) {
                Text(
                    text =
                        if (configured) {
                            "Replace Keyword"
                        } else {
                            "Save Keyword"
                        },
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RulesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = SentinelInset,
        border =
            BorderStroke(
                1.dp,
                SentinelBorder,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Trigger Rules",
                    color =
                        MaterialTheme.colorScheme.onSurface,
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text =
                        "The configured sender and keyword rules must " +
                            "match according to the existing SMS Wipe " +
                            "configuration before the receiver accepts " +
                            "the trigger.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    style =
                        MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun SimulationCard(
    simSender: String,
    simBody: String,
    resultText: String?,
    onSenderChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSimulate: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
        border =
            BorderStroke(
                1.dp,
                SentinelAmber.copy(
                    alpha = 0.55f,
                ),
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(
                        0xFF191307,
                    ),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        15.dp,
                    ),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(
                                40.dp,
                            )
                            .clip(
                                RoundedCornerShape(
                                    10.dp,
                                ),
                            )
                            .background(
                                SentinelAmber.copy(
                                    alpha = 0.12f,
                                ),
                            ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Science,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                23.dp,
                            ),
                        tint =
                            SentinelAmber,
                    )
                }

                Spacer(
                    Modifier.width(
                        12.dp,
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
                            "SMS Simulation",
                        color =
                            MaterialTheme.colorScheme
                                .onSurface,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Spacer(
                        Modifier.height(
                            2.dp,
                        ),
                    )

                    Text(
                        text =
                            "DEBUG ONLY • NON-DESTRUCTIVE",
                        color =
                            SentinelAmber,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            9.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.7.sp,
                    )
                }
            }

            Spacer(
                Modifier.height(
                    12.dp,
                ),
            )

            Text(
                text =
                    "Exercise the Core SMS matching path using simulated " +
                        "sender and message values. This does not invoke the " +
                        "destructive wipe action.",
                color =
                    SentinelAmber.copy(
                        alpha = 0.78f,
                    ),
                style =
                    MaterialTheme.typography
                        .bodySmall,
                lineHeight =
                    17.sp,
            )

            Spacer(
                Modifier.height(
                    12.dp,
                ),
            )

            OutlinedTextField(
                value =
                    simSender,
                onValueChange =
                    onSenderChange,
                label = {
                    Text(
                        text =
                            "Simulated sender",
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Phone,
                    ),
                singleLine =
                    true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Spacer(
                Modifier.height(
                    8.dp,
                ),
            )

            OutlinedTextField(
                value =
                    simBody,
                onValueChange =
                    onBodyChange,
                label = {
                    Text(
                        text =
                            "Simulated message",
                    )
                },
                singleLine =
                    true,
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Spacer(
                Modifier.height(
                    12.dp,
                ),
            )

            OutlinedButton(
                onClick =
                    onSimulate,
                modifier =
                    Modifier.fillMaxWidth(),
                border =
                    BorderStroke(
                        1.dp,
                        SentinelAmber.copy(
                            alpha = 0.55f,
                        ),
                    ),
            ) {
                Text(
                    text =
                        "Run Non-Destructive Simulation",
                    color =
                        SentinelAmber,
                    fontFamily =
                        FontFamily.Monospace,
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            if (!resultText.isNullOrBlank()) {
                Spacer(
                    Modifier.height(
                        10.dp,
                    ),
                )

                val resultColor =
                    when {
                        resultText.contains(
                            "matched",
                            ignoreCase = true,
                        ) ->
                            SentinelRedLight

                        resultText.contains(
                            "not ready",
                            ignoreCase = true,
                        ) ||
                            resultText.contains(
                                "disabled",
                                ignoreCase = true,
                            ) ->
                            SentinelAmber

                        else ->
                            SentinelGrey
                    }

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(
                            8.dp,
                        ),
                    color =
                        resultColor.copy(
                            alpha = 0.1f,
                        ),
                    border =
                        BorderStroke(
                            1.dp,
                            resultColor.copy(
                                alpha = 0.4f,
                            ),
                        ),
                ) {
                    Text(
                        text =
                            resultText.uppercase(),
                        modifier =
                            Modifier.padding(
                                10.dp,
                            ),
                        color =
                            resultColor,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            10.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SentinelFooter() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color =
                SentinelRed.copy(alpha = 0.13f),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint =
                    SentinelRed.copy(alpha = 0.65f),
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = "NEFARIOUS SENTINEL",
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                        .copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = "PRIVACY  •  SECURITY  •  CONTROL",
            modifier = Modifier.fillMaxWidth(),
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
                    .copy(alpha = 0.38f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 1.4.sp,
            textAlign = TextAlign.Center,
        )
    }
}
