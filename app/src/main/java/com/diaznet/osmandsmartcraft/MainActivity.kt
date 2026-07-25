package com.diaznet.osmandsmartcraft

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var bleStatusText: TextView
    private lateinit var osmandStatus: TextView
    private lateinit var openOsmand: TextView
    private lateinit var toggleButton: Button
    private lateinit var autoStartSwitch: Switch
    private lateinit var simulatorSwitch: Switch
    private lateinit var osmandTargetSpinner: Spinner
    private lateinit var tempUnitBtn: Button
    private lateinit var pressureUnitBtn: Button
    private lateinit var flowUnitBtn: Button
    private lateinit var debugSwitch: Switch
    private lateinit var fileLogSwitch: Switch
    private lateinit var fileLogPath: TextView
    private lateinit var debugPanel: LinearLayout
    private lateinit var debugScroll: ScrollView
    private lateinit var debugLog: TextView
    private lateinit var filterBle: Switch
    private lateinit var filterAidl: Switch
    private lateinit var filterSim: Switch
    private lateinit var filterData: Switch
    private lateinit var versionText: TextView
    private lateinit var githubLink: TextView

    private val prefs by lazy { getSharedPreferences("smartcraft", MODE_PRIVATE) }
    private lateinit var unitPrefs: UnitPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var refreshing = false

    private val osmandTargetOptions = listOf("Auto", "OsmAnd", "OsmAnd+")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        bleStatusText = findViewById(R.id.ble_status)
        osmandStatus = findViewById(R.id.osmand_status)
        openOsmand = findViewById(R.id.open_osmand)
        toggleButton = findViewById(R.id.toggle_button)
        autoStartSwitch = findViewById(R.id.auto_start_switch)
        simulatorSwitch = findViewById(R.id.simulator_switch)
        osmandTargetSpinner = findViewById(R.id.osmand_target_spinner)
        tempUnitBtn = findViewById(R.id.temp_unit_btn)
        pressureUnitBtn = findViewById(R.id.pressure_unit_btn)
        flowUnitBtn = findViewById(R.id.flow_unit_btn)
        debugSwitch = findViewById(R.id.debug_switch)
        fileLogSwitch = findViewById(R.id.file_log_switch)
        fileLogPath = findViewById(R.id.file_log_path)
        debugPanel = findViewById(R.id.debug_panel)
        debugScroll = findViewById(R.id.debug_scroll)
        debugLog = findViewById(R.id.debug_log)
        filterBle = findViewById(R.id.filter_ble)
        filterAidl = findViewById(R.id.filter_aidl)
        filterSim = findViewById(R.id.filter_sim)
        filterData = findViewById(R.id.filter_data)
        versionText = findViewById(R.id.version_text)
        githubLink = findViewById(R.id.github_link)

        val pkgInfo = packageManager.getPackageInfo(packageName, 0)
        versionText.text = "v${pkgInfo.versionName}"
        githubLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/diaznet/osmand-smartcraft")))
        }

        unitPrefs = UnitPrefs(this)

        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", false)
        autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_start", checked).apply()
        }

        simulatorSwitch.isChecked = prefs.getBoolean("simulator", false)
        simulatorSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("simulator", checked).apply()
            SmartCraftService.simulatorMode = checked
        }
        SmartCraftService.simulatorMode = simulatorSwitch.isChecked

        setupOsmandTargetSpinner()

        openOsmand.setOnClickListener {
            val pkg = resolveOsmAndPackage()
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) startActivity(intent)
        }

        setupUnitButtons()

        debugSwitch.setOnCheckedChangeListener { _, checked ->
            debugPanel.visibility = if (checked) View.VISIBLE else View.GONE
        }

        fileLogSwitch.isChecked = SmartCraftService.fileLogging
        fileLogSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                SmartCraftService.startFileLogging(this)
                fileLogPath.text = "→ ${getExternalFilesDir(null)?.absolutePath}"
                fileLogPath.visibility = View.VISIBLE
            } else {
                SmartCraftService.stopFileLogging()
                fileLogPath.visibility = View.GONE
            }
        }

        toggleButton.setOnClickListener { if (checkPermissions()) toggle() }

        if (autoStartSwitch.isChecked && !SmartCraftService.isRunning) {
            if (checkPermissions()) start()
        }

        updateUi()
    }

    override fun onResume() { super.onResume(); startRefresh() }
    override fun onPause() { super.onPause(); stopRefresh() }

    private fun toggle() { if (SmartCraftService.isRunning) stop() else start() }

    private fun setupOsmandTargetSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, osmandTargetOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        osmandTargetSpinner.adapter = adapter

        val currentIndex = when (unitPrefs.osmAndTarget) {
            UnitPrefs.OsmAndTarget.AUTO -> 0
            UnitPrefs.OsmAndTarget.OSMAND -> 1
            UnitPrefs.OsmAndTarget.OSMAND_PLUS -> 2
        }
        osmandTargetSpinner.setSelection(currentIndex)

        osmandTargetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                unitPrefs.osmAndTarget = when (position) {
                    1 -> UnitPrefs.OsmAndTarget.OSMAND
                    2 -> UnitPrefs.OsmAndTarget.OSMAND_PLUS
                    else -> UnitPrefs.OsmAndTarget.AUTO
                }
                updateOpenOsmandLabel()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        updateOpenOsmandLabel()
    }

    private fun resolveOsmAndPackage(): String {
        return when (unitPrefs.osmAndTarget) {
            UnitPrefs.OsmAndTarget.OSMAND -> OsmAndBridge.OSMAND_PACKAGE_FREE
            UnitPrefs.OsmAndTarget.OSMAND_PLUS -> OsmAndBridge.OSMAND_PACKAGE_PLUS
            UnitPrefs.OsmAndTarget.AUTO -> {
                if (isPackageInstalled(OsmAndBridge.OSMAND_PACKAGE_PLUS))
                    OsmAndBridge.OSMAND_PACKAGE_PLUS
                else OsmAndBridge.OSMAND_PACKAGE_FREE
            }
        }
    }

    private fun resolveOsmAndLabel(): String {
        val pkg = resolveOsmAndPackage()
        return if (pkg == OsmAndBridge.OSMAND_PACKAGE_PLUS) "OsmAnd+" else "OsmAnd"
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            this.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateOpenOsmandLabel() {
        openOsmand.text = "Open ${resolveOsmAndLabel()} ↗"
    }

    private fun setupUnitButtons() {
        updateUnitButtonLabels()
        tempUnitBtn.setOnClickListener {
            unitPrefs.tempUnit = if (unitPrefs.tempUnit == UnitPrefs.TempUnit.CELSIUS)
                UnitPrefs.TempUnit.FAHRENHEIT else UnitPrefs.TempUnit.CELSIUS
            updateUnitButtonLabels()
        }
        pressureUnitBtn.setOnClickListener {
            unitPrefs.pressureUnit = when (unitPrefs.pressureUnit) {
                UnitPrefs.PressureUnit.KPA -> UnitPrefs.PressureUnit.BAR
                UnitPrefs.PressureUnit.BAR -> UnitPrefs.PressureUnit.PSI
                UnitPrefs.PressureUnit.PSI -> UnitPrefs.PressureUnit.KPA
            }
            updateUnitButtonLabels()
        }
        flowUnitBtn.setOnClickListener {
            unitPrefs.flowUnit = if (unitPrefs.flowUnit == UnitPrefs.FlowUnit.LPH)
                UnitPrefs.FlowUnit.GPH else UnitPrefs.FlowUnit.LPH
            updateUnitButtonLabels()
        }
    }

    private fun updateUnitButtonLabels() {
        tempUnitBtn.text = if (unitPrefs.tempUnit == UnitPrefs.TempUnit.CELSIUS) "°C" else "°F"
        pressureUnitBtn.text = when (unitPrefs.pressureUnit) {
            UnitPrefs.PressureUnit.KPA -> "kPa"
            UnitPrefs.PressureUnit.BAR -> "bar"
            UnitPrefs.PressureUnit.PSI -> "PSI"
        }
        flowUnitBtn.text = if (unitPrefs.flowUnit == UnitPrefs.FlowUnit.LPH) "L/h" else "gal/h"
    }

    private fun start() {
        SmartCraftService.simulatorMode = simulatorSwitch.isChecked
        startForegroundService(Intent(this, SmartCraftService::class.java))
    }

    private fun stop() { stopService(Intent(this, SmartCraftService::class.java)) }

    private fun startRefresh() { if (refreshing) return; refreshing = true; refresh() }
    private fun stopRefresh() { refreshing = false; handler.removeCallbacksAndMessages(null) }

    private fun refresh() {
        if (!refreshing) return
        updateUi()
        if (debugSwitch.isChecked) {
            val filters = buildSet {
                if (filterBle.isChecked) add("BLE")
                if (filterAidl.isChecked) add("AIDL")
                if (filterSim.isChecked) add("SIM")
                if (filterData.isChecked) add("DATA")
            }
            val text = synchronized(SmartCraftService.debugLog) {
                SmartCraftService.debugLog.filter { line -> filters.any { "[${it}]" in line } }.joinToString("\n")
            }
            debugLog.text = text
            debugScroll.post { debugScroll.fullScroll(View.FOCUS_DOWN) }
        }
        handler.postDelayed({ refresh() }, 500)
    }

    private fun updateUi() {
        if (SmartCraftService.isRunning) {
            statusText.text = "● Running" + if (SmartCraftService.simulatorMode) " (simulator)" else ""
            toggleButton.text = "Stop"
            simulatorSwitch.isEnabled = false
        } else {
            statusText.text = "○ Stopped"
            toggleButton.text = "Start"
            simulatorSwitch.isEnabled = true
        }

        if (SmartCraftService.isRunning && !SmartCraftService.simulatorMode) {
            val device = SmartCraftService.bleDeviceName
            val rssi = SmartCraftService.bleRssi
            val status = SmartCraftService.bleStatus
            bleStatusText.text = "BLE: $status${device?.let { " · $it" } ?: ""}${rssi?.let { " ($it dBm)" } ?: ""}"
            bleStatusText.setTextColor(when (status) {
                "Receiving" -> 0xFF4CAF50.toInt()
                "Connected" -> 0xFF8BC34A.toInt()
                "Scanning", "Connecting" -> 0xFFFF9800.toInt()
                else -> 0xFFFF5722.toInt()
            })
            bleStatusText.visibility = View.VISIBLE
        } else if (SmartCraftService.isRunning && SmartCraftService.simulatorMode) {
            bleStatusText.text = "BLE: disabled (simulator)"
            bleStatusText.setTextColor(0xFF888888.toInt())
            bleStatusText.visibility = View.VISIBLE
        } else {
            bleStatusText.visibility = View.GONE
        }

        filterSim.isEnabled = SmartCraftService.simulatorMode

        val label = resolveOsmAndLabel()
        if (SmartCraftService.isRunning && SmartCraftService.osmAndConnected) {
            osmandStatus.text = "$label: ✓ connected"
            osmandStatus.setTextColor(0xFF4CAF50.toInt())
        } else if (SmartCraftService.isRunning) {
            osmandStatus.text = "$label: connecting..."
            osmandStatus.setTextColor(0xFFFF9800.toInt())
        } else {
            osmandStatus.text = "$label: not connected"
            osmandStatus.setTextColor(0xFF888888.toInt())
        }

        updateOpenOsmandLabel()
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (autoStartSwitch.isChecked && !SmartCraftService.isRunning) start()
        }
    }
}
