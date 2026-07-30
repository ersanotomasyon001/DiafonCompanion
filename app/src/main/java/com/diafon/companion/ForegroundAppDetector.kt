package com.diafon.companion

import android.app.usage.UsageStatsManager
import android.content.Context
import java.lang.System.currentTimeMillis

class ForegroundAppDetector(context: Context) {
    private val usageStats =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun currentPackage(): String? {
        val now = currentTimeMillis()
        return usageStats
            .queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 15_000,
                now
            )
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}
