package com.cyanbridge.glasses

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

// ── HeyCyan BLE UUIDs (from the repo SDK) ───────────────────
object GlassesUUIDs {
    val PRIMARY_SERVICE   = UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")
    val SECONDARY_SERVICE = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")

    // Standard characteristic UUIDs used by glasses
    val WRITE_CHAR        = UUID.fromString("7905FFF2-B5CE-4E99-A40F-4B1E122D00D0")
    val NOTIFY_CHAR       = UUID.fromString("7905FFF1-B5CE-4E99-A40F-4B1E122D00D0")
    val DATA_CHAR         = UUID.fromString("6e40fff2-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY_CHAR_2     = UUID.fromString("6e40fff1-b5a3-f393-e0a9-e50e24dcca9e")

    val CLIENT_CHAR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// ── Commands matching QCSDKCmdCreator from repo ──────────────
object GlassesCommands {
    const val MODE_PHOTO      = 0x01
    const val MODE_VIDEO      = 0x02
    const val MODE_VIDEO_STOP = 0x03
    const val MODE_AUDIO      = 0x04
    const val MODE_AUDIO_STOP = 0x05
    const val MODE_AI_PHOTO   = 0x06
    const val GET_BATTERY     = 0x10
    const val GET_MEDIA_COUNT = 0x11
    const val GET_VERSION     = 0x12
    const val SET_TIME        = 0x13
    const val GET_MAC         = 0x14
}

// ── State ────────────────────────────────────────────────────
sealed class GlassesConnectionState {
    object Disconnected : GlassesConnectionState()
    object Scanning : GlassesConnectionState()
    data class Connecting(val name: String) : GlassesConnectionState()
    data class Connected(val name: String, val address: String) : GlassesConnectionState()
    data class Error(val message: String) : GlassesConnectionState()
}

data class GlassesDevice(val name: String, val address: String, val rssi: Int)

data class GlassesStatus(
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val isRecordingVideo: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val firmwareVersion: String = "",
    val macAddress: String = ""
)

// ── Callbacks ────────────────────────────────────────────────
interface GlassesEventListener {
    fun onPhotoCaptured(bitmap: Bitmap) {}
    fun onAiImageReceived(bitmap: Bitmap) {}
    fun onStatusUpdated(status: GlassesStatus) {}
    fun onError(message: String) {}
}

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow<GlassesConnectionState>(GlassesConnectionState.Disconnected)
    val connectionState: StateFlow<GlassesConnectionState> = _connectionState

    private val _foundDevices = MutableStateFlow<List<GlassesDevice>>(emptyList())
    val foundDevices: StateFlow<List<GlassesDevice>> = _foundDevices

    private val _status = MutableStateFlow(GlassesStatus())
    val status: StateFlow<GlassesStatus> = _status

    var eventListener: GlassesEventListener? = null

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var imageBuffer = mutableListOf<Byte>()
    private val handler = Handler(Looper.getMainLooper())

    // ── SCAN ─────────────────────────────────────────────────
    fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = GlassesConnectionState.Error("Bluetooth is off")
            return
        }
        _foundDevices.value = emptyList()
        _connectionState.value = GlassesConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan without filter first to find all BLE devices, then filter by name
        bleScanner?.startScan(null, settings, scanCallback)

        // Auto-stop scan after 15 seconds
        handler.postDelayed({ stopScan() }, 15_000)
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value is GlassesConnectionState.Scanning) {
            _connectionState.value = GlassesConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            // HeyCyan glasses advertise with names like "HeyCyan", "QCSDK", or similar
            if (!name.contains("HeyCyan", ignoreCase = true) &&
                !name.contains("Cyan", ignoreCase = true) &&
                !name.contains("QC", ignoreCase = true)) return

            val device = GlassesDevice(name, result.device.address, result.rssi)
            val current = _foundDevices.value.toMutableList()
            if (current.none { it.address == device.address }) {
                current.add(device)
                _foundDevices.value = current
            }
        }
    }

    // ── CONNECT ──────────────────────────────────────────────
    fun connect(address: String, name: String) {
        stopScan()
        _connectionState.value = GlassesConnectionState.Connecting(name)
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeCharacteristic = null
        _connectionState.value = GlassesConnectionState.Disconnected
    }

    // ── GATT CALLBACKS ───────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = GlassesConnectionState.Disconnected
                    writeCharacteristic = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            // Find write characteristic
            for (service in gatt.services) {
                val wc = service.getCharacteristic(GlassesUUIDs.WRITE_CHAR)
                if (wc != null) {
                    writeCharacteristic = wc
                    break
                }
            }

            // Enable notifications on all notify characteristics
            for (service in gatt.services) {
                listOf(GlassesUUIDs.NOTIFY_CHAR, GlassesUUIDs.NOTIFY_CHAR_2, GlassesUUIDs.DATA_CHAR).forEach { uuid ->
                    service.getCharacteristic(uuid)?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        char.getDescriptor(GlassesUUIDs.CLIENT_CHAR_CONFIG)?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
            }

            val deviceName = gatt.device.name ?: "HeyCyan Glasses"
            val deviceAddr = gatt.device.address
            _connectionState.value = GlassesConnectionState.Connected(deviceName, deviceAddr)

            // Auto-request battery and media counts on connect
            handler.postDelayed({ getBattery() }, 500)
            handler.postDelayed({ getMediaCount() }, 1000)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            parseResponse(data)
        }
    }

    // ── PARSE GLASS RESPONSES ────────────────────────────────
    private fun parseResponse(data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0].toInt() and 0xFF) {
            GlassesCommands.GET_BATTERY -> {
                val level = data.getOrElse(1) { 0 }.toInt() and 0xFF
                val charging = data.getOrElse(2) { 0 }.toInt() == 1
                _status.value = _status.value.copy(batteryLevel = level, isCharging = charging)
                eventListener?.onStatusUpdated(_status.value)
            }
            GlassesCommands.GET_MEDIA_COUNT -> {
                val photos = data.getOrElse(1) { 0 }.toInt() and 0xFF
                val videos = data.getOrElse(2) { 0 }.toInt() and 0xFF
                val audios = data.getOrElse(3) { 0 }.toInt() and 0xFF
                _status.value = _status.value.copy(
                    photoCount = photos, videoCount = videos, audioCount = audios)
                eventListener?.onStatusUpdated(_status.value)
            }
            GlassesCommands.MODE_PHOTO, GlassesCommands.MODE_AI_PHOTO -> {
                // Image data — accumulate chunks
                imageBuffer.addAll(data.drop(1).toList())
                // Check if this is the final chunk (usually indicated by specific byte pattern)
                if (isImageComplete(data)) {
                    val bytes = imageBuffer.toByteArray()
                    imageBuffer.clear()
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        if (data[0].toInt() == GlassesCommands.MODE_AI_PHOTO)
                            eventListener?.onAiImageReceived(bmp)
                        else
                            eventListener?.onPhotoCaptured(bmp)
                    }
                }
            }
        }
    }

    private fun isImageComplete(chunk: ByteArray): Boolean {
        // JPEG end marker: 0xFF 0xD9
        val last = chunk.takeLast(2)
        return last.size == 2 && last[0] == 0xFF.toByte() && last[1] == 0xD9.toByte()
    }

    // ── COMMANDS ─────────────────────────────────────────────
    fun takePhoto() = sendCommand(byteArrayOf(GlassesCommands.MODE_PHOTO.toByte()))
    fun startVideo() = sendCommand(byteArrayOf(GlassesCommands.MODE_VIDEO.toByte()))
    fun stopVideo() = sendCommand(byteArrayOf(GlassesCommands.MODE_VIDEO_STOP.toByte()))
    fun startAudio() = sendCommand(byteArrayOf(GlassesCommands.MODE_AUDIO.toByte()))
    fun stopAudio() = sendCommand(byteArrayOf(GlassesCommands.MODE_AUDIO_STOP.toByte()))
    fun generateAiImage() = sendCommand(byteArrayOf(GlassesCommands.MODE_AI_PHOTO.toByte()))
    fun getBattery() = sendCommand(byteArrayOf(GlassesCommands.GET_BATTERY.toByte()))
    fun getMediaCount() = sendCommand(byteArrayOf(GlassesCommands.GET_MEDIA_COUNT.toByte()))

    fun syncTime() {
        val now = System.currentTimeMillis() / 1000
        val cmd = byteArrayOf(
            GlassesCommands.SET_TIME.toByte(),
            ((now shr 24) and 0xFF).toByte(),
            ((now shr 16) and 0xFF).toByte(),
            ((now shr 8) and 0xFF).toByte(),
            (now and 0xFF).toByte()
        )
        sendCommand(cmd)
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(cmd: ByteArray) {
        val wc = writeCharacteristic ?: return
        wc.value = cmd
        gatt?.writeCharacteristic(wc)
    }

    fun isConnected() = _connectionState.value is GlassesConnectionState.Connected
    fun isBluetoothEnabled() = bluetoothAdapter?.isEnabled == true
}
