package com.locationbased.instagramrestrictor.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.locationbased.instagramrestrictor.R
import com.locationbased.instagramrestrictor.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationMonitoringService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var wifiManager: WifiManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isAtHome = false
    private var homeLatitude = 0.0
    private var homeLongitude = 0.0
    private var homeWifiSSID = ""
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_monitoring"
        private const val HOME_RADIUS_METERS = 50f
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 seconds
        private const val FASTEST_UPDATE_INTERVAL = 15000L // 15 seconds
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        loadHomeLocation()
        createNotificationChannel()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadHomeLocation() {
        homeLatitude = preferencesManager.getHomeLatitude()
        homeLongitude = preferencesManager.getHomeLongitude()
        homeWifiSSID = preferencesManager.getHomeWifiSSID()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    checkLocationStatus(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun checkLocationStatus(currentLocation: Location) {
        serviceScope.launch {
            val wasAtHome = isAtHome
            isAtHome = isUserAtHome(currentLocation)
            
            if (wasAtHome != isAtHome) {
                preferencesManager.setIsAtHome(isAtHome)
                broadcastLocationChange(isAtHome)
                updateNotification()
            }
        }
    }

    private fun isUserAtHome(currentLocation: Location): Boolean {
        // Check GPS proximity
        val gpsAtHome = if (homeLatitude != 0.0 && homeLongitude != 0.0) {
            val homeLocation = Location("home").apply {
                latitude = homeLatitude
                longitude = homeLongitude
            }
            currentLocation.distanceTo(homeLocation) <= HOME_RADIUS_METERS
        } else false

        // Check WiFi network
        val wifiAtHome = if (homeWifiSSID.isNotEmpty()) {
            try {
                val currentWifiInfo = wifiManager.connectionInfo
                val currentSSID = currentWifiInfo?.ssid?.replace("\"", "") ?: ""
                currentSSID == homeWifiSSID
            } catch (e: Exception) {
                false
            }
        } else false

        // User is at home if either GPS or WiFi indicates home
        return gpsAtHome || wifiAtHome
    }

    private fun broadcastLocationChange(atHome: Boolean) {
        val intent = Intent("com.locationbased.instagramrestrictor.LOCATION_CHANGED").apply {
            putExtra("isAtHome", atHome)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val locationStatus = if (isAtHome) "At Home" else "Away from Home"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_notification_title))
            .setContentText("$locationStatus - ${getString(R.string.location_service_notification_text)}")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}