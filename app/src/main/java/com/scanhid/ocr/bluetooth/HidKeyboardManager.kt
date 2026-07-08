package com.scanhid.ocr.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

private const val TAG = "ScanHid"

enum class HidConnectionState {
    UNREGISTERED,
    REGISTERED_WAITING_FOR_PC,
    CONNECTED,
}

/**
 * Registers this device as a Bluetooth HID keyboard peripheral and lets the
 * caller "type" a string into whatever has keyboard focus on the paired PC.
 *
 * The PC never runs any companion software: pairing happens the same way as
 * pairing any wireless Bluetooth keyboard (PC Bluetooth settings > Add device),
 * and every character we send arrives at the PC's OS input stack, which routes
 * it to whichever window/field currently has focus - an Excel cell, a text
 * field, a terminal, anything.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested by the caller before this is used
class HidKeyboardManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(HidConnectionState.UNREGISTERED)
    val connectionState: StateFlow<HidConnectionState> = _connectionState

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val immediateExecutor = Executor { command -> command.run() }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered device=${pluggedDevice?.address}")
            _connectionState.value = if (registered) {
                HidConnectionState.REGISTERED_WAITING_FOR_PC
            } else {
                HidConnectionState.UNREGISTERED
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device?.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectedDeviceName.value = device?.name
                    _connectionState.value = HidConnectionState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == connectedDevice) {
                        connectedDevice = null
                        _connectedDeviceName.value = null
                    }
                    _connectionState.value = HidConnectionState.REGISTERED_WAITING_FOR_PC
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            Log.d(TAG, "onServiceConnected: profile=$profile proxy=$proxy")
            if (profile != BluetoothProfile.HID_DEVICE) return
            val device = proxy as? BluetoothHidDevice ?: run {
                Log.e(TAG, "onServiceConnected: proxy was not a BluetoothHidDevice")
                return
            }
            hidDevice = device

            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                "ScanHid OCR Keyboard",
                "Types OCR results into whatever PC app has focus",
                "ScanHidOcr",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReportDescriptor.DESCRIPTOR,
            )

            try {
                val submitted = device.registerApp(
                    sdpSettings,
                    null,
                    null,
                    immediateExecutor,
                    hidCallback,
                )
                Log.d(TAG, "registerApp() call submitted=$submitted (this is not final success - wait for onAppStatusChanged)")
            } catch (e: SecurityException) {
                Log.e(TAG, "registerApp() threw SecurityException - missing BLUETOOTH_CONNECT permission?", e)
            } catch (e: Exception) {
                Log.e(TAG, "registerApp() threw an unexpected exception", e)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected: profile=$profile")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = HidConnectionState.UNREGISTERED
            }
        }
    }

    /** Call once (e.g. from Application/first Activity) to start advertising as a HID keyboard. */
    fun register() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Log.e(TAG, "register(): BluetoothAdapter.getDefaultAdapter() returned null - no Bluetooth hardware?")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "register(): Bluetooth adapter is present but not enabled")
        }
        val requested = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        Log.d(TAG, "register(): getProfileProxy(HID_DEVICE) requested=$requested")
    }

    fun unregister() {
        val device = connectedDevice
        hidDevice?.let { hid ->
            if (device != null) hid.disconnect(device)
            hid.unregisterApp()
        }
        hidDevice = null
        _connectionState.value = HidConnectionState.UNREGISTERED
    }

    /**
     * Sends [text] to the paired PC as keystrokes, character by character, then
     * optionally a trailing key (e.g. Enter/Tab to auto-advance an Excel cell).
     * Characters with no HID mapping (accents, non-Latin scripts, emoji, ...)
     * are silently skipped. Returns false (without sending anything) if there
     * is no PC currently connected.
     */
    suspend fun typeText(text: String, trailingKey: Int? = null): Boolean {
        val hid = hidDevice ?: return false
        val device = connectedDevice ?: return false

        for (char in text) {
            val (modifier, usageCode) = HidKeyCodes.forChar(char) ?: continue
            sendKeyPress(hid, device, modifier, usageCode)
        }

        if (trailingKey != null) {
            sendKeyPress(hid, device, HidKeyCodes.MODIFIER_NONE, trailingKey)
        }
        return true
    }

    private suspend fun sendKeyPress(hid: BluetoothHidDevice, device: BluetoothDevice, modifier: Int, usageCode: Int) {
        val keyDown = ByteArray(HidReportDescriptor.REPORT_SIZE_BYTES)
        keyDown[0] = modifier.toByte()
        keyDown[2] = usageCode.toByte()
        hid.sendReport(device, HidReportDescriptor.KEYBOARD_REPORT_ID.toInt(), keyDown)

        delay(KEY_EVENT_DELAY_MS)

        val keyUp = ByteArray(HidReportDescriptor.REPORT_SIZE_BYTES) // all-zero = all keys released
        hid.sendReport(device, HidReportDescriptor.KEYBOARD_REPORT_ID.toInt(), keyUp)

        delay(KEY_EVENT_DELAY_MS)
    }

    companion object {
        /** Delay between HID reports; too fast and some PC Bluetooth stacks drop keystrokes. */
        private const val KEY_EVENT_DELAY_MS = 15L
    }
}
