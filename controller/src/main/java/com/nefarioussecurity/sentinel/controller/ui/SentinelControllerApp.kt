package com.nefarioussecurity.sentinel.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.BuildConfig
import com.nefarioussecurity.sentinel.controller.R
import com.nefarioussecurity.sentinel.controller.feature.about.AboutScreen
import com.nefarioussecurity.sentinel.controller.feature.about.LicenseScreen
import com.nefarioussecurity.sentinel.controller.feature.deadman.DeadmanScreen
import com.nefarioussecurity.sentinel.controller.feature.motion.MotionScreen
import com.nefarioussecurity.sentinel.controller.feature.sms.SmsWipeScreen
import com.nefarioussecurity.sentinel.controller.feature.tamper.TamperProtectionScreen
import com.nefarioussecurity.sentinel.controller.feature.wipe.WipeBehaviourScreen
import com.nefarioussecurity.sentinel.controller.feature.usb.UsbProtectionScreen
import com.nefarioussecurity.sentinel.controller.management.ControllerUiState
import com.nefarioussecurity.sentinel.controller.management.ControllerViewModel
import com.nefarioussecurity.sentinel.controller.management.CoreConnectionState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed
import com.runtimebridge.api.ManagementProtocol

private val PanelBackground =
    Color(
        0xFF111214,
    )

private val CardBackground =
    Color(
        0xFF17191C,
    )

private val MutedText =
    Color(
        0xFFA9ADB3,
    )

private val SuccessGreen =
    Color(
        0xFF63C174,
    )

private val WarningAmber =
    Color(
        0xFFE0A94A,
    )

@Composable
fun SentinelControllerApp(
    viewModel: ControllerViewModel,
    onRequestCoreUninstall: (String) -> Unit,
) {
    var state by remember {
        mutableStateOf(
            viewModel.uiState,
        )
    }

    var screen by remember {
        mutableStateOf(
            ControllerScreen.DASHBOARD,
        )
    }

    var uninstallLaunched by remember {
        mutableStateOf(
            false,
        )
    }

    LaunchedEffect(
        state.removalRequested,
        state.authorityMode,
        state.corePackageName,
    ) {
        if (!state.removalRequested) {
            uninstallLaunched =
                false

            return@LaunchedEffect
        }

        val corePackageName =
            state.corePackageName

        if (
            !uninstallLaunched &&
            state.authorityMode ==
                ManagementProtocol.AUTHORITY_NONE &&
            !corePackageName.isNullOrBlank()
        ) {
            uninstallLaunched =
                true

            onRequestCoreUninstall(
                corePackageName,
            )
        }
    }

    LaunchedEffect(
        state.betaLifecycle.destructiveSuppressed,
        screen,
    ) {
        if (
            state.betaLifecycle.destructiveSuppressed &&
            screen in
            setOf(
                ControllerScreen.WIPE_BEHAVIOUR,
                ControllerScreen.TAMPER,
                ControllerScreen.USB,
                ControllerScreen.DEADMAN,
                ControllerScreen.MOTION,
                ControllerScreen.SMS,
            )
        ) {
            viewModel.clearMessage()

            screen =
                ControllerScreen.BETA_LIFECYCLE
        }
    }

    DisposableEffect(
        viewModel,
    ) {
        val observer:
            (ControllerUiState) -> Unit = {
                state =
                    it
            }

        viewModel.observe(
            observer,
        )

        onDispose {
            viewModel.removeObserver(
                observer,
            )
        }
    }

    /*
     * Child pages use local Compose state rather than separate Activities.
     * Without a registered BackHandler, Android Back closes the Controller
     * instead of navigating to the same parent as the on-screen Back arrow.
     *
     * Do not intercept Back while the removal operation is active, while
     * authentication/setup is on screen, or from the dashboard.
     */
    val systemBackTarget =
        screen.systemBackDestination()

    BackHandler(
        enabled =
            !state.removalRequested &&
                state.connectionState ==
                    CoreConnectionState.READY &&
                state.adminActive &&
                state.authenticated &&
                systemBackTarget != null,
    ) {
        val destination =
            systemBackTarget
                ?: return@BackHandler

        viewModel.clearMessage()
        screen =
            destination
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),
        color =
            PanelBackground,
    ) {
        when {
            state.removalRequested -> {
                RemovalProgressScreen(
                    authorityMode =
                        state.authorityMode,
                    message =
                        state.message,
                )
            }

            state.connectionState !=
                CoreConnectionState.READY -> {
                CoreConnectionScreen(
                    state =
                        state,
                    onReconnect = {
                        viewModel.reconnect()
                    },
                )
            }

            !state.adminActive -> {
                StandardModeSetupScreen(
                    onActivate = {
                        viewModel.activateStandardAdmin()
                    },
                    onReconnect = {
                        viewModel.reconnect()
                    },
                )
            }

            !state.authenticated -> {
                AuthenticationScreen(
                    state =
                        state,
                    onEnroll = {
                        pin ->
                        viewModel.enrollPin(
                            pin,
                        )
                    },
                    onAuthenticate = {
                        pin ->
                        viewModel.authenticatePin(
                            pin,
                        )
                    },
                    onReconnect = {
                        viewModel.reconnect()
                    },
                )
            }

            screen ==
                ControllerScreen.BETA_LIFECYCLE -> {
                BetaLifecycleScreen(
                    state =
                        state,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onRearm = {
                        pin ->

                        viewModel.rearmBetaLifecycle(
                            pin,
                        )
                    },
                )
            }

            screen ==
                ControllerScreen.WIPE_BEHAVIOUR -> {
                WipeBehaviourScreen(
                    state =
                        state.wipeBehaviour,
                    message =
                        state.message,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onSetEsimErase = {
                        enabled ->

                        viewModel.setEsimEraseEnabled(
                            enabled,
                        )
                    },
                )
            }

            screen ==
                ControllerScreen.TAMPER -> {
                TamperProtectionScreen(
                    state =
                        state.tamper,
                    authorityMode =
                        state.authorityMode,
                    maintenanceActive =
                        state.usb.maintenanceActive,
                    message =
                        state.message,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onSetUsbDebuggingProtection = {
                        enabled ->

                        viewModel
                            .setUsbDebuggingProtectionEnabled(
                                enabled,
                            )
                    },
                    onSetWirelessDebuggingProtection = {
                        enabled ->

                        viewModel
                            .setWirelessDebuggingProtectionEnabled(
                                enabled,
                            )
                    },
                )
            }

            screen ==
                ControllerScreen.USB -> {
                UsbProtectionScreen(
                    state =
                        state.usb,
                    message =
                        state.message,
                    onMessageConsumed = {
                        viewModel.clearMessage()
                    },
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onSetProtection = {
                        enabled ->

                        viewModel.setUsbProtectionEnabled(
                            enabled,
                        )
                    },
                    onSetStrict = {
                        enabled ->

                        viewModel.setUsbStrictModeEnabled(
                            enabled,
                        )
                    },
                    onStartMaintenance = {
                        viewModel.startUsbMaintenance()
                    },
                    onExtendMaintenance = {
                        viewModel.extendUsbMaintenance()
                    },
                    onCancelMaintenance = {
                        viewModel.cancelUsbMaintenance()
                    },
                )
            }

            screen ==
                ControllerScreen.DEADMAN -> {
                DeadmanScreen(
                    state =
                        state.deadman,
                    message =
                        state.message,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onSetEnabled = {
                        enabled ->

                        viewModel.setDeadmanEnabled(
                            enabled,
                        )
                    },
                    onSetIntervalMs = {
                        intervalMs ->

                        viewModel.setDeadmanIntervalMs(
                            intervalMs,
                        )
                    },
                    onCheckIn = {
                        viewModel.checkInDeadman()
                    },
                )
            }

            screen ==
                ControllerScreen.MOTION -> {
                MotionScreen(
                    state =
                        state.motion,
                    message =
                        state.message,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onSetEnabled = {
                        enabled ->

                        viewModel.setMotionEnabled(
                            enabled,
                        )
                    },
                    onSetAction = {
                        action ->

                        viewModel.setMotionAction(
                            action,
                        )
                    },
                    onSetSensitivity = {
                        sensitivity ->

                        viewModel.setMotionSensitivity(
                            sensitivity,
                        )
                    },
                )
            }

            screen ==
                ControllerScreen.SMS -> {
                SmsWipeScreen(
                    state =
                        state.sms,
                    adminActive =
                        state.adminActive,
                    deviceOwner =
                        state.deviceOwner,
                    message =
                        state.message,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onGrantPermission = {
                        viewModel.grantSmsPermission()
                    },
                    onSetSender = {
                        sender ->

                        viewModel.setSmsSender(
                            sender,
                        )
                    },
                    onSetKeyword = {
                        keyword ->

                        viewModel.setSmsKeyword(
                            keyword,
                        )
                    },
                    onSetEnabled = {
                        enabled ->

                        viewModel.setSmsEnabled(
                            enabled,
                        )
                    },
                    onSimulate = {
                        sender,
                        body ->

                        viewModel.simulateSms(
                            sender,
                            body,
                        )
                    },
                )
            }

            screen ==
                ControllerScreen.ABOUT -> {
                AboutScreen(
                    onBack = {
                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onOpenLicense = {
                        screen =
                            ControllerScreen.LICENSE
                    },
                )
            }

            screen ==
                ControllerScreen.LICENSE -> {
                LicenseScreen(
                    onBack = {
                        screen =
                            ControllerScreen.ABOUT
                    },
                )
            }

            screen ==
                ControllerScreen.REMOVE -> {
                RemovalScreen(
                    state =
                        state,
                    onBack = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.DASHBOARD
                    },
                    onConfirm = {
                        pin ->

                        viewModel.beginStandardRemoval(
                            pin,
                        )
                    },
                )
            }

            else -> {
                DashboardScreen(
                    state =
                        state,
                    onOpenBetaLifecycle = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen
                                .BETA_LIFECYCLE
                    },
                    onOpenUsb = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen.USB
                            }
                    },
                    onOpenWipeBehaviour = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen
                                    .WIPE_BEHAVIOUR
                            }
                    },
                    onOpenTamper = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen.TAMPER
                            }
                    },
                    onOpenDeadman = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen.DEADMAN
                            }
                    },
                    onOpenMotion = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen.MOTION
                            }
                    },
                    onOpenSms = {
                        viewModel.clearMessage()

                        screen =
                            if (
                                state.betaLifecycle
                                    .destructiveSuppressed
                            ) {
                                ControllerScreen
                                    .BETA_LIFECYCLE
                            } else {
                                ControllerScreen.SMS
                            }
                    },
                    onOpenAbout = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.ABOUT
                    },
                    onOpenRemoval = {
                        viewModel.clearMessage()

                        screen =
                            ControllerScreen.REMOVE
                    },
                    onRefresh = {
                        viewModel.refresh()
                    },
                    onLogout = {
                        screen =
                            ControllerScreen.DASHBOARD

                        viewModel.logout()
                    },
                )
            }
        }
    }
}

@Composable
private fun SentinelHeader(
    compact: Boolean = false,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Image(
            painter =
                painterResource(
                    id =
                        R.drawable
                            .nefarious_sentinel_crow_header,
                ),
            contentDescription =
                "Nefarious Sentinel",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        if (compact) {
                            140.dp
                        } else {
                            205.dp
                        },
                    ),
            contentScale =
                ContentScale.Fit,
        )

        Text(
            text =
                "NEFARIOUS SENTINEL",
            color =
                Color.White,
            fontSize =
                if (compact) {
                    22.sp
                } else {
                    26.sp
                },
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                2.sp,
        )

        if (BuildConfig.BETA_BUILD) {
            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Surface(
                color =
                    WarningAmber.copy(
                        alpha =
                            0.16f,
                    ),
                shape =
                    RoundedCornerShape(
                        50.dp,
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        WarningAmber.copy(
                            alpha =
                                0.8f,
                        ),
                    ),
            ) {
                Text(
                    text =
                        "BETA",
                    color =
                        WarningAmber,
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        1.5.sp,
                    modifier =
                        Modifier.padding(
                            horizontal =
                                12.dp,
                            vertical =
                                4.dp,
                        ),
                )
            }
        }

        Text(
            text =
                "privacy  •  security  •  freedom",
            color =
                SentinelRed,
            fontSize =
                12.sp,
            letterSpacing =
                1.sp,
            modifier =
                Modifier.padding(
                    top =
                        5.dp,
                ),
        )
    }
}

@Composable
private fun CoreConnectionScreen(
    state: ControllerUiState,
    onReconnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        20.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader()

        Spacer(
            modifier =
                Modifier.height(
                    28.dp,
                ),
        )

        SecurityPanel(
            title =
                connectionTitle(
                    state.connectionState,
                ),
            body =
                connectionDescription(
                    state,
                ),
        )

        if (
            state.connectionState ==
            CoreConnectionState.SEARCHING ||
            state.connectionState ==
            CoreConnectionState.CONNECTING
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        26.dp,
                    ),
            )

            CircularProgressIndicator(
                color =
                    SentinelRed,
            )
        } else {
            Spacer(
                modifier =
                    Modifier.height(
                        22.dp,
                    ),
            )

            OutlinedButton(
                onClick =
                    onReconnect,
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Refresh,
                    contentDescription =
                        null,
                )

                Spacer(
                    modifier =
                        Modifier.size(
                            8.dp,
                        ),
                )

                Text(
                    "Retry Core Connection",
                )
            }
        }
    }
}

@Composable
private fun StandardModeSetupScreen(
    onActivate: () -> Unit,
    onReconnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        20.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader()

        Spacer(
            modifier =
                Modifier.height(
                    28.dp,
                ),
        )

        SecurityPanel(
            title =
                "STANDARD MODE SETUP",
            body =
                "Sentinel Core is verified, but device administration is not active. " +
                    "Standard mode uses Android Device Admin for Lock and Wipe. " +
                    "Reboot remains available only in Hardened Device Owner mode.",
        )

        Spacer(
            modifier =
                Modifier.height(
                    22.dp,
                ),
        )

        Button(
            onClick =
                onActivate,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        SentinelRed,
                ),
        ) {
            Icon(
                imageVector =
                    Icons.Default.Security,
                contentDescription =
                    null,
            )

            Spacer(
                modifier =
                    Modifier.size(
                        8.dp,
                    ),
            )

            Text(
                "Activate Device Admin",
                fontWeight =
                    FontWeight.Bold,
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp,
                ),
        )

        OutlinedButton(
            onClick =
                onReconnect,
        ) {
            Icon(
                imageVector =
                    Icons.Default.Refresh,
                contentDescription =
                    null,
            )

            Spacer(
                modifier =
                    Modifier.size(
                        8.dp,
                    ),
            )

            Text(
                "Refresh Core State",
            )
        }
    }
}

@Composable
private fun AuthenticationScreen(
    state: ControllerUiState,
    onEnroll: (String) -> Unit,
    onAuthenticate: (String) -> Unit,
    onReconnect: () -> Unit,
) {
    var pin by remember {
        mutableStateOf(
            "",
        )
    }

    var confirmation by remember {
        mutableStateOf(
            "",
        )
    }

    var localMessage by remember {
        mutableStateOf<String?>(
            null,
        )
    }

    val enrolling =
        state.pinState ==
            ManagementProtocol
                .PIN_STATE_NOT_ENROLLED

    val submitAuthentication:
        () -> Unit = {
            localMessage =
                null

            if (enrolling) {
                when {
                    pin.length !in
                        ManagementProtocol.PIN_MIN_LENGTH..
                        ManagementProtocol.PIN_MAX_LENGTH -> {
                        localMessage =
                            "PIN must contain " +
                                "${ManagementProtocol.PIN_MIN_LENGTH}-${ManagementProtocol.PIN_MAX_LENGTH} digits."
                    }

                    pin !=
                        confirmation -> {
                        localMessage =
                            "PIN confirmation does not match."
                    }

                    else -> {
                        val submitted =
                            pin

                        pin =
                            ""

                        confirmation =
                            ""

                        onEnroll(
                            submitted,
                        )
                    }
                }
            } else {
                if (pin.isBlank()) {
                    localMessage =
                        "Enter your Sentinel PIN."
                } else {
                    val submitted =
                        pin

                    pin =
                        ""

                    onAuthenticate(
                        submitted,
                    )
                }
            }
        }

    val authenticationEnabled =
        state.pinState !=
            ManagementProtocol.PIN_STATE_LOCKED &&
            state.pinState !=
            ManagementProtocol.PIN_STATE_TERMINAL

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal =
                        28.dp,
                    vertical =
                        18.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader()

        Spacer(
            modifier =
                Modifier.height(
                    22.dp,
                ),
        )

        CoreTrustStrip(
            state =
                state,
        )

        Spacer(
            modifier =
                Modifier.height(
                    24.dp,
                ),
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        CardBackground,
                ),
            shape =
                RoundedCornerShape(
                    18.dp,
                ),
            border =
                BorderStroke(
                    1.dp,
                    Color(
                        0xFF303237,
                    ),
                ),
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        22.dp,
                    ),
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Lock,
                        contentDescription =
                            null,
                        tint =
                            SentinelRed,
                    )

                    Spacer(
                        modifier =
                            Modifier.size(
                                12.dp,
                            ),
                    )

                    Text(
                        text =
                            if (enrolling) {
                                "CREATE SENTINEL PIN"
                            } else {
                                "AUTHENTICATE"
                            },
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            18.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            18.dp,
                        ),
                )

                Text(
                    text =
                        if (enrolling) {
                            "Create the independent PIN used to manage the headless Sentinel Core."
                        } else {
                            "Enter your Sentinel PIN to open the protected management interface."
                        },
                    color =
                        MutedText,
                    fontSize =
                        14.sp,
                    lineHeight =
                        20.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            18.dp,
                        ),
                )

                SentinelPinField(
                    value =
                        pin,
                    onValueChange = {
                        pin =
                            it
                    },
                    label =
                        if (enrolling) {
                            "New Sentinel PIN"
                        } else {
                            "Sentinel PIN"
                        },
                    imeAction =
                        if (enrolling) {
                            ImeAction.Next
                        } else {
                            ImeAction.Done
                        },
                    onDone = {
                        if (
                            authenticationEnabled &&
                            !enrolling
                        ) {
                            submitAuthentication()
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                if (enrolling) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp,
                            ),
                    )

                        SentinelPinField(
                        value =
                            confirmation,
                        onValueChange = {
                            confirmation =
                                it
                        },
                        label =
                            "Confirm Sentinel PIN",
                        imeAction =
                            ImeAction.Done,
                        onDone = {
                            if (authenticationEnabled) {
                                submitAuthentication()
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth(),
                    )
                }

                val message =
                    localMessage
                        ?: state.message

                if (!message.isNullOrBlank()) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp,
                            ),
                    )

                    Text(
                        text =
                            message,
                        color =
                            WarningAmber,
                        fontSize =
                            13.sp,
                    )
                }

                if (
                    state.pinState ==
                    ManagementProtocol.PIN_STATE_LOCKED
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp,
                            ),
                    )

                    Text(
                        text =
                            "Retry available in " +
                                formatDuration(
                                    state.remainingLockoutMs,
                                ),
                        color =
                            WarningAmber,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            20.dp,
                        ),
                )

                Button(
                    onClick =
                        submitAuthentication,
                    enabled =
                        authenticationEnabled,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                SentinelRed,
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (enrolling) {
                                "ENROL SENTINEL PIN"
                            } else {
                                "UNLOCK SENTINEL"
                            },
                        fontWeight =
                            FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    16.dp,
                ),
        )

        OutlinedButton(
            onClick =
                onReconnect,
        ) {
            Text(
                "Refresh Core",
            )
        }
    }
}


@Composable
private fun DashboardScreen(
    state: ControllerUiState,
    onOpenBetaLifecycle: () -> Unit,
    onOpenUsb: () -> Unit,
    onOpenWipeBehaviour: () -> Unit,
    onOpenTamper: () -> Unit,
    onOpenDeadman: () -> Unit,
    onOpenMotion: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRemoval: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    bottom =
                        32.dp,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black,
                    )
                    .padding(
                        horizontal =
                            20.dp,
                        vertical =
                            12.dp,
                    ),
        ) {
            SentinelHeader(
                compact =
                    true,
            )
        }

        Column(
            modifier =
                Modifier.padding(
                    horizontal =
                        18.dp,
                ),
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        18.dp,
                    ),
            )

            CoreTrustStrip(
                state =
                    state,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        22.dp,
                    ),
            )

            if (
                state.betaLifecycle
                    .destructiveSuppressed
            ) {
                BetaLifecycleWarningCard(
                    state =
                        state,
                    onClick = {
                        onOpenBetaLifecycle()
                    },
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            20.dp,
                        ),
                )
            }

            Text(
                text =
                    "PROTECTION STATUS",
                color =
                    Color.White,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Bold,
                letterSpacing =
                    1.5.sp,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            FeatureCard(
                icon =
                    Icons.Default.Usb,
                title =
                    "USB Protection",
                status =
                    if (
                        state.usb
                            .maintenanceActive
                    ) {
                        "MAINTENANCE"
                    } else if (
                        state.usb
                            .protectionEnabled
                    ) {
                        "ARMED"
                    } else {
                        "DISABLED"
                    },
                statusActive =
                    state.usb
                        .protectionEnabled,
                maintenanceState =
                    state.usb
                        .maintenanceActive,
                detail =
                    if (
                        state.usb
                            .maintenanceActive
                    ) {
                        formatMaintenanceCountdown(
                            state.usb
                                .maintenanceRemainingMs,
                        ) +
                            " remaining"
                    } else {
                        buildString {
                            append(
                                connectionText(
                                    state.usb
                                        .connectionState,
                                ),
                            )

                            if (
                                state.usb
                                    .strictModeEnabled
                            ) {
                                append(
                                    "  •  Strict USB",
                                )
                            }
                        }
                    },
                onClick =
                    onOpenUsb,
            )

            FeatureCard(
                icon =
                    Icons.Default.AccessTime,
                title =
                    "Dead Man's Switch",
                status =
                    deadmanStateText(
                        state.deadman
                            .state,
                    ),
                statusActive =
                    state.deadman
                        .state ==
                        ManagementProtocol
                            .DEADMAN_STATE_ARMED,
                detail =
                    if (
                        state.deadman
                            .state ==
                        ManagementProtocol
                            .DEADMAN_STATE_ARMED
                    ) {
                        formatDuration(
                            state.deadman
                                .remainingMs,
                        ) +
                            " remaining"
                    } else {
                        "Direct Boot protection"
                    },
                onClick =
                    onOpenDeadman,
            )

            FeatureCard(
                icon =
                    Icons.AutoMirrored.Outlined.DirectionsRun,
                title =
                    "Snatch Protection",
                status =
                    motionStateText(
                        state.motion
                            .operationalState,
                    ),
                statusActive =
                    state.motion
                        .operationalState ==
                        ManagementProtocol
                            .MOTION_OPERATIONAL_ACTIVE,
                detail =
                    "Sensitivity " +
                        state.motion
                            .sensitivity +
                        "  •  " +
                        if (
                            state.motion.action ==
                            ManagementProtocol
                                .MOTION_ACTION_REBOOT
                        ) {
                            "Reboot response"
                        } else {
                            "Lock response"
                        },
                onClick =
                    onOpenMotion,
            )

            FeatureCard(
                icon =
                    Icons.AutoMirrored.Filled.Message,
                title =
                    "SMS Wipe",
                status =
                    if (
                        state.sms
                            .armed
                    ) {
                        "ARMED"
                    } else if (
                        state.sms
                            .enabled
                    ) {
                        "INCOMPLETE"
                    } else {
                        "DISABLED"
                    },
                statusActive =
                    state.sms
                        .armed,
                detail =
                    buildString {
                        append(
                            if (
                                state.sms
                                    .senderConfigured
                            ) {
                                "Sender configured"
                            } else {
                                "Sender not configured"
                            },
                        )

                        append(
                            "  •  ",
                        )

                        append(
                            if (
                                state.sms
                                    .permissionGranted
                            ) {
                                "Permission ready"
                            } else {
                                "Permission required"
                            },
                        )
                    },
                onClick =
                    onOpenSms,
            )

            FeatureCard(
                icon =
                    Icons.Default.Delete,
                title =
                    "Wipe Behaviour",
                status =
                    if (
                        state.wipeBehaviour
                            .esimEraseEnabled
                    ) {
                        "ESIM ERASE ON"
                    } else if (
                        state.wipeBehaviour
                            .esimEraseAvailable
                    ) {
                        "HARDENED"
                    } else {
                        "STANDARD"
                    },
                statusActive =
                    true,
                detail =
                    if (
                        state.wipeBehaviour
                            .esimEraseAvailable
                    ) {
                        "Factory reset  •  eSIM erase " +
                            if (
                                state.wipeBehaviour
                                    .esimEraseEnabled
                            ) {
                                "ON"
                            } else {
                                "OFF"
                            }
                    } else {
                        "Factory reset  •  eSIM erase requires Hardened mode"
                    },
                onClick =
                    onOpenWipeBehaviour,
            )

            FeatureCard(
                icon =
                    Icons.Default.Security,
                title =
                    "Tamper Proofing",
                status =
                    if (
                        state.tamper
                            .usbDebuggingProtectionEnabled ||
                        state.tamper
                            .wirelessDebuggingProtectionEnabled
                    ) {
                        "ACTIVE"
                    } else {
                        "DISABLED"
                    },
                statusActive =
                    state.tamper
                        .usbDebuggingProtectionEnabled ||
                        state.tamper
                            .wirelessDebuggingProtectionEnabled,
                detail =
                    "USB debugging " +
                        if (
                            state.tamper
                                .usbDebuggingProtectionEnabled
                        ) {
                            "ON"
                        } else {
                            "OFF"
                        } +
                        "  •  Wireless debugging " +
                        if (
                            state.tamper
                                .wirelessDebuggingProtectionEnabled
                        ) {
                            "ON"
                        } else {
                            "OFF"
                        },
                onClick =
                    onOpenTamper,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(
                                0xFF101113,
                            ),
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        Color(
                            0xFF292B2F,
                        ),
                    ),
                shape =
                    RoundedCornerShape(
                        14.dp,
                    ),
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            18.dp,
                        ),
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Security,
                            contentDescription =
                                null,
                            tint =
                                SuccessGreen,
                        )

                        Spacer(
                            modifier =
                                Modifier.size(
                                    10.dp,
                                ),
                        )

                        Text(
                            text =
                                "HEADLESS CORE VERIFIED",
                            color =
                                Color.White,
                            fontWeight =
                                FontWeight.Bold,
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                10.dp,
                            ),
                    )

                    Text(
                        text =
                            "Protocol " +
                                (
                                    state.protocolVersion
                                        ?: ManagementProtocol
                                            .PROTOCOL_VERSION
                                    ) +
                                "  •  " +
                                if (state.deviceOwner) {
                                    "Hardened • Device Owner • Signature verified"
                                } else {
                                    "Standard • Device Admin • Signature verified"
                                },
                        color =
                            MutedText,
                        fontSize =
                            13.sp,
                    )

                    Text(
                        text =
                            "Controller may be removed without removing the Core.",
                        color =
                            MutedText,
                        fontSize =
                            12.sp,
                        modifier =
                            Modifier.padding(
                                top =
                                    5.dp,
                            ),
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp,
                    ),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                OutlinedButton(
                    onClick =
                        onRefresh,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Refresh,
                        contentDescription =
                            null,
                    )

                    Spacer(
                        modifier =
                            Modifier.size(
                                6.dp,
                            ),
                    )

                    Text(
                        "Refresh",
                    )
                }

                OutlinedButton(
                    onClick =
                        onLogout,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Lock,
                        contentDescription =
                            null,
                    )

                    Spacer(
                        modifier =
                            Modifier.size(
                                6.dp,
                            ),
                    )

                    Text(
                        "End Session",
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            OutlinedButton(
                onClick =
                    onOpenAbout,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Info,
                    contentDescription =
                        null,
                )

                Spacer(
                    modifier =
                        Modifier.size(
                            7.dp,
                        ),
                )

                Text(
                    text =
                        "ABOUT SENTINEL",
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            if (
                state.authorityMode ==
                ManagementProtocol.AUTHORITY_DEVICE_ADMIN
            ) {
                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp,
                        ),
                )

                HorizontalDivider(
                    color =
                        Color(
                            0xFF303237,
                        ),
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp,
                        ),
                )

                OutlinedButton(
                    onClick =
                        onOpenRemoval,
                    border =
                        BorderStroke(
                            1.dp,
                            SentinelRed.copy(
                                alpha = 0.75f,
                            ),
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            "REMOVE SENTINEL",
                        color =
                            SentinelRed,
                        fontWeight =
                            FontWeight.Bold,
                    )
                }

                Text(
                    text =
                        "Authenticated removal safely releases Standard-mode Device Admin before Android uninstalls the hidden Core and Controller.",
                    color =
                        MutedText,
                    fontSize =
                        11.sp,
                    lineHeight =
                        16.sp,
                    textAlign =
                        TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top =
                                    8.dp,
                            ),
                )
            }
        }
    }
}

@Composable
private fun BetaLifecycleWarningCard(
    state: ControllerUiState,
    onClick: () -> Unit,
) {
    val title =
        when (
            state.betaLifecycle.state
        ) {
            ManagementProtocol
                .BETA_STATE_CURRENT_BUILD_EXPIRED ->
                "BETA BUILD EXPIRED"

            ManagementProtocol
                .BETA_STATE_REARM_REQUIRED ->
                "PROTECTION RE-ARM REQUIRED"

            ManagementProtocol
                .BETA_STATE_ERROR ->
                "BETA LIFECYCLE ERROR"

            else ->
                "BETA PROTECTION SUPPRESSED"
        }

    val body =
        when (
            state.betaLifecycle.state
        ) {
            ManagementProtocol
                .BETA_STATE_CURRENT_BUILD_EXPIRED ->
                "Destructive actions are disabled. Install a newer Sentinel build before protections can be restored."

            ManagementProtocol
                .BETA_STATE_REARM_REQUIRED ->
                "A newer Sentinel build is installed, but destructive actions remain disabled until you explicitly re-arm them."

            ManagementProtocol
                .BETA_STATE_ERROR ->
                "Sentinel cannot safely verify Beta lifecycle state. Destructive actions remain disabled."

            else ->
                "Sentinel has suppressed destructive actions."
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(
                        0xFF261B08,
                    ),
            ),
        border =
            BorderStroke(
                1.dp,
                WarningAmber.copy(
                    alpha =
                        0.85f,
                ),
            ),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick =
                        onClick,
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Security,
                    contentDescription =
                        null,
                    tint =
                        WarningAmber,
                )

                Spacer(
                    modifier =
                        Modifier.size(
                            10.dp,
                        ),
                )

                Text(
                    text =
                        title,
                    color =
                        WarningAmber,
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        0.7.sp,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Text(
                text =
                    body,
                color =
                    Color.White,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Text(
                text =
                    "Protection configuration is locked while this condition is active.",
                color =
                    MutedText,
                fontSize =
                    12.sp,
            )
        }
    }
}


@Composable
private fun BetaLifecycleScreen(
    state: ControllerUiState,
    onBack: () -> Unit,
    onRearm: (String) -> Unit,
) {
    var pin by remember {
        mutableStateOf(
            "",
        )
    }

    var localMessage by remember {
        mutableStateOf<String?>(
            null,
        )
    }

    val lifecycle =
        state.betaLifecycle

    /*
     * The local UI deliberately only decides whether to present the operation.
     * Core remains authoritative for versionCode, expiry and persisted state.
     */
    val showRearm =
        lifecycle.state ==
            ManagementProtocol
                .BETA_STATE_REARM_REQUIRED &&
            lifecycle.stateHealthy

    val title =
        when (lifecycle.state) {
            ManagementProtocol
                .BETA_STATE_CURRENT_BUILD_EXPIRED ->
                "BETA BUILD EXPIRED"

            ManagementProtocol
                .BETA_STATE_REARM_REQUIRED ->
                "RE-ARM PROTECTIONS"

            ManagementProtocol
                .BETA_STATE_ERROR ->
                "LIFECYCLE STATE ERROR"

            else ->
                "BETA LIFECYCLE"
        }

    val body =
        when (lifecycle.state) {
            ManagementProtocol
                .BETA_STATE_CURRENT_BUILD_EXPIRED ->
                "This Sentinel Beta has reached its expiry date. Destructive security actions are suppressed and protection configuration is locked.\n\n" +
                    "Install a newer Sentinel build. Installing it will not automatically restore destructive actions."

            ManagementProtocol
                .BETA_STATE_REARM_REQUIRED ->
                "Sentinel previously observed an expired build and has kept destructive actions suppressed after the upgrade.\n\n" +
                    "Re-arming requires fresh Sentinel PIN verification. Core will independently reject the request unless this installed build is newer than the build which expired and the current build itself is still valid."

            ManagementProtocol
                .BETA_STATE_ERROR ->
                "Sentinel cannot safely verify its persisted Beta lifecycle state. Destructive actions remain suppressed and configuration is locked.\n\n" +
                    "Do not rely on destructive protection until the lifecycle-state problem is resolved."

            else ->
                "The Sentinel Beta lifecycle is active."
        }

    val submit:
        () -> Unit = {
            localMessage =
                null

            when {
                !showRearm -> {
                    localMessage =
                        "Re-arm is not available in the current lifecycle state."
                }

                pin.length !in
                    ManagementProtocol.PIN_MIN_LENGTH..
                    ManagementProtocol.PIN_MAX_LENGTH -> {
                    localMessage =
                        "PIN must contain " +
                            "${ManagementProtocol.PIN_MIN_LENGTH}-${ManagementProtocol.PIN_MAX_LENGTH} digits."
                }

                else -> {
                    val submitted =
                        pin

                    pin =
                        ""

                    onRearm(
                        submitted,
                    )
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        20.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader(
            compact =
                true,
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(
                            0xFF261B08,
                        ),
                ),
            border =
                BorderStroke(
                    1.dp,
                    WarningAmber.copy(
                        alpha =
                            0.85f,
                    ),
                ),
            shape =
                RoundedCornerShape(
                    16.dp,
                ),
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        20.dp,
                    ),
            ) {
                Text(
                    text =
                        title,
                    color =
                        WarningAmber,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        17.sp,
                    letterSpacing =
                        0.8.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp,
                        ),
                )

                Text(
                    text =
                        body,
                    color =
                        Color.White,
                    fontSize =
                        13.sp,
                    lineHeight =
                        20.sp,
                )

                if (
                    lifecycle.expiredVersionCode >
                    0
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp,
                            ),
                    )

                    Text(
                        text =
                            "Expired build versionCode: " +
                                lifecycle
                                    .expiredVersionCode,
                        color =
                            MutedText,
                        fontSize =
                            12.sp,
                    )
                }
            }
        }

        if (showRearm) {
            Spacer(
                modifier =
                    Modifier.height(
                        18.dp,
                    ),
            )

            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            CardBackground,
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        Color(
                            0xFF303237,
                        ),
                    ),
                shape =
                    RoundedCornerShape(
                        16.dp,
                    ),
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            20.dp,
                        ),
                ) {
                    Text(
                        text =
                            "FRESH AUTHENTICATION REQUIRED",
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
                                10.dp,
                            ),
                    )

                    Text(
                        text =
                            "Enter the Sentinel management PIN again. A successful re-arm restores destructive protection for this upgraded build.",
                        color =
                            MutedText,
                        fontSize =
                            13.sp,
                        lineHeight =
                            19.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                16.dp,
                            ),
                    )

                    SentinelPinField(
                        value =
                            pin,
                        onValueChange = {
                            pin =
                                it
                        },
                        label =
                            "Sentinel PIN",
                        imeAction =
                            ImeAction.Done,
                        onDone =
                            submit,
                        modifier =
                            Modifier.fillMaxWidth(),
                    )

                    val message =
                        localMessage
                            ?: state.message

                    if (!message.isNullOrBlank()) {
                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp,
                                ),
                        )

                        Text(
                            text =
                                message,
                            color =
                                WarningAmber,
                            fontSize =
                                13.sp,
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                18.dp,
                            ),
                    )

                    Button(
                        onClick =
                            submit,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    SentinelRed,
                            ),
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                "RE-ARM PROTECTIONS",
                            fontWeight =
                                FontWeight.Bold,
                        )
                    }
                }
            }
        } else if (!state.message.isNullOrBlank()) {
            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            Text(
                text =
                    state.message,
                color =
                    WarningAmber,
                fontSize =
                    13.sp,
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp,
                ),
        )

        OutlinedButton(
            onClick =
                onBack,
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            Text(
                "BACK TO DASHBOARD",
            )
        }
    }
}


@Composable
private fun RemovalScreen(
    state: ControllerUiState,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember {
        mutableStateOf(
            "",
        )
    }

    var localMessage by remember {
        mutableStateOf<String?>(
            null,
        )
    }

    val submitRemoval:
        () -> Unit = {
            localMessage =
                null

            when {
                state.authorityMode !=
                    ManagementProtocol.AUTHORITY_DEVICE_ADMIN -> {
                    localMessage =
                        "Removal is available only while Standard Device Admin mode is active."
                }

                pin.length !in
                    ManagementProtocol.PIN_MIN_LENGTH..
                    ManagementProtocol.PIN_MAX_LENGTH -> {
                    localMessage =
                        "PIN must contain " +
                            "${ManagementProtocol.PIN_MIN_LENGTH}-${ManagementProtocol.PIN_MAX_LENGTH} digits."
                }

                else -> {
                    val submitted =
                        pin

                    pin =
                        ""

                    onConfirm(
                        submitted,
                    )
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        20.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader(
            compact =
                true,
        )

        Spacer(
            modifier =
                Modifier.height(
                    22.dp,
                ),
        )

        SecurityPanel(
            title =
                "REMOVE SENTINEL",
            body =
                "This deliberately disables Sentinel protection on this device. " +
                    "The hidden Core will first relinquish Standard-mode Device Admin, " +
                    "then Android will ask you to uninstall the Core and finally the Controller.",
        )

        Spacer(
            modifier =
                Modifier.height(
                    18.dp,
                ),
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(
                            0xFF1A0707,
                        ),
                ),
            border =
                BorderStroke(
                    1.dp,
                    SentinelRed.copy(
                        alpha =
                            0.78f,
                    ),
                ),
            shape =
                RoundedCornerShape(
                    16.dp,
                ),
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        20.dp,
                    ),
            ) {
                Text(
                    text =
                        "FRESH AUTHENTICATION REQUIRED",
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
                            10.dp,
                        ),
                )

                Text(
                    text =
                        "Enter the Sentinel PIN again. The Core verifies this PIN itself before it will release Device Admin authority.",
                    color =
                        MutedText,
                    fontSize =
                        13.sp,
                    lineHeight =
                        19.sp,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            16.dp,
                        ),
                )

                SentinelPinField(
                    value =
                        pin,
                    onValueChange = {
                        pin =
                            it
                    },
                    label =
                        "Sentinel PIN",
                    imeAction =
                        ImeAction.Done,
                    onDone =
                        submitRemoval,
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                val message =
                    localMessage
                        ?: state.message

                if (!message.isNullOrBlank()) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp,
                            ),
                    )

                    Text(
                        text =
                            message,
                        color =
                            WarningAmber,
                        fontSize =
                            12.sp,
                        lineHeight =
                            17.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            18.dp,
                        ),
                )

                Button(
                    onClick =
                        submitRemoval,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                SentinelRed,
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            "AUTHORISE REMOVAL",
                        fontWeight =
                            FontWeight.Bold,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp,
                        ),
                )

                OutlinedButton(
                    onClick =
                        onBack,
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "CANCEL",
                    )
                }
            }
        }
    }
}

@Composable
private fun RemovalProgressScreen(
    authorityMode: Int,
    message: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal =
                        24.dp,
                    vertical =
                        20.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        SentinelHeader(
            compact =
                true,
        )

        Spacer(
            modifier =
                Modifier.height(
                    28.dp,
                ),
        )

        CircularProgressIndicator(
            color =
                SentinelRed,
        )

        Spacer(
            modifier =
                Modifier.height(
                    22.dp,
                ),
        )

        SecurityPanel(
            title =
                "REMOVAL IN PROGRESS",
            body =
                buildString {
                    append(
                        if (
                            authorityMode ==
                            ManagementProtocol.AUTHORITY_NONE
                        ) {
                            "Device Admin has been safely released. Opening Android's Core uninstall confirmation."
                        } else {
                            "The Core is safely releasing Standard-mode Device Admin authority."
                        },
                    )

                    if (!message.isNullOrBlank()) {
                        append(
                            "\n\n",
                        )

                        append(
                            message,
                        )
                    }
                },
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    status: String,
    statusActive: Boolean,
    maintenanceState: Boolean = false,
    detail: String,
    onClick: (() -> Unit)?,
) {
    val sentinelGreen =
        Color(
            0xFF55D6A0,
        )

    val sentinelCard =
        Color(
            0xFF101212,
        )

    val sentinelDeepRed =
        Color(
            0xFF1A0707,
        )

    val warningState =
        status.equals(
            "INCOMPLETE",
            ignoreCase = true,
        ) ||
            status.equals(
                "PERMISSION",
                ignoreCase = true,
            ) ||
            status.equals(
                "STARTING",
                ignoreCase = true,
            ) ||
            status.equals(
                "INVALID",
                ignoreCase = true,
            )

    val statusColor =
        when {
            maintenanceState ->
                WarningAmber

            statusActive ->
                sentinelGreen

            warningState ->
                WarningAmber

            else ->
                SentinelRed
        }

    val cardBorder =
        when {
            maintenanceState ->
                WarningAmber.copy(
                    alpha = 0.82f,
                )

            statusActive ->
                sentinelGreen.copy(
                    alpha = 0.72f,
                )

            warningState ->
                WarningAmber.copy(
                    alpha = 0.72f,
                )

            else ->
                SentinelRed.copy(
                    alpha = 0.62f,
                )
        }

    val modifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom = 11.dp,
                )
                .clickable(
                    onClick = onClick,
                )
        } else {
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom = 11.dp,
                )
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    sentinelCard,
            ),
        border =
            BorderStroke(
                1.dp,
                cardBorder,
            ),
        shape =
            RoundedCornerShape(
                14.dp,
            ),
        modifier =
            modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            maintenanceState ->
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            WarningAmber.copy(
                                                alpha = 0.20f,
                                            ),
                                            sentinelCard,
                                            sentinelCard,
                                        ),
                                )

                            statusActive ->
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            sentinelDeepRed.copy(
                                                alpha = 0.42f,
                                            ),
                                            sentinelCard,
                                            sentinelCard,
                                        ),
                                )

                            else ->
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            sentinelCard,
                                            sentinelCard,
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
                        Modifier.size(
                            44.dp,
                        ),
                    shape =
                        RoundedCornerShape(
                            11.dp,
                        ),
                    color =
                        statusColor.copy(
                            alpha =
                                if (statusActive) {
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
                            imageVector =
                                icon,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    25.dp,
                                ),
                            tint =
                                statusColor,
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
                        fontSize =
                            15.sp,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp,
                            ),
                    )

                    Text(
                        text =
                            if (maintenanceState) {
                                "MAINTENANCE ACTIVE"
                            } else if (statusActive) {
                                "PROTECTION ACTIVE"
                            } else if (warningState) {
                                "ATTENTION REQUIRED"
                            } else {
                                "PROTECTION INACTIVE"
                            },
                        color =
                            if (maintenanceState) {
                                WarningAmber
                            } else if (statusActive) {
                                SentinelRed.copy(
                                    alpha = 0.78f,
                                )
                            } else {
                                MutedText.copy(
                                    alpha = 0.72f,
                                )
                            },
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.4.sp,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                5.dp,
                            ),
                    )

                    Text(
                        text =
                            detail,
                        color =
                            if (maintenanceState) {
                                WarningAmber.copy(
                                    alpha = 0.94f,
                                )
                            } else {
                                MutedText
                            },
                        fontSize =
                            12.sp,
                        lineHeight =
                            16.sp,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.size(
                            8.dp,
                        ),
                )

                FeatureStatusPill(
                    status =
                        status,
                    color =
                        statusColor,
                )

                if (onClick != null) {
                    Spacer(
                        modifier =
                            Modifier.size(
                                7.dp,
                            ),
                    )

                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Filled
                                .KeyboardArrowRight,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                22.dp,
                            ),
                        tint =
                            MutedText.copy(
                                alpha = 0.62f,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureStatusPill(
    status: String,
    color: Color,
) {
    Surface(
        shape =
            RoundedCornerShape(
                20.dp,
            ),
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
            Surface(
                modifier =
                    Modifier.size(
                        7.dp,
                    ),
                shape =
                    CircleShape,
                color =
                    color,
            ) {}

            Spacer(
                modifier =
                    Modifier.size(
                        5.dp,
                    ),
            )

            Text(
                text =
                    status,
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
private fun CoreTrustStrip(
    state: ControllerUiState,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(
                        0xFF0E1510,
                    ),
                    RoundedCornerShape(
                        12.dp,
                    ),
                )
                .padding(
                    horizontal =
                        14.dp,
                    vertical =
                        11.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                Icons.Default.CheckCircle,
            contentDescription =
                null,
            tint =
                SuccessGreen,
            modifier =
                Modifier.size(
                    18.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.size(
                    9.dp,
                ),
        )

        Text(
            text =
                "CORE VERIFIED",
            color =
                SuccessGreen,
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                0.8.sp,
        )

        Spacer(
            modifier =
                Modifier.weight(
                    1f,
                ),
        )

        Text(
            text =
                if (state.deviceOwner) {
                    "HARDENED • PROTOCOL " +
                        (
                            state.protocolVersion
                                ?: ManagementProtocol
                                    .PROTOCOL_VERSION
                            )
                } else {
                    "STANDARD • PROTOCOL " +
                        (
                            state.protocolVersion
                                ?: ManagementProtocol
                                    .PROTOCOL_VERSION
                            )
                },
            color =
                MutedText,
            fontSize =
                10.sp,
        )
    }
}

@Composable
private fun SecurityPanel(
    title: String,
    body: String,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    CardBackground,
            ),
        border =
            BorderStroke(
                1.dp,
                Color(
                    0xFF303237,
                ),
            ),
        shape =
            RoundedCornerShape(
                16.dp,
            ),
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    20.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector =
                    Icons.Default.Security,
                contentDescription =
                    null,
                tint =
                    SentinelRed,
                modifier =
                    Modifier.size(
                        34.dp,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            Text(
                text =
                    title,
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            HorizontalDivider(
                color =
                    Color(
                        0xFF303237,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp,
                    ),
            )

            Text(
                text =
                    body,
                color =
                    MutedText,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp,
                textAlign =
                    TextAlign.Center,
            )
        }
    }
}

private fun connectionTitle(
    state: CoreConnectionState,
): String =
    when (state) {
        CoreConnectionState.IDLE ->
            "INITIALISING"

        CoreConnectionState.SEARCHING ->
            "SEARCHING FOR CORE"

        CoreConnectionState.CONNECTING ->
            "VERIFYING CORE"

        CoreConnectionState.READY ->
            "CORE READY"

        CoreConnectionState.CORE_NOT_FOUND ->
            "CORE NOT FOUND"

        CoreConnectionState.SIGNATURE_FAILED ->
            "CORE TRUST FAILED"

        CoreConnectionState.PROTOCOL_MISMATCH ->
            "PROTOCOL MISMATCH"

        CoreConnectionState.DEVICE_OWNER_MISSING ->
            "DEVICE OWNER MISSING"

        CoreConnectionState.BIND_FAILED ->
            "CORE CONNECTION FAILED"

        CoreConnectionState.CONNECTION_LOST ->
            "CORE CONNECTION LOST"
    }

private fun connectionDescription(
    state: ControllerUiState,
): String {
    state.message
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let {
            return it
        }

    return buildString {
        append(
            if (state.coreFound) {
                "Core discovered"
            } else {
                "Locating the headless Sentinel Core"
            },
        )

        if (
            state.signatureVerified
        ) {
            append(
                "\nSignature verified",
            )
        }

        state.protocolVersion
            ?.let {
                append(
                    "\nProtocol $it",
                )
            }

        when {
            state.deviceOwner ->
                append(
                    "\nHardened Device Owner verified",
                )

            state.adminActive ->
                append(
                    "\nStandard Device Admin active",
                )
        }
    }
}

private fun connectionText(
    state: Int,
): String =
    when (state) {
        ManagementProtocol
            .USB_CONNECTION_DISCONNECTED ->
            "USB disconnected"

        ManagementProtocol
            .USB_CONNECTION_PENDING ->
            "USB classification pending"

        ManagementProtocol
            .USB_CONNECTION_CHARGE_ONLY ->
            "Charge-only connection"

        ManagementProtocol
            .USB_CONNECTION_DATA_CAPABLE ->
            "Data-capable connection"

        else ->
            "USB state unknown"
    }

private fun deadmanStateText(
    state: Int,
): String =
    when (state) {
        ManagementProtocol
            .DEADMAN_STATE_DISABLED ->
            "DISABLED"

        ManagementProtocol
            .DEADMAN_STATE_INVALID ->
            "INVALID"

        ManagementProtocol
            .DEADMAN_STATE_EXPIRED ->
            "EXPIRED"

        ManagementProtocol
            .DEADMAN_STATE_ARMED ->
            "ARMED"

        else ->
            "UNKNOWN"
    }

private fun motionStateText(
    state: Int,
): String =
    when (state) {
        ManagementProtocol
            .MOTION_OPERATIONAL_DISABLED ->
            "DISABLED"

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

private fun formatMaintenanceCountdown(
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
        "%d:%02d:%02d".format(
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

private fun formatDuration(
    milliseconds: Long,
): String {
    val totalSeconds =
        milliseconds
            .coerceAtLeast(
                0L,
            ) /
            1_000L

    val days =
        totalSeconds /
            86_400L

    val hours =
        (
            totalSeconds %
                86_400L
            ) /
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

    return when {
        days > 0L ->
            "${days}d ${hours}h ${minutes}m"

        hours > 0L ->
            "${hours}h ${minutes}m"

        minutes > 0L ->
            "${minutes}m ${seconds}s"

        else ->
            "${seconds}s"
    }
}
