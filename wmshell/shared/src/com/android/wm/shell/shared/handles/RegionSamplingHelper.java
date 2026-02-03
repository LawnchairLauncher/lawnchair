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

package com.android.wm.shell.shared.handles;

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

import androidx.annotation.VisibleForTesting;

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
    private final SysuiCompositionSamplingListener mCompositionSamplingListener;
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

    /**
     * @deprecated Pass a main executor.
     */
    public RegionSamplingHelper(View sampledView, SamplingCallback samplingCallback,
            Executor backgroundExecutor) {
        this(sampledView, samplingCallback, sampledView.getContext().getMainExecutor(),
                backgroundExecutor);
    }

    public RegionSamplingHelper(View sampledView, SamplingCallback samplingCallback,
            Executor mainExecutor, Executor backgroundExecutor) {
        this(sampledView, samplingCallback, mainExecutor,
                backgroundExecutor, new SysuiCompositionSamplingListener());
    }

    @VisibleForTesting
    RegionSamplingHelper(View sampledView, SamplingCallback samplingCallback,
            Executor mainExecutor, Executor backgroundExecutor,
            SysuiCompositionSamplingListener compositionSamplingListener) {
        mBackgroundExecutor = backgroundExecutor;
        mCompositionSamplingListener = compositionSamplingListener;
        mSamplingListener = new CompositionSamplingListener(mainExecutor) {
            @Override
            public void onSampleCollected(float medianLuma) {
                if (mSamplingEnabled) {
                    updateMedianLuma(medianLuma);
                }
            }
        };
        mSampledView = sampledView;
        mSampledView.addOnAttachStateChangeListener(this);
        mSampledView.addOnLayoutChangeListener(this);

        mCallback = samplingCallback;
    }

    /**
     * Make callback accessible
     */
    @VisibleForTesting
    public SamplingCallback getCallback() {
        return mCallback;
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
        mBackgroundExecutor.execute(mSamplingListener::destroy);
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
                SurfaceControl wrappedStopLayer = wrap(stopLayerControl);

                // pass this to background thread to avoid empty Rect race condition
                final Rect boundsCopy = new Rect(mSamplingRequestBounds);

                mBackgroundExecutor.execute(() -> {
                    if (wrappedStopLayer != null && !wrappedStopLayer.isValid()) {
                        return;
                    }
                    mCompositionSamplingListener.register(mSamplingListener, DEFAULT_DISPLAY,
                            wrappedStopLayer, boundsCopy);
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

    @VisibleForTesting
    protected SurfaceControl wrap(SurfaceControl stopLayerControl) {
        return stopLayerControl == null ? null : new SurfaceControl(stopLayerControl,
                "regionSampling");
    }

    private void unregisterSamplingListener() {
        if (mSamplingListenerRegistered) {
            mSamplingListenerRegistered = false;
            SurfaceControl wrappedStopLayer = mWrappedStopLayer;
            mRegisteredStopLayer = null;
            mWrappedStopLayer = null;
            mRegisteredSamplingBounds.setEmpty();
            mBackgroundExecutor.execute(() -> {
                mCompositionSamplingListener.unregister(mSamplingListener);
                if (wrappedStopLayer != null && wrappedStopLayer.isValid()) {
                    wrappedStopLayer.release();
                }
            });
        }
    }

    private void updateMedianLuma(float medianLuma) {
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
        dump("", pw);
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "RegionSamplingHelper:");
        pw.println(prefix + "\tsampleView isAttached: " + mSampledView.isAttachedToWindow());
        pw.println(prefix + "\tsampleView isScValid: " + (mSampledView.isAttachedToWindow()
                ? mSampledView.getViewRootImpl().getSurfaceControl().isValid()
                : "notAttached"));
        pw.println(prefix + "\tmSamplingEnabled: " + mSamplingEnabled);
        pw.println(prefix + "\tmSamplingListenerRegistered: " + mSamplingListenerRegistered);
        pw.println(prefix + "\tmSamplingRequestBounds: " + mSamplingRequestBounds);
        pw.println(prefix + "\tmRegisteredSamplingBounds: " + mRegisteredSamplingBounds);
        pw.println(prefix + "\tmLastMedianLuma: " + mLastMedianLuma);
        pw.println(prefix + "\tmCurrentMedianLuma: " + mCurrentMedianLuma);
        pw.println(prefix + "\tmWindowVisible: " + mWindowVisible);
        pw.println(prefix + "\tmWindowHasBlurs: " + mWindowHasBlurs);
        pw.println(prefix + "\tmWaitingOnDraw: " + mWaitingOnDraw);
        pw.println(prefix + "\tmRegisteredStopLayer: " + mRegisteredStopLayer);
        pw.println(prefix + "\tmWrappedStopLayer: " + mWrappedStopLayer);
        pw.println(prefix + "\tmIsDestroyed: " + mIsDestroyed);
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
         * @return the region to be sampled in screen coordinates. Return {@code null} to avoid
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

    @VisibleForTesting
    public static class SysuiCompositionSamplingListener {
        public void register(CompositionSamplingListener listener,
                int displayId, SurfaceControl stopLayer, Rect samplingArea) {
            CompositionSamplingListener.register(listener, displayId, stopLayer, samplingArea);
        }

        /**
         * Unregisters a sampling listener.
         */
        public void unregister(CompositionSamplingListener listener) {
            CompositionSamplingListener.unregister(listener);
        }
    }
}
