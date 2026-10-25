package com.nefarioussecurity.sentinel.controller.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefarioussecurity.sentinel.controller.BuildConfig
import com.nefarioussecurity.sentinel.controller.R
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelRed

private val Background =
    Color(
        0xFF0B0C0C,
    )

private val CardBackground =
    Color(
        0xFF101212,
    )

private val CardBorder =
    Color(
        0xFF303232,
    )

private val MutedText =
    Color(
        0xFFA9ADB3,
    )

private val BetaAmber =
    Color(
        0xFFE0A94A,
    )

private const val OBLIVION_V2_URL =
    "https://github.com/lethe-labs/oblivion-v2"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val uriHandler =
        LocalUriHandler.current

    Scaffold(
        containerColor =
            Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            "ABOUT SENTINEL",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            1.2.sp,
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
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            CardBackground,
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        CardBorder,
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
                    Row {
                        Icon(
                            imageVector =
                                Icons.Default.Info,
                            contentDescription =
                                null,
                            tint =
                                SentinelRed,
                        )

                        Spacer(
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        5.dp,
                                ),
                        )

                        Column {
                            Text(
                                text =
                                    "Nefarious Sentinel",
                                color =
                                    Color.White,
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    20.sp,
                            )

                            Text(
                                text =
                                    BuildConfig.VERSION_NAME,
                                color =
                                    MutedText,
                                fontSize =
                                    13.sp,
                            )
                        }
                    }

                    if (BuildConfig.BETA_BUILD) {
                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp,
                                ),
                        )

                        Surface(
                            color =
                                BetaAmber.copy(
                                    alpha =
                                        0.14f,
                                ),
                            border =
                                BorderStroke(
                                    1.dp,
                                    BetaAmber.copy(
                                        alpha =
                                            0.75f,
                                    ),
                                ),
                            shape =
                                RoundedCornerShape(
                                    10.dp,
                                ),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        12.dp,
                                    ),
                            ) {
                                Text(
                                    text =
                                        "BETA BUILD",
                                    color =
                                        BetaAmber,
                                    fontWeight =
                                        FontWeight.Bold,
                                    letterSpacing =
                                        1.1.sp,
                                )

                                Text(
                                    text =
                                        "Expires ${BuildConfig.BETA_EXPIRY_DISPLAY}",
                                    color =
                                        Color.White,
                                    fontSize =
                                        13.sp,
                                    modifier =
                                        Modifier.padding(
                                            top =
                                                4.dp,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            AboutSection(
                title =
                    "ABOUT",
                body =
                    "Nefarious Sentinel is an open-source Android security application developed by Nefarious Security.\n\n" +
                        "This is a modified work derived from Oblivion V2. " +
                        "The Sentinel modifications represented by this release were made in 2026.",
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            AboutSection(
                title =
                    "ACKNOWLEDGEMENTS",
                body =
                    "Original project: Oblivion V2\n" +
                        "Original developers: Lethe Labs\n\n" +
                        "Nefarious Sentinel is not affiliated with or endorsed by Lethe Labs. " +
                        "We thank the Oblivion V2 developers for making their work available as free and open-source software.",
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            AboutSection(
                title =
                    "OPEN SOURCE",
                body =
                    "Nefarious Sentinel is distributed under the GNU Affero General Public License v3.0 (AGPL-3.0).\n\n" +
                        "Corresponding source code is supplied with this beta distribution. " +
                        "You may redistribute and modify the covered work subject to the terms of the license.",
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            OutlinedButton(
                onClick =
                    onOpenLicense,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Description,
                    contentDescription =
                        null,
                )

                Spacer(
                    modifier =
                        Modifier.padding(
                            horizontal =
                                4.dp,
                        ),
                )

                Text(
                    "VIEW GNU AGPL v3 LICENSE",
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            OutlinedButton(
                onClick = {
                    uriHandler.openUri(
                        OBLIVION_V2_URL,
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Code,
                    contentDescription =
                        null,
                )

                Spacer(
                    modifier =
                        Modifier.padding(
                            horizontal =
                                4.dp,
                        ),
                )

                Text(
                    "OBLIVION V2 SOURCE",
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            AboutSection(
                title =
                    "NO WARRANTY",
                body =
                    "This software is provided without warranty to the extent permitted by applicable law. " +
                        "See the GNU AGPL v3 license for the complete warranty disclaimer and terms.",
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            AboutSection(
                title =
                    "BUILD INFORMATION",
                body =
                    "Version: ${BuildConfig.VERSION_NAME}\n" +
                        "Channel: ${BuildConfig.BUILD_CHANNEL}" +
                        (if (BuildConfig.BETA_BUILD) {
                            "\nExpiry: ${BuildConfig.BETA_EXPIRY_DISPLAY}"
                        } else {
                            ""
                        }),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
) {
    val context =
        LocalContext.current

    val licenseText =
        remember {
            context.resources
                .openRawResource(
                    R.raw.agpl_3_0,
                )
                .bufferedReader()
                .use {
                    it.readText()
                }
        }

    Scaffold(
        containerColor =
            Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            "GNU AGPL v3",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
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

        Text(
            text =
                licenseText,
            color =
                Color.White,
            fontSize =
                12.sp,
            lineHeight =
                17.sp,
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
                        18.dp,
                    ),
        )
    }
}

@Composable
private fun AboutSection(
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
                CardBorder,
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
                    16.dp,
                ),
        ) {
            Text(
                text =
                    title,
                color =
                    SentinelRed,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    13.sp,
                letterSpacing =
                    1.1.sp,
            )

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
                    MutedText,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp,
            )
        }
    }
}
