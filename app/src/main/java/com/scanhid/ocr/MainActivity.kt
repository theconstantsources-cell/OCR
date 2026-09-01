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
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Pre-scoped-storage (Android 9): needed to save scans into the Gallery.
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                // Needed for adapter.cancelDiscovery() in BluetoothSppManager.connectTo() -
                // without it, connecting throws a SecurityException on Android 12+.
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()

        setContent {
            ScanHidOcrTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { /* Bluetooth connection is only attempted once the user picks a device
                       on the Connection screen, so there's nothing to kick off here. */ }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(requiredPermissions)
                }

                AppRoot(viewModel)
            }
        }
    }
}
