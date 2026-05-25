package com.diaznet.osmandsmartcraft

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import java.util.UUID

@SuppressLint("MissingPermission")
class SmartCraftService : Service() {

    companion object {
        private const val TAG = "SmartCraftService"
        private const val CHANNEL_ID = "smartcraft_channel"
        private const val NOTIFICATION_ID = 1
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val MAX_LOG_LINES = 200
        private const val STALE_TIMEOUT_MS = 10_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        @Volatile var isRunning = false; private set
        @Volatile var simulatorMode = false
        @Volatile var osmAndConnected = false; private set
        @Volatile var bleStatus = ""; private set
        @Volatile var bleDeviceName: String? = null; private set
        @Volatile var bleRssi: Int? = null; private set
        @Volatile var fileLogging = false

        val debugLog = mutableListOf<String>()
        private var fileLogger: java.io.FileWriter? = null

        fun startFileLogging(context: android.content.Context) {
            try {
                val dir = context.getExternalFilesDir(null)
                val ts = android.text.format.DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis())
                val file = java.io.File(dir, "smartcraft_log_$ts.txt")
                fileLogger = java.io.FileWriter(file, true)
                fileLogger?.write("=== SmartCraft log started $ts ===\n")
                fileLogger?.flush()
                fileLogging = true
                log("BLE", "File logging started: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start file logging", e)
            }
        }

        fun stopFileLogging() {
            try {
                fileLogger?.write("=== Log ended ===\n")
                fileLogger?.close()
            } catch (_: Exception) {}
            fileLogger = null
            fileLogging = false
        }

        fun log(category: String, msg: String) {
            val line = "${android.text.format.DateFormat.format("HH:mm:ss.SSS", System.currentTimeMillis())} [$category] $msg"
            synchronized(debugLog) {
                debugLog.add(line)
                if (debugLog.size > MAX_LOG_LINES) debugLog.removeAt(0)
            }
            fileLogger?.let {
                try { it.write(line + "\n"); it.flush() } catch (_: Exception) {}
            }
            Log.i(TAG, "[$category] $msg")
        }

        private fun log(msg: String) = log("BLE", msg)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val parser = SmartCraftParser()
    private lateinit var osmAndBridge: OsmAndBridge
    private var wakeLock: PowerManager.WakeLock? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private var reconnectAttempt = 0
    private var lastDataTimestamp = 0L
    private var stalenessJob: Job? = null

    private val dataCharUuids = listOf(
        SmartCraftParser.RPM_UUID, SmartCraftParser.COOLANT_TEMP_UUID,
        SmartCraftParser.VOLTAGE_UUID, SmartCraftParser.FUEL_USED_UUID,
        SmartCraftParser.RUNTIME_UUID, SmartCraftParser.FUEL_FLOW_UUID,
        SmartCraftParser.FUEL_LEVEL_UUID, SmartCraftParser.GEAR_UUID,
        SmartCraftParser.OIL_PRESSURE_UUID, SmartCraftParser.BLOCK_PRESSURE_UUID,
        SmartCraftParser.OIL_TEMP_UUID, SmartCraftParser.SEAWATER_TEMP_UUID,
    )

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        bleStatus = "Starting"
        createNotificationChannel()
        osmAndBridge = OsmAndBridge(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        osmAndBridge.connect()

        if (simulatorMode) {
            bleStatus = "Simulator"
            log("SIM", "Simulator mode started")
            startSimulator()
        } else {
            acquireWakeLock()
            startBleScan()
            startStalenessMonitor()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        osmAndConnected = false
        bleStatus = ""
        bleDeviceName = null
        bleRssi = null
        scope.cancel()
        if (!simulatorMode) {
            stopBleScan()
            bluetoothGatt?.close()
            releaseWakeLock()
        }
        osmAndBridge.disconnect()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCraft::BLE").apply { acquire() }
        log("BLE", "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startStalenessMonitor() {
        stalenessJob = scope.launch {
            while (isActive) {
                delay(2000)
                if (lastDataTimestamp > 0 && System.currentTimeMillis() - lastDataTimestamp > STALE_TIMEOUT_MS) {
                    osmAndBridge.pushStale()
                    log("DATA", "Values stale (no data for ${STALE_TIMEOUT_MS / 1000}s)")
                    lastDataTimestamp = 0
                }
            }
        }
    }

    private fun startSimulator() {
        updateNotification("Simulator running")
        scope.launch {
            var tick = 0
            while (isActive) {
                osmAndConnected = osmAndBridge.isOsmAndConnected()
                if (osmAndConnected) {
                    val data = SmartCraftData(
                        rpm = 800 + (kotlin.math.sin(tick * 0.1) * 400).toInt(),
                        coolantTempC = 72f + (tick % 10),
                        voltageV = 12.4f + (kotlin.math.sin(tick * 0.05) * 0.5).toFloat(),
                        fuelUsed = 1000 + tick * 2,
                        runtimeMin = 1234 + tick,
                        fuelFlowLph = 5.0f + (kotlin.math.sin(tick * 0.2) * 3).toFloat(),
                        fuelLevelPct = 75f - (tick % 50) * 0.5f,
                        gear = (tick / 10) % 3,
                        oilPressureKpa = 350f + (kotlin.math.sin(tick * 0.15) * 50).toFloat(),
                        blockPressureKpa = 100f + (kotlin.math.sin(tick * 0.12) * 20).toFloat(),
                        oilTempC = 85f + (kotlin.math.sin(tick * 0.08) * 10).toFloat(),
                        seawaterTempC = 18f + (kotlin.math.sin(tick * 0.03) * 4).toFloat()
                    )
                    osmAndBridge.updateData(data)
                    if (tick % 10 == 0) log("SIM", "RPM=${data.rpm} V=%.1f T=%.0f".format(data.voltageV, data.coolantTempC))
                } else {
                    if (tick % 5 == 0) log("SIM", "Waiting for OsmAnd connection...")
                }
                tick++
                delay(1000)
            }
        }
    }

    private fun startBleScan() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: run { log("Bluetooth not available"); bleStatus = "No Bluetooth"; return }
        val scanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
        bleStatus = "Scanning"
        log("BLE scan started")
        updateNotification("Scanning for gateway...")
    }

    private fun stopBleScan() {
        if (!scanning) return
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            val address = device.address ?: return
            if (name == "VVM_${address.replace(":", "")}") {
                stopBleScan()
                bleRssi = result.rssi
                log("Found device: $name ($address) RSSI=${result.rssi}")
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("BLE scan failed: error $errorCode")
            bleStatus = "Scan failed"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bleDeviceName = device.name
        bleStatus = "Connecting"
        updateNotification("Connecting to ${device.name}...")
        bluetoothGatt = device.connectGatt(this, true, gattCallback)
    }

    private fun scheduleReconnect(gatt: BluetoothGatt?, status: Int) {
        val recoverable = status == BluetoothGatt.GATT_SUCCESS || status == 8 || status == 19

        if (gatt != null && recoverable && bleDeviceName != null) {
            bleStatus = "Waiting for gateway"
            log("Auto-reconnect active (status=$status), waiting for gateway...")
            updateNotification("Waiting for ${bleDeviceName}...")
        } else {
            gatt?.close()
            bluetoothGatt = null
            reconnectAttempt++
            val delayMs = (3000L * (1L shl (reconnectAttempt - 1).coerceAtMost(3))).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            bleStatus = "Reconnecting (${delayMs / 1000}s)"
            log("Reconnect attempt $reconnectAttempt in ${delayMs}ms (status=$status)")
            updateNotification("Reconnecting in ${delayMs / 1000}s...")
            scope.launch { delay(delayMs); if (isActive) startBleScan() }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempt = 0
                    bleStatus = "Connected"
                    log("Connected to gateway (status=$status)")
                    updateNotification("Connected to ${gatt.device.name}")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    bleStatus = "Disconnected"
                    log("Disconnected from gateway (status=$status)")
                    scheduleReconnect(gatt, status)
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) bleRssi = rssi
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val sdpService = gatt.getService(UUID.fromString(SmartCraftParser.SDP_SERVICE_UUID))
            val sdpChar = sdpService?.getCharacteristic(UUID.fromString(SmartCraftParser.SDP_CHARACTERISTIC_UUID))
            if (sdpChar != null) {
                sdpChar.value = SmartCraftParser.ENABLE_DATA_CMD
                gatt.writeCharacteristic(sdpChar)
                log("SDP enable sent")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == UUID.fromString(SmartCraftParser.SDP_CHARACTERISTIC_UUID)) {
                subscribeToNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val uuid = characteristic.uuid.toString()
            val data = characteristic.value ?: return
            lastDataTimestamp = System.currentTimeMillis()
            log("DATA", "${uuid.substring(4, 8)}: ${data.joinToString(" ") { "%02X".format(it) }}")
            val parsed = parser.parseCharacteristic(uuid, data)
            osmAndConnected = osmAndBridge.isOsmAndConnected()
            osmAndBridge.updateData(parsed)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(gatt)
        }
    }

    private fun startRssiPolling() {
        scope.launch { while (isActive) { bluetoothGatt?.readRemoteRssi(); delay(5000) } }
    }

    private var subscriptionIndex = 0

    private fun subscribeToNotifications(gatt: BluetoothGatt) {
        subscriptionIndex = 0
        subscribeNext(gatt)
    }

    private fun subscribeNext(gatt: BluetoothGatt) {
        if (subscriptionIndex >= dataCharUuids.size) {
            log("All ${dataCharUuids.size} characteristics subscribed")
            bleStatus = "Receiving"
            updateNotification("Receiving data from ${gatt.device.name}")
            startRssiPolling()
            return
        }
        val dataService = gatt.getService(UUID.fromString(SmartCraftParser.DATA_SERVICE_UUID)) ?: return
        val characteristic = dataService.getCharacteristic(UUID.fromString(dataCharUuids[subscriptionIndex]))
        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString(CCCD_UUID))
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }
        subscriptionIndex++
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "SmartCraft Connection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartCraft for OsmAnd")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }
}
