package com.nefarioussecurity.sentinel.controller.feature.wipe

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.management.WipeBehaviourUiState
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed

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
fun WipeBehaviourScreen(
    state: WipeBehaviourUiState,
    message: String?,
    onBack: () -> Unit,
    onSetEsimErase: (Boolean) -> Unit,
) {
    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    LaunchedEffect(
        message,
    ) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message,
            )
        }
    }

    Scaffold(
        containerColor =
            Background,
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
                        text =
                            "WIPE BEHAVIOUR",
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
            WipeSummaryCard(
                state =
                    state,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            FactoryResetCard()

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            EsimEraseCard(
                state =
                    state,
                onSetEsimErase =
                    onSetEsimErase,
            )

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

@Composable
private fun WipeSummaryCard(
    state: WipeBehaviourUiState,
) {
    val hardened =
        state.esimEraseAvailable

    val color =
        if (hardened) {
            SentinelGreen
        } else {
            SentinelAmber
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
                        0.65f,
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
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    SentinelDeepRed.copy(
                                        alpha =
                                            0.34f,
                                    ),
                                    SentinelCard,
                                    SentinelCard,
                                ),
                        ),
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
                            "Wipe Behaviour",
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
                            "TRIGGERED WIPE POLICY",
                        color =
                            SentinelRed.copy(
                                alpha =
                                    0.78f,
                            ),
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
                        if (hardened) {
                            "HARDENED"
                        } else {
                            "STANDARD"
                        },
                    color =
                        color,
                )
            }
        }
    }
}

@Composable
private fun FactoryResetCard() {
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
                SentinelGreen.copy(
                    alpha =
                        0.72f,
                ),
            ),
        shape =
            RoundedCornerShape(
                14.dp,
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
                    SentinelGreen.copy(
                        alpha =
                            0.13f,
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
                        modifier =
                            Modifier.size(
                                25.dp,
                            ),
                        tint =
                            SentinelGreen,
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
                        "FACTORY RESET",
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
                        "PRIMARY WIPE ACTION",
                    color =
                        SentinelRed.copy(
                            alpha =
                                0.78f,
                        ),
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
                        "Triggered wipes erase device user data through Sentinel Core.",
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

            StatusPill(
                text =
                    "ALWAYS",
                color =
                    SentinelGreen,
            )
        }
    }
}

@Composable
private fun EsimEraseCard(
    state: WipeBehaviourUiState,
    onSetEsimErase: (Boolean) -> Unit,
) {
    val color =
        when {
            state.esimEraseEnabled ->
                SentinelGreen

            !state.esimEraseAvailable ->
                SentinelAmber

            else ->
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
                        0.65f,
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
                        if (
                            state.esimEraseEnabled
                        ) {
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
                                Icons.Default.Security,
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
                            "ERASE INSTALLED eSIMs",
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
                            if (
                                state.esimEraseAvailable
                            ) {
                                "HARDENED WIPE OPTION"
                            } else {
                                "HARDENED MODE REQUIRED"
                            },
                        color =
                            if (
                                state.esimEraseAvailable
                            ) {
                                SentinelRed.copy(
                                    alpha =
                                        0.78f,
                                )
                            } else {
                                SentinelAmber
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
                            if (
                                state.esimEraseAvailable
                            ) {
                                "Include installed eSIM profiles when Sentinel performs a supported Hardened wipe."
                            } else {
                                "Available only when Sentinel Core has Hardened Device Owner authority."
                            },
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
                        state.esimEraseEnabled,
                    onCheckedChange = {
                        enabled ->

                        onSetEsimErase(
                            enabled,
                        )
                    },
                    colors =
                        sentinelSwitchColors(),
                )
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
                    "eSIM erasure defaults to OFF. Standard mode cannot enable it. " +
                        "Sentinel Core remains authoritative for wipe configuration.",
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
