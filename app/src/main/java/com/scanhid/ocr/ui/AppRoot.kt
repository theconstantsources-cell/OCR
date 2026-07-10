package com.scanhid.ocr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.scanhid.ocr.AppScreen
import com.scanhid.ocr.MainViewModel

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val screen by viewModel.currentScreen.collectAsState()

    when (screen) {
        AppScreen.CAPTURE -> CaptureScreen(viewModel)
        AppScreen.REVIEW -> ReviewScreen(viewModel)
        AppScreen.CONNECTION -> ConnectionScreen(viewModel)
        AppScreen.HISTORY -> HistoryScreen(viewModel)
    }
}
