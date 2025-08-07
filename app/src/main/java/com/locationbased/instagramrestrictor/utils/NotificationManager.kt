package com.locationbased.instagramrestrictor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.locationbased.instagramrestrictor.MainActivity
import com.locationbased.instagramrestrictor.R

class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferencesManager = PreferencesManager(context)
    
    companion object {
        const val CHANNEL_LOCATION = "location_updates"
        const val CHANNEL_RESTRICTIONS = "restrictions"
        const val CHANNEL_TIME_WARNINGS = "time_warnings" 
        const val CHANNEL_EMERGENCY = "emergency_overrides"
        
        const val NOTIFICATION_LOCATION_UPDATE = 3001
        const val NOTIFICATION_TIME_WARNING = 3002
        const val NOTIFICATION_DAILY_LIMIT = 3003
        const val NOTIFICATION_EMERGENCY_USED = 3004
        const val NOTIFICATION_MONTHLY_RESET = 3005
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_LOCATION,
                    "Location Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications about location changes (home/away)"
                    setShowBadge(false)
                },
                
                NotificationChannel(
                    CHANNEL_RESTRICTIONS,
                    "Instagram Restrictions", 
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when Instagram is blocked"
                    setShowBadge(true)
                },
                
                NotificationChannel(
                    CHANNEL_TIME_WARNINGS,
                    "Time Limit Warnings",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Warnings about approaching time limits"
                    setShowBadge(true)
                },
                
                NotificationChannel(
                    CHANNEL_EMERGENCY,
                    "Emergency Overrides",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Emergency override notifications"
                    setShowBadge(true)
                }
            )
            
            notificationManager.createNotificationChannels(channels)
        }
    }
    
    fun showLocationChangeNotification(isAtHome: Boolean) {
        val title = if (isAtHome) "Arrived at Home" else "Left Home"
        val message = if (isAtHome) {
            val remainingTime = preferencesManager.getRemainingDailyTime()
            "Instagram restrictions active. $remainingTime minutes remaining today."
        } else {
            "Instagram restrictions lifted. You can use Instagram freely."
        }
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(if (isAtHome) R.drawable.ic_home else R.drawable.ic_away)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_LOCATION_UPDATE, notification)
    }
    
    fun showTimeWarningNotification(remainingMinutes: Int) {
        val title = when {
            remainingMinutes <= 5 -> "⚠️ Only $remainingMinutes minutes left!"
            remainingMinutes <= 15 -> "15 Minutes Remaining"
            remainingMinutes <= 30 -> "30 Minutes Remaining"
            else -> return // Don't show warning for more than 30 minutes
        }
        
        val message = "Your daily Instagram time limit is almost reached."
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_TIME_WARNINGS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_time_warning)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        
        notificationManager.notify(NOTIFICATION_TIME_WARNING, notification)
    }
    
    fun showDailyLimitExceededNotification() {
        val canUseOverride = preferencesManager.canUseEmergencyOverride()
        val isAtHome = preferencesManager.isAtHome()
        
        val title = "Daily Instagram Limit Reached"
        val message = if (isAtHome && canUseOverride) {
            "2 hour limit exceeded. Use emergency override or wait until tomorrow."
        } else if (isAtHome) {
            "2 hour limit exceeded. Wait until tomorrow or go outside to use Instagram."
        } else {
            "2 hour limit exceeded but you're away from home. Instagram is available."
        }
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_RESTRICTIONS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_limit_exceeded)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        
        // Add emergency override action if available and at home
        if (canUseOverride && isAtHome) {
            val overrideIntent = Intent(context, EmergencyOverrideReceiver::class.java)
            val overridePendingIntent = PendingIntent.getBroadcast(
                context, 0, overrideIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder.addAction(
                R.drawable.ic_emergency,
                "Emergency Override (${preferencesManager.getRemainingEmergencyOverrides()} left)",
                overridePendingIntent
            )
        }
        
        notificationManager.notify(NOTIFICATION_DAILY_LIMIT, notificationBuilder.build())
    }
    
    fun showEmergencyOverrideUsedNotification() {
        val remainingOverrides = preferencesManager.getRemainingEmergencyOverrides()
        val overrideTimeRemaining = preferencesManager.getEmergencyOverrideRemainingTime()
        
        val title = "Emergency Override Activated"
        val message = "1 hour Instagram access granted. $remainingOverrides overrides remaining this month. $overrideTimeRemaining minutes left."
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_emergency_active)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(context.getColor(R.color.emergency_override))
            .build()
        
        notificationManager.notify(NOTIFICATION_EMERGENCY_USED, notification)
    }
    
    fun showMonthlyResetNotification() {
        val title = "Monthly Reset"
        val message = "Emergency overrides have been reset. You now have 3 overrides available for this month."
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_reset)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(NOTIFICATION_MONTHLY_RESET, notification)
    }
    
    fun showDailyUsageStatsNotification(totalMinutesUsed: Int, blocksCount: Int) {
        if (totalMinutesUsed == 0) return // Don't show if no usage
        
        val title = "Daily Instagram Usage"
        val message = "Used: ${totalMinutesUsed} minutes | Blocks: $blocksCount | Remaining: ${preferencesManager.getRemainingDailyTime()} minutes"
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_TIME_WARNINGS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stats)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true) // Don't alert repeatedly
            .build()
        
        notificationManager.notify(NOTIFICATION_TIME_WARNING + 1, notification)
    }
    
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
    
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}

// Extension functions for easy usage
fun Context.showLocationChangeNotification(isAtHome: Boolean) {
    NotificationHelper(this).showLocationChangeNotification(isAtHome)
}

fun Context.showTimeWarningNotification(remainingMinutes: Int) {
    NotificationHelper(this).showTimeWarningNotification(remainingMinutes)
}

fun Context.showDailyLimitExceededNotification() {
    NotificationHelper(this).showDailyLimitExceededNotification()
}

fun Context.showEmergencyOverrideUsedNotification() {
    NotificationHelper(this).showEmergencyOverrideUsedNotification()
}