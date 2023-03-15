/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.states.RotationHelper.deltaRotation;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;

import com.android.launcher3.R;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.window.CachedDisplayInfo;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains state for supporting nav bars and tracking their gestures in multiple orientations.
 * See {@link OrientationRectF#applyTransformToRotation(MotionEvent, int, boolean)} for
 * transformation of MotionEvents from one orientation's coordinate space to another's.
 *
 * This class only supports single touch/pointer gesture tracking for touches started in a supported
 * nav bar region.
 */
class OrientationTouchTransformer {

    private static final String TAG = "OrientationTouchTransformer";
    private static final boolean DEBUG = false;

    private static final int QUICKSTEP_ROTATION_UNINITIALIZED = -1;

    private final Map<CachedDisplayInfo, OrientationRectF> mSwipeTouchRegions =
            new HashMap<CachedDisplayInfo, OrientationRectF>();
    private final RectF mAssistantLeftRegion = new RectF();
    private final RectF mAssistantRightRegion = new RectF();
    private final RectF mOneHandedModeRegion = new RectF();
    private CachedDisplayInfo mCachedDisplayInfo = new CachedDisplayInfo();
    private int mNavBarGesturalHeight;
    private final int mNavBarLargerGesturalHeight;
    private boolean mEnableMultipleRegions;
    private Resources mResources;
    private OrientationRectF mLastRectTouched;
    /**
     * The rotation of the last touched nav bar, whether that be through the last region the user
     * touched down on or valid rotation user turned their device to.
     * Note this is different than
     * {@link #mQuickStepStartingRotation} as it always updates its value on every touch whereas
     * mQuickstepStartingRotation only updates when device rotation matches touch rotation.
     */
    private int mActiveTouchRotation;
    private NavigationMode mMode;
    private QuickStepContractInfo mContractInfo;

    /**
     * Represents if we're currently in a swipe "session" of sorts. If value is
     * QUICKSTEP_ROTATION_UNINITIALIZED, then user has not tapped on an active nav region.
     * Otherwise it will be the rotation of the display when the user first interacted with the
     * active nav bar region.
     * The "session" ends when {@link #enableMultipleRegions(boolean, Info)} is
     * called - usually from a timeout or if user starts interacting w/ the foreground app.
     *
     * This is different than {@link #mLastRectTouched} as it can get reset by the system whereas
     * the rect is purely used for tracking touch interactions and usually this "session" will
     * outlast the touch interaction.
     */
    private int mQuickStepStartingRotation = QUICKSTEP_ROTATION_UNINITIALIZED;

    /** For testability */
    interface QuickStepContractInfo {
        float getWindowCornerRadius();
    }


    OrientationTouchTransformer(Resources resources, NavigationMode mode,
            QuickStepContractInfo contractInfo) {
        mResources = resources;
        mMode = mode;
        mContractInfo = contractInfo;
        mNavBarGesturalHeight = getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
        mNavBarLargerGesturalHeight = ResourceUtils.getDimenByName(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_LARGER_SIZE, resources,
                mNavBarGesturalHeight);
    }

    private void refreshTouchRegion(Info info, Resources newRes) {
        // Swipe touch regions are independent of nav mode, so we have to clear them explicitly
        // here to avoid, for ex, a nav region for 2-button rotation 0 being used for 3-button mode
        // It tries to cache and reuse swipe regions whenever possible based only on rotation
        mResources = newRes;
        mSwipeTouchRegions.clear();
        resetSwipeRegions(info);
    }

    void setNavigationMode(NavigationMode newMode, Info info, Resources newRes) {
        if (enableLog()) {
            Log.d(TAG, "setNavigationMode new: " + newMode + " oldMode: " + mMode + " " + this);
        }
        if (mMode == newMode) {
            return;
        }
        this.mMode = newMode;
        refreshTouchRegion(info, newRes);
    }

    void setGesturalHeight(int newGesturalHeight, Info info, Resources newRes) {
        if (mNavBarGesturalHeight == newGesturalHeight) {
            return;
        }
        mNavBarGesturalHeight = newGesturalHeight;
        refreshTouchRegion(info, newRes);
    }

    /**
     * Sets the current nav bar region to listen to events for as determined by
     * {@param info}. If multiple nav bar regions are enabled, then this region will be added
     * alongside other regions.
     * Ok to call multiple times
     *
     * @see #enableMultipleRegions(boolean, Info)
     */
    void createOrAddTouchRegion(Info info) {
        mCachedDisplayInfo = new CachedDisplayInfo(info.currentSize, info.rotation);

        if (mQuickStepStartingRotation > QUICKSTEP_ROTATION_UNINITIALIZED
                && mCachedDisplayInfo.rotation == mQuickStepStartingRotation) {
            // User already was swiping and the current screen is same rotation as the starting one
            // Remove active nav bars in other rotations except for the one we started out in
            resetSwipeRegions(info);
            return;
        }
        OrientationRectF region = mSwipeTouchRegions.get(mCachedDisplayInfo);
        if (region != null) {
            return;
        }

        if (mEnableMultipleRegions) {
            mSwipeTouchRegions.put(mCachedDisplayInfo, createRegionForDisplay(info));
        } else {
            resetSwipeRegions(info);
        }
    }

    /**
     * Call when we want to start tracking nav bar touch regions in multiple orientations.
     * ALSO, you BETTER call this with {@param enableMultipleRegions} set to false once you're done.
     *
     * @param enableMultipleRegions Set to true to start tracking multiple nav bar regions
     * @param info The current displayInfo which will be the start of the quickswitch gesture
     */
    void enableMultipleRegions(boolean enableMultipleRegions, Info info) {
        mEnableMultipleRegions = enableMultipleRegions && mMode != NavigationMode.TWO_BUTTONS;
        if (mEnableMultipleRegions) {
            mQuickStepStartingRotation = info.rotation;
        } else {
            mActiveTouchRotation = 0;
            mQuickStepStartingRotation = QUICKSTEP_ROTATION_UNINITIALIZED;
        }
        resetSwipeRegions(info);
    }

    /**
     * Call when removing multiple regions to swipe from, but still in active quickswitch mode (task
     * list is still frozen).
     * Ex. This would be called when user has quickswitched to the same app rotation that
     * they started quickswitching in, indicating that extra nav regions can be ignored. Calling
     * this will update the value of {@link #mActiveTouchRotation}
     *
     * @param displayInfo The display whos rotation will be used as the current active rotation
     */
    void setSingleActiveRegion(Info displayInfo) {
        mActiveTouchRotation = displayInfo.rotation;
        resetSwipeRegions(displayInfo);
    }

    /**
     * Only saves the swipe region represented by {@param region}, clears the
     * rest from {@link #mSwipeTouchRegions}
     * To be called whenever we want to stop tracking more than one swipe region.
     * Ok to call multiple times.
     */
    private void resetSwipeRegions(Info region) {
        if (enableLog()) {
            Log.d(TAG, "clearing all regions except rotation: " + mCachedDisplayInfo.rotation);
        }

        mCachedDisplayInfo = new CachedDisplayInfo(region.currentSize, region.rotation);
        OrientationRectF regionToKeep = mSwipeTouchRegions.get(mCachedDisplayInfo);
        if (regionToKeep == null) {
            regionToKeep = createRegionForDisplay(region);
        }
        mSwipeTouchRegions.clear();
        mSwipeTouchRegions.put(mCachedDisplayInfo, regionToKeep);
        updateAssistantRegions(regionToKeep);
    }

    private void resetSwipeRegions() {
        OrientationRectF regionToKeep = mSwipeTouchRegions.get(mCachedDisplayInfo);
        mSwipeTouchRegions.clear();
        if (regionToKeep != null) {
            mSwipeTouchRegions.put(mCachedDisplayInfo, regionToKeep);
            updateAssistantRegions(regionToKeep);
        }
    }

    private OrientationRectF createRegionForDisplay(Info display) {
        if (enableLog()) {
            Log.d(TAG, "creating rotation region for: " + mCachedDisplayInfo.rotation
            + " with mode: " + mMode + " displayRotation: " + display.rotation +
                    " displaySize: " + display.currentSize +
                    " navBarHeight: " + mNavBarGesturalHeight);
        }

        Point size = display.currentSize;
        int rotation = display.rotation;
        int touchHeight = mNavBarGesturalHeight;
        OrientationRectF orientationRectF = new OrientationRectF(0, 0, size.x, size.y, rotation);
        if (mMode == NavigationMode.NO_BUTTON) {
            orientationRectF.top = orientationRectF.bottom - touchHeight;
            updateAssistantRegions(orientationRectF);
        } else {
            mAssistantLeftRegion.setEmpty();
            mAssistantRightRegion.setEmpty();
            int navbarSize = getNavbarSize(ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);
            switch (rotation) {
                case Surface.ROTATION_90:
                    orientationRectF.left = orientationRectF.right
                            - navbarSize;
                    break;
                case Surface.ROTATION_270:
                    orientationRectF.right = orientationRectF.left
                            + navbarSize;
                    break;
                default:
                    orientationRectF.top = orientationRectF.bottom - touchHeight;
            }
        }
        // One handed gestural only active on portrait mode
        mOneHandedModeRegion.set(0, orientationRectF.bottom - mNavBarLargerGesturalHeight,
                size.x, size.y);

        return orientationRectF;
    }

    private void updateAssistantRegions(OrientationRectF orientationRectF) {
        int navbarHeight = getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
        int assistantWidth = mResources.getDimensionPixelSize(R.dimen.gestures_assistant_width);
        float assistantHeight = Math.max(navbarHeight, mContractInfo.getWindowCornerRadius());
        mAssistantLeftRegion.bottom = mAssistantRightRegion.bottom = orientationRectF.bottom;
        mAssistantLeftRegion.top = mAssistantRightRegion.top =
                orientationRectF.bottom - assistantHeight;

        mAssistantLeftRegion.left = 0;
        mAssistantLeftRegion.right = assistantWidth;

        mAssistantRightRegion.right = orientationRectF.right;
        mAssistantRightRegion.left = orientationRectF.right - assistantWidth;
    }

    boolean touchInAssistantRegion(MotionEvent ev) {
        return mAssistantLeftRegion.contains(ev.getX(), ev.getY())
                || mAssistantRightRegion.contains(ev.getX(), ev.getY());

    }

    boolean touchInOneHandedModeRegion(MotionEvent ev) {
        return mOneHandedModeRegion.contains(ev.getX(), ev.getY());
    }

    private int getNavbarSize(String resName) {
        return ResourceUtils.getNavbarSize(resName, mResources);
    }

    boolean touchInValidSwipeRegions(float x, float y) {
        if (enableLog()) {
            Log.d(TAG, "touchInValidSwipeRegions " + x + "," + y + " in " + mLastRectTouched);
        }
        if (mLastRectTouched != null) {
            return mLastRectTouched.contains(x, y);
        }
        return false;
    }

    int getCurrentActiveRotation() {
        return mActiveTouchRotation;
    }

    int getQuickStepStartingRotation() {
        return mQuickStepStartingRotation;
    }

    public void transform(MotionEvent event) {
        int eventAction = event.getActionMasked();
        switch (eventAction) {
            case ACTION_MOVE: {
                if (mLastRectTouched == null) {
                    return;
                }
                if (TaskAnimationManager.SHELL_TRANSITIONS_ROTATION) {
                    if (event.getSurfaceRotation() != mActiveTouchRotation) {
                        // With Shell transitions, we should rotated to the orientation at the start
                        // of the gesture not the current display rotation which will happen early
                        mLastRectTouched.applyTransform(event,
                                deltaRotation(event.getSurfaceRotation(), mActiveTouchRotation),
                                true);
                    }
                } else {
                    mLastRectTouched.applyTransformFromRotation(event, mCachedDisplayInfo.rotation,
                            true);
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP: {
                if (mLastRectTouched == null) {
                    return;
                }
                if (TaskAnimationManager.SHELL_TRANSITIONS_ROTATION) {
                    if (event.getSurfaceRotation() != mActiveTouchRotation) {
                        // With Shell transitions, we should rotated to the orientation at the start
                        // of the gesture not the current display rotation which will happen early
                        mLastRectTouched.applyTransform(event,
                                deltaRotation(event.getSurfaceRotation(), mActiveTouchRotation),
                                true);
                    }
                } else {
                    mLastRectTouched.applyTransformFromRotation(event, mCachedDisplayInfo.rotation,
                            true);
                }
                mLastRectTouched = null;
                break;
            }
            case ACTION_POINTER_DOWN:
            case ACTION_DOWN: {
                if (enableLog()) {
                    Log.d(TAG, "ACTION_DOWN mLastRectTouched: " + mLastRectTouched);
                }
                if (mLastRectTouched != null) {
                    return;
                }

                for (OrientationRectF rect : mSwipeTouchRegions.values()) {
                    if (enableLog()) {
                        Log.d(TAG, "ACTION_DOWN rect: " + rect);
                    }
                    if (rect == null) {
                        continue;
                    }
                    if (rect.applyTransformFromRotation(
                            event, mCachedDisplayInfo.rotation, false)) {
                        mLastRectTouched = rect;
                        mActiveTouchRotation = rect.getRotation();
                        if (mEnableMultipleRegions
                                && mCachedDisplayInfo.rotation == mActiveTouchRotation) {
                            // TODO(b/154580671) might make this block unnecessary
                            // Start a touch session for the default nav region for the display
                            mQuickStepStartingRotation = mLastRectTouched.getRotation();
                            resetSwipeRegions();
                        }
                        if (enableLog()) {
                            Log.d(TAG, "set active region: " + rect);
                        }
                        return;
                    }
                }
                break;
            }
        }
    }

    private boolean enableLog() {
        return DEBUG || TestProtocol.sDebugTracing;
    }

    public void dump(PrintWriter pw) {
        pw.println("OrientationTouchTransformerState: ");
        pw.println("  currentActiveRotation=" + getCurrentActiveRotation());
        pw.println("  lastTouchedRegion=" + mLastRectTouched);
        pw.println("  multipleRegionsEnabled=" + mEnableMultipleRegions);
        StringBuilder regions = new StringBuilder("  currentTouchableRotations=");
        for (CachedDisplayInfo key: mSwipeTouchRegions.keySet()) {
            OrientationRectF rectF = mSwipeTouchRegions.get(key);
            regions.append(rectF).append(" ");
        }
        pw.println(regions.toString());
        pw.println("  mNavBarGesturalHeight=" + mNavBarGesturalHeight);
        pw.println("  mNavBarLargerGesturalHeight=" + mNavBarLargerGesturalHeight);
        pw.println("  mOneHandedModeRegion=" + mOneHandedModeRegion);
    }
}
