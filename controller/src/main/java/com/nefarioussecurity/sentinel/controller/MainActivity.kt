package com.nefarioussecurity.sentinel.controller

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.nefarioussecurity.sentinel.controller.management.ControllerViewModel
import com.nefarioussecurity.sentinel.controller.ui.SentinelControllerApp
import com.nefarioussecurity.sentinel.controller.ui.theme.SentinelTheme

class MainActivity : ComponentActivity() {

    private val controllerViewModel:
        ControllerViewModel by viewModels()

    private var pendingCoreUninstallPackage:
        String? = null

    private val coreUninstallLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            val corePackageName =
                pendingCoreUninstallPackage

            pendingCoreUninstallPackage =
                null

            if (
                corePackageName != null &&
                !isPackageInstalled(
                    corePackageName,
                )
            ) {
                launchControllerUninstall()
            } else {
                controllerViewModel
                    .cancelRemovalAfterCoreUninstallCancelled()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        setContent {
            SentinelTheme {
                SentinelControllerApp(
                    viewModel =
                        controllerViewModel,
                    onRequestCoreUninstall = {
                        corePackageName ->

                        launchCoreUninstall(
                            corePackageName,
                        )
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        controllerViewModel.start()
    }

    override fun onStop() {
        controllerViewModel.stop()

        super.onStop()
    }

    private fun launchCoreUninstall(
        corePackageName: String,
    ) {
        if (
            corePackageName.isBlank() ||
            corePackageName ==
            packageName
        ) {
            controllerViewModel
                .cancelRemovalAfterCoreUninstallCancelled()

            return
        }

        pendingCoreUninstallPackage =
            corePackageName

        val intent =
            Intent(
                Intent.ACTION_DELETE,
                Uri.parse(
                    "package:$corePackageName",
                ),
            ).apply {
                putExtra(
                    Intent.EXTRA_RETURN_RESULT,
                    true,
                )
            }

        runCatching {
            coreUninstallLauncher.launch(
                intent,
            )
        }.onFailure {
            pendingCoreUninstallPackage =
                null

            controllerViewModel
                .cancelRemovalAfterCoreUninstallCancelled()
        }
    }

    private fun launchControllerUninstall() {
        val intent =
            Intent(
                Intent.ACTION_DELETE,
                Uri.parse(
                    "package:$packageName",
                ),
            ).apply {
                putExtra(
                    Intent.EXTRA_RETURN_RESULT,
                    true,
                )
            }

        runCatching {
            startActivity(
                intent,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(
        targetPackageName: String,
    ): Boolean =
        try {
            packageManager.getPackageInfo(
                targetPackageName,
                0,
            )

            true
        } catch (
            ignored: PackageManager.NameNotFoundException,
        ) {
            false
        }
}
