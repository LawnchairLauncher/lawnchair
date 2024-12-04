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

package com.android.wm.shell.pip.tv;

import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;

import static org.junit.Assert.assertEquals;

import android.view.Gravity;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.LegacySizeSpecSource;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.SizeSpecSource;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

public class TvPipGravityTest extends ShellTestCase {

    private static final float VERTICAL_EXPANDED_ASPECT_RATIO = 1f / 3;
    private static final float HORIZONTAL_EXPANDED_ASPECT_RATIO = 3f;

    @Mock
    private PipSnapAlgorithm mMockPipSnapAlgorithm;

    private TvPipBoundsState mTvPipBoundsState;
    private TvPipBoundsAlgorithm mTvPipBoundsAlgorithm;
    private SizeSpecSource mSizeSpecSource;
    private PipDisplayLayoutState mPipDisplayLayoutState;

    @Before
    public void setUp() {
        assumeTelevision();

        MockitoAnnotations.initMocks(this);
        mPipDisplayLayoutState = new PipDisplayLayoutState(mContext);
        mSizeSpecSource = new LegacySizeSpecSource(mContext, mPipDisplayLayoutState);
        mTvPipBoundsState = new TvPipBoundsState(mContext, mSizeSpecSource,
                mPipDisplayLayoutState);
        mTvPipBoundsAlgorithm = new TvPipBoundsAlgorithm(mContext, mTvPipBoundsState,
                mMockPipSnapAlgorithm, mPipDisplayLayoutState, mSizeSpecSource);

        setRTL(false);
    }

    private void checkGravity(int gravityActual, int gravityExpected) {
        assertEquals(gravityExpected, gravityActual);
    }

    private void setRTL(boolean isRtl) {
        mContext.getResources().getConfiguration().setLayoutDirection(
                isRtl ? new Locale("ar") : Locale.ENGLISH);
        mTvPipBoundsState.onConfigurationChanged();
        mTvPipBoundsAlgorithm.onConfigurationChanged(mContext);
    }

    private void assertGravityAfterExpansion(int gravityFrom, int gravityTo) {
        mTvPipBoundsState.setTvPipExpanded(false);
        mTvPipBoundsState.setTvPipGravity(gravityFrom);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(true);
        checkGravity(mTvPipBoundsState.getTvPipGravity(), gravityTo);
    }

    private void assertGravityAfterCollapse(int gravityFrom, int gravityTo) {
        mTvPipBoundsState.setTvPipExpanded(true);
        mTvPipBoundsState.setTvPipGravity(gravityFrom);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(false);
        checkGravity(mTvPipBoundsState.getTvPipGravity(), gravityTo);
    }

    private void assertGravityAfterExpandAndCollapse(int gravityStartAndEnd) {
        mTvPipBoundsState.setTvPipGravity(gravityStartAndEnd);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(true);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(false);
        checkGravity(mTvPipBoundsState.getTvPipGravity(), gravityStartAndEnd);
    }

    @Test
    public void regularPip_defaultGravity() {
        checkGravity(mTvPipBoundsState.getDefaultGravity(), Gravity.RIGHT | Gravity.BOTTOM);
    }

    @Test
    public void regularPip_defaultTvPipGravity() {
        checkGravity(mTvPipBoundsState.getTvPipGravity(), Gravity.RIGHT | Gravity.BOTTOM);
    }

    @Test
    public void regularPip_defaultGravity_RTL() {
        setRTL(true);
        checkGravity(mTvPipBoundsState.getDefaultGravity(), Gravity.LEFT | Gravity.BOTTOM);
    }

    @Test
    public void updateGravity_expand_vertical() {
        // Vertical expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);

        assertGravityAfterExpansion(Gravity.BOTTOM | Gravity.RIGHT,
                Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        assertGravityAfterExpansion(Gravity.TOP | Gravity.RIGHT,
                Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        assertGravityAfterExpansion(Gravity.BOTTOM | Gravity.LEFT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT);
        assertGravityAfterExpansion(Gravity.TOP | Gravity.LEFT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT);
    }

    @Test
    public void updateGravity_expand_horizontal() {
        // Horizontal expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);

        assertGravityAfterExpansion(Gravity.BOTTOM | Gravity.RIGHT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        assertGravityAfterExpansion(Gravity.TOP | Gravity.RIGHT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        assertGravityAfterExpansion(Gravity.BOTTOM | Gravity.LEFT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        assertGravityAfterExpansion(Gravity.TOP | Gravity.LEFT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    }

    @Test
    public void updateGravity_collapse() {
        // Vertical expansion
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);
        assertGravityAfterCollapse(Gravity.CENTER_VERTICAL | Gravity.RIGHT,
                Gravity.BOTTOM | Gravity.RIGHT);
        assertGravityAfterCollapse(Gravity.CENTER_VERTICAL | Gravity.LEFT,
                Gravity.BOTTOM | Gravity.LEFT);

        // Horizontal expansion
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);
        assertGravityAfterCollapse(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                Gravity.TOP | Gravity.RIGHT);
        assertGravityAfterCollapse(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                Gravity.BOTTOM | Gravity.RIGHT);
    }

    @Test
    public void updateGravity_collapse_RTL() {
        setRTL(true);

        // Horizontal expansion
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);
        assertGravityAfterCollapse(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                Gravity.TOP | Gravity.LEFT);
        assertGravityAfterCollapse(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                Gravity.BOTTOM | Gravity.LEFT);
    }

    @Test
    public void updateGravity_expand_collapse() {
        // Vertical expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);

        assertGravityAfterExpandAndCollapse(Gravity.BOTTOM | Gravity.RIGHT);
        assertGravityAfterExpandAndCollapse(Gravity.BOTTOM | Gravity.LEFT);
        assertGravityAfterExpandAndCollapse(Gravity.TOP | Gravity.LEFT);
        assertGravityAfterExpandAndCollapse(Gravity.TOP | Gravity.RIGHT);

        // Horizontal expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);

        assertGravityAfterExpandAndCollapse(Gravity.BOTTOM | Gravity.RIGHT);
        assertGravityAfterExpandAndCollapse(Gravity.BOTTOM | Gravity.LEFT);
        assertGravityAfterExpandAndCollapse(Gravity.TOP | Gravity.LEFT);
        assertGravityAfterExpandAndCollapse(Gravity.TOP | Gravity.RIGHT);
    }

    @Test
    public void updateGravity_expand_move_collapse() {
        // Vertical expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);
        expandMoveCollapseCheck(Gravity.TOP | Gravity.RIGHT, KEYCODE_DPAD_LEFT,
                Gravity.TOP | Gravity.LEFT);

        // Horizontal expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);
        expandMoveCollapseCheck(Gravity.BOTTOM | Gravity.LEFT, KEYCODE_DPAD_UP,
                Gravity.TOP | Gravity.LEFT);
    }

    private void expandMoveCollapseCheck(int gravityFrom, int keycode, int gravityTo) {
        // Expand
        mTvPipBoundsState.setTvPipExpanded(false);
        mTvPipBoundsState.setTvPipGravity(gravityFrom);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(true);
        // Move
        mTvPipBoundsAlgorithm.updateGravity(keycode);
        // Collapse
        mTvPipBoundsState.setTvPipExpanded(true);
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(false);

        checkGravity(mTvPipBoundsState.getTvPipGravity(), gravityTo);
    }

    private void moveAndCheckGravity(int keycode, int gravityEnd, boolean expectChange) {
        assertEquals(expectChange, mTvPipBoundsAlgorithm.updateGravity(keycode));
        checkGravity(mTvPipBoundsState.getTvPipGravity(), gravityEnd);
    }

    @Test
    public void updateGravity_move_regular_valid() {
        mTvPipBoundsState.setTvPipGravity(Gravity.BOTTOM | Gravity.RIGHT);
        // clockwise
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.BOTTOM | Gravity.LEFT, true);
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.TOP | Gravity.LEFT, true);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.TOP | Gravity.RIGHT, true);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.BOTTOM | Gravity.RIGHT, true);
        // anti-clockwise
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.TOP | Gravity.RIGHT, true);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.TOP | Gravity.LEFT, true);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.BOTTOM | Gravity.LEFT, true);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.BOTTOM | Gravity.RIGHT, true);
    }

    @Test
    public void updateGravity_move_expanded_valid() {
        mTvPipBoundsState.setTvPipExpanded(true);

        // Vertical expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);
        mTvPipBoundsState.setTvPipGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.CENTER_VERTICAL | Gravity.LEFT, true);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, true);

        // Horizontal expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);
        mTvPipBoundsState.setTvPipGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.TOP | Gravity.CENTER_HORIZONTAL, true);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, true);
    }

    @Test
    public void updateGravity_move_regular_invalid() {
        int gravity = Gravity.BOTTOM | Gravity.RIGHT;
        mTvPipBoundsState.setTvPipGravity(gravity);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, gravity, false);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, gravity, false);

        gravity = Gravity.BOTTOM | Gravity.LEFT;
        mTvPipBoundsState.setTvPipGravity(gravity);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, gravity, false);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, gravity, false);

        gravity = Gravity.TOP | Gravity.LEFT;
        mTvPipBoundsState.setTvPipGravity(gravity);
        moveAndCheckGravity(KEYCODE_DPAD_UP, gravity, false);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, gravity, false);

        gravity = Gravity.TOP | Gravity.RIGHT;
        mTvPipBoundsState.setTvPipGravity(gravity);
        moveAndCheckGravity(KEYCODE_DPAD_UP, gravity, false);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, gravity, false);
    }

    @Test
    public void updateGravity_move_expanded_invalid() {
        mTvPipBoundsState.setTvPipExpanded(true);

        // Vertical expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(VERTICAL_EXPANDED_ASPECT_RATIO, true);
        mTvPipBoundsState.setTvPipGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, false);
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.CENTER_VERTICAL | Gravity.RIGHT, false);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.CENTER_VERTICAL | Gravity.RIGHT, false);

        mTvPipBoundsState.setTvPipGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.CENTER_VERTICAL | Gravity.LEFT, false);
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.CENTER_VERTICAL | Gravity.LEFT, false);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.CENTER_VERTICAL | Gravity.LEFT, false);

        // Horizontal expanded PiP.
        mTvPipBoundsState.setDesiredTvExpandedAspectRatio(HORIZONTAL_EXPANDED_ASPECT_RATIO, true);
        mTvPipBoundsState.setTvPipGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        moveAndCheckGravity(KEYCODE_DPAD_DOWN, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, false);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, false);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, false);

        mTvPipBoundsState.setTvPipGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        moveAndCheckGravity(KEYCODE_DPAD_UP, Gravity.TOP | Gravity.CENTER_HORIZONTAL, false);
        moveAndCheckGravity(KEYCODE_DPAD_LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, false);
        moveAndCheckGravity(KEYCODE_DPAD_RIGHT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, false);
    }

    @Test
    public void previousCollapsedGravity_defaultValue() {
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                mTvPipBoundsState.getDefaultGravity());
        setRTL(true);
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                mTvPipBoundsState.getDefaultGravity());
    }

    @Test
    public void previousCollapsedGravity_changes_on_RTL() {
        mTvPipBoundsState.setTvPipPreviousCollapsedGravity(Gravity.TOP | Gravity.LEFT);
        setRTL(true);
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                Gravity.TOP | Gravity.RIGHT);
        setRTL(false);
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                Gravity.TOP | Gravity.LEFT);

        mTvPipBoundsState.setTvPipPreviousCollapsedGravity(Gravity.BOTTOM | Gravity.RIGHT);
        setRTL(true);
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                Gravity.BOTTOM | Gravity.LEFT);
        setRTL(false);
        assertEquals(mTvPipBoundsState.getTvPipPreviousCollapsedGravity(),
                Gravity.BOTTOM | Gravity.RIGHT);
    }

}
