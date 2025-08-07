package com.locationbased.instagramrestrictor.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.locationbased.instagramrestrictor.utils.PreferencesManager

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val preferencesManager = PreferencesManager(context)
        preferencesManager.setDeviceAdminEnabled(true)
        Toast.makeText(context, "Device admin enabled - App protection activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val preferencesManager = PreferencesManager(context)
        preferencesManager.setDeviceAdminEnabled(false)
        Toast.makeText(context, "Device admin disabled - App protection deactivated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val preferencesManager = PreferencesManager(context)
        
        return if (preferencesManager.isAtHome()) {
            "You cannot disable device admin while at home location. Please go outside to modify app settings."
        } else {
            "Disabling device admin will remove app uninstall protection."
        }
    }

    companion object {
        fun isDeviceAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getComponentName(context)
            return devicePolicyManager.isAdminActive(adminComponent)
        }

        fun getComponentName(context: Context): android.content.ComponentName {
            return android.content.ComponentName(context, DeviceAdminReceiver::class.java)
        }

        fun requestDeviceAdminPermission(context: Context) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                "This app needs device admin permission to prevent uninstallation when you're at home, " +
                "ensuring the Instagram restriction system remains effective.")
            
            if (context is android.app.Activity) {
                context.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        const val REQUEST_CODE_ENABLE_ADMIN = 1001
    }
}