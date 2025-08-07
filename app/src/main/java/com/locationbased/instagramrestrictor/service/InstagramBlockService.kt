package com.locationbased.instagramrestrictor.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.locationbased.instagramrestrictor.MainActivity
import com.locationbased.instagramrestrictor.R
import com.locationbased.instagramrestrictor.utils.PreferencesManager
import kotlinx.coroutines.*

class InstagramBlockService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isBlocking = false
    private var lastBlockNotificationTime = 0L
    
    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val BLOCK_NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "instagram_block"
        private const val MIN_NOTIFICATION_INTERVAL = 5000L // 5 seconds
        private const val OVERRIDE_NOTIFICATION_ID = 2002
        
        // Browser packages that might access Instagram
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "org.mozilla.focus"
        )
    }

    private val locationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.locationbased.instagramrestrictor.LOCATION_CHANGED") {
                val isAtHome = intent.getBooleanExtra("isAtHome", false)
                updateBlockingStatus()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        
        val filter = IntentFilter("com.locationbased.instagramrestrictor.LOCATION_CHANGED")
        registerReceiver(locationChangeReceiver, filter)
        
        updateBlockingStatus()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(accessibilityEvent)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    if (isBlocking) {
                        checkAndBlockInstagram(accessibilityEvent.packageName?.toString())
                    }
                }
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        when {
            packageName == INSTAGRAM_PACKAGE -> {
                handleInstagramOpened()
            }
            BROWSER_PACKAGES.contains(packageName) -> {
                if (isBlocking) {
                    serviceScope.launch {
                        delay(1000) // Wait for page to load
                        checkForInstagramInBrowser()
                    }
                }
            }
        }
    }

    private fun handleInstagramOpened() {
        if (shouldBlockInstagram()) {
            blockInstagramApp()
        } else {
            // Allow Instagram usage - start tracking session
            if (!preferencesManager.isInstagramSessionActive()) {
                preferencesManager.startInstagramSession()
            }
        }
    }

    private fun shouldBlockInstagram(): Boolean {
        val isAtHome = preferencesManager.isAtHome()
        val hasExceededLimit = preferencesManager.hasExceededDailyLimit()
        val isOverrideActive = preferencesManager.isEmergencyOverrideActive()
        
        // Block if at home AND (exceeded limit OR no override active)
        return isAtHome && (hasExceededLimit || !isOverrideActive) && !isOverrideActive
    }

    private fun blockInstagramApp() {
        preferencesManager.incrementTotalBlocksCount()
        preferencesManager.endInstagramSession() // End any active session
        
        // Go back to home screen
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        showBlockNotification()
        isBlocking = true
    }

    private fun checkAndBlockInstagram(packageName: String?) {
        if (packageName == INSTAGRAM_PACKAGE && shouldBlockInstagram()) {
            blockInstagramApp()
        }
    }

    private fun checkForInstagramInBrowser() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // Look for Instagram-related content in the browser
            val nodes = rootNode.findAccessibilityNodeInfosByText("instagram")
            if (nodes.isNotEmpty()) {
                // Check URL bar or page content for Instagram
                val urlNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
                urlNodes.forEach { node ->
                    val url = node.text?.toString()?.lowercase()
                    if (url?.contains("instagram.com") == true || url?.contains("instagram") == true) {
                        if (shouldBlockInstagram()) {
                            blockInstagramInBrowser()
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handle accessibility errors gracefully
        }
    }

    private fun blockInstagramInBrowser() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockNotification()
        preferencesManager.incrementTotalBlocksCount()
    }

    private fun updateBlockingStatus() {
        val shouldBlock = shouldBlockInstagram()
        
        if (shouldBlock && !isBlocking) {
            isBlocking = true
            // If Instagram is currently running, block it
            serviceScope.launch {
                checkCurrentRunningApps()
            }
        } else if (!shouldBlock && isBlocking) {
            isBlocking = false
        }
    }

    private fun checkCurrentRunningApps() {
        // This would require usage stats permission to check currently running apps
        // For now, we'll rely on accessibility events
    }

    private fun showBlockNotification() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBlockNotificationTime < MIN_NOTIFICATION_INTERVAL) {
            return // Avoid spam notifications
        }
        lastBlockNotificationTime = currentTime
        
        val isAtHome = preferencesManager.isAtHome()
        val hasExceededLimit = preferencesManager.hasExceededDailyLimit()
        val remainingTime = preferencesManager.getRemainingDailyTime()
        val canUseOverride = preferencesManager.canUseEmergencyOverride()
        
        val message = when {
            isAtHome && hasExceededLimit -> "Daily 2-hour limit exceeded"
            isAtHome && remainingTime > 0 -> "Instagram restricted at home. $remainingTime minutes remaining today"
            else -> "Instagram blocked at home location"
        }
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.instagram_blocked_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Add emergency override action if available
        if (canUseOverride && isAtHome) {
            val overrideIntent = Intent(this, EmergencyOverrideReceiver::class.java)
            val overridePendingIntent = PendingIntent.getBroadcast(
                this, 0, overrideIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder.addAction(
                R.drawable.ic_emergency,
                "Emergency Override (${preferencesManager.getRemainingEmergencyOverrides()} left)",
                overridePendingIntent
            )
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(BLOCK_NOTIFICATION_ID, notificationBuilder.build())
        
        // Also show toast for immediate feedback
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Instagram Block Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when Instagram is blocked"
                setShowBadge(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(locationChangeReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        serviceScope.cancel()
    }
}

// Emergency Override Broadcast Receiver
class EmergencyOverrideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val preferencesManager = PreferencesManager(it)
            if (preferencesManager.canUseEmergencyOverride()) {
                preferencesManager.useEmergencyOverride()
                
                // Show confirmation notification
                val notificationManager = it.getSystemService(NotificationManager::class.java)
                val remainingOverrides = preferencesManager.getRemainingEmergencyOverrides()
                val overrideTime = preferencesManager.getEmergencyOverrideRemainingTime()
                
                val notification = NotificationCompat.Builder(it, "instagram_block")
                    .setContentTitle("Emergency Override Activated")
                    .setContentText("1 hour access granted. $remainingOverrides overrides remaining this month.")
                    .setSmallIcon(R.drawable.ic_emergency)
                    .setAutoCancel(true)
                    .build()
                
                notificationManager.notify(InstagramBlockService.OVERRIDE_NOTIFICATION_ID, notification)
                
                Toast.makeText(it, "Emergency override activated: $overrideTime minutes remaining", Toast.LENGTH_LONG).show()
            }
        }
    }
}