/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.views

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.AppUsageLimit
import android.graphics.Outline
import android.graphics.Paint
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.isVisible
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.quickstep.TaskUtils
import com.android.systemui.shared.recents.model.Task
import java.time.Duration
import java.util.Locale

@SuppressLint("AppCompatCustomView")
class DigitalWellBeingToast
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : TextView(context, attrs, defStyleAttr, defStyleRes) {
    private val recentsViewContainer: RecentsViewContainer =
        RecentsViewContainer.containerFromContext(context)

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)

    private val bannerHeight =
        context.resources.getDimensionPixelSize(R.dimen.digital_wellbeing_toast_height)

    private lateinit var task: Task
    private lateinit var taskView: TaskView
    private lateinit var snapshotView: View
    @StagePosition private var stagePosition = STAGE_POSITION_UNDEFINED

    private var appRemainingTimeMs: Long = 0
    private var splitOffsetTranslationY = 0f
        set(value) {
            if (field != value) {
                field = value
                updateTranslationY()
            }
        }

    private var isDestroyed = false

    var hasLimit = false
    var splitBounds: SplitConfigurationOptions.SplitBounds? = null
    var bannerOffsetPercentage = 0f
        set(value) {
            if (field != value) {
                field = value
                updateTranslationY()
            }
        }

    init {
        setOnClickListener(::openAppUsageSettings)
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    BACKGROUND.getOutline(view, outline)
                    val verticalTranslation = splitOffsetTranslationY - translationY
                    outline.offset(0, Math.round(verticalTranslation))
                }
            }
        clipToOutline = true
    }

    private fun setNoLimit() {
        isVisible = false
        hasLimit = false
        appRemainingTimeMs = -1
        setContentDescription(appUsageLimitTimeMs = -1, appRemainingTimeMs = -1)
    }

    private fun setLimit(appUsageLimitTimeMs: Long, appRemainingTimeMs: Long) {
        isVisible = true
        hasLimit = true
        this.appRemainingTimeMs = appRemainingTimeMs
        setContentDescription(appUsageLimitTimeMs, appRemainingTimeMs)
        text = Utilities.prefixTextWithIcon(context, R.drawable.ic_hourglass_top, getBannerText())
    }

    private fun setContentDescription(appUsageLimitTimeMs: Long, appRemainingTimeMs: Long) {
        val contentDescription =
            getContentDescriptionForTask(task, appUsageLimitTimeMs, appRemainingTimeMs)
        snapshotView.contentDescription = contentDescription
    }

    fun initialize() {
        check(!isDestroyed) { "Cannot re-initialize a destroyed toast" }
        setupTranslations()
        Executors.ORDERED_BG_EXECUTOR.execute {
            var usageLimit: AppUsageLimit? = null
            try {
                usageLimit =
                    launcherApps?.getAppUsageLimit(
                        task.topComponent.packageName,
                        UserHandle.of(task.key.userId),
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing digital well being toast", e)
            }
            val appUsageLimitTimeMs = usageLimit?.totalUsageLimit ?: -1
            val appRemainingTimeMs = usageLimit?.usageRemaining ?: -1

            taskView.post {
                if (isDestroyed) return@post
                if (appUsageLimitTimeMs < 0 || appRemainingTimeMs < 0) {
                    setNoLimit()
                } else {
                    setLimit(appUsageLimitTimeMs, appRemainingTimeMs)
                }
            }
        }
    }

    /** Bind the DWB toast to its dependencies. */
    fun bind(
        task: Task,
        taskView: TaskView,
        snapshotView: View,
        @StagePosition stagePosition: Int,
    ) {
        this.task = task
        this.taskView = taskView
        this.snapshotView = snapshotView
        this.stagePosition = stagePosition
        isDestroyed = false
    }

    /** Mark the DWB toast as destroyed and hide it. */
    fun destroy() {
        isVisible = false
        isDestroyed = true
    }

    private fun getSplitBannerConfig(): SplitBannerConfig {
        val splitBounds = splitBounds
        return when {
            splitBounds == null ||
                !recentsViewContainer.deviceProfile.isTablet ||
                taskView.isLargeTile -> SplitBannerConfig.SPLIT_BANNER_FULLSCREEN
            // For portrait grid only height of task changes, not width. So we keep the text the
            // same
            !recentsViewContainer.deviceProfile.isLeftRightSplit ->
                SplitBannerConfig.SPLIT_GRID_BANNER_LARGE
            // For landscape grid, for 30% width we only show icon, otherwise show icon and time
            task.key.id == splitBounds.leftTopTaskId ->
                if (splitBounds.leftTopTaskPercent < THRESHOLD_LEFT_ICON_ONLY)
                    SplitBannerConfig.SPLIT_GRID_BANNER_SMALL
                else SplitBannerConfig.SPLIT_GRID_BANNER_LARGE
            else ->
                if (splitBounds.leftTopTaskPercent > THRESHOLD_RIGHT_ICON_ONLY)
                    SplitBannerConfig.SPLIT_GRID_BANNER_SMALL
                else SplitBannerConfig.SPLIT_GRID_BANNER_LARGE
        }
    }

    private fun getReadableDuration(
        duration: Duration,
        @StringRes durationLessThanOneMinuteStringId: Int,
    ): String {
        val hours = Math.toIntExact(duration.toHours())
        val minutes = Math.toIntExact(duration.minusHours(hours.toLong()).toMinutes())
        return when {
            // Apply FormatWidth.WIDE if both the hour part and the minute part are non-zero.
            hours > 0 && minutes > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)
                    .formatMeasures(
                        Measure(hours, MeasureUnit.HOUR),
                        Measure(minutes, MeasureUnit.MINUTE),
                    )
            // Apply FormatWidth.WIDE if only the hour part is non-zero (unless forced).
            hours > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(hours, MeasureUnit.HOUR))
            // Apply FormatWidth.WIDE if only the minute part is non-zero (unless forced).
            minutes > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
            // Use a specific string for usage less than one minute but non-zero.
            duration > Duration.ZERO -> context.getString(durationLessThanOneMinuteStringId)
            // Otherwise, return 0-minute string.
            else ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(0, MeasureUnit.MINUTE))
        }
    }

    /**
     * Returns text to show for the banner depending on [.getSplitBannerConfig] If {@param
     * forContentDesc} is `true`, this will always return the full string corresponding to
     * [.SPLIT_BANNER_FULLSCREEN]
     */
    @JvmOverloads
    @VisibleForTesting
    fun getBannerText(
        remainingTime: Long = appRemainingTimeMs,
        forContentDesc: Boolean = false,
    ): String {
        val duration =
            Duration.ofMillis(
                if (remainingTime > MINUTE_MS)
                    (remainingTime + MINUTE_MS - 1) / MINUTE_MS * MINUTE_MS
                else remainingTime
            )
        val readableDuration =
            getReadableDuration(
                duration,
                R.string.shorter_duration_less_than_one_minute, /* forceFormatWidth */
            )
        val splitBannerConfig = getSplitBannerConfig()
        return when {
            forContentDesc || splitBannerConfig == SplitBannerConfig.SPLIT_BANNER_FULLSCREEN ->
                context.getString(R.string.time_left_for_app, readableDuration)
            // show no text
            splitBannerConfig == SplitBannerConfig.SPLIT_GRID_BANNER_SMALL -> ""
            // SPLIT_GRID_BANNER_LARGE only show time
            else -> readableDuration
        }
    }

    private fun openAppUsageSettings(view: View) {
        val intent =
            Intent(OPEN_APP_USAGE_SETTINGS_TEMPLATE)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, task.topComponent.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            val options = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height)
            context.startActivity(intent, options.toBundle())

            // TODO: add WW logging on the app usage settings click.
        } catch (e: ActivityNotFoundException) {
            Log.e(
                TAG,
                "Failed to open app usage settings for task " + task.topComponent.packageName,
                e,
            )
        }
    }

    private fun getContentDescriptionForTask(
        task: Task,
        appUsageLimitTimeMs: Long,
        appRemainingTimeMs: Long,
    ): String? =
        if (appUsageLimitTimeMs >= 0 && appRemainingTimeMs >= 0)
            context.getString(
                R.string.task_contents_description_with_remaining_time,
                task.titleDescription,
                getBannerText(appRemainingTimeMs, true /* forContentDesc */),
            )
        else task.titleDescription

    fun setupLayout() {
        val snapshotWidth: Int
        val snapshotHeight: Int
        val splitBounds = splitBounds
        if (splitBounds == null) {
            snapshotWidth = taskView.layoutParams.width
            snapshotHeight =
                taskView.layoutParams.height -
                    recentsViewContainer.deviceProfile.overviewTaskThumbnailTopMarginPx
        } else {
            val groupedTaskSize =
                taskView.pagedOrientationHandler.getGroupedTaskViewSizes(
                    recentsViewContainer.deviceProfile,
                    splitBounds,
                    taskView.layoutParams.width,
                    taskView.layoutParams.height,
                )
            if (stagePosition == STAGE_POSITION_TOP_OR_LEFT) {
                snapshotWidth = groupedTaskSize.first.x
                snapshotHeight = groupedTaskSize.first.y
            } else {
                snapshotWidth = groupedTaskSize.second.x
                snapshotHeight = groupedTaskSize.second.y
            }
        }
        taskView.pagedOrientationHandler.updateDwbBannerLayout(
            taskView.layoutParams.width,
            taskView.layoutParams.height,
            taskView is GroupedTaskView,
            recentsViewContainer.deviceProfile,
            snapshotWidth,
            snapshotHeight,
            this,
        )
    }

    private fun setupTranslations() {
        val (translationX, translationY) =
            taskView.pagedOrientationHandler.getDwbBannerTranslations(
                taskView.layoutParams.width,
                taskView.layoutParams.height,
                splitBounds,
                recentsViewContainer.deviceProfile,
                taskView.snapshotViews,
                task.key.id,
                this,
            )
        this.translationX = translationX
        this.splitOffsetTranslationY = translationY
    }

    private fun updateTranslationY() {
        translationY = bannerOffsetPercentage * bannerHeight + splitOffsetTranslationY
        invalidateOutline()
    }

    fun setColorTint(color: Int, amount: Float) {
        if (amount == 0f) {
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
        val layerPaint = Paint()
        layerPaint.setColorFilter(Utilities.makeColorTintingColorFilter(color, amount))
        setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint)
        setLayerPaint(layerPaint)
    }

    private fun getAccessibilityActionId(): Int =
        if (splitBounds?.rightBottomTaskId == task.key.id)
            R.id.action_digital_wellbeing_bottom_right
        else R.id.action_digital_wellbeing_top_left

    fun getDWBAccessibilityAction(): AccessibilityNodeInfo.AccessibilityAction? {
        if (!hasLimit) return null
        val label =
            if (taskView.containsMultipleTasks())
                context.getString(
                    R.string.split_app_usage_settings,
                    TaskUtils.getTitle(context, task),
                )
            else context.getString(R.string.accessibility_app_usage_settings)
        return AccessibilityNodeInfo.AccessibilityAction(getAccessibilityActionId(), label)
    }

    fun handleAccessibilityAction(action: Int): Boolean {
        if (getAccessibilityActionId() != action) return false
        openAppUsageSettings(taskView)
        return true
    }

    companion object {
        private const val THRESHOLD_LEFT_ICON_ONLY = 0.4f
        private const val THRESHOLD_RIGHT_ICON_ONLY = 0.6f

        enum class SplitBannerConfig {
            /** Will span entire width of taskView with full text */
            SPLIT_BANNER_FULLSCREEN,
            /** Used for grid task view, only showing icon and time */
            SPLIT_GRID_BANNER_LARGE,
            /** Used for grid task view, only showing icon */
            SPLIT_GRID_BANNER_SMALL,
        }

        val OPEN_APP_USAGE_SETTINGS_TEMPLATE: Intent = Intent(Settings.ACTION_APP_USAGE_SETTINGS)
        const val MINUTE_MS: Int = 60000

        private const val TAG = "DigitalWellBeingToast"
    }
}
