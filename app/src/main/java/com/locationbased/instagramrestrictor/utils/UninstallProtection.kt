package com.locationbased.instagramrestrictor.utils

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import com.locationbased.instagramrestrictor.admin.DeviceAdminReceiver

class UninstallProtection(private val context: Context) {
    
    private val devicePolicyManager: DevicePolicyManager = 
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val preferencesManager = PreferencesManager(context)
    
    fun enableUninstallProtection(): Boolean {
        return try {
            val adminComponent = DeviceAdminReceiver.getComponentName(context)
            
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                // Set this app as a protected app (prevent uninstallation)
                val packageName = context.packageName
                
                // Note: This method requires the app to be a device owner or profile owner
                // For regular device admin, we use other protection methods
                protectApp(packageName)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun disableUninstallProtection(): Boolean {
        return try {
            val adminComponent = DeviceAdminReceiver.getComponentName(context)
            
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                val packageName = context.packageName
                unprotectApp(packageName)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun protectApp(packageName: String) {
        try {
            // Method 1: Try to set as system app protection (requires elevated permissions)
            setAppProtectionStatus(packageName, true)
            
            // Method 2: Use device policy manager to add restrictions
            val adminComponent = DeviceAdminReceiver.getComponentName(context)
            
            // Add the app to protected list (if device owner)
            try {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            } catch (e: SecurityException) {
                // Fall back to other methods if not device owner
                setAlternativeProtection(true)
            }
            
        } catch (e: Exception) {
            // Use alternative protection methods
            setAlternativeProtection(true)
        }
    }
    
    private fun unprotectApp(packageName: String) {
        try {
            setAppProtectionStatus(packageName, false)
            
            val adminComponent = DeviceAdminReceiver.getComponentName(context)
            
            try {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, false)
            } catch (e: SecurityException) {
                setAlternativeProtection(false)
            }
            
        } catch (e: Exception) {
            setAlternativeProtection(false)
        }
    }
    
    private fun setAppProtectionStatus(packageName: String, protect: Boolean) {
        // This would require system-level permissions
        // For now, we'll implement alternative protection methods
        
        // Store protection status in preferences
        preferencesManager.setDeviceAdminEnabled(protect)
    }
    
    private fun setAlternativeProtection(protect: Boolean) {
        // Alternative method 1: Hide app from launcher when at home
        // Alternative method 2: Disable app components when at home
        // Alternative method 3: Show persistent notifications
        
        val packageManager = context.packageManager
        val packageName = context.packageName
        
        try {
            if (protect) {
                // Make app harder to uninstall by disabling certain components temporarily
                val componentState = if (preferencesManager.isAtHome()) 
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED 
                else 
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                
                // This will make the app less discoverable in settings when at home
                packageManager.setApplicationEnabledSetting(
                    packageName,
                    componentState,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // Re-enable normal functionality
                packageManager.setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            // Ignore if we can't change component states
        }
    }
    
    fun updateProtectionBasedOnLocation() {
        val isAtHome = preferencesManager.isAtHome()
        val adminComponent = DeviceAdminReceiver.getComponentName(context)
        
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            if (isAtHome) {
                enableUninstallProtection()
            } else {
                // When away from home, reduce protection but keep some safeguards
                setAlternativeProtection(false)
            }
        }
    }
    
    fun canUninstallApp(): Boolean {
        val isAtHome = preferencesManager.isAtHome()
        val isDeviceAdminActive = DeviceAdminReceiver.isDeviceAdminActive(context)
        
        // Can only uninstall when away from home and device admin is active
        return !isAtHome && isDeviceAdminActive
    }
    
    fun getUninstallBlockReason(): String? {
        val isAtHome = preferencesManager.isAtHome()
        val isDeviceAdminActive = DeviceAdminReceiver.isDeviceAdminActive(context)
        
        return when {
            isAtHome -> "Cannot uninstall while at home location. Please go outside to uninstall the app."
            !isDeviceAdminActive -> "Device admin must be enabled for location-based restrictions."
            else -> null // Can uninstall
        }
    }
}