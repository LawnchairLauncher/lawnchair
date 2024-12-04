/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.graphics.Point;
import android.graphics.Region;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.util.Size;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import com.google.common.testing.EqualsTester;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link DragResizeWindowGeometry}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DragResizeWindowGeometryTests
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
public class DragResizeWindowGeometryTests {
    private static final Size TASK_SIZE = new Size(500, 1000);
    private static final int TASK_CORNER_RADIUS = 10;
    private static final int EDGE_RESIZE_THICKNESS = 15;
    private static final int EDGE_RESIZE_DEBUG_THICKNESS = EDGE_RESIZE_THICKNESS
            + (DragResizeWindowGeometry.DEBUG ? DragResizeWindowGeometry.EDGE_DEBUG_BUFFER : 0);
    private static final int FINE_CORNER_SIZE = EDGE_RESIZE_THICKNESS * 2 + 10;
    private static final int LARGE_CORNER_SIZE = FINE_CORNER_SIZE + 10;
    private static final DragResizeWindowGeometry GEOMETRY = new DragResizeWindowGeometry(
            TASK_CORNER_RADIUS, TASK_SIZE, EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE,
            LARGE_CORNER_SIZE);
    // Points in the edge resize handle. Note that coordinates start from the top left.
    private static final Point TOP_EDGE_POINT = new Point(TASK_SIZE.getWidth() / 2,
            -EDGE_RESIZE_THICKNESS / 2);
    private static final Point LEFT_EDGE_POINT = new Point(-EDGE_RESIZE_THICKNESS / 2,
            TASK_SIZE.getHeight() / 2);
    private static final Point RIGHT_EDGE_POINT = new Point(
            TASK_SIZE.getWidth() + EDGE_RESIZE_THICKNESS / 2, TASK_SIZE.getHeight() / 2);
    private static final Point BOTTOM_EDGE_POINT = new Point(TASK_SIZE.getWidth() / 2,
            TASK_SIZE.getHeight() + EDGE_RESIZE_THICKNESS / 2);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    /**
     * Check that both groups of objects satisfy equals/hashcode within each group, and that each
     * group is distinct from the next.
     */
    @Test
    public void testEqualsAndHash() {
        new EqualsTester()
                .addEqualityGroup(
                        GEOMETRY,
                        new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE, LARGE_CORNER_SIZE))
                .addEqualityGroup(
                        new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS + 10, FINE_CORNER_SIZE, LARGE_CORNER_SIZE),
                        new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS + 10, FINE_CORNER_SIZE, LARGE_CORNER_SIZE))
                .addEqualityGroup(new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE, LARGE_CORNER_SIZE + 5),
                        new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE, LARGE_CORNER_SIZE + 5))
                .addEqualityGroup(new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE + 4, LARGE_CORNER_SIZE),
                        new DragResizeWindowGeometry(TASK_CORNER_RADIUS, TASK_SIZE,
                                EDGE_RESIZE_THICKNESS, FINE_CORNER_SIZE + 4, LARGE_CORNER_SIZE))
                .testEquals();
    }

    @Test
    public void testGetTaskSize() {
        assertThat(GEOMETRY.getTaskSize()).isEqualTo(TASK_SIZE);
    }

    @Test
    public void testRegionUnionContainsEdges() {
        Region region = new Region();
        GEOMETRY.union(region);
        assertThat(region.isComplex()).isTrue();
        // Region excludes task area. Note that coordinates start from top left.
        assertThat(region.contains(TASK_SIZE.getWidth() / 2, TASK_SIZE.getHeight() / 2)).isFalse();
        // Region includes edges outside the task window.
        verifyVerticalEdge(region, LEFT_EDGE_POINT);
        verifyHorizontalEdge(region, TOP_EDGE_POINT);
        verifyVerticalEdge(region, RIGHT_EDGE_POINT);
        verifyHorizontalEdge(region, BOTTOM_EDGE_POINT);
    }

    private static void verifyHorizontalEdge(@NonNull Region region, @NonNull Point point) {
        assertThat(region.contains(point.x, point.y)).isTrue();
        // Horizontally along the edge is still contained.
        assertThat(region.contains(point.x + EDGE_RESIZE_DEBUG_THICKNESS, point.y)).isTrue();
        assertThat(region.contains(point.x - EDGE_RESIZE_DEBUG_THICKNESS, point.y)).isTrue();
        // Vertically along the edge is not contained.
        assertThat(region.contains(point.x, point.y - EDGE_RESIZE_DEBUG_THICKNESS)).isFalse();
        assertThat(region.contains(point.x, point.y + EDGE_RESIZE_DEBUG_THICKNESS)).isFalse();
    }

    private static void verifyVerticalEdge(@NonNull Region region, @NonNull Point point) {
        assertThat(region.contains(point.x, point.y)).isTrue();
        // Horizontally along the edge is not contained.
        assertThat(region.contains(point.x + EDGE_RESIZE_DEBUG_THICKNESS, point.y)).isFalse();
        assertThat(region.contains(point.x - EDGE_RESIZE_DEBUG_THICKNESS, point.y)).isFalse();
        // Vertically along the edge is contained.
        assertThat(region.contains(point.x, point.y - EDGE_RESIZE_DEBUG_THICKNESS)).isTrue();
        assertThat(region.contains(point.x, point.y + EDGE_RESIZE_DEBUG_THICKNESS)).isTrue();
    }

    /**
     * Validate that with the flag enabled, the corner resize regions are the largest size, to
     * capture all eligible input regardless of source (touchscreen or cursor).
     * <p>Note that capturing input does not necessarily mean that the event will be handled.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testRegionUnion_edgeDragResizeEnabled_containsLargeCorners() {
        Region region = new Region();
        GEOMETRY.union(region);
        // Make sure we're choosing a point outside of any debug region buffer.
        final int cornerRadius = DragResizeWindowGeometry.DEBUG
                ? Math.max(LARGE_CORNER_SIZE / 2, EDGE_RESIZE_DEBUG_THICKNESS)
                : LARGE_CORNER_SIZE / 2;

        new TestPoints(TASK_SIZE, cornerRadius).validateRegion(region);
    }

    /**
     * Validate that with the flag disabled, the corner resize regions are the original smaller
     * size.
     */
    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testRegionUnion_edgeDragResizeDisabled_containsFineCorners() {
        Region region = new Region();
        GEOMETRY.union(region);
        final int cornerRadius = DragResizeWindowGeometry.DEBUG
                ? Math.max(LARGE_CORNER_SIZE / 2, EDGE_RESIZE_DEBUG_THICKNESS)
                : LARGE_CORNER_SIZE / 2;

        new TestPoints(TASK_SIZE, cornerRadius).validateRegion(region);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testCalculateControlType_edgeDragResizeEnabled_edges() {
        // The input source (touchscreen or cursor) shouldn't impact the edge resize size.
        validateCtrlTypeForEdges(/* isTouchscreen= */ false, /* isEdgeResizePermitted= */ false);
        validateCtrlTypeForEdges(/* isTouchscreen= */ true, /* isEdgeResizePermitted= */ false);
        validateCtrlTypeForEdges(/* isTouchscreen= */ false, /* isEdgeResizePermitted= */ true);
        validateCtrlTypeForEdges(/* isTouchscreen= */ true, /* isEdgeResizePermitted= */ true);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testCalculateControlType_edgeDragResizeDisabled_edges() {
        // Edge resizing is not supported for touchscreen input when the flag is disabled.
        validateCtrlTypeForEdges(/* isTouchscreen= */ false, /* isEdgeResizePermitted= */ true);
        validateCtrlTypeForEdges(/* isTouchscreen= */ true, /* isEdgeResizePermitted= */ false);
    }

    private void validateCtrlTypeForEdges(boolean isTouchscreen, boolean isEdgeResizePermitted) {
        assertThat(GEOMETRY.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                LEFT_EDGE_POINT.x, LEFT_EDGE_POINT.y)).isEqualTo(
                        isEdgeResizePermitted ? CTRL_TYPE_LEFT : CTRL_TYPE_UNDEFINED);
        assertThat(GEOMETRY.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                TOP_EDGE_POINT.x, TOP_EDGE_POINT.y)).isEqualTo(
                        isEdgeResizePermitted ? CTRL_TYPE_TOP : CTRL_TYPE_UNDEFINED);
        assertThat(GEOMETRY.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                RIGHT_EDGE_POINT.x, RIGHT_EDGE_POINT.y)).isEqualTo(
                        isEdgeResizePermitted ? CTRL_TYPE_RIGHT : CTRL_TYPE_UNDEFINED);
        assertThat(GEOMETRY.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                BOTTOM_EDGE_POINT.x, BOTTOM_EDGE_POINT.y)).isEqualTo(
                        isEdgeResizePermitted ? CTRL_TYPE_BOTTOM : CTRL_TYPE_UNDEFINED);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testCalculateControlType_edgeDragResizeEnabled_corners() {
        final TestPoints fineTestPoints = new TestPoints(TASK_SIZE, FINE_CORNER_SIZE / 2);
        final TestPoints largeCornerTestPoints = new TestPoints(TASK_SIZE, LARGE_CORNER_SIZE / 2);

        // When the flag is enabled, points within fine corners should pass regardless of touch or
        // not. Points outside fine corners should not pass when using a course input (non-touch).
        // Edge resizing permitted (events from stylus/cursor) should have no impact on corners.
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, true);
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ false, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ false, true);
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ false, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ false, false);

        // When the flag is enabled, points near the large corners should only pass when the point
        // is within the corner for large touch inputs.
        largeCornerTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, true);
        largeCornerTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, false);
        largeCornerTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
        largeCornerTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE)
    public void testCalculateControlType_edgeDragResizeDisabled_corners() {
        final TestPoints fineTestPoints = new TestPoints(TASK_SIZE, FINE_CORNER_SIZE / 2);
        final TestPoints largeCornerTestPoints = new TestPoints(TASK_SIZE, LARGE_CORNER_SIZE / 2);

        // When the flag is disabled, points within fine corners should pass only from touchscreen.
        // Edge resize permitted (indicating the event is from a cursor/stylus) should have no
        // impact.
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, false);
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ false, true);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ false, false);

        // Points within fine corners should never pass when not from touchscreen; expect edge
        // resizing only.
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
        fineTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ false, false);
        fineTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ false, false);

        // When the flag is disabled, points near the large corners should never pass.
        largeCornerTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, false);
        largeCornerTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                true, /* isEdgeResizePermitted= */ true, false);
        largeCornerTestPoints.validateCtrlTypeForInnerPoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
        largeCornerTestPoints.validateCtrlTypeForOutsidePoints(GEOMETRY, /* isTouchscreen= */
                false, /* isEdgeResizePermitted= */ true, false);
    }

    /**
     * Class for creating points for testing the drag resize corners.
     *
     * <p>Creates points that are both just within the bounds of each corner, and just outside.
     */
    private static final class TestPoints {
        private final Point mTopLeftPoint;
        private final Point mTopLeftPointOutside;
        private final Point mTopRightPoint;
        private final Point mTopRightPointOutside;
        private final Point mBottomLeftPoint;
        private final Point mBottomLeftPointOutside;
        private final Point mBottomRightPoint;
        private final Point mBottomRightPointOutside;

        TestPoints(@NonNull Size taskSize, int cornerRadius) {
            // Point just inside corner square is included.
            mTopLeftPoint = new Point(-cornerRadius + 1, -cornerRadius + 1);
            // Point just outside corner square is excluded.
            mTopLeftPointOutside = new Point(mTopLeftPoint.x - 5, mTopLeftPoint.y - 5);

            mTopRightPoint = new Point(taskSize.getWidth() + cornerRadius - 1, -cornerRadius + 1);
            mTopRightPointOutside = new Point(mTopRightPoint.x + 5, mTopRightPoint.y - 5);

            mBottomLeftPoint = new Point(-cornerRadius + 1,
                    taskSize.getHeight() + cornerRadius - 1);
            mBottomLeftPointOutside = new Point(mBottomLeftPoint.x - 5, mBottomLeftPoint.y + 5);

            mBottomRightPoint = new Point(taskSize.getWidth() + cornerRadius - 1,
                    taskSize.getHeight() + cornerRadius - 1);
            mBottomRightPointOutside = new Point(mBottomRightPoint.x + 5, mBottomRightPoint.y + 5);
        }

        /**
         * Validates that all test points are either within or without the given region.
         */
        public void validateRegion(@NonNull Region region) {
            // Point just inside corner square is included.
            assertThat(region.contains(mTopLeftPoint.x, mTopLeftPoint.y)).isTrue();
            // Point just outside corner square is excluded.
            assertThat(region.contains(mTopLeftPointOutside.x, mTopLeftPointOutside.y)).isFalse();

            assertThat(region.contains(mTopRightPoint.x, mTopRightPoint.y)).isTrue();
            assertThat(
                    region.contains(mTopRightPointOutside.x, mTopRightPointOutside.y)).isFalse();

            assertThat(region.contains(mBottomLeftPoint.x, mBottomLeftPoint.y)).isTrue();
            assertThat(region.contains(mBottomLeftPointOutside.x,
                    mBottomLeftPointOutside.y)).isFalse();

            assertThat(region.contains(mBottomRightPoint.x, mBottomRightPoint.y)).isTrue();
            assertThat(region.contains(mBottomRightPointOutside.x,
                    mBottomRightPointOutside.y)).isFalse();
        }

        /**
         * Validates that all test points within this drag corner size give the correct
         * {@code @DragPositioningCallback.CtrlType}.
         */
        public void validateCtrlTypeForInnerPoints(@NonNull DragResizeWindowGeometry geometry,
                boolean isTouchscreen, boolean isEdgeResizePermitted,
                boolean expectedWithinGeometry) {
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mTopLeftPoint.x, mTopLeftPoint.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_LEFT | CTRL_TYPE_TOP : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mTopRightPoint.x, mTopRightPoint.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_RIGHT | CTRL_TYPE_TOP : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mBottomLeftPoint.x, mBottomLeftPoint.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM
                            : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mBottomRightPoint.x, mBottomRightPoint.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM
                            : CTRL_TYPE_UNDEFINED);
        }

        /**
         * Validates that all test points outside this drag corner size give the correct
         * {@code @DragPositioningCallback.CtrlType}.
         */
        public void validateCtrlTypeForOutsidePoints(@NonNull DragResizeWindowGeometry geometry,
                boolean isTouchscreen, boolean isEdgeResizePermitted,
                boolean expectedWithinGeometry) {
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mTopLeftPointOutside.x, mTopLeftPointOutside.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_LEFT | CTRL_TYPE_TOP : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mTopRightPointOutside.x, mTopRightPointOutside.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_RIGHT | CTRL_TYPE_TOP : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mBottomLeftPointOutside.x, mBottomLeftPointOutside.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM
                            : CTRL_TYPE_UNDEFINED);
            assertThat(geometry.calculateCtrlType(isTouchscreen, isEdgeResizePermitted,
                    mBottomRightPointOutside.x, mBottomRightPointOutside.y)).isEqualTo(
                    expectedWithinGeometry ? CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM
                            : CTRL_TYPE_UNDEFINED);
        }
    }
}
