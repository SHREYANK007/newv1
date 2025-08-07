package com.locationbased.instagramrestrictor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.locationbased.instagramrestrictor.service.LocationMonitoringService
import com.locationbased.instagramrestrictor.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val preferencesManager = PreferencesManager(context)
                
                // Only start services if app is fully configured
                if (preferencesManager.isAppFullyConfigured()) {
                    // Start location monitoring service
                    val serviceIntent = Intent(context, LocationMonitoringService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                    
                    // Update uninstall protection based on location
                    try {
                        val uninstallProtection = com.locationbased.instagramrestrictor.utils.UninstallProtection(context)
                        uninstallProtection.updateProtectionBasedOnLocation()
                    } catch (e: Exception) {
                        // Handle any errors gracefully
                    }
                }
            }
        }
    }
}