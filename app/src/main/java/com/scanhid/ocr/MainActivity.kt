package com.scanhid.ocr

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.scanhid.ocr.ui.AppRoot
import com.scanhid.ocr.ui.theme.ScanHidOcrTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }.toTypedArray()

        setContent {
            ScanHidOcrTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    if (result.values.all { it }) {
                        viewModel.startHidAdvertising()
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(requiredPermissions)
                }

                AppRoot(viewModel)
            }
        }
    }
}
