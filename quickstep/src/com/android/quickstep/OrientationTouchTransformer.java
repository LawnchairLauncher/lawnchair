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

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.Surface;

import com.android.launcher3.R;
import com.android.launcher3.ResourceUtils;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.util.DefaultDisplay;

import java.io.PrintWriter;

/**
 * Maintains state for supporting nav bars and tracking their gestures in multiple orientations.
 * See {@link OrientationRectF#applyTransform(MotionEvent, boolean)} for transformation of
 * MotionEvents from one orientation's coordinate space to another's.
 *
 * This class only supports single touch/pointer gesture tracking for touches started in a supported
 * nav bar region.
 */
class OrientationTouchTransformer {

    private static final String TAG = "OrientationTouchTransformer";
    private static final boolean DEBUG = false;
    private static final int MAX_ORIENTATIONS = 4;

    private SparseArray<OrientationRectF> mSwipeTouchRegions = new SparseArray<>(MAX_ORIENTATIONS);
    private final RectF mAssistantLeftRegion = new RectF();
    private final RectF mAssistantRightRegion = new RectF();
    private int mCurrentRotation;
    private boolean mEnableMultipleRegions;
    private Resources mResources;
    private OrientationRectF mLastRectTouched;
    private SysUINavigationMode.Mode mMode;
    private QuickStepContractInfo mContractInfo;
    private int mQuickStepStartingRotation = -1;

    /** For testability */
    interface QuickStepContractInfo {
        float getWindowCornerRadius();
    }

    OrientationTouchTransformer(Resources resources, SysUINavigationMode.Mode mode,
            QuickStepContractInfo contractInfo) {
        mResources = resources;
        mMode = mode;
        mContractInfo = contractInfo;
    }

    void setNavigationMode(SysUINavigationMode.Mode newMode, DefaultDisplay.Info info) {
        if (mMode == newMode) {
            return;
        }
        this.mMode = newMode;
        resetSwipeRegions(info);
    }

    /**
     * Sets the current nav bar region to listen to events for as determined by
     * {@param info}. If multiple nav bar regions are enabled, then this region will be added
     * alongside other regions.
     * Ok to call multiple times
     *
     * @see #enableMultipleRegions(boolean, DefaultDisplay.Info)
     */
    void createOrAddTouchRegion(DefaultDisplay.Info info) {
        mCurrentRotation = info.rotation;
        if (mQuickStepStartingRotation > -1 && mCurrentRotation == mQuickStepStartingRotation) {
            // Ignore nav bars in other rotations except for the one we started out in
            resetSwipeRegions(info);
            return;
        }

        OrientationRectF region = mSwipeTouchRegions.get(mCurrentRotation);
        if (region != null) {
            return;
        }

        if (mEnableMultipleRegions) {
            mSwipeTouchRegions.put(mCurrentRotation, createRegionForDisplay(info));
        } else {
            resetSwipeRegions(info);
        }
    }

    /**
     * Call when we want to start tracking nav bar touch regions in multiple orientations.
     * ALSO, you BETTER call this with {@param enableMultipleRegions} set to false once you're done.
     *
     * @param enableMultipleRegions Set to true to start tracking multiple nav bar regions
     * @param info The current displayInfo
     */
    void enableMultipleRegions(boolean enableMultipleRegions, DefaultDisplay.Info info) {
        mEnableMultipleRegions = enableMultipleRegions;
        if (!enableMultipleRegions) {
            mQuickStepStartingRotation = -1;
            resetSwipeRegions(info);
        } else {
            if (mLastRectTouched != null) {
                // mLastRectTouched can be null if gesture type is changed (ex. from settings)
                // but nav bar hasn't been interacted with yet.
                mQuickStepStartingRotation = mLastRectTouched.mRotation;
            }
        }
    }

    /**
     * Only saves the swipe region represented by {@param region}, clears the
     * rest from {@link #mSwipeTouchRegions}
     * To be called whenever we want to stop tracking more than one swipe region.
     * Ok to call multiple times.
     */
    private void resetSwipeRegions(DefaultDisplay.Info region) {
        if (DEBUG) {
            Log.d(TAG, "clearing all regions except rotation: " + mCurrentRotation);
        }

        mCurrentRotation = region.rotation;
        mSwipeTouchRegions.clear();
        mSwipeTouchRegions.put(mCurrentRotation, createRegionForDisplay(region));
    }

    private OrientationRectF createRegionForDisplay(DefaultDisplay.Info display) {
        if (DEBUG) {
            Log.d(TAG, "creating rotation region for: " + mCurrentRotation);
        }

        Point size = display.realSize;
        int rotation = display.rotation;
        OrientationRectF orientationRectF =
                new OrientationRectF(0, 0, size.x, size.y, rotation);
        if (mMode == SysUINavigationMode.Mode.NO_BUTTON) {
            int touchHeight = getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
            orientationRectF.top = orientationRectF.bottom - touchHeight;

            final int assistantWidth = mResources
                    .getDimensionPixelSize(R.dimen.gestures_assistant_width);
            final float assistantHeight = Math.max(touchHeight,
                    mContractInfo.getWindowCornerRadius());
            mAssistantLeftRegion.bottom = mAssistantRightRegion.bottom = orientationRectF.bottom;
            mAssistantLeftRegion.top = mAssistantRightRegion.top =
                    orientationRectF.bottom - assistantHeight;

            mAssistantLeftRegion.left = 0;
            mAssistantLeftRegion.right = assistantWidth;

            mAssistantRightRegion.right = orientationRectF.right;
            mAssistantRightRegion.left = orientationRectF.right - assistantWidth;
        } else {
            mAssistantLeftRegion.setEmpty();
            mAssistantRightRegion.setEmpty();
            switch (rotation) {
                case Surface.ROTATION_90:
                    orientationRectF.left = orientationRectF.right
                            - getNavbarSize(ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);
                    break;
                case Surface.ROTATION_270:
                    orientationRectF.right = orientationRectF.left
                            + getNavbarSize(ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);
                    break;
                default:
                    orientationRectF.top = orientationRectF.bottom
                            - getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
            }
        }

        return orientationRectF;
    }

    boolean touchInAssistantRegion(MotionEvent ev) {
        return mAssistantLeftRegion.contains(ev.getX(), ev.getY())
                || mAssistantRightRegion.contains(ev.getX(), ev.getY());

    }

    private int getNavbarSize(String resName) {
        return ResourceUtils.getNavbarSize(resName, mResources);
    }

    boolean touchInValidSwipeRegions(float x, float y) {
        if (mLastRectTouched != null) {
            return mLastRectTouched.contains(x, y);
        }
        return false;
    }

    int getCurrentActiveRotation() {
        if (mLastRectTouched == null) {
            return 0;
        } else {
            return mLastRectTouched.mRotation;
        }
    }

    public void transform(MotionEvent event) {
        int eventAction = event.getActionMasked();
        switch (eventAction) {
            case ACTION_MOVE: {
                if (mLastRectTouched == null) {
                    return;
                }
                mLastRectTouched.applyTransform(event, true);
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP: {
                if (mLastRectTouched == null) {
                    return;
                }
                mLastRectTouched.applyTransform(event, true);
                mLastRectTouched = null;
                break;
            }
            case ACTION_POINTER_DOWN:
            case ACTION_DOWN: {
                if (mLastRectTouched != null) {
                    return;
                }

                for (int i = 0; i < MAX_ORIENTATIONS; i++) {
                    OrientationRectF rect = mSwipeTouchRegions.get(i);
                    if (rect == null) {
                        continue;
                    }
                    if (rect.applyTransform(event, false)) {
                        mLastRectTouched = rect;
                        if (DEBUG) {
                            Log.d(TAG, "set active region: " + rect);
                        }
                        return;
                    }
                }
                break;
            }
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("OrientationTouchTransformerState: ");
        pw.println("  currentActiveRotation=" + getCurrentActiveRotation());
        pw.println("  lastTouchedRegion=" + mLastRectTouched);
        pw.println("  multipleRegionsEnabled=" + mEnableMultipleRegions);
        StringBuilder regions = new StringBuilder("  currentTouchableRotations=");
        for(int i = 0; i < mSwipeTouchRegions.size(); i++) {
            OrientationRectF rectF = mSwipeTouchRegions.get(mSwipeTouchRegions.keyAt(i));
            regions.append(rectF.mRotation).append(" ");
        }
        pw.println(regions.toString());
    }

    private class OrientationRectF extends RectF {

        /**
         * Delta to subtract width and height by because if we report the translated touch
         * bounds as the width and height, calling {@link RectF#contains(float, float)} will
         * be false
         */
        private float maxDelta = 0.001f;

        private int mRotation;
        private float mHeight;
        private float mWidth;

        OrientationRectF(float left, float top, float right, float bottom, int rotation) {
            super(left, top, right, bottom);
            this.mRotation = rotation;
            mHeight = bottom - maxDelta;
            mWidth = right - maxDelta;
        }

        @Override
        public String toString() {
            String s = super.toString();
            s += " rotation: " + mRotation;
            return s;
        }

        boolean applyTransform(MotionEvent event, boolean forceTransform) {
            // TODO(b/149658423): See if we can use RotationHelper.getRotationMatrix here
            MotionEvent tmp = MotionEvent.obtain(event);
            Matrix outMatrix = new Matrix();
            int delta = RotationHelper.deltaRotation(mCurrentRotation, mRotation);
            switch (delta) {
                case Surface.ROTATION_0:
                    outMatrix.reset();
                    break;
                case Surface.ROTATION_90:
                    outMatrix.setRotate(270);
                    outMatrix.postTranslate(0, mHeight);
                    break;
                case Surface.ROTATION_180:
                    outMatrix.setRotate(180);
                    outMatrix.postTranslate(mHeight, mWidth);
                    break;
                case Surface.ROTATION_270:
                    outMatrix.setRotate(90);
                    outMatrix.postTranslate(mWidth, 0);
                    break;
            }

            tmp.transform(outMatrix);
            if (DEBUG) {
                Log.d(TAG, "original: " + event.getX() + ", " + event.getY()
                                + " new: " + tmp.getX() + ", " + tmp.getY()
                                + " rect: " + this + " forceTransform: " + forceTransform
                                + " contains: " + contains(tmp.getX(), tmp.getY()));
            }

            if (forceTransform || contains(tmp.getX(), tmp.getY())) {
                event.transform(outMatrix);
                tmp.recycle();
                return true;
            }
            return false;
        }
    }
}
