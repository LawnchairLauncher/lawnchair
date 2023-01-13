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

package com.android.systemui.shared.navigationbar;

import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.CompositionSamplingListener;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

/**
 * A helper class to sample regions on the screen and inspect its luminosity.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class RegionSamplingHelper implements View.OnAttachStateChangeListener,
        View.OnLayoutChangeListener {

    // Luminance threshold to determine black/white contrast for the navigation affordances.
    // Passing the threshold of this luminance value will make the button black otherwise white
    private static final float NAVIGATION_LUMINANCE_THRESHOLD = 0.5f;
    // Luminance change threshold that allows applying new value if difference was exceeded
    private static final float NAVIGATION_LUMINANCE_CHANGE_THRESHOLD = 0.05f;

    private final Handler mHandler = new Handler();
    private final View mSampledView;

    private final CompositionSamplingListener mSamplingListener;

    /**
     * The requested sampling bounds that we want to sample from
     */
    private final Rect mSamplingRequestBounds = new Rect();

    /**
     * The sampling bounds that are currently registered.
     */
    private final Rect mRegisteredSamplingBounds = new Rect();
    private final SamplingCallback mCallback;
    private final Executor mBackgroundExecutor;
    private boolean mSamplingEnabled = false;
    private boolean mSamplingListenerRegistered = false;

    private float mLastMedianLuma;
    private float mCurrentMedianLuma;
    private boolean mWaitingOnDraw;
    private boolean mIsDestroyed;

    private boolean mFirstSamplingAfterStart;
    private boolean mWindowVisible;
    private boolean mWindowHasBlurs;
    private SurfaceControl mRegisteredStopLayer = null;
    // A copy of mRegisteredStopLayer where we own the life cycle and can access from a bg thread.
    private SurfaceControl mWrappedStopLayer = null;
    private ViewTreeObserver.OnDrawListener mUpdateOnDraw = new ViewTreeObserver.OnDrawListener() {
        @Override
        public void onDraw() {
            // We need to post the remove runnable, since it's not allowed to remove in onDraw
            mHandler.post(mRemoveDrawRunnable);
            RegionSamplingHelper.this.onDraw();
        }
    };
    private Runnable mRemoveDrawRunnable = new Runnable() {
        @Override
        public void run() {
            mSampledView.getViewTreeObserver().removeOnDrawListener(mUpdateOnDraw);
        }
    };

    public RegionSamplingHelper(View sampledView, SamplingCallback samplingCallback,
            Executor backgroundExecutor) {
        mBackgroundExecutor = backgroundExecutor;
        mSamplingListener = new CompositionSamplingListener(
                sampledView.getContext().getMainExecutor()) {
            @Override
            public void onSampleCollected(float medianLuma) {
                if (mSamplingEnabled) {
                    updateMediaLuma(medianLuma);
                }
            }
        };
        mSampledView = sampledView;
        mSampledView.addOnAttachStateChangeListener(this);
        mSampledView.addOnLayoutChangeListener(this);

        mCallback = samplingCallback;
    }

    private void onDraw() {
        if (mWaitingOnDraw) {
            mWaitingOnDraw = false;
            updateSamplingListener();
        }
    }

    public void start(Rect initialSamplingBounds) {
        if (!mCallback.isSamplingEnabled()) {
            return;
        }
        if (initialSamplingBounds != null) {
            mSamplingRequestBounds.set(initialSamplingBounds);
        }
        mSamplingEnabled = true;
        // make sure we notify once
        mLastMedianLuma = -1;
        mFirstSamplingAfterStart = true;
        updateSamplingListener();
    }

    public void stop() {
        mSamplingEnabled = false;
        updateSamplingListener();
    }

    public void stopAndDestroy() {
        stop();
        mSamplingListener.destroy();
        mIsDestroyed = true;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        updateSamplingListener();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        stopAndDestroy();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        updateSamplingRect();
    }

    private void updateSamplingListener() {
        boolean isSamplingEnabled = mSamplingEnabled
                && !mSamplingRequestBounds.isEmpty()
                && mWindowVisible
                && !mWindowHasBlurs
                && (mSampledView.isAttachedToWindow() || mFirstSamplingAfterStart);
        if (isSamplingEnabled) {
            ViewRootImpl viewRootImpl = mSampledView.getViewRootImpl();
            SurfaceControl stopLayerControl = null;
            if (viewRootImpl != null) {
                 stopLayerControl = viewRootImpl.getSurfaceControl();
            }
            if (stopLayerControl == null || !stopLayerControl.isValid()) {
                if (!mWaitingOnDraw) {
                    mWaitingOnDraw = true;
                    // The view might be attached but we haven't drawn yet, so wait until the
                    // next draw to update the listener again with the stop layer, such that our
                    // own drawing doesn't affect the sampling.
                    if (mHandler.hasCallbacks(mRemoveDrawRunnable)) {
                        mHandler.removeCallbacks(mRemoveDrawRunnable);
                    } else {
                        mSampledView.getViewTreeObserver().addOnDrawListener(mUpdateOnDraw);
                    }
                }
                // If there's no valid surface, let's just sample without a stop layer, so we
                // don't have to delay
                stopLayerControl = null;
            }
            if (!mSamplingRequestBounds.equals(mRegisteredSamplingBounds)
                    || mRegisteredStopLayer != stopLayerControl) {
                // We only want to re-register if something actually changed
                unregisterSamplingListener();
                mSamplingListenerRegistered = true;
                SurfaceControl wrappedStopLayer = stopLayerControl == null
                        ? null : new SurfaceControl(stopLayerControl, "regionSampling");
                mBackgroundExecutor.execute(() -> {
                    if (wrappedStopLayer != null && !wrappedStopLayer.isValid()) {
                        return;
                    }
                    CompositionSamplingListener.register(mSamplingListener, DEFAULT_DISPLAY,
                            wrappedStopLayer, mSamplingRequestBounds);
                });
                mRegisteredSamplingBounds.set(mSamplingRequestBounds);
                mRegisteredStopLayer = stopLayerControl;
                mWrappedStopLayer = wrappedStopLayer;
            }
            mFirstSamplingAfterStart = false;
        } else {
            unregisterSamplingListener();
        }
    }

    private void unregisterSamplingListener() {
        if (mSamplingListenerRegistered) {
            mSamplingListenerRegistered = false;
            SurfaceControl wrappedStopLayer = mWrappedStopLayer;
            mRegisteredStopLayer = null;
            mRegisteredSamplingBounds.setEmpty();
            mBackgroundExecutor.execute(() -> {
                CompositionSamplingListener.unregister(mSamplingListener);
                if (wrappedStopLayer != null && wrappedStopLayer.isValid()) {
                    wrappedStopLayer.release();
                }
            });
        }
    }

    private void updateMediaLuma(float medianLuma) {
        mCurrentMedianLuma = medianLuma;

        // If the difference between the new luma and the current luma is larger than threshold
        // then apply the current luma, this is to prevent small changes causing colors to flicker
        if (Math.abs(mCurrentMedianLuma - mLastMedianLuma)
                > NAVIGATION_LUMINANCE_CHANGE_THRESHOLD) {
            mCallback.onRegionDarknessChanged(
                    medianLuma < NAVIGATION_LUMINANCE_THRESHOLD /* isRegionDark */);
            mLastMedianLuma = medianLuma;
        }
    }

    public void updateSamplingRect() {
        Rect sampledRegion = mCallback.getSampledRegion(mSampledView);
        if (!mSamplingRequestBounds.equals(sampledRegion)) {
            mSamplingRequestBounds.set(sampledRegion);
            updateSamplingListener();
        }
    }

    public void setWindowVisible(boolean visible) {
        mWindowVisible = visible;
        updateSamplingListener();
    }

    /**
     * If we're blurring the shade window.
     */
    public void setWindowHasBlurs(boolean hasBlurs) {
        mWindowHasBlurs = hasBlurs;
        updateSamplingListener();
    }

    public void dump(PrintWriter pw) {
        pw.println("RegionSamplingHelper:");
        pw.println("  sampleView isAttached: " + mSampledView.isAttachedToWindow());
        pw.println("  sampleView isScValid: " + (mSampledView.isAttachedToWindow()
                ? mSampledView.getViewRootImpl().getSurfaceControl().isValid()
                : "notAttached"));
        pw.println("  mSamplingEnabled: " + mSamplingEnabled);
        pw.println("  mSamplingListenerRegistered: " + mSamplingListenerRegistered);
        pw.println("  mSamplingRequestBounds: " + mSamplingRequestBounds);
        pw.println("  mRegisteredSamplingBounds: " + mRegisteredSamplingBounds);
        pw.println("  mLastMedianLuma: " + mLastMedianLuma);
        pw.println("  mCurrentMedianLuma: " + mCurrentMedianLuma);
        pw.println("  mWindowVisible: " + mWindowVisible);
        pw.println("  mWindowHasBlurs: " + mWindowHasBlurs);
        pw.println("  mWaitingOnDraw: " + mWaitingOnDraw);
        pw.println("  mRegisteredStopLayer: " + mRegisteredStopLayer);
        pw.println("  mWrappedStopLayer: " + mWrappedStopLayer);
        pw.println("  mIsDestroyed: " + mIsDestroyed);
    }

    public interface SamplingCallback {
        /**
         * Called when the darkness of the sampled region changes
         * @param isRegionDark true if the sampled luminance is below the luminance threshold
         */
        void onRegionDarknessChanged(boolean isRegionDark);

        /**
         * Get the sampled region of interest from the sampled view
         * @param sampledView The view that this helper is attached to for convenience
         * @return the region to be sampled in sceen coordinates. Return {@code null} to avoid
         * sampling in this frame
         */
        Rect getSampledRegion(View sampledView);

        /**
         * @return if sampling should be enabled in the current configuration
         */
        default boolean isSamplingEnabled() {
            return true;
        }
    }
}
