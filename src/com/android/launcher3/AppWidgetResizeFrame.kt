/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.core.view.children
import com.android.launcher3.AppWidgetResizeFrame.Companion.DragHandles.Companion.HANDLE_COUNT
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.LauncherConstants.ActivityCodes
import com.android.launcher3.LauncherPrefs.Companion.get
import com.android.launcher3.accessibility.DragViewStateAnnouncer
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.InstanceIdSequence
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.PopupContainer.Companion.getOpen
import com.android.launcher3.util.PendingRequestArgs
import com.android.launcher3.views.ArrowTipView
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A floating view representing the frame shown with resize handles (dots) around the widgets when
 * you hold press it.
 */
class AppWidgetResizeFrame
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractFloatingView(context, attrs, defStyleAttr),
    View.OnKeyListener,
    DragController.DragListener {
    private val launcher: Launcher = Launcher.getLauncher(context)
    private var stateAnnouncer: DragViewStateAnnouncer?
    private val firstFrameAnimatorHelper: FirstFrameAnimatorHelper
    private var systemGestureExclusionRectsHolder: List<Rect>

    private lateinit var dragHandles: DragHandles
    private lateinit var widgetView: LauncherAppWidgetHostView
    private lateinit var cellLayout: CellLayout
    private lateinit var dragLayer: DragLayer

    @Deprecated("Replaced with an option in popup when homeScreenEditImprovements flag is on")
    private var reconfigureButton: ImageButton? = null

    private val backgroundPadding: Int
    private val touchTargetWidth: Int

    private val directionVector = IntArray(2)
    private val lastDirectionVector = IntArray(2)

    private val tempRange1 = IntRange()
    private val tempRange2 = IntRange()

    private val deltaXRange = IntRange()
    private val baselineXRange = IntRange()

    private val deltaYRange = IntRange()
    private val baselineYRange = IntRange()

    private val logInstanceId: InstanceId = InstanceIdSequence().newInstanceId()

    private val dragLayerRelativeCoordinateHelper: ViewGroupFocusHelper

    /**
     * In the two panel UI, it is not possible to resize a widget to cross its host [CellLayout]'s
     * sibling. When this happens, we gradually reduce the alpha of the sibling [CellLayout] from 1f
     * to [CELL_LAYOUT_INVALID_RESIZE_MAX_ALPHA]. This param is the margin used to calculate the
     * progress.
     */
    private val crossPanelInvalidDragMargin: Float

    private var isLeftBorderActive = false
    private var isRightBorderActive = false
    private var isTopBorderActive = false
    private var isBottomBorderActive = false

    private var horizontalResizeActive = false
    private var verticalResizeActive = false

    private var runningHInc = 0
    private var runningVInc = 0
    private var minHSpan = 0
    private var minVSpan = 0
    private var maxHSpan = 0
    private var maxVSpan = 0
    private var deltaX = 0
    private var deltaY = 0
    private var deltaXAddOn = 0
    private var deltaYAddOn = 0

    private var topTouchRegionAdjustment = 0
    private var bottomTouchRegionAdjustment = 0

    private val widgetViewLayoutListener: OnLayoutChangeListener

    private var xDown = 0
    private var yDown = 0

    init {
        launcher.dragController.addDragListener(this)
        stateAnnouncer = DragViewStateAnnouncer.createFor(this)

        backgroundPadding = resources.getDimensionPixelSize(R.dimen.resize_frame_background_padding)
        touchTargetWidth = 2 * backgroundPadding
        firstFrameAnimatorHelper = FirstFrameAnimatorHelper(this)
        systemGestureExclusionRectsHolder = List(HANDLE_COUNT) { Rect() }

        crossPanelInvalidDragMargin =
            resources
                .getDimensionPixelSize(
                    R.dimen.resize_frame_invalid_drag_across_two_panel_opacity_margin
                )
                .toFloat()
        dragLayerRelativeCoordinateHelper = ViewGroupFocusHelper(launcher.dragLayer)

        widgetViewLayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            setCornerRadiusFromWidget()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        dragHandles =
            DragHandles(
                left = findViewById(R.id.widget_resize_left_handle),
                top = findViewById(R.id.widget_resize_top_handle),
                right = findViewById(R.id.widget_resize_right_handle),
                bottom = findViewById(R.id.widget_resize_bottom_handle),
            )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        dragHandles.all.forEachIndexed { index, view ->
            systemGestureExclusionRectsHolder[index].set(
                view.left,
                view.top,
                view.right,
                view.bottom,
            )
        }
        systemGestureExclusionRects = systemGestureExclusionRectsHolder
    }

    private fun setCornerRadiusFromWidget() {
        if (widgetView.hasEnforcedCornerRadius()) {
            val resizeFrame = findViewById<ImageView>(R.id.widget_resize_frame)
            resizeFrame.drawable.let { drawable ->
                if (drawable is GradientDrawable) {
                    val gradientDrawable = drawable.mutate() as GradientDrawable
                    gradientDrawable.cornerRadius = widgetView.enforcedCornerRadius
                }
            }
        }
    }

    /** Retrieves the view where accessibility actions happen. */
    fun getViewForAccessibility(): View = widgetView

    /** Initializes a resize frame that can be shown around the provided [widgetView]. */
    private fun setupForWidget(
        widgetView: LauncherAppWidgetHostView,
        cellLayout: CellLayout,
        dragLayer: DragLayer,
    ) {
        fun initializeWidgetViewLayoutParams(widgetInfo: ItemInfo) {
            val presenterPos = launcher.cellPosMapper.mapModelToPresenter(widgetInfo)
            (this.widgetView.layoutParams as CellLayoutLayoutParams).apply {
                cellX = presenterPos.cellX
                tmpCellX = presenterPos.cellX
                cellY = presenterPos.cellY
                tmpCellY = presenterPos.cellY
                cellHSpan = widgetInfo.spanX
                cellVSpan = widgetInfo.spanY
                isLockedToGrid = true
            }
        }

        @Deprecated("Will be removed as part of homeScreenEditImprovements flag")
        fun initializeReconfigureButton() {
            reconfigureButton =
                (findViewById<View>(R.id.widget_reconfigure_button) as ImageButton).apply {
                    visibility = VISIBLE
                    setOnClickListener {
                        launcher.setWaitingForResult(
                            PendingRequestArgs.forWidgetInfo(
                                this@AppWidgetResizeFrame.widgetView.appWidgetId,
                                /* widgetHandler= */ null, // since reconfiguring existing widget.
                                this@AppWidgetResizeFrame.widgetView.tag as ItemInfo,
                            )
                        )
                        launcher.appWidgetHolder?.startConfigActivity(
                            launcher,
                            this@AppWidgetResizeFrame.widgetView.appWidgetId,
                            ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET,
                        )
                    }
                }
            if (!hasSeenReconfigurableWidgetEducationTip()) {
                post {
                    if (showReconfigurableWidgetEducationTip() != null) {
                        get(context)
                            .put(LauncherPrefs.RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN, true)
                    }
                }
            }
        }

        fun updateResizeHandlesForGrid(
            currentSpanX: Int,
            currentSpanY: Int,
            info: LauncherAppWidgetProviderInfo,
            numRows: Int,
            numColumns: Int,
        ) {
            val isWidgetVSpanInvalid = currentSpanY < minVSpan
            val isWidgetHSpanInvalid = currentSpanX < minHSpan

            // On font / display change, the dp/px size of a cell changes, which means, existing
            // spans may be invalid. User should be able to resize to the correct widget size.
            verticalResizeActive =
                info.hasVerticalResizeModeEnabled() &&
                    ((minVSpan < numRows && maxVSpan > 1 && minVSpan < maxVSpan) ||
                        isWidgetVSpanInvalid)
            if (!verticalResizeActive) {
                dragHandles.top.visibility = GONE
                dragHandles.bottom.visibility = GONE
            }

            horizontalResizeActive =
                info.hasHorizontalResizeModeEnabled() &&
                    ((minHSpan < numColumns && maxHSpan > 1 && minHSpan < maxHSpan) ||
                        isWidgetHSpanInvalid)
            if (!horizontalResizeActive) {
                dragHandles.left.visibility = GONE
                dragHandles.right.visibility = GONE
            }
        }

        this.cellLayout = cellLayout
        this.widgetView = widgetView
        val info = widgetView.appWidgetInfo as LauncherAppWidgetProviderInfo
        this.dragLayer = dragLayer

        minHSpan = info.minSpanX
        minVSpan = info.minSpanY
        maxHSpan = info.maxSpanX
        maxVSpan = info.maxSpanY

        val widgetInfoOnView = this.widgetView.tag as LauncherAppWidgetInfo
        val idp = getIDP(cellLayout.context)

        // Only show resize handles for the directions in which resizing is possible.
        updateResizeHandlesForGrid(
            currentSpanX = widgetInfoOnView.spanX,
            currentSpanY = widgetInfoOnView.spanY,
            info = info,
            numRows = idp.numRows,
            numColumns = idp.numColumns,
        )

        if (!Flags.homeScreenEditImprovements() && info.isReconfigurable) {
            initializeReconfigureButton()
        }

        initializeWidgetViewLayoutParams(widgetInfoOnView)

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        this.cellLayout.markCellsAsUnoccupiedForView(this.widgetView)

        launcher.statsLogManager
            .logger()
            .withInstanceId(logInstanceId)
            .withItemInfo(widgetInfoOnView)
            .log(LauncherEvent.LAUNCHER_WIDGET_RESIZE_STARTED)

        setOnKeyListener(this)
        setCornerRadiusFromWidget()
        this.widgetView.addOnLayoutChangeListener(widgetViewLayoutListener)
    }

    /**
     * Identifies the handle from which user is trying to resize; if none, returns false.
     * Additionally, evaluates & saves the resize bounds / ranges necessary for the active resize.
     */
    private fun beginResizeIfPointInRegion(x: Int, y: Int): Boolean {
        isLeftBorderActive = (x < touchTargetWidth) && horizontalResizeActive
        isRightBorderActive = (x > width - touchTargetWidth) && horizontalResizeActive
        isTopBorderActive =
            (y < touchTargetWidth + topTouchRegionAdjustment) && verticalResizeActive
        isBottomBorderActive =
            (y > height - touchTargetWidth + bottomTouchRegionAdjustment) && verticalResizeActive

        val anyBordersActive =
            isLeftBorderActive || isRightBorderActive || isTopBorderActive || isBottomBorderActive

        if (anyBordersActive) {
            dragHandles.left.alpha = if (isLeftBorderActive) VISIBLE_ALPHA else DIMMED_ALPHA
            dragHandles.right.alpha = if (isRightBorderActive) VISIBLE_ALPHA else DIMMED_ALPHA
            dragHandles.top.alpha = if (isTopBorderActive) VISIBLE_ALPHA else DIMMED_ALPHA
            dragHandles.bottom.alpha = if (isBottomBorderActive) VISIBLE_ALPHA else DIMMED_ALPHA
        }

        when {
            isLeftBorderActive -> deltaXRange.set(start = -left, end = width - 2 * touchTargetWidth)

            isRightBorderActive ->
                deltaXRange.set(start = 2 * touchTargetWidth - width, end = dragLayer.width - right)

            else -> deltaXRange.reset()
        }
        baselineXRange.set(start = left, end = right)

        when {
            isTopBorderActive -> deltaYRange.set(start = -top, end = height - 2 * touchTargetWidth)

            isBottomBorderActive ->
                deltaYRange.set(
                    start = 2 * touchTargetWidth - height,
                    end = dragLayer.height - bottom,
                )

            else -> deltaYRange.reset()
        }
        baselineYRange.set(start = top, end = bottom)

        return anyBordersActive
    }

    /** Based on the deltas, we resize the frame. */
    private fun visualizeResizeForDelta(deltaX: Int, deltaY: Int) {
        this.deltaX = deltaXRange.clamp(deltaX)
        this.deltaY = deltaYRange.clamp(deltaY)
        val lp = layoutParams as BaseDragLayer.LayoutParams

        baselineXRange.applyDelta(
            moveStart = isLeftBorderActive,
            moveEnd = isRightBorderActive,
            delta = this.deltaX,
            outputRange = tempRange1,
        )
        lp.x = tempRange1.start
        lp.width = tempRange1.size()

        baselineYRange.applyDelta(
            moveStart = isTopBorderActive,
            moveEnd = isBottomBorderActive,
            delta = this.deltaY,
            outputRange = tempRange1,
        )
        lp.y = tempRange1.start
        lp.height = tempRange1.size()

        resizeWidgetIfNeeded(onDismiss = false)

        // Handle invalid resize across CellLayouts in the two panel UI.
        if (cellLayout.parent is Workspace<*>) {
            val workspace = cellLayout.parent as Workspace<*>
            val pairedCellLayout = workspace.getScreenPair(cellLayout)
            if (pairedCellLayout != null) {
                handleInvalidResizeForTwoPanelUi(workspace, pairedCellLayout)
            }
        }

        requestLayout()
    }

    private fun handleInvalidResizeForTwoPanelUi(
        workspace: Workspace<*>,
        pairedCellLayout: CellLayout,
    ) {
        val focusedCellLayoutBound = TempRect
        dragLayerRelativeCoordinateHelper.viewToRect(cellLayout, focusedCellLayoutBound)

        val resizeFrameBound = TempRect2
        findViewById<View>(R.id.widget_resize_frame).getGlobalVisibleRect(resizeFrameBound)

        val progress =
            when {
                workspace.indexOfChild(pairedCellLayout) < workspace.indexOfChild(cellLayout) &&
                    this.deltaX < 0 &&
                    resizeFrameBound.left < focusedCellLayoutBound.left ->
                    // Resize from right to left.
                    ((crossPanelInvalidDragMargin + this.deltaX) / crossPanelInvalidDragMargin)

                (workspace.indexOfChild(pairedCellLayout) > workspace.indexOfChild(cellLayout)) &&
                    this.deltaX > 0 &&
                    resizeFrameBound.right > focusedCellLayoutBound.right ->
                    // Resize from left to right.
                    ((crossPanelInvalidDragMargin - this.deltaX) / crossPanelInvalidDragMargin)

                else -> SPRING_LOADED_PROGRESS_MAX
            }

        val alpha =
            max(CELL_LAYOUT_INVALID_RESIZE_MAX_ALPHA.toDouble(), progress.toDouble()).toFloat()
        val springLoadedProgress =
            min(SPRING_LOADED_PROGRESS_MAX, (SPRING_LOADED_PROGRESS_MAX - progress))
        updateInvalidResizeEffect(
            cellLayout = cellLayout,
            pairedCellLayout = pairedCellLayout,
            alpha = alpha,
            springLoadedProgress = springLoadedProgress,
            animatorSet = null,
        )
    }

    /** Based on the current deltas, we determine if and how to resize the widget. */
    private fun resizeWidgetIfNeeded(onDismiss: Boolean) {
        val wlp: ViewGroup.LayoutParams? = widgetView.layoutParams
        if (wlp == null || wlp !is CellLayoutLayoutParams) return

        val dp = launcher.deviceProfile
        val xThreshold =
            (cellLayout.cellWidth + dp.workspaceIconProfile.cellLayoutBorderSpacePx.x).toFloat()
        val yThreshold =
            (cellLayout.cellHeight + dp.workspaceIconProfile.cellLayoutBorderSpacePx.y).toFloat()

        val hSpanInc = getSpanIncrement((deltaX + deltaXAddOn) / xThreshold - runningHInc)
        val vSpanInc = getSpanIncrement((deltaY + deltaYAddOn) / yThreshold - runningVInc)

        if (!onDismiss && (hSpanInc == 0 && vSpanInc == 0)) return

        directionVector[DIRECTION_HORIZONTAL_INDEX] = DIRECTION_NONE
        directionVector[DIRECTION_VERTICAL_INDEX] = DIRECTION_NONE

        var spanX = wlp.cellHSpan
        var spanY = wlp.cellVSpan
        var cellX = if (wlp.useTmpCoords) wlp.tmpCellX else wlp.cellX
        var cellY = if (wlp.useTmpCoords) wlp.tmpCellY else wlp.cellY

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        tempRange1.set(cellX, spanX + cellX)
        val hSpanDelta =
            tempRange1.applyDeltaAndBound(
                moveStart = isLeftBorderActive,
                moveEnd = isRightBorderActive,
                delta = hSpanInc,
                minSize = minHSpan,
                maxSize = maxHSpan,
                maxEnd = cellLayout.countX,
                outputRange = tempRange2,
            )
        cellX = tempRange2.start
        spanX = tempRange2.size()
        if (hSpanDelta != 0) {
            directionVector[DIRECTION_HORIZONTAL_INDEX] =
                if (isLeftBorderActive) DIRECTION_LEFT else DIRECTION_RIGHT
        }

        tempRange1.set(cellY, spanY + cellY)
        val vSpanDelta =
            tempRange1.applyDeltaAndBound(
                moveStart = isTopBorderActive,
                moveEnd = isBottomBorderActive,
                delta = vSpanInc,
                minSize = minVSpan,
                maxSize = maxVSpan,
                maxEnd = cellLayout.countY,
                outputRange = tempRange2,
            )
        cellY = tempRange2.start
        spanY = tempRange2.size()
        if (vSpanDelta != 0) {
            directionVector[DIRECTION_VERTICAL_INDEX] =
                if (isTopBorderActive) DIRECTION_TOP else DIRECTION_BOTTOM
        }

        if (!onDismiss && vSpanDelta == 0 && hSpanDelta == 0) return

        // We always want the final commit to match the feedback, so we make sure to use the
        // last used direction vector when committing the resize / reorder.
        if (onDismiss) {
            directionVector[DIRECTION_HORIZONTAL_INDEX] =
                lastDirectionVector[DIRECTION_HORIZONTAL_INDEX]
            directionVector[DIRECTION_VERTICAL_INDEX] =
                lastDirectionVector[DIRECTION_VERTICAL_INDEX]
        } else {
            lastDirectionVector[DIRECTION_HORIZONTAL_INDEX] =
                directionVector[DIRECTION_HORIZONTAL_INDEX]
            lastDirectionVector[DIRECTION_VERTICAL_INDEX] =
                directionVector[DIRECTION_VERTICAL_INDEX]
        }

        // We don't want to evaluate resize if a widget was pending config activity and was already
        // occupying a space on the screen. This otherwise will cause reorder algorithm evaluate a
        // different location for the widget and cause a jump.
        if (
            widgetView !is PendingAppWidgetHostView &&
                cellLayout.createAreaForResize(
                    cellX,
                    cellY,
                    spanX,
                    spanY,
                    /*dragView=*/ widgetView,
                    directionVector,
                    /*commit=*/ onDismiss,
                )
        ) {
            if (wlp.cellHSpan != spanX || wlp.cellVSpan != spanY) {
                stateAnnouncer?.announce(launcher.getString(R.string.widget_resized, spanX, spanY))
            }

            wlp.tmpCellX = cellX
            wlp.tmpCellY = cellY
            wlp.cellHSpan = spanX
            wlp.cellVSpan = spanY
            runningVInc += vSpanDelta
            runningHInc += hSpanDelta

            if (!onDismiss) {
                widgetView.updateSizeRanges(spanX, spanY)
            }
        }
        widgetView.requestLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        launcher.dragController.removeDragListener(this)
        // We are done with resizing the widget. Save the widget size & position to LauncherModel
        resizeWidgetIfNeeded(true)
        launcher.statsLogManager
            .logger()
            .withInstanceId(logInstanceId)
            .withItemInfo(widgetView.tag as ItemInfo)
            .log(LauncherEvent.LAUNCHER_WIDGET_RESIZE_COMPLETED)
    }

    private fun onTouchUp() {
        val dp = launcher.deviceProfile
        val xThreshold = cellLayout.cellWidth + dp.workspaceIconProfile.cellLayoutBorderSpacePx.x
        val yThreshold = cellLayout.cellHeight + dp.workspaceIconProfile.cellLayoutBorderSpacePx.y

        deltaXAddOn = runningHInc * xThreshold
        deltaYAddOn = runningVInc * yThreshold
        deltaX = 0
        deltaY = 0

        post { snapToWidget(true) }
    }

    /**
     * Returns the rect of this view when the frame is snapped around the widget, with the bounds
     * relative to the [DragLayer].
     */
    private fun getSnappedRectRelativeToDragLayer(out: Rect) {
        val scale = widgetView.scaleToFit
        dragLayer.getViewRectRelativeToSelf(widgetView, out)

        val width = 2 * backgroundPadding + Math.round(scale * out.width())
        val height = 2 * backgroundPadding + Math.round(scale * out.height())
        val x = out.left - backgroundPadding
        val y = out.top - backgroundPadding

        out.left = x
        out.top = y
        out.right = out.left + width
        out.bottom = out.top + height
    }

    private fun snapToWidget(animate: Boolean) {
        getSnappedRectRelativeToDragLayer(TempRect)
        val newWidth = TempRect.width()
        val newHeight = TempRect.height()
        val newX = TempRect.left
        val newY = TempRect.top

        // We need to make sure the frame's touchable regions lie fully within the bounds of the
        // DragLayer. We allow the actual handles to be clipped, but we shift the touch regions
        // down accordingly to provide a proper touch target.
        topTouchRegionAdjustment =
            if (newY < 0) {
                // In this case we shift the touch region down to start at the top of the DragLayer
                -newY
            } else {
                0
            }
        bottomTouchRegionAdjustment =
            if (newY + newHeight > dragLayer.height) {
                // In this case we shift the touch region up to end at the bottom of the DragLayer
                -(newY + newHeight - dragLayer.height)
            } else {
                0
            }

        val lp = layoutParams as BaseDragLayer.LayoutParams
        val pairedCellLayout: CellLayout?
        if (cellLayout.parent is Workspace<*>) {
            val workspace = cellLayout.parent as Workspace<*>
            pairedCellLayout = workspace.getScreenPair(cellLayout)
        } else {
            pairedCellLayout = null
        }
        if (!animate) {
            lp.width = newWidth
            lp.height = newHeight
            lp.x = newX
            lp.y = newY
            dragHandles.all.forEach { it.alpha = VISIBLE_ALPHA }
            if (pairedCellLayout != null) {
                updateInvalidResizeEffect(
                    cellLayout = cellLayout,
                    pairedCellLayout = pairedCellLayout,
                    alpha = VISIBLE_ALPHA,
                    springLoadedProgress = SPRING_LOADED_PROGRESS_MIN,
                    animatorSet = null,
                )
            }
            requestLayout()
        } else {
            val oa =
                ObjectAnimator.ofPropertyValuesHolder(
                    lp,
                    PropertyValuesHolder.ofInt(LauncherAnimUtils.LAYOUT_WIDTH, lp.width, newWidth),
                    PropertyValuesHolder.ofInt(
                        LauncherAnimUtils.LAYOUT_HEIGHT,
                        lp.height,
                        newHeight,
                    ),
                    PropertyValuesHolder.ofInt(BaseDragLayer.LAYOUT_X, lp.x, newX),
                    PropertyValuesHolder.ofInt(BaseDragLayer.LAYOUT_Y, lp.y, newY),
                )
            firstFrameAnimatorHelper.addTo(oa).addUpdateListener { requestLayout() }

            val animatorSet = AnimatorSet()
            animatorSet.play(oa)
            dragHandles.all.forEach { handle ->
                animatorSet.play(
                    firstFrameAnimatorHelper.addTo(
                        ObjectAnimator.ofFloat(handle, ALPHA, VISIBLE_ALPHA)
                    )
                )
            }

            if (pairedCellLayout != null) {
                updateInvalidResizeEffect(
                    cellLayout = cellLayout,
                    pairedCellLayout = pairedCellLayout,
                    alpha = VISIBLE_ALPHA,
                    springLoadedProgress = SPRING_LOADED_PROGRESS_MIN,
                    animatorSet = animatorSet,
                )
            }

            animatorSet.setDuration(SNAP_DURATION_MS.toLong())
            animatorSet.start()
        }

        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        // Clear the frame and give focus to the widget host view when a directional key is pressed.
        if (shouldCloseResizeFrame(keyCode)) {
            close(/* animate= */ false)
            widgetView.requestFocus()
            return true
        }
        return false
    }

    private fun handleTouchDown(ev: MotionEvent): Boolean {
        val hitRect = Rect()
        val x = ev.x.toInt()
        val y = ev.y.toInt()

        getHitRect(hitRect)
        if (hitRect.contains(x, y)) {
            if (beginResizeIfPointInRegion(x - left, y - top)) {
                xDown = x
                yDown = y
                return true
            }
        }
        return false
    }

    private fun isTouchOnReconfigureButton(ev: MotionEvent): Boolean {
        val configureButton = reconfigureButton ?: return false
        val xFrame = ev.x.toInt() - left
        val yFrame = ev.y.toInt() - top
        configureButton.getHitRect(TempRect)
        return TempRect.contains(xFrame, yFrame)
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        val x = ev.x.toInt()
        val y = ev.y.toInt()

        when (action) {
            MotionEvent.ACTION_DOWN -> return handleTouchDown(ev)
            MotionEvent.ACTION_MOVE -> {
                closePopupIfOpen()
                visualizeResizeForDelta(deltaX = x - xDown, deltaY = y - yDown)
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                visualizeResizeForDelta(deltaX = x - xDown, deltaY = y - yDown)
                onTouchUp()
                xDown = 0
                yDown = 0
            }
        }
        return true
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && handleTouchDown(ev)) {
            return true
        }
        // Keep the resize frame open but let a click on the reconfigure button fall through to the
        // button's OnClickListener.
        if (isTouchOnReconfigureButton(ev)) {
            return false
        }

        if (Flags.homeScreenEditImprovements()) {
            if (shouldIgnoreTouch()) {
                return false
            }
            // We want to close any open popup if we're not dragging and the touch event is outside
            // this frame.
            closePopupIfOpen()
        }
        close(/* animate= */ false)
        return false
    }

    private fun closePopupIfOpen() = getOpen(launcher)?.close(/* animate= */ true)

    // When dragging we should ignore touch.
    private fun shouldIgnoreTouch(): Boolean = launcher.dragController.isDragging

    override fun handleClose(animate: Boolean) {
        dragLayer.removeView(this)
        widgetView.removeOnLayoutChangeListener(widgetViewLayoutListener)
        launcher.dragController.removeDragListener(this)
    }

    private fun updateInvalidResizeEffect(
        cellLayout: CellLayout,
        pairedCellLayout: CellLayout,
        alpha: Float,
        springLoadedProgress: Float,
        animatorSet: AnimatorSet?,
    ) {
        pairedCellLayout.children.forEach { child ->
            if (animatorSet != null) {
                animatorSet.play(
                    firstFrameAnimatorHelper.addTo(ObjectAnimator.ofFloat(child, ALPHA, alpha))
                )
            } else {
                child.alpha = alpha
            }
        }

        if (animatorSet != null) {
            animatorSet.play(
                firstFrameAnimatorHelper.addTo(
                    ObjectAnimator.ofFloat(
                        cellLayout,
                        CellLayout.SPRING_LOADED_PROGRESS,
                        springLoadedProgress,
                    )
                )
            )
            animatorSet.play(
                firstFrameAnimatorHelper.addTo(
                    ObjectAnimator.ofFloat(
                        pairedCellLayout,
                        CellLayout.SPRING_LOADED_PROGRESS,
                        springLoadedProgress,
                    )
                )
            )
        } else {
            cellLayout.springLoadedProgress = springLoadedProgress
            pairedCellLayout.springLoadedProgress = springLoadedProgress
        }

        val shouldShowCellLayoutBorder = springLoadedProgress > SPRING_LOADED_PROGRESS_MIN
        if (animatorSet != null) {
            animatorSet.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        cellLayout.isDragOverlapping = shouldShowCellLayoutBorder
                        pairedCellLayout.isDragOverlapping = shouldShowCellLayoutBorder
                    }
                }
            )
        } else {
            cellLayout.isDragOverlapping = shouldShowCellLayoutBorder
            pairedCellLayout.isDragOverlapping = shouldShowCellLayoutBorder
        }
    }

    override fun isOfType(type: Int): Boolean = (type and TYPE_WIDGET_RESIZE_FRAME) != 0

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        close(true)
    }

    override fun onDragEnd() {
        // No-op
    }

    /** A mutable class for describing the range of two int values. */
    @VisibleForTesting
    class IntRange {
        var start: Int = 0
        var end: Int = 0

        fun clamp(value: Int) = Utilities.boundToRange(value, start, end)

        /** Resets the range to 0, 0 */
        fun reset() = set(start = 0, end = 0)

        fun set(start: Int, end: Int) {
            this.start = start
            this.end = end
        }

        fun size(): Int = end - start

        /**
         * Moves either the start or end edge (but never both) by {@param delta} and sets the result
         * in {@param out}
         */
        fun applyDelta(moveStart: Boolean, moveEnd: Boolean, delta: Int, outputRange: IntRange) {
            outputRange.start =
                if (moveStart) {
                    start + delta
                } else {
                    start
                }

            outputRange.end =
                if (moveEnd) {
                    end + delta
                } else {
                    end
                }
        }

        /**
         * Applies delta similar to [applyDelta], with extra conditions.
         *
         * @param minSize minimum size after with the moving edge should not be shifted any further.
         *   For eg, if delta = -3 when moving the endEdge brings the size to less than minSize,
         *   only delta = -2 will applied
         * @param maxSize maximum size after with the moving edge should not be shifted any further.
         *   For eg, if delta = -3 when moving the endEdge brings the size to greater than maxSize,
         *   only delta = -2 will applied
         * @param maxEnd The maximum value to the end edge (start edge is always restricted to 0)
         * @return the amount of increase when endEdge was moves and the amount of decrease when the
         *   start edge was moved.
         */
        fun applyDeltaAndBound(
            moveStart: Boolean,
            moveEnd: Boolean,
            delta: Int,
            minSize: Int,
            maxSize: Int,
            maxEnd: Int,
            outputRange: IntRange,
        ): Int {
            applyDelta(moveStart, moveEnd, delta, outputRange)
            outputRange.start = outputRange.start.coerceAtLeast(0)
            outputRange.end = outputRange.end.coerceAtMost(maxEnd)

            if (outputRange.size() < minSize) {
                if (moveStart) {
                    outputRange.start = outputRange.end - minSize
                } else if (moveEnd) {
                    outputRange.end = outputRange.start + minSize
                }
            }

            if (outputRange.size() > maxSize) {
                if (moveStart) {
                    outputRange.start = outputRange.end - maxSize
                } else if (moveEnd) {
                    outputRange.end = outputRange.start + maxSize
                }
            }

            return if (moveEnd) {
                outputRange.size() - size()
            } else {
                size() - outputRange.size()
            }
        }
    }

    @Deprecated("Will be removed as part of homeScreenEditImprovements flag")
    private fun showReconfigurableWidgetEducationTip(): ArrowTipView? {
        val configureButton = reconfigureButton ?: return null

        val rect = Rect()
        if (!configureButton.getGlobalVisibleRect(rect)) return null

        val tipMarginPx: Int =
            launcher.resources.getDimensionPixelSize(R.dimen.widget_reconfigure_tip_top_margin)
        return ArrowTipView(launcher, /* isPointingUp= */ true)
            .showAroundRect(
                context.getString(R.string.reconfigurable_widget_education_tip),
                /*arrowXCoord=*/ rect.left + configureButton.width / 2,
                /*rect=*/ rect,
                /*margin=*/ tipMarginPx,
            )
    }

    @Deprecated("Will be removed as part of homeScreenEditImprovements flag")
    private fun hasSeenReconfigurableWidgetEducationTip(): Boolean {
        return get(context).get(LauncherPrefs.RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN) ||
            Utilities.isRunningInTestHarness()
    }

    companion object {
        private const val SNAP_DURATION_MS = 150

        private const val DIMMED_ALPHA = 0f
        private const val VISIBLE_ALPHA = 1f

        private const val SPRING_LOADED_PROGRESS_MIN = 0f
        private const val SPRING_LOADED_PROGRESS_MAX = 1f

        private const val RESIZE_THRESHOLD = 0.66f

        // Reusable static objects pre-initialized for temporary usage.
        private val TempRect = Rect()
        private val TempRect2 = Rect()

        private const val DIRECTION_HORIZONTAL_INDEX = 0
        private const val DIRECTION_VERTICAL_INDEX = 1
        private const val DIRECTION_TOP = -1
        private const val DIRECTION_LEFT = -1
        private const val DIRECTION_RIGHT = 1
        private const val DIRECTION_BOTTOM = 1
        private const val DIRECTION_NONE = 0

        private const val CELL_LAYOUT_INVALID_RESIZE_MAX_ALPHA = 0.5f

        /**
         * Shows the resize frame for a widget
         *
         * @param widget is the widget view for which we want to show the resize frame.
         * @param cellLayout is the cellLayout in which the widget is placed.
         */
        @JvmStatic
        fun showForWidget(widget: LauncherAppWidgetHostView?, cellLayout: CellLayout) {
            // If widget is not added to view hierarchy, we cannot show resize frame at correct
            // location
            if (widget == null || widget.parent == null) return

            val activityContext = cellLayout.mActivity
            val dragLayer = activityContext.dragLayer as DragLayer

            closeAllOpenViewsExcept(activityContext, TYPE_ACTION_POPUP)

            val frame =
                activityContext.layoutInflater.inflate(
                    R.layout.app_widget_resize_frame,
                    /*root*/ dragLayer,
                    /*attachToRoot*/ false,
                ) as AppWidgetResizeFrame

            frame.apply {
                setupForWidget(widget, cellLayout, dragLayer)
                // Save widget item info as tag on resize frame; so that, the accessibility delegate
                // can
                // attach actions that typically happen on widget (e.g. resize, move) also on the
                // resize
                // frame.
                tag = widget.tag
                accessibilityDelegate = activityContext.accessibilityDelegate
                contentDescription =
                    activityContext
                        .asContext()
                        .getString(
                            R.string.widget_frame_name,
                            (widget.tag as ItemInfo).contentDescription,
                        )
                (layoutParams as BaseDragLayer.LayoutParams).customPosition = true
            }

            dragLayer.addView(frame)
            frame.mIsOpen = true
            frame.post { frame.snapToWidget(false) }
        }

        private fun getSpanIncrement(deltaFrac: Float): Int {
            return if (abs(deltaFrac.toDouble()) > RESIZE_THRESHOLD) {
                Math.round(deltaFrac)
            } else {
                0
            }
        }

        /** When true, resize frame can be dismissed. */
        private fun shouldCloseResizeFrame(keyCode: Int): Boolean {
            return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
                keyCode == KeyEvent.KEYCODE_MOVE_END ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN)
        }

        private fun AppWidgetProviderInfo.hasHorizontalResizeModeEnabled() =
            resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0

        private fun AppWidgetProviderInfo.hasVerticalResizeModeEnabled() =
            resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0

        /**
         * The dots on each side of a resize frame. Prefer accessing these named variables when you
         * want to access a specific handle.
         */
        private data class DragHandles(
            val left: View,
            val top: View,
            val right: View,
            val bottom: View,
        ) {
            /** Convenience method to iterate on all handles in clockwise order. */
            val all: List<View> = listOf(left, top, right, bottom)

            companion object {
                const val HANDLE_COUNT = 4
            }
        }
    }
}
