package com.locationbased.instagramrestrictor

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.locationbased.instagramrestrictor.admin.DeviceAdminReceiver
import com.locationbased.instagramrestrictor.service.LocationMonitoringService
import com.locationbased.instagramrestrictor.utils.NotificationHelper
import com.locationbased.instagramrestrictor.utils.PreferencesManager
import com.locationbased.instagramrestrictor.utils.UninstallProtection
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var uninstallProtection: UninstallProtection
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    // UI Components
    private lateinit var statusCard: LinearLayout
    private lateinit var locationStatusText: TextView
    private lateinit var dailyUsageText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var timeLimitSeekBar: SeekBar
    private lateinit var timeLimitValueText: TextView
    private lateinit var emergencyOverrideButton: Button
    private lateinit var emergencyOverrideStatusText: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var setupProgressText: TextView
    private lateinit var locationSetupButton: Button
    private lateinit var deviceAdminButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var statisticsCard: LinearLayout
    private lateinit var totalUsageText: TextView
    private lateinit var totalBlocksText: TextView
    private lateinit var averageDailyUsageText: TextView
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val DEVICE_ADMIN_REQUEST = 1002
        private const val ACCESSIBILITY_SETTINGS_REQUEST = 1003
        private const val OVERLAY_PERMISSION_REQUEST = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeComponents()
        setupUI()
        updateUI()
        
        // Start monitoring service if all permissions are granted
        if (preferencesManager.isAppFullyConfigured()) {
            startLocationService()
        }
    }
    
    private fun initializeComponents() {
        preferencesManager = PreferencesManager(this)
        uninstallProtection = UninstallProtection(this)
        notificationHelper = NotificationHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize UI components
        statusCard = findViewById(R.id.statusCard)
        locationStatusText = findViewById(R.id.locationStatusText)
        dailyUsageText = findViewById(R.id.dailyUsageText)
        remainingTimeText = findViewById(R.id.remainingTimeText)
        timeLimitSeekBar = findViewById(R.id.timeLimitSeekBar)
        timeLimitValueText = findViewById(R.id.timeLimitValueText)
        emergencyOverrideButton = findViewById(R.id.emergencyOverrideButton)
        emergencyOverrideStatusText = findViewById(R.id.emergencyOverrideStatusText)
        setupCard = findViewById(R.id.setupCard)
        setupProgressText = findViewById(R.id.setupProgressText)
        locationSetupButton = findViewById(R.id.locationSetupButton)
        deviceAdminButton = findViewById(R.id.deviceAdminButton)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        statisticsCard = findViewById(R.id.statisticsCard)
        totalUsageText = findViewById(R.id.totalUsageText)
        totalBlocksText = findViewById(R.id.totalBlocksText)
        averageDailyUsageText = findViewById(R.id.averageDailyUsageText)
    }
    
    private fun setupUI() {
        // Time limit seekbar
        timeLimitSeekBar.max = 240 // 4 hours max
        timeLimitSeekBar.min = 30  // 30 minutes min
        timeLimitSeekBar.progress = preferencesManager.getDailyTimeLimit()
        
        timeLimitSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val minutes = maxOf(30, progress) // Minimum 30 minutes
                    timeLimitValueText.text = "${minutes / 60}h ${minutes % 60}m"
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val minutes = maxOf(30, seekBar?.progress ?: 120)
                preferencesManager.setDailyTimeLimit(minutes)
                updateUI()
                Toast.makeText(this@MainActivity, "Daily time limit updated to ${minutes / 60}h ${minutes % 60}m", Toast.LENGTH_SHORT).show()
            }
        })
        
        // Emergency override button
        emergencyOverrideButton.setOnClickListener {
            showEmergencyOverrideDialog()
        }
        
        // Setup buttons
        locationSetupButton.setOnClickListener { setupLocation() }
        deviceAdminButton.setOnClickListener { setupDeviceAdmin() }
        accessibilityButton.setOnClickListener { setupAccessibilityService() }
    }
    
    private fun updateUI() {
        activityScope.launch {
            // Update location status
            val isAtHome = preferencesManager.isAtHome()
            locationStatusText.text = if (isAtHome) {
                "🏠 At Home - Instagram Restricted"
            } else {
                "🌍 Away from Home - Instagram Available"
            }
            locationStatusText.setTextColor(
                ContextCompat.getColor(this@MainActivity, 
                    if (isAtHome) R.color.status_restricted else R.color.status_available
                )
            )
            
            // Update daily usage
            val dailyUsage = preferencesManager.getDailyUsageTime()
            val dailyLimit = preferencesManager.getDailyTimeLimit()
            val remainingTime = preferencesManager.getRemainingDailyTime()
            
            dailyUsageText.text = "Today's Usage: ${dailyUsage / 60}h ${dailyUsage % 60}m of ${dailyLimit / 60}h ${dailyLimit % 60}m"
            remainingTimeText.text = "Remaining: ${remainingTime / 60}h ${remainingTime % 60}m"
            remainingTimeText.setTextColor(
                ContextCompat.getColor(this@MainActivity,
                    when {
                        remainingTime <= 15 -> R.color.status_critical
                        remainingTime <= 60 -> R.color.status_warning
                        else -> R.color.status_normal
                    }
                )
            )
            
            // Update time limit seekbar
            timeLimitSeekBar.progress = dailyLimit
            val minutes = dailyLimit
            timeLimitValueText.text = "${minutes / 60}h ${minutes % 60}m"
            
            // Update emergency override status
            val canUseOverride = preferencesManager.canUseEmergencyOverride()
            val isOverrideActive = preferencesManager.isEmergencyOverrideActive()
            val remainingOverrides = preferencesManager.getRemainingEmergencyOverrides()
            
            when {
                isOverrideActive -> {
                    val overrideTimeLeft = preferencesManager.getEmergencyOverrideRemainingTime()
                    emergencyOverrideButton.text = "Override Active"
                    emergencyOverrideButton.isEnabled = false
                    emergencyOverrideStatusText.text = "Emergency override active: $overrideTimeLeft minutes remaining"
                    emergencyOverrideStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emergency_active))
                }
                canUseOverride -> {
                    emergencyOverrideButton.text = "Use Emergency Override"
                    emergencyOverrideButton.isEnabled = isAtHome
                    emergencyOverrideStatusText.text = "$remainingOverrides emergency overrides remaining this month"
                    emergencyOverrideStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                }
                else -> {
                    emergencyOverrideButton.text = "No Overrides Left"
                    emergencyOverrideButton.isEnabled = false
                    emergencyOverrideStatusText.text = "No emergency overrides remaining this month"
                    emergencyOverrideStatusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_disabled))
                }
            }
            
            // Update setup progress
            updateSetupProgress()
            
            // Update statistics
            updateStatistics()
        }
    }
    
    private fun updateSetupProgress() {
        val hasLocationPermission = hasLocationPermissions()
        val isLocationSet = preferencesManager.isHomeLocationSet()
        val isDeviceAdminEnabled = DeviceAdminReceiver.isDeviceAdminActive(this)
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        
        val completedSteps = listOf(
            hasLocationPermission && isLocationSet,
            isDeviceAdminEnabled,
            isAccessibilityEnabled
        ).count { it }
        
        setupProgressText.text = "Setup Progress: $completedSteps/3 completed"
        
        // Update button states
        locationSetupButton.text = if (hasLocationPermission && isLocationSet) "✓ Location Set" else "Set Home Location"
        locationSetupButton.isEnabled = !(hasLocationPermission && isLocationSet)
        
        deviceAdminButton.text = if (isDeviceAdminEnabled) "✓ Device Admin Enabled" else "Enable Device Admin"
        deviceAdminButton.isEnabled = !isDeviceAdminEnabled
        
        accessibilityButton.text = if (isAccessibilityEnabled) "✓ Accessibility Enabled" else "Enable Accessibility"
        accessibilityButton.isEnabled = !isAccessibilityEnabled
        
        // Show/hide setup card
        setupCard.visibility = if (completedSteps < 3) View.VISIBLE else View.GONE
        statusCard.visibility = if (completedSteps == 3) View.VISIBLE else View.GONE
    }
    
    private fun updateStatistics() {
        val totalUsage = preferencesManager.getTotalUsageTime()
        val totalBlocks = preferencesManager.getTotalBlocksCount()
        val dailyAverage = if (totalUsage > 0) totalUsage / 7 else 0 // Rough weekly average
        
        totalUsageText.text = "Total Usage: ${totalUsage / 60}h ${totalUsage % 60}m"
        totalBlocksText.text = "Total Blocks: $totalBlocks"
        averageDailyUsageText.text = "Weekly Average: ${dailyAverage / 60}h ${dailyAverage % 60}m per day"
    }
    
    private fun setupLocation() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions()
            return
        }
        
        showLocationSetupDialog()
    }
    
    private fun showLocationSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Set Home Location")
            .setMessage("This will set your current location and WiFi network as home. Make sure you're at home before proceeding.")
            .setPositiveButton("Set Current Location") { _, _ ->
                getCurrentLocationAndSave()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getCurrentLocationAndSave() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                val ssid = wifiInfo.ssid?.replace("\"", "") ?: ""
                
                preferencesManager.setHomeLocation(location.latitude, location.longitude, ssid)
                Toast.makeText(this, "Home location set successfully", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Toast.makeText(this, "Unable to get current location. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupDeviceAdmin() {
        DeviceAdminReceiver.requestDeviceAdminPermission(this)
    }
    
    private fun setupAccessibilityService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, ACCESSIBILITY_SETTINGS_REQUEST)
        
        Toast.makeText(this, "Please enable 'IG Location Restrictor' accessibility service", Toast.LENGTH_LONG).show()
    }
    
    private fun showEmergencyOverrideDialog() {
        if (!preferencesManager.canUseEmergencyOverride()) {
            Toast.makeText(this, "No emergency overrides remaining this month", Toast.LENGTH_SHORT).show()
            return
        }
        
        val remainingOverrides = preferencesManager.getRemainingEmergencyOverrides()
        
        AlertDialog.Builder(this)
            .setTitle("Emergency Override")
            .setMessage("This will give you 1 hour of Instagram access at home.\n\nOverrides remaining: $remainingOverrides\n\nUse emergency override?")
            .setPositiveButton("Activate Override") { _, _ ->
                preferencesManager.useEmergencyOverride()
                notificationHelper.showEmergencyOverrideUsedNotification()
                updateUI()
                Toast.makeText(this, "Emergency override activated for 1 hour", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST
        )
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(packageName) == true
    }
    
    private fun startLocationService() {
        val intent = Intent(this, LocationMonitoringService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateUI()
                } else {
                    Toast.makeText(this, "Location permission is required for the app to work", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            DEVICE_ADMIN_REQUEST -> {
                updateUI()
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Device admin enabled successfully", Toast.LENGTH_SHORT).show()
                }
            }
            ACCESSIBILITY_SETTINGS_REQUEST -> {
                updateUI()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}