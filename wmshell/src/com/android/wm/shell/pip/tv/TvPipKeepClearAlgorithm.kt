/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.pip.tv

import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.util.Size
import android.view.Gravity
import com.android.wm.shell.common.pip.PipBoundsState
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_BOTTOM
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_LEFT
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_TOP
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_PIP_MARGINS = 48
private const val RELAX_DEPTH = 1
private const val DEFAULT_MAX_RESTRICTED_DISTANCE_FRACTION = 0.15

/**
 * This class calculates an appropriate position for a Picture-In-Picture (PiP) window, taking
 * into account app defined keep clear areas.
 */
class TvPipKeepClearAlgorithm() {
    /**
     * Result of the positioning algorithm.
     *
     * @param bounds The bounds the PiP should be placed at
     * @param anchorBounds The bounds of the PiP anchor position
     *     (where the PiP would be placed if there were no keep clear areas)
     * @param stashType Where the PiP has been stashed, if at all
     * @param unstashDestinationBounds If stashed, the PiP should move to this position when
     *     unstashing.
     * @param triggerStash Whether this placement should trigger the PiP to stash, or extend
     *     the unstash timeout if already stashed.
     */
    data class Placement(
            val bounds: Rect,
            val anchorBounds: Rect,
            @PipBoundsState.StashType val stashType: Int = STASH_TYPE_NONE,
            val unstashDestinationBounds: Rect? = null,
            val triggerStash: Boolean = false
    ) {
        /** Bounds to use if the PiP should not be stashed. */
        fun getUnstashedBounds() = unstashDestinationBounds ?: bounds
    }

    /**  The size of the screen */
    private var screenSize = Size(0, 0)

    /** The bounds the PiP is allowed to move in */
    private var movementBounds = Rect()

    /** Padding to add between a keep clear area that caused the PiP to move and the PiP */
    var pipAreaPadding = DEFAULT_PIP_MARGINS

    /** The distance the PiP peeks into the screen when stashed */
    var stashOffset = DEFAULT_PIP_MARGINS

    /** The fraction of screen width/height restricted keep clear areas can move the PiP */
    var maxRestrictedDistanceFraction = DEFAULT_MAX_RESTRICTED_DISTANCE_FRACTION

    private var pipGravity = Gravity.BOTTOM or Gravity.RIGHT
    private var transformedScreenBounds = Rect()
    private var transformedMovementBounds = Rect()

    private var lastAreasOverlappingUnstashPosition: Set<Rect> = emptySet()

    /** Spaces around the PiP that we should leave space for when placing the PiP. Permanent PiP
     * decorations are relevant for calculating intersecting keep clear areas */
    private var pipPermanentDecorInsets = Insets.NONE

    /**
     * Calculates the position the PiP should be placed at, taking into consideration the
     * given keep clear areas.
     *
     * Restricted keep clear areas can move the PiP only by a limited amount, and may be ignored
     * if there is no space for the PiP to move to.
     * Apps holding the permission [android.Manifest.permission.USE_UNRESTRICTED_KEEP_CLEAR_AREAS]
     * can declare unrestricted keep clear areas, which can move the PiP farther and placement will
     * always try to respect these areas.
     *
     * If no free space the PiP is allowed to move to can be found, a stashed position is returned
     * as [Placement.bounds], along with a position to move to when the PiP unstashes
     * as [Placement.unstashDestinationBounds].
     *
     * @param pipSize The size of the PiP window
     * @param restrictedAreas The restricted keep clear areas
     * @param unrestrictedAreas The unrestricted keep clear areas
     *
     */
    fun calculatePipPosition(
        pipSize: Size,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): Placement {
        val transformedRestrictedAreas = transformAndFilterAreas(restrictedAreas)
        val transformedUnrestrictedAreas = transformAndFilterAreas(unrestrictedAreas)

        val pipSizeWithAllDecors = addDecors(pipSize)
        val pipAnchorBoundsWithDecors =
            getNormalPipAnchorBounds(pipSizeWithAllDecors, transformedMovementBounds)

        val result = calculatePipPositionTransformed(
            pipAnchorBoundsWithDecors,
            transformedRestrictedAreas,
            transformedUnrestrictedAreas
        )

        val pipBounds = removePermanentDecors(fromTransformedSpace(result.bounds))
        val anchorBounds = removePermanentDecors(fromTransformedSpace(result.anchorBounds))
        val unstashedDestBounds = result.unstashDestinationBounds?.let {
            removePermanentDecors(fromTransformedSpace(it))
        }

        return Placement(
            pipBounds,
            anchorBounds,
            getStashType(pipBounds, unstashedDestBounds),
            unstashedDestBounds,
            result.triggerStash
        )
    }

    /**
     * Filters out areas that encompass the entire movement bounds and returns them mapped to
     * the base case space.
     *
     * Areas encompassing the entire movement bounds can occur when a full-screen View gets focused,
     * but we don't want this to cause the PiP to get stashed.
     */
    private fun transformAndFilterAreas(areas: Set<Rect>): Set<Rect> {
        return areas.mapNotNullTo(mutableSetOf()) {
            when {
                it.contains(movementBounds) -> null
                else -> toTransformedSpace(it)
            }
        }
    }

    /**
     * Calculates the position the PiP should be placed at, taking into consideration the
     * given keep clear areas.
     * All parameters are transformed from screen space to the base case space, where the PiP
     * anchor is in the bottom right corner / on the right side.
     *
     * @see [calculatePipPosition]
     */
    private fun calculatePipPositionTransformed(
        pipAnchorBounds: Rect,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): Placement {
        // If PiP is not covered by any keep clear areas, we can leave it at the anchor bounds
        val keepClearAreas = restrictedAreas + unrestrictedAreas
        if (keepClearAreas.none { it.intersects(pipAnchorBounds) }) {
            lastAreasOverlappingUnstashPosition = emptySet()
            return Placement(pipAnchorBounds, pipAnchorBounds)
        }

        // First try to find a free position to move to
        val freeMovePos = findFreeMovePosition(pipAnchorBounds, restrictedAreas, unrestrictedAreas)
        if (freeMovePos != null) {
            lastAreasOverlappingUnstashPosition = emptySet()
            return Placement(freeMovePos, pipAnchorBounds)
        }

        // If no free position is found, we have to stash the PiP.
        // Find the position the PiP should return to once it unstashes by doing a relaxed
        // search, or ignoring restricted areas, or returning to the anchor position
        val unstashBounds =
            findRelaxedMovePosition(pipAnchorBounds, restrictedAreas, unrestrictedAreas)
                ?: findFreeMovePosition(pipAnchorBounds, emptySet(), unrestrictedAreas)
                ?: pipAnchorBounds

        val areasOverlappingUnstashPosition =
            keepClearAreas.filterTo(mutableSetOf()) { it.intersects(unstashBounds) }
        val areasOverlappingUnstashPositionChanged =
            !lastAreasOverlappingUnstashPosition.containsAll(areasOverlappingUnstashPosition)
        lastAreasOverlappingUnstashPosition = areasOverlappingUnstashPosition

        val stashedBounds = getNearbyStashedPosition(unstashBounds, keepClearAreas)
        return Placement(
            stashedBounds,
            pipAnchorBounds,
            getStashType(stashedBounds, unstashBounds),
            unstashBounds,
            areasOverlappingUnstashPositionChanged
        )
    }

    @PipBoundsState.StashType
    private fun getStashType(stashedBounds: Rect, unstashedDestBounds: Rect?): Int {
        if (unstashedDestBounds == null) {
            return STASH_TYPE_NONE
        }
        return when {
            stashedBounds.left < unstashedDestBounds.left -> STASH_TYPE_LEFT
            stashedBounds.right > unstashedDestBounds.right -> STASH_TYPE_RIGHT
            stashedBounds.top < unstashedDestBounds.top -> STASH_TYPE_TOP
            stashedBounds.bottom > unstashedDestBounds.bottom -> STASH_TYPE_BOTTOM
            else -> STASH_TYPE_NONE
        }
    }

    private fun findRelaxedMovePosition(
        pipAnchorBounds: Rect,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): Rect? {
        if (RELAX_DEPTH <= 0) {
            // relaxed search disabled
            return null
        }

        return findRelaxedMovePosition(
            RELAX_DEPTH,
            pipAnchorBounds,
            restrictedAreas.toMutableSet(),
            unrestrictedAreas
        )
    }

    private fun findRelaxedMovePosition(
        depth: Int,
        pipAnchorBounds: Rect,
        restrictedAreas: MutableSet<Rect>,
        unrestrictedAreas: Set<Rect>
    ): Rect? {
        if (depth == 0) {
            return findFreeMovePosition(pipAnchorBounds, restrictedAreas, unrestrictedAreas)
        }

        val candidates = mutableListOf<Rect>()
        val areasToExclude = restrictedAreas.toList()
        for (area in areasToExclude) {
            restrictedAreas.remove(area)
            val candidate = findRelaxedMovePosition(
                depth - 1,
                pipAnchorBounds,
                restrictedAreas,
                unrestrictedAreas
            )
            restrictedAreas.add(area)

            if (candidate != null) {
                candidates.add(candidate)
            }
        }
        return candidates.minByOrNull { candidateCost(it, pipAnchorBounds) }
    }

    /** Cost function to evaluate candidate bounds */
    private fun candidateCost(candidateBounds: Rect, pipAnchorBounds: Rect): Int {
        // squared euclidean distance of corresponding rect corners
        val dx = candidateBounds.left - pipAnchorBounds.left
        val dy = candidateBounds.top - pipAnchorBounds.top
        return dx * dx + dy * dy
    }

    private fun findFreeMovePosition(
        pipAnchorBounds: Rect,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): Rect? {
        val movementBounds = transformedMovementBounds
        val candidateEdgeRects = mutableListOf<Rect>()
        val maxRestrictedXDistanceFraction =
                if (isPipAnchoredToCorner()) maxRestrictedDistanceFraction else 0.0
        val minRestrictedLeft =
                pipAnchorBounds.right - screenSize.width * maxRestrictedXDistanceFraction

        candidateEdgeRects.add(
            movementBounds.offsetCopy(movementBounds.width() + pipAreaPadding, 0)
        )
        candidateEdgeRects.addAll(unrestrictedAreas)
        candidateEdgeRects.addAll(restrictedAreas.filter { it.left >= minRestrictedLeft })

        // throw out edges that are too close to the left screen edge to fit the PiP
        val minLeft = movementBounds.left + pipAnchorBounds.width()
        candidateEdgeRects.retainAll { it.left - pipAreaPadding > minLeft }

        val maxRestrictedDY = (screenSize.height * maxRestrictedDistanceFraction).roundToInt()

        val candidateBounds = mutableListOf<Rect>()
        for (edgeRect in candidateEdgeRects) {
            val edge = edgeRect.left - pipAreaPadding
            val dx = (edge - pipAnchorBounds.width()) - pipAnchorBounds.left
            val candidatePipBounds = pipAnchorBounds.offsetCopy(dx, 0)
            val searchUp = true
            val searchDown = !isPipAnchoredToCorner()

            if (searchUp) {
                val event = findMinMoveUp(candidatePipBounds, restrictedAreas, unrestrictedAreas)
                val padding = if (event.start) 0 else pipAreaPadding
                val dy = event.pos - pipAnchorBounds.bottom - padding
                val maxDY = if (event.unrestricted) movementBounds.height() else maxRestrictedDY
                val candidate = pipAnchorBounds.offsetCopy(dx, dy)
                val isOnScreen = candidate.top > movementBounds.top
                val hangingMidAir = !candidate.intersectsY(edgeRect)
                if (isOnScreen && abs(dy) <= maxDY && !hangingMidAir) {
                    candidateBounds.add(candidate)
                }
            }

            if (searchDown) {
                val event = findMinMoveDown(candidatePipBounds, restrictedAreas, unrestrictedAreas)
                val padding = if (event.start) 0 else pipAreaPadding
                val dy = event.pos - pipAnchorBounds.top + padding
                val maxDY = if (event.unrestricted) movementBounds.height() else maxRestrictedDY
                val candidate = pipAnchorBounds.offsetCopy(dx, dy)
                val isOnScreen = candidate.bottom < movementBounds.bottom
                val hangingMidAir = !candidate.intersectsY(edgeRect)
                if (isOnScreen && abs(dy) <= maxDY && !hangingMidAir) {
                    candidateBounds.add(candidate)
                }
            }
        }

        return candidateBounds.minByOrNull { candidateCost(it, pipAnchorBounds) }
    }

    private fun getNearbyStashedPosition(bounds: Rect, keepClearAreas: Set<Rect>): Rect {
        val screenBounds = transformedScreenBounds
        val stashCandidates = mutableListOf<Rect>()
        val areasOverlappingPipX = keepClearAreas.filter { it.intersectsX(bounds) }
        val areasOverlappingPipY = keepClearAreas.filter { it.intersectsY(bounds) }

        if (areasOverlappingPipX.isNotEmpty()) {
            if (screenBounds.bottom - bounds.bottom <= bounds.top - screenBounds.top) {
                val fullStashTop = screenBounds.bottom - stashOffset

                val maxBottom = areasOverlappingPipX.maxByOrNull { it.bottom }!!.bottom
                val partialStashTop = maxBottom + pipAreaPadding

                val newTop = min(fullStashTop, partialStashTop)
                if (newTop > bounds.top) {
                    val downPosition = Rect(bounds)
                    downPosition.offsetTo(bounds.left, newTop)
                    stashCandidates += downPosition
                }
            }
            if (screenBounds.bottom - bounds.bottom >= bounds.top - screenBounds.top) {
                val fullStashBottom = screenBounds.top - bounds.height() + stashOffset

                val minTop = areasOverlappingPipX.minByOrNull { it.top }!!.top
                val partialStashBottom = minTop - bounds.height() - pipAreaPadding

                val newTop = max(fullStashBottom, partialStashBottom)
                if (newTop < bounds.top) {
                    val upPosition = Rect(bounds)
                    upPosition.offsetTo(bounds.left, newTop)
                    stashCandidates += upPosition
                }
            }
        }

        if (areasOverlappingPipY.isNotEmpty()) {
            if (screenBounds.right - bounds.right <= bounds.left - screenBounds.left) {
                val fullStashRight = screenBounds.right - stashOffset

                val maxRight = areasOverlappingPipY.maxByOrNull { it.right }!!.right
                val partialStashRight = maxRight + pipAreaPadding

                val newLeft = min(fullStashRight, partialStashRight)
                if (newLeft > bounds.left) {
                    val rightPosition = Rect(bounds)
                    rightPosition.offsetTo(newLeft, bounds.top)
                    stashCandidates += rightPosition
                }
            }
            if (screenBounds.right - bounds.right >= bounds.left - screenBounds.left) {
                val fullStashLeft = screenBounds.left - bounds.width() + stashOffset

                val minLeft = areasOverlappingPipY.minByOrNull { it.left }!!.left
                val partialStashLeft = minLeft - bounds.width() - pipAreaPadding

                val newLeft = max(fullStashLeft, partialStashLeft)
                if (newLeft < bounds.left) {
                    val leftPosition = Rect(bounds)
                    leftPosition.offsetTo(newLeft, bounds.top)
                    stashCandidates += leftPosition
                }
            }
        }

        return stashCandidates.minByOrNull {
            val dx = abs(it.left - bounds.left)
            val dy = abs(it.top - bounds.top)
            return@minByOrNull dx + dy
        } ?: bounds
    }

    /**
     * Updates the size of the screen.
     *
     * @param size The new size of the screen
     */
    fun setScreenSize(size: Size) {
        if (screenSize == size) {
            return
        }

        screenSize = size
        transformedScreenBounds =
            toTransformedSpace(Rect(0, 0, screenSize.width, screenSize.height))
        transformedMovementBounds = toTransformedSpace(transformedMovementBounds)
    }

    /**
     * Updates the bounds within which the PiP is allowed to move.
     *
     * @param bounds The new movement bounds
     */
    fun setMovementBounds(bounds: Rect) {
        if (movementBounds == bounds) {
            return
        }

        movementBounds.set(bounds)
        transformedMovementBounds = toTransformedSpace(movementBounds)
    }

    /**
     * Sets the corner/side of the PiP's home position.
     */
    fun setGravity(gravity: Int) {
        if (pipGravity == gravity) return

        pipGravity = gravity
        transformedScreenBounds =
            toTransformedSpace(Rect(0, 0, screenSize.width, screenSize.height))
        transformedMovementBounds = toTransformedSpace(movementBounds)
    }

    fun setPipPermanentDecorInsets(insets: Insets) {
        pipPermanentDecorInsets = insets
    }

    /**
     * @param open Whether this event marks the opening of an occupied segment
     * @param pos The coordinate of this event
     * @param unrestricted Whether this event was generated by an unrestricted keep clear area
     * @param start Marks the special start event. Earlier events are skipped when sweeping
     */
    data class SweepLineEvent(
        val open: Boolean,
        val pos: Int,
        val unrestricted: Boolean,
        val start: Boolean = false
    )

    /**
     * Returns a [SweepLineEvent] representing the minimal move up from [pipBounds] that clears
     * the given keep clear areas.
     */
    private fun findMinMoveUp(
        pipBounds: Rect,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): SweepLineEvent {
        val events = mutableListOf<SweepLineEvent>()
        val generateEvents: (Boolean) -> (Rect) -> Unit = { unrestricted ->
            { area ->
                if (pipBounds.intersectsX(area)) {
                    events.add(SweepLineEvent(true, area.bottom, unrestricted))
                    events.add(SweepLineEvent(false, area.top, unrestricted))
                }
            }
        }

        restrictedAreas.forEach(generateEvents(false))
        unrestrictedAreas.forEach(generateEvents(true))

        return sweepLineFindEarliestGap(
            events,
            pipBounds.height() + pipAreaPadding,
            pipBounds.bottom,
            pipBounds.height()
        )
    }

    /**
     * Returns a [SweepLineEvent] representing the minimal move down from [pipBounds] that clears
     * the given keep clear areas.
     */
    private fun findMinMoveDown(
        pipBounds: Rect,
        restrictedAreas: Set<Rect>,
        unrestrictedAreas: Set<Rect>
    ): SweepLineEvent {
        val events = mutableListOf<SweepLineEvent>()
        val generateEvents: (Boolean) -> (Rect) -> Unit = { unrestricted ->
            { area ->
                if (pipBounds.intersectsX(area)) {
                    events.add(SweepLineEvent(true, -area.top, unrestricted))
                    events.add(SweepLineEvent(false, -area.bottom, unrestricted))
                }
            }
        }

        restrictedAreas.forEach(generateEvents(false))
        unrestrictedAreas.forEach(generateEvents(true))

        val earliestEvent = sweepLineFindEarliestGap(
            events,
            pipBounds.height() + pipAreaPadding,
            -pipBounds.top,
            pipBounds.height()
        )

        return earliestEvent.copy(pos = -earliestEvent.pos)
    }

    /**
     * Takes a list of events representing the starts & ends of occupied segments, and
     * returns the earliest event whose position is unoccupied and has [gapSize] distance to the
     * next event.
     *
     * @param events List of [SweepLineEvent] representing occupied segments
     * @param gapSize Size of the gap to search for
     * @param startPos The position to start the search on.
     *     Inserts a special event marked with [SweepLineEvent.start].
     * @param startGapSize Used instead of [gapSize] for the start event
     */
    private fun sweepLineFindEarliestGap(
        events: MutableList<SweepLineEvent>,
        gapSize: Int,
        startPos: Int,
        startGapSize: Int
    ): SweepLineEvent {
        events.add(
            SweepLineEvent(
                open = false,
                pos = startPos,
                unrestricted = true,
                start = true
            )
        )
        events.sortBy { -it.pos }

        // sweep
        var openCount = 0
        var i = 0
        while (i < events.size) {
            val event = events[i]
            if (!event.start) {
                if (event.open) {
                    openCount++
                } else {
                    openCount--
                }
            }

            if (openCount == 0) {
                // check if placement is possible
                val candidate = event.pos
                if (candidate > startPos) {
                    i++
                    continue
                }

                val eventGapSize = if (event.start) startGapSize else gapSize
                val nextEvent = events.getOrNull(i + 1)
                if (nextEvent == null || nextEvent.pos < candidate - eventGapSize) {
                    return event
                }
            }
            i++
        }

        return events.last()
    }

    private fun shouldTransformFlipX(): Boolean {
        return when (pipGravity) {
            (Gravity.TOP), (Gravity.TOP or Gravity.CENTER_HORIZONTAL) -> true
            (Gravity.TOP or Gravity.LEFT) -> true
            (Gravity.LEFT), (Gravity.LEFT or Gravity.CENTER_VERTICAL) -> true
            (Gravity.BOTTOM or Gravity.LEFT) -> true
            else -> false
        }
    }

    private fun shouldTransformFlipY(): Boolean {
        return when (pipGravity) {
            (Gravity.TOP or Gravity.LEFT) -> true
            (Gravity.TOP or Gravity.RIGHT) -> true
            else -> false
        }
    }

    private fun shouldTransformRotate(): Boolean {
        val horizontalGravity = pipGravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val leftOrRight = horizontalGravity == Gravity.LEFT || horizontalGravity == Gravity.RIGHT

        if (leftOrRight) return false
        return when (pipGravity and Gravity.VERTICAL_GRAVITY_MASK) {
            (Gravity.TOP) -> true
            (Gravity.BOTTOM) -> true
            else -> false
        }
    }

    /**
     * Transforms the given rect from screen space into the base case space, where the PiP
     * anchor is positioned in the bottom right corner or on the right side (for expanded PiP).
     *
     * @see [fromTransformedSpace]
     */
    private fun toTransformedSpace(r: Rect): Rect {
        var screenWidth = screenSize.width
        var screenHeight = screenSize.height

        val tl = Point(r.left, r.top)
        val tr = Point(r.right, r.top)
        val br = Point(r.right, r.bottom)
        val bl = Point(r.left, r.bottom)
        val corners = arrayOf(tl, tr, br, bl)

        // rotate first (CW)
        if (shouldTransformRotate()) {
            corners.forEach { p ->
                val px = p.x
                val py = p.y
                p.x = py
                p.y = -px
                p.y += screenWidth // shift back screen into positive quadrant
            }
            screenWidth = screenSize.height
            screenHeight = screenSize.width
        }

        // flip second
        corners.forEach {
            if (shouldTransformFlipX()) it.x = screenWidth - it.x
            if (shouldTransformFlipY()) it.y = screenHeight - it.y
        }

        val top = corners.minByOrNull { it.y }!!.y
        val right = corners.maxByOrNull { it.x }!!.x
        val bottom = corners.maxByOrNull { it.y }!!.y
        val left = corners.minByOrNull { it.x }!!.x

        return Rect(left, top, right, bottom)
    }

    /**
     * Transforms the given rect from the base case space, where the PiP anchor is positioned in
     * the bottom right corner or on the right side, back into screen space.
     *
     * @see [toTransformedSpace]
     */
    private fun fromTransformedSpace(r: Rect): Rect {
        val rotate = shouldTransformRotate()
        val transformedScreenWidth = if (rotate) screenSize.height else screenSize.width
        val transformedScreenHeight = if (rotate) screenSize.width else screenSize.height

        val tl = Point(r.left, r.top)
        val tr = Point(r.right, r.top)
        val br = Point(r.right, r.bottom)
        val bl = Point(r.left, r.bottom)
        val corners = arrayOf(tl, tr, br, bl)

        // flip first
        corners.forEach {
            if (shouldTransformFlipX()) it.x = transformedScreenWidth - it.x
            if (shouldTransformFlipY()) it.y = transformedScreenHeight - it.y
        }

        // rotate second (CCW)
        if (rotate) {
            corners.forEach { p ->
                p.y -= screenSize.width // undo shift back screen into positive quadrant
                val px = p.x
                val py = p.y
                p.x = -py
                p.y = px
            }
        }

        val top = corners.minByOrNull { it.y }!!.y
        val right = corners.maxByOrNull { it.x }!!.x
        val bottom = corners.maxByOrNull { it.y }!!.y
        val left = corners.minByOrNull { it.x }!!.x

        return Rect(left, top, right, bottom)
    }

    /** PiP anchor bounds in base case for given gravity */
    private fun getNormalPipAnchorBounds(pipSize: Size, movementBounds: Rect): Rect {
        var size = pipSize
        val rotateCW = shouldTransformRotate()
        if (rotateCW) {
            size = Size(pipSize.height, pipSize.width)
        }

        val pipBounds = Rect()
        if (isPipAnchoredToCorner()) {
            // bottom right
            Gravity.apply(
                Gravity.BOTTOM or Gravity.RIGHT,
                size.width,
                size.height,
                movementBounds,
                pipBounds
            )
            return pipBounds
        } else {
            // expanded, right side
            Gravity.apply(Gravity.RIGHT, size.width, size.height, movementBounds, pipBounds)
            return pipBounds
        }
    }

    private fun isPipAnchoredToCorner(): Boolean {
        val left = (pipGravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT
        val right = (pipGravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT
        val top = (pipGravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP
        val bottom = (pipGravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM

        val horizontal = left || right
        val vertical = top || bottom

        return horizontal && vertical
    }

    /**
     * Adds space around [size] to leave space for decorations that will be drawn around the PiP
     */
    private fun addDecors(size: Size): Size {
        val bounds = Rect(0, 0, size.width, size.height)
        bounds.inset(pipPermanentDecorInsets)

        return Size(bounds.width(), bounds.height())
    }

    /**
     * Removes the space that was reserved for permanent decorations around the PiP
     * @param bounds the bounds (in screen space) to remove the insets from
     */
    private fun removePermanentDecors(bounds: Rect): Rect {
        val pipDecorReverseInsets = Insets.subtract(Insets.NONE, pipPermanentDecorInsets)
        bounds.inset(pipDecorReverseInsets)
        return bounds
    }

    private fun Rect.offsetCopy(dx: Int, dy: Int) = Rect(this).apply { offset(dx, dy) }
    private fun Rect.intersectsX(other: Rect) = right >= other.left && left <= other.right
    private fun Rect.intersectsY(other: Rect) = bottom >= other.top && top <= other.bottom
    private fun Rect.intersects(other: Rect) = intersectsX(other) && intersectsY(other)
}
