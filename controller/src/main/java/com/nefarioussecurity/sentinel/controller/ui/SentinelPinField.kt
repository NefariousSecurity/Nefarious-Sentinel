package com.nefarioussecurity.sentinel.controller.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.runtimebridge.api.ManagementProtocol

/**
 * Sentinel's standard management-PIN entry field.
 *
 * Security / UX behaviour:
 *
 * - PIN is masked by default;
 * - the eye must be pressed and held to reveal the PIN;
 * - releasing or cancelling the gesture immediately masks it again;
 * - reveal state exists only in Compose memory and is never persisted;
 * - leaving the screen destroys the reveal state;
 * - all management PIN fields use the same numeric and maximum-length rules.
 */
@Composable
internal fun SentinelPinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember {
        mutableStateOf(
            false,
        )
    }

    OutlinedTextField(
        value =
            value,
        onValueChange = {
            candidate ->

            if (
                candidate.length <=
                ManagementProtocol.PIN_MAX_LENGTH &&
                candidate.all {
                    it.isDigit()
                }
            ) {
                onValueChange(
                    candidate,
                )
            }
        },
        label = {
            Text(
                label,
            )
        },
        singleLine =
            true,
        visualTransformation =
            if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.NumberPassword,
                imeAction =
                    imeAction,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onDone()
                },
            ),
        trailingIcon = {
            Box(
                modifier =
                    Modifier
                        .size(
                            48.dp,
                        )
                        .pointerInput(
                            Unit,
                        ) {
                            detectTapGestures(
                                onPress = {
                                    revealed =
                                        true

                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        /*
                                         * Also runs when Android cancels the
                                         * gesture because focus/window state
                                         * changes.
                                         */
                                        revealed =
                                            false
                                    }
                                },
                            )
                        },
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (revealed) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                    contentDescription =
                        if (revealed) {
                            "Release to hide Sentinel PIN"
                        } else {
                            "Press and hold to reveal Sentinel PIN"
                        },
                )
            }
        },
        modifier =
            modifier,
    )
}
