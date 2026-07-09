package com.scanhid.ocr.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "ScanHidSpp"

enum class PcConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class PairedDeviceInfo(val name: String, val address: String)

/**
 * Connects to a paired Windows PC over plain Bluetooth RFCOMM (Serial Port Profile) and
 * streams recognized text to it. The PC runs a small companion program (see
 * windows-companion/) that receives the text and types it out locally using Windows'
 * own keystroke injection API - wherever the cursor/focus currently is.
 *
 * This replaced an earlier approach where the phone pretended to be a Bluetooth HID
 * keyboard, which requires a fussier secure-pairing handshake that some Android
 * Bluetooth chipsets don't reliably complete with Windows. Plain RFCOMM pairing (not
 * the keyboard/HID kind) is a much more universally supported flow - the phone still
 * needs to be paired/bonded with the PC first via normal Bluetooth settings, same as
 * before, but without the input-device-specific pairing quirks.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested by the caller before this is used
class BluetoothSppManager(private val context: Context) {

    private val _connectionState = MutableStateFlow(PcConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PcConnectionState> = _connectionState

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var socket: BluetoothSocket? = null
    private val connectMutex = Mutex()

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun bondedDevices(): List<PairedDeviceInfo> {
        val adapter = adapter ?: return emptyList()
        return adapter.bondedDevices.orEmpty().map { PairedDeviceInfo(it.name ?: it.address, it.address) }
    }

    suspend fun connectTo(address: String) {
        connectMutex.withLock {
            _connectionState.value = PcConnectionState.CONNECTING
            _lastError.value = null
            Log.d(TAG, "connectTo: starting, address=$address")

            withContext(Dispatchers.IO) {
                // Deliberately one try/catch around the *entire* body (including reading the
                // adapter and bonded-device list), not just the socket connect - any exception
                // here, including a permission-related SecurityException, must be caught and
                // surfaced instead of silently escaping this coroutine unseen.
                try {
                    val adapter = adapter
                        ?: throw IllegalStateException("Bluetooth isn't available on this device")
                    val device = adapter.bondedDevices.orEmpty().find { it.address == address }
                        ?: throw IllegalStateException("That device is no longer paired - re-pair it in Windows Bluetooth settings")

                    adapter.cancelDiscovery()
                    val newSocket = openSocket(device)
                    socket = newSocket
                    _connectedDeviceName.value = device.name
                    _connectionState.value = PcConnectionState.CONNECTED
                    Log.d(TAG, "Connected to ${device.name}")
                } catch (e: Throwable) {
                    val reason = "${e.javaClass.simpleName}: ${e.message}"
                    Log.e(TAG, "connectTo failed - $reason", e)
                    _lastError.value = "Couldn't connect ($reason). Make sure the AI ScanHid receiver program is running on the PC."
                    _connectionState.value = PcConnectionState.DISCONNECTED
                    socket = null
                }
            }
        }
    }

    /**
     * Connects to [device]'s RFCOMM service. Tries the standard, documented API first
     * (SDP lookup by UUID), which works on many device/stack combinations. If that fails -
     * a known issue when an Android client connects to a non-Android RFCOMM server, showing
     * up as "read failed, socket might closed or timeout, read ret: -1" - falls back to
     * connecting directly to a fixed RFCOMM channel number via a hidden API, skipping the
     * SDP lookup entirely. Channel 1 is the conventional default for a single-service SPP
     * setup like the Windows companion program, so it's tried first in the fallback loop.
     */
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        runCatching {
            val standardSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            standardSocket.connect()
            return standardSocket
        }.onFailure { e ->
            Log.w(TAG, "Standard createRfcommSocketToServiceRecord failed (${e.javaClass.simpleName}), trying fixed channels", e)
        }

        var lastError: Exception? = null
        for (channel in 1..30) {
            try {
                val fallbackSocket = createRfcommSocketOnChannel(device, channel)
                fallbackSocket.connect()
                Log.d(TAG, "Connected via fallback RFCOMM channel $channel")
                return fallbackSocket
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No RFCOMM channel accepted a connection")
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createRfcommSocketOnChannel(device: BluetoothDevice, channel: Int): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, channel) as BluetoothSocket
    }

    /** Sends [text] to the PC to be typed out. Returns false if not currently connected. */
    suspend fun sendText(text: String): Boolean = withContext(Dispatchers.IO) {
        val currentSocket = socket
        if (currentSocket == null) {
            false
        } else {
            runCatching {
                val payload = text.toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
                currentSocket.outputStream.write(header)
                currentSocket.outputStream.write(payload)
                currentSocket.outputStream.flush()
                true
            }.getOrElse { e ->
                Log.e(TAG, "sendText failed - connection likely dropped", e)
                _connectionState.value = PcConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                socket = null
                false
            }
        }
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        _connectionState.value = PcConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    companion object {
        // Must match Program.cs's ServiceUuid on the Windows companion app exactly.
        val SPP_UUID: UUID = UUID.fromString("7d2f6c1a-9b3e-4a5d-8f2c-1e6a9d4b7c3f")
    }
}
