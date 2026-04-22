/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.common

import android.graphics.Rect
import android.util.Log
import com.android.wm.shell.common.FloatingContentCoordinator.FloatingContent
import java.util.HashMap

/** Tag for debug logging. */
private const val TAG = "FloatingCoordinator"

/**
 * Coordinates the positions and movement of floating content, such as PIP and Bubbles, to ensure
 * that they don't overlap. If content does overlap due to content appearing or moving, the
 * coordinator will ask content to move to resolve the conflict.
 *
 * After implementing [FloatingContent], content should call [onContentAdded] to begin coordination.
 * Subsequently, call [onContentMoved] whenever the content moves, and the coordinator will move
 * other content out of the way. [onContentRemoved] should be called when the content is removed or
 * no longer visible.
 */

class FloatingContentCoordinator constructor() {
    /**
     * Represents a piece of floating content, such as PIP or the Bubbles stack. Provides methods
     * that allow the [FloatingContentCoordinator] to determine the current location of the content,
     * as well as the ability to ask it to move out of the way of other content.
     *
     * The default implementation of [calculateNewBoundsOnOverlap] moves the content up or down,
     * depending on the position of the conflicting content. You can override this method if you
     * want your own custom conflict resolution logic.
     */
    interface FloatingContent {

        /**
         * Return the bounds claimed by this content. This should include the bounds occupied by the
         * content itself, as well as any padding, if desired. The coordinator will ensure that no
         * other content is located within these bounds.
         *
         * If the content is animating, this method should return the bounds to which the content is
         * animating. If that animation is cancelled, or updated, be sure that your implementation
         * of this method returns the appropriate bounds, and call [onContentMoved] so that the
         * coordinator moves other content out of the way.
         */
        fun getFloatingBoundsOnScreen(): Rect

        /**
         * Return the area within which this floating content is allowed to move. When resolving
         * conflicts, the coordinator will never ask your content to move to a position where any
         * part of the content would be out of these bounds.
         */
        fun getAllowedFloatingBoundsRegion(): Rect

        /**
         * Called when the coordinator needs this content to move to the given bounds. It's up to
         * you how to do that.
         *
         * Note that if you start an animation to these bounds, [getFloatingBoundsOnScreen] should
         * return the destination bounds, not the in-progress animated bounds. This is so the
         * coordinator knows where floating content is going to be and can resolve conflicts
         * accordingly.
         */
        fun moveToBounds(bounds: Rect)

        /**
         * Called by the coordinator when it needs to find a new home for this floating content,
         * because a new or moving piece of content is now overlapping with it.
         *
         * [findAreaForContentVertically] and [findAreaForContentAboveOrBelow] are helpful utility
         * functions that will find new bounds for your content automatically. Unless you require
         * specific conflict resolution logic, these should be sufficient. By default, this method
         * delegates to [findAreaForContentVertically].
         *
         * @param overlappingContentBounds The bounds of the other piece of content, which
         * necessitated this content's relocation. Your new position must not overlap with these
         * bounds.
         * @param otherContentBounds The bounds of any other pieces of floating content. Your new
         * position must not overlap with any of these either. These bounds are guaranteed to be
         * non-overlapping.
         * @return The new bounds for this content.
         */
        fun calculateNewBoundsOnOverlap(
            overlappingContentBounds: Rect,
            otherContentBounds: List<Rect>
        ): Rect {
            return findAreaForContentVertically(
                    getFloatingBoundsOnScreen(),
                    overlappingContentBounds,
                    otherContentBounds,
                    getAllowedFloatingBoundsRegion())
        }
    }

    /** The bounds of all pieces of floating content added to the coordinator. */
    private val allContentBounds: MutableMap<FloatingContent, Rect> = HashMap()

    /**
     * Whether we are currently resolving conflicts by asking content to move. If we are, we'll
     * temporarily ignore calls to [onContentMoved] - those calls are from the content that is
     * moving to new, conflict-free bounds, so we don't need to perform conflict detection
     * calculations in response.
     */
    private var currentlyResolvingConflicts = false

    /**
     * Makes the coordinator aware of a new piece of floating content, and moves any existing
     * content out of the way, if necessary.
     *
     * If you don't want your new content to move existing content, use [getOccupiedBounds] to find
     * an unoccupied area, and move the content there before calling this method.
     */
    fun onContentAdded(newContent: FloatingContent) {
        updateContentBounds()
        allContentBounds[newContent] = newContent.getFloatingBoundsOnScreen()
        maybeMoveConflictingContent(newContent)
    }

    /**
     * Called to notify the coordinator that a piece of floating content has moved (or is animating)
     * to a new position, and that any conflicting floating content should be moved out of the way.
     *
     * The coordinator will call [FloatingContent.getFloatingBoundsOnScreen] to find the new bounds
     * for the moving content. If you're animating the content, be sure that your implementation of
     * getFloatingBoundsOnScreen returns the bounds to which it's animating, not the content's
     * current bounds.
     *
     * If the animation moving this content is cancelled or updated, you'll need to call this method
     * again, to ensure that content is moved out of the way of the latest bounds.
     *
     * @param content The content that has moved.
     */
    fun onContentMoved(content: FloatingContent) {

        // Ignore calls when we are currently resolving conflicts, since those calls are from
        // content that is moving to new, conflict-free bounds.
        if (currentlyResolvingConflicts) {
            return
        }

        if (!allContentBounds.containsKey(content)) {
            Log.wtf(TAG, "Received onContentMoved call before onContentAdded! " +
                    "This should never happen.")
            return
        }

        updateContentBounds()
        maybeMoveConflictingContent(content)
    }

    /**
     * Called to notify the coordinator that a piece of floating content has been removed or is no
     * longer visible.
     */
    fun onContentRemoved(removedContent: FloatingContent) {
        allContentBounds.remove(removedContent)
    }

    /**
     * Returns a set of Rects that represent the bounds of all of the floating content on the
     * screen.
     *
     * [onContentAdded] will move existing content out of the way if the added content intersects
     * existing content. That's fine - but if your specific starting position is not important, you
     * can use this function to find unoccupied space for your content before calling
     * [onContentAdded], so that moving existing content isn't necessary.
     */
    fun getOccupiedBounds(): Collection<Rect> {
        return allContentBounds.values
    }

    /**
     * Identifies any pieces of content that are now overlapping with the given content, and asks
     * them to move out of the way.
     */
    private fun maybeMoveConflictingContent(fromContent: FloatingContent) {
        currentlyResolvingConflicts = true

        val conflictingNewBounds = allContentBounds[fromContent]!!
        allContentBounds
                // Filter to content that intersects with the new bounds. That's content that needs
                // to move.
                .filter { (content, bounds) ->
                    content != fromContent && Rect.intersects(conflictingNewBounds, bounds) }
                // Tell that content to get out of the way, and save the bounds it says it's moving
                // (or animating) to.
                .forEach { (content, bounds) ->
                    val newBounds = content.calculateNewBoundsOnOverlap(
                            conflictingNewBounds,
                            // Pass all of the content bounds except the bounds of the
                            // content we're asking to move, and the conflicting new bounds
                            // (since those are passed separately).
                            otherContentBounds = allContentBounds.values
                                    .minus(bounds)
                                    .minus(conflictingNewBounds))

                    // If the new bounds are empty, it means there's no non-overlapping position
                    // that is in bounds. Just leave the content where it is. This should normally
                    // not happen, but sometimes content like PIP reports incorrect bounds
                    // temporarily.
                    if (!newBounds.isEmpty) {
                        content.moveToBounds(newBounds)
                        allContentBounds[content] = content.getFloatingBoundsOnScreen()
                    }
                }

        currentlyResolvingConflicts = false
    }

    /**
     * Update [allContentBounds] by calling [FloatingContent.getFloatingBoundsOnScreen] for all
     * content and saving the result.
     */
    private fun updateContentBounds() {
        allContentBounds.keys.forEach { allContentBounds[it] = it.getFloatingBoundsOnScreen() }
    }

    companion object {
        /**
         * Finds new bounds for the given content, either above or below its current position. The
         * new bounds won't intersect with the newly overlapping rect or the exclusion rects, and
         * will be within the allowed bounds unless no possible position exists.
         *
         * You can use this method to help find a new position for your content when the coordinator
         * calls [FloatingContent.moveToAreaExcluding].
         *
         * @param contentRect The bounds of the content for which we're finding a new home.
         * @param newlyOverlappingRect The bounds of the content that forced this relocation by
         * intersecting with the content we now need to move. If the overlapping content is
         * overlapping the top half of this content, we'll try to move this content downward if
         * possible (since the other content is 'pushing' it down), and vice versa.
         * @param exclusionRects Any other areas that we need to avoid when finding a new home for
         * the content. These areas must be non-overlapping with each other.
         * @param allowedBounds The area within which we're allowed to find new bounds for the
         * content.
         * @return New bounds for the content that don't intersect the exclusion rects or the
         * newly overlapping rect, and that is within bounds - or an empty Rect if no in-bounds
         * position exists.
         */
        @JvmStatic
        fun findAreaForContentVertically(
            contentRect: Rect,
            newlyOverlappingRect: Rect,
            exclusionRects: Collection<Rect>,
            allowedBounds: Rect
        ): Rect {
            // If the newly overlapping Rect's center is above the content's center, we'll prefer to
            // find a space for this content that is below the overlapping content, since it's
            // 'pushing' it down. This may not be possible due to to screen bounds, in which case
            // we'll find space in the other direction.
            val overlappingContentPushingDown =
                    newlyOverlappingRect.centerY() < contentRect.centerY()

            // Filter to exclusion rects that are above or below the content that we're finding a
            // place for. Then, split into two lists - rects above the content, and rects below it.
            var (rectsToAvoidAbove, rectsToAvoidBelow) = exclusionRects
                    .filter { rectToAvoid -> rectsIntersectVertically(rectToAvoid, contentRect) }
                    .partition { rectToAvoid -> rectToAvoid.top < contentRect.top }

            // Lazily calculate the closest possible new tops for the content, above and below its
            // current location.
            val newContentBoundsAbove by lazy {
                findAreaForContentAboveOrBelow(
                        contentRect,
                        exclusionRects = rectsToAvoidAbove.plus(newlyOverlappingRect),
                        findAbove = true)
            }
            val newContentBoundsBelow by lazy {
                findAreaForContentAboveOrBelow(
                        contentRect,
                        exclusionRects = rectsToAvoidBelow.plus(newlyOverlappingRect),
                        findAbove = false)
            }

            val positionAboveInBounds by lazy { allowedBounds.contains(newContentBoundsAbove) }
            val positionBelowInBounds by lazy { allowedBounds.contains(newContentBoundsBelow) }

            // Use the 'below' position if the content is being overlapped from the top, unless it's
            // out of bounds. Also use it if the content is being overlapped from the bottom, but
            // the 'above' position is out of bounds. Otherwise, use the 'above' position.
            val usePositionBelow =
                    overlappingContentPushingDown && positionBelowInBounds ||
                            !overlappingContentPushingDown && !positionAboveInBounds

            // Return the content rect, but offset to reflect the new position.
            val newBounds = if (usePositionBelow) newContentBoundsBelow else newContentBoundsAbove

            // If the new bounds are within the allowed bounds, return them. If not, it means that
            // there are no legal new bounds. This can happen if the new content's bounds are too
            // large (for example, full-screen PIP). Since there is no reasonable action to take
            // here, return an empty Rect and we will just not move the content.
            return if (allowedBounds.contains(newBounds)) newBounds else Rect()
        }

        /**
         * Finds a new position for the given content, either above or below its current position
         * depending on whether [findAbove] is true or false, respectively. This new position will
         * not intersect with any of the [exclusionRects].
         *
         * This method is useful as a helper method for implementing your own conflict resolution
         * logic. Otherwise, you'd want to use [findAreaForContentVertically], which takes screen
         * bounds and conflicting bounds' location into account when deciding whether to move to new
         * bounds above or below the current bounds.
         *
         * @param contentRect The content we're finding an area for.
         * @param exclusionRects The areas we need to avoid when finding a new area for the content.
         * These areas must be non-overlapping with each other.
         * @param findAbove Whether we are finding an area above the content's current position,
         * rather than an area below it.
         */
        fun findAreaForContentAboveOrBelow(
            contentRect: Rect,
            exclusionRects: Collection<Rect>,
            findAbove: Boolean
        ): Rect {
            // Sort the rects, since we want to move the content as little as possible. We'll
            // start with the rects closest to the content and move outward. If we're finding an
            // area above the content, that means we sort in reverse order to search the rects
            // from highest to lowest y-value.
            val sortedExclusionRects =
                    exclusionRects.sortedBy { if (findAbove) -it.top else it.top }

            val proposedNewBounds = Rect(contentRect)
            for (exclusionRect in sortedExclusionRects) {
                // If the proposed new bounds don't intersect with this exclusion rect, that
                // means there's room for the content here. We know this because the rects are
                // sorted and non-overlapping, so any subsequent exclusion rects would be higher
                // (or lower) than this one and can't possibly intersect if this one doesn't.
                if (!Rect.intersects(proposedNewBounds, exclusionRect)) {
                    break
                } else {
                    // Otherwise, we need to keep searching for new bounds. If we're finding an
                    // area above, propose new bounds that place the content just above the
                    // exclusion rect. If we're finding an area below, propose new bounds that
                    // place the content just below the exclusion rect.
                    val verticalOffset =
                            if (findAbove) -contentRect.height() else exclusionRect.height()
                    proposedNewBounds.offsetTo(
                            proposedNewBounds.left,
                            exclusionRect.top + verticalOffset)
                }
            }

            return proposedNewBounds
        }

        /** Returns whether or not the two Rects share any of the same space on the X axis. */
        private fun rectsIntersectVertically(r1: Rect, r2: Rect): Boolean {
            return (r1.left >= r2.left && r1.left <= r2.right) ||
                    (r1.right <= r2.right && r1.right >= r2.left)
        }
    }
}
