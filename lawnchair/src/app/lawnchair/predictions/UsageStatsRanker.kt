package app.lawnchair.predictions

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import com.android.launcher3.AppFilter
import java.util.concurrent.TimeUnit

/**
 * Queries [UsageStatsManager] across multiple time windows with configurable
 * weights, scores packages by launch count, foreground time, and recency,
 * then returns a ranked list of store keys.
 */
class UsageStatsRanker(private val context: Context) {

    /**
     * Queries usage stats across all configured time windows and returns
     * package store keys ranked by weighted score.
     *
     * @param launcherApps System [LauncherApps] service for resolving activities.
     * @param appFilter Filter for apps that should appear in the launcher.
     * @param resolveStoreKey Resolves a package name to a store key, or `null`
     *   if the package should be excluded.
     */
    fun getUsageStatsRanked(
        launcherApps: LauncherApps,
        appFilter: AppFilter,
        resolveStoreKey: (String, LauncherApps, AppFilter) -> String?,
    ): List<String> {
        val usageStatsManager =
            context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val scores = LinkedHashMap<String, Double>()

        try {
            USAGE_WINDOWS.forEach { window ->
                addUsageStatsToScores(
                    scores = scores,
                    stats = usageStatsManager.queryAndAggregateUsageStats(
                        now - window.durationMs,
                        now,
                    ),
                    now = now,
                    window = window,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query usage stats", e)
            return emptyList()
        }

        if (scores.isEmpty()) return emptyList()

        return scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (packageName, _) ->
                resolveStoreKey(packageName, launcherApps, appFilter)
            }
    }

    private fun addUsageStatsToScores(
        scores: MutableMap<String, Double>,
        stats: Map<String, UsageStats>,
        now: Long,
        window: UsageStatsWindow,
    ) {
        stats.values.forEach { stat ->
            val packageName = stat.packageName
            if (packageName.isNullOrEmpty() || packageName == context.packageName) return@forEach

            val foregroundMinutes =
                TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground).toDouble()
            val recencyScore = when {
                stat.lastTimeUsed <= 0L -> 0.0

                else -> {
                    val ageHours =
                        (now - stat.lastTimeUsed).coerceAtLeast(0L).toDouble() / RECENCY_HOUR_MS
                    1.0 / (1.0 + ageHours)
                }
            }

            val score =
                window.presenceWeight +
                    (foregroundMinutes * window.foregroundMinutesWeight) +
                    (recencyScore * window.recencyWeight)
            if (score <= 0.0) return@forEach

            scores[packageName] = (scores[packageName] ?: 0.0) + score
        }
    }

    private data class UsageStatsWindow(
        /** Weight period */
        val durationMs: Long,
        /** Weight for when the app is presence in the usage window. It is merely just is this app appeared in the window, nothing more. */
        val presenceWeight: Double,
        /** Weight for when the app is being in foreground */
        val foregroundMinutesWeight: Double,
        /** Weight for when was the app being used recently */
        val recencyWeight: Double,
    )

    companion object {
        private const val TAG = "UsageStatsRanker"
        private val RECENCY_HOUR_MS = TimeUnit.HOURS.toMillis(1).toDouble()

        private val USAGE_WINDOWS = listOf(
            UsageStatsWindow(
                durationMs = TimeUnit.HOURS.toMillis(6),
                presenceWeight = 12.0,
                foregroundMinutesWeight = 0.25,
                recencyWeight = 4.0,
            ),
            UsageStatsWindow(
                durationMs = TimeUnit.DAYS.toMillis(1),
                presenceWeight = 4.0,
                foregroundMinutesWeight = 0.1,
                recencyWeight = 1.5,
            ),
            UsageStatsWindow(
                durationMs = TimeUnit.DAYS.toMillis(7),
                presenceWeight = 1.0,
                foregroundMinutesWeight = 0.02,
                recencyWeight = 0.5,
            ),
        )
    }
}
