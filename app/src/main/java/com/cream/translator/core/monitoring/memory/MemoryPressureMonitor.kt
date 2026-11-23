package com.cream.translator.core.monitoring.memory

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import timber.log.Timber

/**
 * Monitors OS memory pressure callbacks and records diagnostic telemetry.
 * Dispatches cleanup signals when Android notifies that RAM is critically low.
 */
class MemoryPressureMonitor(
    private val context: Context,
    private val onCriticalMemoryPressure: (() -> Unit)? = null,
) : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
        val levelDescription = getTrimLevelDescription(level)
        val memInfo = getMemoryInfo()

        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        Timber.w(
            "MemoryPressure: trimLevel=$levelDescription, availMem=${availMb}MB, " +
                "totalMem=${totalMb}MB, isLowMemory=${memInfo.lowMemory}",
        )

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            onCriticalMemoryPressure?.invoke()
        }
    }

    override fun onLowMemory() {
        val memInfo = getMemoryInfo()
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        Timber.e(
            "MemoryPressure: CRITICAL onLowMemory event! availMem=${availMb}MB, totalMem=${totalMb}MB",
        )
        onCriticalMemoryPressure?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op for memory monitoring
    }

    fun getMemoryInfo(): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    companion object {
        fun getTrimLevelDescription(level: Int): String =
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
                ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
                else -> "UNKNOWN($level)"
            }
    }
}
