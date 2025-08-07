package com.locationbased.instagramrestrictor.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

class PreferencesManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "instagram_restrictor_prefs"
        
        // Location preferences
        private const val KEY_HOME_LATITUDE = "home_latitude"
        private const val KEY_HOME_LONGITUDE = "home_longitude"
        private const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
        private const val KEY_IS_AT_HOME = "is_at_home"
        
        // Time tracking preferences
        private const val KEY_DAILY_TIME_LIMIT = "daily_time_limit" // in minutes
        private const val KEY_DAILY_USAGE_TIME = "daily_usage_time" // in minutes
        private const val KEY_LAST_USAGE_DATE = "last_usage_date"
        private const val KEY_SESSION_START_TIME = "session_start_time"
        private const val KEY_IS_INSTAGRAM_SESSION_ACTIVE = "is_instagram_session_active"
        
        // Emergency override preferences
        private const val KEY_EMERGENCY_OVERRIDES_USED = "emergency_overrides_used"
        private const val KEY_EMERGENCY_OVERRIDE_MONTH = "emergency_override_month"
        private const val KEY_EMERGENCY_OVERRIDE_ACTIVE = "emergency_override_active"
        private const val KEY_EMERGENCY_OVERRIDE_START_TIME = "emergency_override_start_time"
        
        // App setup preferences
        private const val KEY_IS_DEVICE_ADMIN_ENABLED = "is_device_admin_enabled"
        private const val KEY_IS_ACCESSIBILITY_SERVICE_ENABLED = "is_accessibility_service_enabled"
        private const val KEY_IS_HOME_LOCATION_SET = "is_home_location_set"
        
        // Usage statistics
        private const val KEY_TOTAL_BLOCKS_COUNT = "total_blocks_count"
        private const val KEY_TOTAL_USAGE_TIME = "total_usage_time"
        
        private const val DEFAULT_DAILY_TIME_LIMIT = 120 // 2 hours in minutes
        private const val EMERGENCY_OVERRIDE_LIMIT = 3 // per month
        private const val EMERGENCY_OVERRIDE_DURATION = 60 // 1 hour in minutes
    }
    
    // Location methods
    fun setHomeLocation(latitude: Double, longitude: Double, wifiSSID: String) {
        sharedPreferences.edit()
            .putFloat(KEY_HOME_LATITUDE, latitude.toFloat())
            .putFloat(KEY_HOME_LONGITUDE, longitude.toFloat())
            .putString(KEY_HOME_WIFI_SSID, wifiSSID)
            .putBoolean(KEY_IS_HOME_LOCATION_SET, true)
            .apply()
    }
    
    fun getHomeLatitude(): Double = sharedPreferences.getFloat(KEY_HOME_LATITUDE, 0f).toDouble()
    fun getHomeLongitude(): Double = sharedPreferences.getFloat(KEY_HOME_LONGITUDE, 0f).toDouble()
    fun getHomeWifiSSID(): String = sharedPreferences.getString(KEY_HOME_WIFI_SSID, "") ?: ""
    fun isHomeLocationSet(): Boolean = sharedPreferences.getBoolean(KEY_IS_HOME_LOCATION_SET, false)
    
    fun setIsAtHome(isAtHome: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_AT_HOME, isAtHome).apply()
    }
    
    fun isAtHome(): Boolean = sharedPreferences.getBoolean(KEY_IS_AT_HOME, false)
    
    // Time tracking methods
    fun setDailyTimeLimit(minutes: Int) {
        sharedPreferences.edit().putInt(KEY_DAILY_TIME_LIMIT, minutes).apply()
    }
    
    fun getDailyTimeLimit(): Int = sharedPreferences.getInt(KEY_DAILY_TIME_LIMIT, DEFAULT_DAILY_TIME_LIMIT)
    
    fun addUsageTime(minutes: Int) {
        val currentDate = getCurrentDate()
        val lastUsageDate = getLastUsageDate()
        
        val currentUsage = if (currentDate == lastUsageDate) {
            getDailyUsageTime()
        } else {
            // New day, reset usage
            0
        }
        
        sharedPreferences.edit()
            .putInt(KEY_DAILY_USAGE_TIME, currentUsage + minutes)
            .putString(KEY_LAST_USAGE_DATE, currentDate)
            .apply()
    }
    
    fun getDailyUsageTime(): Int {
        val currentDate = getCurrentDate()
        val lastUsageDate = getLastUsageDate()
        
        return if (currentDate == lastUsageDate) {
            sharedPreferences.getInt(KEY_DAILY_USAGE_TIME, 0)
        } else {
            0 // New day
        }
    }
    
    fun getRemainingDailyTime(): Int {
        return maxOf(0, getDailyTimeLimit() - getDailyUsageTime())
    }
    
    fun hasExceededDailyLimit(): Boolean = getDailyUsageTime() >= getDailyTimeLimit()
    
    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
    
    private fun getLastUsageDate(): String = sharedPreferences.getString(KEY_LAST_USAGE_DATE, "") ?: ""
    
    // Session tracking
    fun startInstagramSession() {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_INSTAGRAM_SESSION_ACTIVE, true)
            .putLong(KEY_SESSION_START_TIME, System.currentTimeMillis())
            .apply()
    }
    
    fun endInstagramSession() {
        if (isInstagramSessionActive()) {
            val sessionDuration = (System.currentTimeMillis() - getSessionStartTime()) / 60000 // minutes
            addUsageTime(sessionDuration.toInt())
            addTotalUsageTime(sessionDuration.toInt())
        }
        
        sharedPreferences.edit()
            .putBoolean(KEY_IS_INSTAGRAM_SESSION_ACTIVE, false)
            .putLong(KEY_SESSION_START_TIME, 0)
            .apply()
    }
    
    fun isInstagramSessionActive(): Boolean = sharedPreferences.getBoolean(KEY_IS_INSTAGRAM_SESSION_ACTIVE, false)
    private fun getSessionStartTime(): Long = sharedPreferences.getLong(KEY_SESSION_START_TIME, 0)
    
    // Emergency override methods
    fun canUseEmergencyOverride(): Boolean {
        val currentMonth = getCurrentMonth()
        val lastOverrideMonth = getEmergencyOverrideMonth()
        
        val overridesUsed = if (currentMonth == lastOverrideMonth) {
            getEmergencyOverridesUsed()
        } else {
            0 // New month, reset count
        }
        
        return overridesUsed < EMERGENCY_OVERRIDE_LIMIT
    }
    
    fun useEmergencyOverride() {
        val currentMonth = getCurrentMonth()
        val lastOverrideMonth = getEmergencyOverrideMonth()
        
        val overridesUsed = if (currentMonth == lastOverrideMonth) {
            getEmergencyOverridesUsed()
        } else {
            0 // New month
        }
        
        sharedPreferences.edit()
            .putInt(KEY_EMERGENCY_OVERRIDES_USED, overridesUsed + 1)
            .putString(KEY_EMERGENCY_OVERRIDE_MONTH, currentMonth)
            .putBoolean(KEY_EMERGENCY_OVERRIDE_ACTIVE, true)
            .putLong(KEY_EMERGENCY_OVERRIDE_START_TIME, System.currentTimeMillis())
            .apply()
    }
    
    fun isEmergencyOverrideActive(): Boolean {
        if (!sharedPreferences.getBoolean(KEY_EMERGENCY_OVERRIDE_ACTIVE, false)) {
            return false
        }
        
        val startTime = sharedPreferences.getLong(KEY_EMERGENCY_OVERRIDE_START_TIME, 0)
        val elapsed = (System.currentTimeMillis() - startTime) / 60000 // minutes
        
        if (elapsed >= EMERGENCY_OVERRIDE_DURATION) {
            // Override expired
            sharedPreferences.edit()
                .putBoolean(KEY_EMERGENCY_OVERRIDE_ACTIVE, false)
                .putLong(KEY_EMERGENCY_OVERRIDE_START_TIME, 0)
                .apply()
            return false
        }
        
        return true
    }
    
    fun getRemainingEmergencyOverrides(): Int {
        val currentMonth = getCurrentMonth()
        val lastOverrideMonth = getEmergencyOverrideMonth()
        
        val overridesUsed = if (currentMonth == lastOverrideMonth) {
            getEmergencyOverridesUsed()
        } else {
            0
        }
        
        return maxOf(0, EMERGENCY_OVERRIDE_LIMIT - overridesUsed)
    }
    
    fun getEmergencyOverrideRemainingTime(): Int {
        if (!isEmergencyOverrideActive()) return 0
        
        val startTime = sharedPreferences.getLong(KEY_EMERGENCY_OVERRIDE_START_TIME, 0)
        val elapsed = (System.currentTimeMillis() - startTime) / 60000 // minutes
        
        return maxOf(0, EMERGENCY_OVERRIDE_DURATION - elapsed.toInt())
    }
    
    private fun getCurrentMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(Date())
    }
    
    private fun getEmergencyOverridesUsed(): Int = sharedPreferences.getInt(KEY_EMERGENCY_OVERRIDES_USED, 0)
    private fun getEmergencyOverrideMonth(): String = sharedPreferences.getString(KEY_EMERGENCY_OVERRIDE_MONTH, "") ?: ""
    
    // App setup methods
    fun setDeviceAdminEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_DEVICE_ADMIN_ENABLED, enabled).apply()
    }
    
    fun isDeviceAdminEnabled(): Boolean = sharedPreferences.getBoolean(KEY_IS_DEVICE_ADMIN_ENABLED, false)
    
    fun setAccessibilityServiceEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_ACCESSIBILITY_SERVICE_ENABLED, enabled).apply()
    }
    
    fun isAccessibilityServiceEnabled(): Boolean = sharedPreferences.getBoolean(KEY_IS_ACCESSIBILITY_SERVICE_ENABLED, false)
    
    // Usage statistics
    fun incrementTotalBlocksCount() {
        val current = sharedPreferences.getInt(KEY_TOTAL_BLOCKS_COUNT, 0)
        sharedPreferences.edit().putInt(KEY_TOTAL_BLOCKS_COUNT, current + 1).apply()
    }
    
    fun getTotalBlocksCount(): Int = sharedPreferences.getInt(KEY_TOTAL_BLOCKS_COUNT, 0)
    
    private fun addTotalUsageTime(minutes: Int) {
        val current = sharedPreferences.getInt(KEY_TOTAL_USAGE_TIME, 0)
        sharedPreferences.edit().putInt(KEY_TOTAL_USAGE_TIME, current + minutes).apply()
    }
    
    fun getTotalUsageTime(): Int = sharedPreferences.getInt(KEY_TOTAL_USAGE_TIME, 0)
    
    fun isAppFullyConfigured(): Boolean {
        return isHomeLocationSet() && isDeviceAdminEnabled() && isAccessibilityServiceEnabled()
    }
}