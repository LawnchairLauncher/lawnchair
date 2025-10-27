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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import androidx.annotation.DimenRes
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode

/** A class for creating drag zones for dragging bubble objects or dragging into bubbles. */
class DragZoneFactory(
    private val context: Context,
    private val deviceConfig: DeviceConfig,
    private val splitScreenModeChecker: SplitScreenModeChecker,
    private val desktopWindowModeChecker: DesktopWindowModeChecker,
) {

    private val windowBounds: Rect
        get() = deviceConfig.windowBounds

    private var dismissDragZoneSize = 0
    private var bubbleDragZoneTabletSize = 0
    private var bubbleDragZoneFoldableSize = 0
    private var fullScreenDragZoneWidth = 0
    private var fullScreenDragZoneHeight = 0
    private var desktopWindowDragZoneWidth = 0
    private var desktopWindowDragZoneHeight = 0
    private var desktopWindowFromExpandedViewDragZoneWidth = 0
    private var desktopWindowFromExpandedViewDragZoneHeight = 0
    private var splitFromBubbleDragZoneHeight = 0
    private var splitFromBubbleDragZoneWidth = 0
    private var hSplitFromExpandedViewDragZoneWidth = 0
    private var vSplitFromExpandedViewDragZoneWidth = 0
    private var vSplitFromExpandedViewDragZoneHeightTablet = 0
    private var vSplitFromExpandedViewDragZoneHeightFoldTall = 0
    private var vSplitFromExpandedViewDragZoneHeightFoldShort = 0

    private var fullScreenDropTargetPadding = 0
    private var desktopWindowDropTargetPaddingSmall = 0
    private var desktopWindowDropTargetPaddingLarge = 0
    private var expandedViewDropTargetWidth = 0
    private var expandedViewDropTargetHeight = 0
    private var expandedViewDropTargetPaddingBottom = 0
    private var expandedViewDropTargetPaddingHorizontal = 0

    private val fullScreenDropTarget: Rect
        get() =
            Rect(windowBounds).apply {
                inset(fullScreenDropTargetPadding, fullScreenDropTargetPadding)
            }

    private val desktopWindowDropTarget: Rect
        get() =
            Rect(windowBounds).apply {
                if (deviceConfig.isLandscape) {
                    inset(
                        /* dx= */ desktopWindowDropTargetPaddingLarge,
                        /* dy= */ desktopWindowDropTargetPaddingSmall
                    )
                } else {
                    inset(
                        /* dx= */ desktopWindowDropTargetPaddingSmall,
                        /* dy= */ desktopWindowDropTargetPaddingLarge
                    )
                }
            }

    private val expandedViewDropTargetLeft: Rect
        get() =
            Rect(
                expandedViewDropTargetPaddingHorizontal,
                windowBounds.bottom -
                    expandedViewDropTargetPaddingBottom -
                    expandedViewDropTargetHeight,
                expandedViewDropTargetWidth + expandedViewDropTargetPaddingHorizontal,
                windowBounds.bottom - expandedViewDropTargetPaddingBottom
            )

    private val expandedViewDropTargetRight: Rect
        get() =
            Rect(
                windowBounds.right -
                    expandedViewDropTargetPaddingHorizontal -
                    expandedViewDropTargetWidth,
                windowBounds.bottom -
                    expandedViewDropTargetPaddingBottom -
                    expandedViewDropTargetHeight,
                windowBounds.right - expandedViewDropTargetPaddingHorizontal,
                windowBounds.bottom - expandedViewDropTargetPaddingBottom
            )

    init {
        onConfigurationUpdated()
    }

    /** Updates all dimensions after a configuration change. */
    fun onConfigurationUpdated() {
        // TODO b/396539130: Use the shared xml resources once we can easily access them from
        //  launcher
        dismissDragZoneSize =
            if (deviceConfig.isSmallTablet) 140.dpToPx() else 200.dpToPx()
        bubbleDragZoneTabletSize = 200.dpToPx()
        bubbleDragZoneFoldableSize = 140.dpToPx()
        fullScreenDragZoneWidth = 512.dpToPx()
        fullScreenDragZoneHeight = 44.dpToPx()
        desktopWindowDragZoneWidth = 880.dpToPx()
        desktopWindowDragZoneHeight = 300.dpToPx()
        desktopWindowFromExpandedViewDragZoneWidth = 200.dpToPx()
        desktopWindowFromExpandedViewDragZoneHeight = 350.dpToPx()
        splitFromBubbleDragZoneHeight = 100.dpToPx()
        splitFromBubbleDragZoneWidth = 60.dpToPx()
        hSplitFromExpandedViewDragZoneWidth = 60.dpToPx()
        vSplitFromExpandedViewDragZoneWidth = 200.dpToPx()
        vSplitFromExpandedViewDragZoneHeightTablet = 285.dpToPx()
        vSplitFromExpandedViewDragZoneHeightFoldTall = 150.dpToPx()
        vSplitFromExpandedViewDragZoneHeightFoldShort = 100.dpToPx()
        fullScreenDropTargetPadding = 20.dpToPx()
        desktopWindowDropTargetPaddingSmall = 100.dpToPx()
        desktopWindowDropTargetPaddingLarge = 130.dpToPx()
        expandedViewDropTargetWidth = 330.dpToPx()
        expandedViewDropTargetHeight = 578.dpToPx()
        expandedViewDropTargetPaddingBottom = 108.dpToPx()
        expandedViewDropTargetPaddingHorizontal = 24.dpToPx()
    }

    private fun Context.resolveDimension(@DimenRes dimension: Int) =
        resources.getDimensionPixelSize(dimension)

    private fun Int.dpToPx() =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                this.toFloat(),
                context.resources.displayMetrics
            )
            .toInt()

    /**
     * Creates the list of drag zones for the dragged object.
     *
     * Drag zones may have overlap, but the list is sorted by priority where the first drag zone has
     * the highest priority so it should be checked first.
     */
    fun createSortedDragZones(draggedObject: DraggedObject): List<DragZone> {
        val dragZones = mutableListOf<DragZone>()
        when (draggedObject) {
            is DraggedObject.BubbleBar -> {
                dragZones.add(createDismissDragZone())
                dragZones.addAll(createBubbleHalfScreenDragZones())
            }
            is DraggedObject.Bubble -> {
                dragZones.add(createDismissDragZone())
                dragZones.addAll(createBubbleCornerDragZones())
                dragZones.add(createFullScreenDragZone())
                if (shouldShowDesktopWindowDragZones()) {
                    dragZones.add(createDesktopWindowDragZoneForBubble())
                }
                dragZones.addAll(createSplitScreenDragZonesForBubble())
            }
            is DraggedObject.ExpandedView -> {
                dragZones.add(createDismissDragZone())
                dragZones.add(createFullScreenDragZone())
                if (shouldShowDesktopWindowDragZones()) {
                    dragZones.add(createDesktopWindowDragZoneForExpandedView())
                }
                if (deviceConfig.isSmallTablet) {
                    dragZones.addAll(createSplitScreenDragZonesForExpandedViewOnFoldable())
                } else {
                    dragZones.addAll(createSplitScreenDragZonesForExpandedViewOnTablet())
                }
                dragZones.addAll(createBubbleHalfScreenDragZones())
            }
        }
        return dragZones
    }

    private fun createDismissDragZone(): DragZone {
        return DragZone.Dismiss(
            bounds =
                Rect(
                    windowBounds.right / 2 - dismissDragZoneSize / 2,
                    windowBounds.bottom - dismissDragZoneSize,
                    windowBounds.right / 2 + dismissDragZoneSize / 2,
                    windowBounds.bottom
                )
        )
    }

    private fun createBubbleCornerDragZones(): List<DragZone> {
        val dragZoneSize =
            if (deviceConfig.isSmallTablet) {
                bubbleDragZoneFoldableSize
            } else {
                bubbleDragZoneTabletSize
            }
        return listOf(
            DragZone.Bubble.Left(
                bounds =
                    Rect(0, windowBounds.bottom - dragZoneSize, dragZoneSize, windowBounds.bottom),
                dropTarget = expandedViewDropTargetLeft,
            ),
            DragZone.Bubble.Right(
                bounds =
                    Rect(
                        windowBounds.right - dragZoneSize,
                        windowBounds.bottom - dragZoneSize,
                        windowBounds.right,
                        windowBounds.bottom,
                    ),
                dropTarget = expandedViewDropTargetRight,
            )
        )
    }

    private fun createBubbleHalfScreenDragZones(): List<DragZone> {
        return listOf(
            DragZone.Bubble.Left(
                bounds = Rect(0, 0, windowBounds.right / 2, windowBounds.bottom),
                dropTarget = expandedViewDropTargetLeft,
            ),
            DragZone.Bubble.Right(
                bounds =
                    Rect(
                        windowBounds.right / 2,
                        0,
                        windowBounds.right,
                        windowBounds.bottom,
                    ),
                dropTarget = expandedViewDropTargetRight,
            )
        )
    }

    private fun createFullScreenDragZone(): DragZone {
        return DragZone.FullScreen(
            bounds =
                Rect(
                    windowBounds.right / 2 - fullScreenDragZoneWidth / 2,
                    0,
                    windowBounds.right / 2 + fullScreenDragZoneWidth / 2,
                    fullScreenDragZoneHeight
                ),
            dropTarget = fullScreenDropTarget
        )
    }

    private fun shouldShowDesktopWindowDragZones() =
        !deviceConfig.isSmallTablet && desktopWindowModeChecker.isSupported()

    private fun createDesktopWindowDragZoneForBubble(): DragZone {
        return DragZone.DesktopWindow(
            bounds =
                if (deviceConfig.isLandscape) {
                    Rect(
                        windowBounds.right / 2 - desktopWindowDragZoneWidth / 2,
                        windowBounds.bottom / 2 - desktopWindowDragZoneHeight / 2,
                        windowBounds.right / 2 + desktopWindowDragZoneWidth / 2,
                        windowBounds.bottom / 2 + desktopWindowDragZoneHeight / 2
                    )
                } else {
                    Rect(
                        0,
                        windowBounds.bottom / 2 - desktopWindowDragZoneHeight / 2,
                        windowBounds.right,
                        windowBounds.bottom / 2 + desktopWindowDragZoneHeight / 2
                    )
                },
            dropTarget = desktopWindowDropTarget
        )
    }

    private fun createDesktopWindowDragZoneForExpandedView(): DragZone {
        return DragZone.DesktopWindow(
            bounds =
                Rect(
                    windowBounds.right / 2 - desktopWindowFromExpandedViewDragZoneWidth / 2,
                    windowBounds.bottom / 2 - desktopWindowFromExpandedViewDragZoneHeight / 2,
                    windowBounds.right / 2 + desktopWindowFromExpandedViewDragZoneWidth / 2,
                    windowBounds.bottom / 2 + desktopWindowFromExpandedViewDragZoneHeight / 2
                ),
            dropTarget = desktopWindowDropTarget
        )
    }

    private fun createSplitScreenDragZonesForBubble(): List<DragZone> {
        // for foldables in landscape mode or tables in portrait modes we have vertical split drag
        // zones. otherwise we have horizontal split drag zones.
        val isVerticalSplit = deviceConfig.isSmallTablet == deviceConfig.isLandscape
        return if (isVerticalSplit) {
            when (splitScreenModeChecker.getSplitScreenMode()) {
                SplitScreenMode.UNSUPPORTED -> emptyList()
                SplitScreenMode.SPLIT_50_50,
                SplitScreenMode.NONE ->
                    listOf(
                        DragZone.Split.Top(
                            bounds = Rect(0, 0, windowBounds.right, windowBounds.bottom / 2),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    0,
                                    windowBounds.bottom / 2,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
                SplitScreenMode.SPLIT_90_10 -> {
                    listOf(
                        DragZone.Split.Top(
                            bounds =
                                Rect(
                                    0,
                                    0,
                                    windowBounds.right,
                                    windowBounds.bottom - splitFromBubbleDragZoneHeight
                                ),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    0,
                                    windowBounds.bottom - splitFromBubbleDragZoneHeight,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
                }
                SplitScreenMode.SPLIT_10_90 -> {
                    listOf(
                        DragZone.Split.Top(
                            bounds = Rect(0, 0, windowBounds.right, splitFromBubbleDragZoneHeight),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    0,
                                    splitFromBubbleDragZoneHeight,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
                }
            }
        } else {
            when (splitScreenModeChecker.getSplitScreenMode()) {
                SplitScreenMode.UNSUPPORTED -> emptyList()
                SplitScreenMode.SPLIT_50_50,
                SplitScreenMode.NONE ->
                    listOf(
                        DragZone.Split.Left(
                            bounds = Rect(0, 0, windowBounds.right / 2, windowBounds.bottom),
                        ),
                        DragZone.Split.Right(
                            bounds =
                                Rect(
                                    windowBounds.right / 2,
                                    0,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
                SplitScreenMode.SPLIT_90_10 ->
                    listOf(
                        DragZone.Split.Left(
                            bounds =
                                Rect(
                                    0,
                                    0,
                                    windowBounds.right - splitFromBubbleDragZoneWidth,
                                    windowBounds.bottom
                                ),
                        ),
                        DragZone.Split.Right(
                            bounds =
                                Rect(
                                    windowBounds.right - splitFromBubbleDragZoneWidth,
                                    0,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
                SplitScreenMode.SPLIT_10_90 ->
                    listOf(
                        DragZone.Split.Left(
                            bounds = Rect(0, 0, splitFromBubbleDragZoneWidth, windowBounds.bottom),
                        ),
                        DragZone.Split.Right(
                            bounds =
                                Rect(
                                    splitFromBubbleDragZoneWidth,
                                    0,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
            }
        }
    }

    private fun createSplitScreenDragZonesForExpandedViewOnTablet(): List<DragZone> {
        return if (deviceConfig.isLandscape) {
            createHorizontalSplitDragZonesForExpandedView()
        } else {
            // for tablets in portrait mode, split drag zones appear below the full screen drag zone
            // for the top split zone, and above the dismiss zone. Both are horizontally centered.
            val splitZoneLeft = windowBounds.right / 2 - vSplitFromExpandedViewDragZoneWidth / 2
            val splitZoneRight = splitZoneLeft + vSplitFromExpandedViewDragZoneWidth
            val bottomSplitZoneBottom = windowBounds.bottom - dismissDragZoneSize
            listOf(
                DragZone.Split.Top(
                    bounds =
                        Rect(
                            splitZoneLeft,
                            fullScreenDragZoneHeight,
                            splitZoneRight,
                            fullScreenDragZoneHeight + vSplitFromExpandedViewDragZoneHeightTablet
                        ),
                ),
                DragZone.Split.Bottom(
                    bounds =
                        Rect(
                            splitZoneLeft,
                            bottomSplitZoneBottom - vSplitFromExpandedViewDragZoneHeightTablet,
                            splitZoneRight,
                            bottomSplitZoneBottom
                        ),
                )
            )
        }
    }

    private fun createSplitScreenDragZonesForExpandedViewOnFoldable(): List<DragZone> {
        return if (deviceConfig.isLandscape) {
            // vertical split drag zones are aligned with the full screen drag zone width
            val splitZoneLeft = windowBounds.right / 2 - fullScreenDragZoneWidth / 2
            when (splitScreenModeChecker.getSplitScreenMode()) {
                SplitScreenMode.UNSUPPORTED -> emptyList()
                SplitScreenMode.SPLIT_50_50,
                SplitScreenMode.NONE ->
                    listOf(
                        DragZone.Split.Top(
                            bounds =
                                Rect(
                                    splitZoneLeft,
                                    fullScreenDragZoneHeight,
                                    splitZoneLeft + fullScreenDragZoneWidth,
                                    fullScreenDragZoneHeight +
                                        vSplitFromExpandedViewDragZoneHeightFoldTall
                                ),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    splitZoneLeft,
                                    windowBounds.bottom / 2,
                                    splitZoneLeft + fullScreenDragZoneWidth,
                                    windowBounds.bottom / 2 +
                                        vSplitFromExpandedViewDragZoneHeightFoldTall
                                ),
                        )
                    )
                SplitScreenMode.SPLIT_10_90 ->
                    listOf(
                        DragZone.Split.Top(
                            bounds =
                                Rect(
                                    0,
                                    0,
                                    windowBounds.right,
                                    vSplitFromExpandedViewDragZoneHeightFoldShort
                                ),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    splitZoneLeft,
                                    vSplitFromExpandedViewDragZoneHeightFoldShort,
                                    splitZoneLeft + fullScreenDragZoneWidth,
                                    vSplitFromExpandedViewDragZoneHeightFoldShort +
                                        vSplitFromExpandedViewDragZoneHeightFoldTall
                                ),
                        )
                    )
                SplitScreenMode.SPLIT_90_10 ->
                    listOf(
                        DragZone.Split.Top(
                            bounds =
                                Rect(
                                    splitZoneLeft,
                                    fullScreenDragZoneHeight,
                                    splitZoneLeft + fullScreenDragZoneWidth,
                                    fullScreenDragZoneHeight +
                                        vSplitFromExpandedViewDragZoneHeightFoldTall
                                ),
                        ),
                        DragZone.Split.Bottom(
                            bounds =
                                Rect(
                                    0,
                                    windowBounds.bottom -
                                        vSplitFromExpandedViewDragZoneHeightFoldShort,
                                    windowBounds.right,
                                    windowBounds.bottom
                                ),
                        )
                    )
            }
        } else {
            // horizontal split drag zones
            createHorizontalSplitDragZonesForExpandedView()
        }
    }

    private fun createHorizontalSplitDragZonesForExpandedView(): List<DragZone> {
        // horizontal split drag zones for expanded view appear on the edges of the screen from the
        // top down until the dismiss drag zone height
        return listOf(
            DragZone.Split.Left(
                bounds =
                    Rect(
                        0,
                        0,
                        hSplitFromExpandedViewDragZoneWidth,
                        windowBounds.bottom - dismissDragZoneSize
                    ),
            ),
            DragZone.Split.Right(
                bounds =
                    Rect(
                        windowBounds.right - hSplitFromExpandedViewDragZoneWidth,
                        0,
                        windowBounds.right,
                        windowBounds.bottom - dismissDragZoneSize
                    ),
            )
        )
    }

    /** Checks the current split screen mode. */
    fun interface SplitScreenModeChecker {
        enum class SplitScreenMode {
            NONE,
            SPLIT_50_50,
            SPLIT_10_90,
            SPLIT_90_10,
            UNSUPPORTED
        }

        fun getSplitScreenMode(): SplitScreenMode
    }

    /** Checks if desktop window mode is supported. */
    fun interface DesktopWindowModeChecker {
        fun isSupported(): Boolean
    }
}
