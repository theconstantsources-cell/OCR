package com.scanhid.ocr

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanhid.ocr.bluetooth.HidConnectionState
import com.scanhid.ocr.bluetooth.HidKeyCodes
import com.scanhid.ocr.bluetooth.HidKeyboardManager
import com.scanhid.ocr.ocr.OcrProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AppScreen { CAPTURE, REVIEW, CONNECTION }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val hidKeyboardManager = HidKeyboardManager(application)
    private val ocrProcessor = OcrProcessor()

    val hidConnectionState: StateFlow<HidConnectionState> = hidKeyboardManager.connectionState
    val connectedDeviceName: StateFlow<String?> = hidKeyboardManager.connectedDeviceName

    private val _currentScreen = MutableStateFlow(AppScreen.CAPTURE)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr

    private val _lastSendSucceeded = MutableStateFlow<Boolean?>(null)
    val lastSendSucceeded: StateFlow<Boolean?> = _lastSendSucceeded

    fun startHidAdvertising() = hidKeyboardManager.register()

    fun goToConnectionScreen() {
        _currentScreen.value = AppScreen.CONNECTION
    }

    fun goToCaptureScreen() {
        _currentScreen.value = AppScreen.CAPTURE
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _currentScreen.value = AppScreen.REVIEW
        _isProcessingOcr.value = true
        _lastSendSucceeded.value = null
        viewModelScope.launch {
            val text = runCatching { ocrProcessor.recognize(bitmap) }.getOrDefault("")
            _recognizedText.value = text
            _isProcessingOcr.value = false
        }
    }

    fun onTextEdited(newText: String) {
        _recognizedText.value = newText
    }

    fun retakePhoto() {
        _capturedBitmap.value = null
        _recognizedText.value = ""
        _currentScreen.value = AppScreen.CAPTURE
    }

    /** Called after the user confirms the "send this to the PC?" approval dialog. */
    fun approveAndSend() {
        val text = _recognizedText.value
        viewModelScope.launch {
            _lastSendSucceeded.value = null
            val succeeded = runCatching {
                hidKeyboardManager.typeText(text, trailingKey = HidKeyCodes.KEYCODE_TAB)
            }.getOrDefault(false)
            _lastSendSucceeded.value = succeeded
            if (succeeded) retakePhoto()
        }
    }

    override fun onCleared() {
        hidKeyboardManager.unregister()
        super.onCleared()
    }
}
