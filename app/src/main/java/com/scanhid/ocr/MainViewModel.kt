package com.scanhid.ocr

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanhid.ocr.bluetooth.BluetoothSppManager
import com.scanhid.ocr.bluetooth.PairedDeviceInfo
import com.scanhid.ocr.bluetooth.PcConnectionState
import com.scanhid.ocr.ocr.OcrProcessor
import com.scanhid.ocr.storage.GallerySaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AppScreen { CAPTURE, REVIEW, CONNECTION }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sppManager = BluetoothSppManager(application)
    private val ocrProcessor = OcrProcessor()

    val pcConnectionState: StateFlow<PcConnectionState> = sppManager.connectionState
    val connectedDeviceName: StateFlow<String?> = sppManager.connectedDeviceName
    val connectionError: StateFlow<String?> = sppManager.lastError

    private val _bondedDevices = MutableStateFlow<List<PairedDeviceInfo>>(emptyList())
    val bondedDevices: StateFlow<List<PairedDeviceInfo>> = _bondedDevices

    private val _currentScreen = MutableStateFlow(AppScreen.CAPTURE)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _ocrConfidence = MutableStateFlow<Float?>(null)
    val ocrConfidence: StateFlow<Float?> = _ocrConfidence

    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr

    private val _lastSendSucceeded = MutableStateFlow<Boolean?>(null)
    val lastSendSucceeded: StateFlow<Boolean?> = _lastSendSucceeded

    private val _gallerySaved = MutableStateFlow<Boolean?>(null)
    val gallerySaved: StateFlow<Boolean?> = _gallerySaved

    fun goToConnectionScreen() {
        refreshBondedDevices()
        _currentScreen.value = AppScreen.CONNECTION
    }

    fun goToCaptureScreen() {
        _currentScreen.value = AppScreen.CAPTURE
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = sppManager.bondedDevices()
    }

    fun connectToDevice(address: String) {
        viewModelScope.launch {
            sppManager.connectTo(address)
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _currentScreen.value = AppScreen.REVIEW
        _isProcessingOcr.value = true
        _lastSendSucceeded.value = null
        _gallerySaved.value = null
        viewModelScope.launch {
            val result = runCatching { ocrProcessor.recognize(bitmap) }.getOrNull()
            _recognizedText.value = result?.text.orEmpty()
            _ocrConfidence.value = result?.confidence
            _isProcessingOcr.value = false
        }
    }

    fun onTextEdited(newText: String) {
        _recognizedText.value = newText
    }

    fun retakePhoto() {
        _capturedBitmap.value = null
        _recognizedText.value = ""
        _ocrConfidence.value = null
        _gallerySaved.value = null
        _currentScreen.value = AppScreen.CAPTURE
    }

    /**
     * Called after the user confirms the "send this to the PC?" approval dialog. Sends
     * the text to the PC and, independent of whether that succeeds, saves the photo into
     * the Gallery with the approved text embedded as EXIF metadata - the archived record
     * is worth keeping even if the PC happened to be disconnected at that moment.
     */
    fun approveAndSend() {
        val approvedText = _recognizedText.value
        val bitmap = _capturedBitmap.value
        viewModelScope.launch {
            _lastSendSucceeded.value = null
            // Trailing tab so, e.g., an Excel selection advances to the next cell.
            val succeeded = sppManager.sendText(approvedText + "\t")
            _lastSendSucceeded.value = succeeded

            if (bitmap != null) {
                _gallerySaved.value = GallerySaver.save(getApplication(), bitmap, approvedText)
            }

            if (succeeded) retakePhoto()
        }
    }

    override fun onCleared() {
        sppManager.disconnect()
        super.onCleared()
    }
}
