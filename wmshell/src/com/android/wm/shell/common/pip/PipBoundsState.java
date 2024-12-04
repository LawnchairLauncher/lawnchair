/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common.pip;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Size;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.function.TriConsumer;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Singleton source of truth for the current state of PIP bounds.
 */
public class PipBoundsState {
    public static final int STASH_TYPE_NONE = 0;
    public static final int STASH_TYPE_LEFT = 1;
    public static final int STASH_TYPE_RIGHT = 2;
    public static final int STASH_TYPE_BOTTOM = 3;
    public static final int STASH_TYPE_TOP = 4;

    @IntDef(prefix = { "STASH_TYPE_" }, value =  {
            STASH_TYPE_NONE,
            STASH_TYPE_LEFT,
            STASH_TYPE_RIGHT,
            STASH_TYPE_BOTTOM,
            STASH_TYPE_TOP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StashType {}

    private static final String TAG = PipBoundsState.class.getSimpleName();

    private final @NonNull Rect mBounds = new Rect();
    private final @NonNull Rect mMovementBounds = new Rect();
    private final @NonNull Rect mNormalBounds = new Rect();
    private final @NonNull Rect mExpandedBounds = new Rect();
    private final @NonNull Rect mNormalMovementBounds = new Rect();
    private final @NonNull Rect mExpandedMovementBounds = new Rect();
    private final @NonNull PipDisplayLayoutState mPipDisplayLayoutState;
    private final Point mMaxSize = new Point();
    private final Point mMinSize = new Point();
    private final @NonNull Context mContext;
    private float mAspectRatio;
    private int mStashedState = STASH_TYPE_NONE;
    private int mStashOffset;
    private @Nullable PipReentryState mPipReentryState;
    private final LauncherState mLauncherState = new LauncherState();
    private final @NonNull SizeSpecSource mSizeSpecSource;
    private @Nullable ComponentName mLastPipComponentName;
    private final @NonNull MotionBoundsState mMotionBoundsState = new MotionBoundsState();
    private boolean mIsImeShowing;
    private int mImeHeight;
    private boolean mIsShelfShowing;
    private int mShelfHeight;
    /** Whether the user has resized the PIP manually. */
    private boolean mHasUserResizedPip;
    /** Whether the user has moved the PIP manually. */
    private boolean mHasUserMovedPip;
    /**
     * Areas defined by currently visible apps that they prefer to keep clear from overlays such as
     * the PiP. Restricted areas may only move the PiP a limited amount from its anchor position.
     * The system will try to respect these areas, but when not possible will ignore them.
     *
     * @see android.view.View#setPreferKeepClearRects
     */
    private final Set<Rect> mRestrictedKeepClearAreas = new ArraySet<>();
    /**
     * Areas defined by currently visible apps holding
     * {@link android.Manifest.permission#SET_UNRESTRICTED_KEEP_CLEAR_AREAS} that they prefer to
     * keep clear from overlays such as the PiP.
     * Unrestricted areas can move the PiP farther than restricted areas, and the system will try
     * harder to respect these areas.
     *
     * @see android.view.View#setPreferKeepClearRects
     */
    private final Set<Rect> mUnrestrictedKeepClearAreas = new ArraySet<>();
    /**
     * Additional to {@link #mUnrestrictedKeepClearAreas}, allow the caller to append named bounds
     * as unrestricted keep clear area. Values in this map would be appended to
     * {@link #getUnrestrictedKeepClearAreas()} and this is meant for internal usage only.
     */
    private final Map<String, Rect> mNamedUnrestrictedKeepClearAreas = new HashMap<>();

    private @Nullable Runnable mOnMinimalSizeChangeCallback;
    private @Nullable TriConsumer<Boolean, Integer, Boolean> mOnShelfVisibilityChangeCallback;
    private List<Consumer<Rect>> mOnPipExclusionBoundsChangeCallbacks = new ArrayList<>();
    private List<Consumer<Float>> mOnAspectRatioChangedCallbacks = new ArrayList<>();

    // the size of the current bounds relative to the max size spec
    private float mBoundsScale;

    public PipBoundsState(@NonNull Context context, @NonNull SizeSpecSource sizeSpecSource,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState) {
        mContext = context;
        reloadResources();
        mSizeSpecSource = sizeSpecSource;
        mPipDisplayLayoutState = pipDisplayLayoutState;

        // Update the relative proportion of the bounds compared to max possible size. Max size
        // spec takes the aspect ratio of the bounds into account, so both width and height
        // scale by the same factor.
        addPipExclusionBoundsChangeCallback((bounds) -> {
            updateBoundsScale();
        });
    }

    /** Reloads the resources. */
    public void onConfigurationChanged() {
        reloadResources();

        // update the size spec resources upon config change too
        mSizeSpecSource.onConfigurationChanged();
    }

    /** Update the bounds scale percentage value. */
    public void updateBoundsScale() {
        mBoundsScale = Math.min((float) mBounds.width() / mMaxSize.x, 1.0f);
    }

    private void reloadResources() {
        mStashOffset = mContext.getResources().getDimensionPixelSize(R.dimen.pip_stash_offset);
    }

    /** Set the current PIP bounds. */
    public void setBounds(@NonNull Rect bounds) {
        mBounds.set(bounds);
        for (Consumer<Rect> callback : mOnPipExclusionBoundsChangeCallbacks) {
            callback.accept(bounds);
        }
    }

    /** Get the current PIP bounds. */
    @NonNull
    public Rect getBounds() {
        return new Rect(mBounds);
    }

    /**
     * Get the scale of the current bounds relative to the maximum size possible.
     *
     * @return 1.0 if {@link PipBoundsState#getBounds()} equals {@link PipBoundsState#getMaxSize()}.
     */
    public float getBoundsScale() {
        return mBoundsScale;
    }

    /** Returns the current movement bounds. */
    @NonNull
    public Rect getMovementBounds() {
        return mMovementBounds;
    }

    /** Set the current normal PIP bounds. */
    public void setNormalBounds(@NonNull Rect bounds) {
        mNormalBounds.set(bounds);
    }

    /** Get the current normal PIP bounds. */
    @NonNull
    public Rect getNormalBounds() {
        return mNormalBounds;
    }

    /** Set the expanded bounds of PIP. */
    public void setExpandedBounds(@NonNull Rect bounds) {
        mExpandedBounds.set(bounds);
    }

    /** Get the PIP expanded bounds. */
    @NonNull
    public Rect getExpandedBounds() {
        return mExpandedBounds;
    }

    /** Set the normal movement bounds. */
    public void setNormalMovementBounds(@NonNull Rect bounds) {
        mNormalMovementBounds.set(bounds);
    }

    /** Returns the normal movement bounds. */
    @NonNull
    public Rect getNormalMovementBounds() {
        return mNormalMovementBounds;
    }

    /** Set the expanded movement bounds. */
    public void setExpandedMovementBounds(@NonNull Rect bounds) {
        mExpandedMovementBounds.set(bounds);
    }

    /** Updates the min and max sizes based on the size spec and aspect ratio. */
    public void updateMinMaxSize(float aspectRatio) {
        final Size minSize = mSizeSpecSource.getMinSize(aspectRatio);
        mMinSize.set(minSize.getWidth(), minSize.getHeight());
        final Size maxSize = mSizeSpecSource.getMaxSize(aspectRatio);
        mMaxSize.set(maxSize.getWidth(), maxSize.getHeight());
    }

    /** Sets the max possible size for resize. */
    public void setMaxSize(int width, int height) {
        mMaxSize.set(width, height);
    }

    /** Sets the min possible size for resize. */
    public void setMinSize(int width, int height) {
        mMinSize.set(width, height);
    }

    public Point getMaxSize() {
        return mMaxSize;
    }

    public Point getMinSize() {
        return mMinSize;
    }

    /** Returns the expanded movement bounds. */
    @NonNull
    public Rect getExpandedMovementBounds() {
        return mExpandedMovementBounds;
    }

    /** Dictate where PiP currently should be stashed, if at all. */
    public void setStashed(@StashType int stashedState) {
        if (mStashedState == stashedState) {
            return;
        }

        mStashedState = stashedState;
        try {
            ActivityTaskManager.getService().onPictureInPictureUiStateChanged(
                    new PictureInPictureUiState(stashedState != STASH_TYPE_NONE /* isStashed */)
            );
        } catch (RemoteException | IllegalStateException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Unable to set alert PiP state change.", TAG);
        }
    }

    /**
     * Return where the PiP is stashed, if at all.
     * @return {@code STASH_NONE}, {@code STASH_LEFT} or {@code STASH_RIGHT}.
     */
    public @StashType int getStashedState() {
        return mStashedState;
    }

    /** Whether PiP is stashed or not. */
    public boolean isStashed() {
        return mStashedState != STASH_TYPE_NONE;
    }

    /** Returns the offset from the edge of the screen for PiP stash. */
    public int getStashOffset() {
        return mStashOffset;
    }

    /** Set the PIP aspect ratio. */
    public void setAspectRatio(float aspectRatio) {
        if (Float.compare(mAspectRatio, aspectRatio) != 0) {
            mAspectRatio = aspectRatio;
            for (Consumer<Float> callback : mOnAspectRatioChangedCallbacks) {
                callback.accept(mAspectRatio);
            }
        }
    }

    /** Get the PIP aspect ratio. */
    public float getAspectRatio() {
        return mAspectRatio;
    }

    /** Save the reentry state to restore to when re-entering PIP mode. */
    public void saveReentryState(float fraction) {
        mPipReentryState = new PipReentryState(mBoundsScale, fraction);
    }

    /** Returns the saved reentry state. */
    @Nullable
    public PipReentryState getReentryState() {
        return mPipReentryState;
    }

    /** Set the last {@link ComponentName} to enter PIP mode. */
    public void setLastPipComponentName(@Nullable ComponentName lastPipComponentName) {
        final boolean changed = !Objects.equals(mLastPipComponentName, lastPipComponentName);
        mLastPipComponentName = lastPipComponentName;
        if (changed) {
            clearReentryState();
            setHasUserResizedPip(false);
            setHasUserMovedPip(false);
        }
    }

    /** Get the last PIP component name, if any. */
    @Nullable
    public ComponentName getLastPipComponentName() {
        return mLastPipComponentName;
    }

    /** Returns the display's bounds. */
    @NonNull
    public Rect getDisplayBounds() {
        return mPipDisplayLayoutState.getDisplayBounds();
    }

    /** Get a copy of the display layout. */
    @NonNull
    public DisplayLayout getDisplayLayout() {
        return mPipDisplayLayoutState.getDisplayLayout();
    }

    /**
     * Clears the PiP re-entry state.
     */
    @VisibleForTesting
    public void clearReentryState() {
        mPipReentryState = null;
    }

    /** Sets the preferred size of PIP as specified by the activity in PIP mode. */
    public void setOverrideMinSize(@Nullable Size overrideMinSize) {
        final boolean changed = !Objects.equals(overrideMinSize, getOverrideMinSize());
        mSizeSpecSource.setOverrideMinSize(overrideMinSize);
        if (changed && mOnMinimalSizeChangeCallback != null) {
            mOnMinimalSizeChangeCallback.run();
        }
    }

    /** Returns the preferred minimal size specified by the activity in PIP. */
    @Nullable
    public Size getOverrideMinSize() {
        return mSizeSpecSource.getOverrideMinSize();
    }

    /** Returns the minimum edge size of the override minimum size, or 0 if not set. */
    public int getOverrideMinEdgeSize() {
        return mSizeSpecSource.getOverrideMinEdgeSize();
    }

    /** Get the state of the bounds in motion. */
    @NonNull
    public MotionBoundsState getMotionBoundsState() {
        return mMotionBoundsState;
    }

    /** Set whether the IME is currently showing and its height. */
    public void setImeVisibility(boolean imeShowing, int imeHeight) {
        mIsImeShowing = imeShowing;
        mImeHeight = imeHeight;
    }

    /** Returns whether the IME is currently showing. */
    public boolean isImeShowing() {
        return mIsImeShowing;
    }

    /** Returns the IME height. */
    public int getImeHeight() {
        return mImeHeight;
    }

    /** Set whether the shelf is showing and its height. */
    public void setShelfVisibility(boolean showing, int height) {
        setShelfVisibility(showing, height, true);
    }

    /** Set whether the shelf is showing and its height. */
    public void setShelfVisibility(boolean showing, int height, boolean updateMovementBounds) {
        final boolean shelfShowing = showing && height > 0;
        if (shelfShowing == mIsShelfShowing && height == mShelfHeight) {
            return;
        }

        mIsShelfShowing = showing;
        mShelfHeight = height;
        if (mOnShelfVisibilityChangeCallback != null) {
            mOnShelfVisibilityChangeCallback.accept(mIsShelfShowing, mShelfHeight,
                    updateMovementBounds);
        }
    }

    /** Set the keep clear areas onscreen. The PiP should ideally not cover them. */
    public void setKeepClearAreas(@NonNull Set<Rect> restrictedAreas,
            @NonNull Set<Rect> unrestrictedAreas) {
        mRestrictedKeepClearAreas.clear();
        mRestrictedKeepClearAreas.addAll(restrictedAreas);
        mUnrestrictedKeepClearAreas.clear();
        mUnrestrictedKeepClearAreas.addAll(unrestrictedAreas);
    }

    /** Add a named unrestricted keep clear area. */
    public void addNamedUnrestrictedKeepClearArea(@NonNull String name, Rect unrestrictedArea) {
        mNamedUnrestrictedKeepClearAreas.put(name, unrestrictedArea);
    }

    /** Remove a named unrestricted keep clear area. */
    public void removeNamedUnrestrictedKeepClearArea(@NonNull String name) {
        mNamedUnrestrictedKeepClearAreas.remove(name);
    }


    /**
     * @return restricted keep clear areas.
     */
    @NonNull
    public Set<Rect> getRestrictedKeepClearAreas() {
        return mRestrictedKeepClearAreas;
    }

    /**
     * @return unrestricted keep clear areas.
     */
    @NonNull
    public Set<Rect> getUnrestrictedKeepClearAreas() {
        if (mNamedUnrestrictedKeepClearAreas.isEmpty()) return mUnrestrictedKeepClearAreas;
        final Set<Rect> unrestrictedAreas = new ArraySet<>(mUnrestrictedKeepClearAreas);
        unrestrictedAreas.addAll(mNamedUnrestrictedKeepClearAreas.values());
        return unrestrictedAreas;
    }

    /**
     * Initialize states when first entering PiP.
     */
    public void setBoundsStateForEntry(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams params, PipBoundsAlgorithm pipBoundsAlgorithm) {
        setLastPipComponentName(componentName);
        setAspectRatio(pipBoundsAlgorithm.getAspectRatioOrDefault(params));
        setOverrideMinSize(pipBoundsAlgorithm.getMinimalSize(activityInfo));
    }

    /** Returns whether the shelf is currently showing. */
    public boolean isShelfShowing() {
        return mIsShelfShowing;
    }

    /** Returns the shelf height. */
    public int getShelfHeight() {
        return mShelfHeight;
    }

    /** Returns whether the user has resized the PIP. */
    public boolean hasUserResizedPip() {
        return mHasUserResizedPip;
    }

    /** Set whether the user has resized the PIP. */
    public void setHasUserResizedPip(boolean hasUserResizedPip) {
        mHasUserResizedPip = hasUserResizedPip;
    }

    /** Returns whether the user has moved the PIP. */
    public boolean hasUserMovedPip() {
        return mHasUserMovedPip;
    }

    /** Set whether the user has moved the PIP. */
    public void setHasUserMovedPip(boolean hasUserMovedPip) {
        mHasUserMovedPip = hasUserMovedPip;
    }

    /**
     * Registers a callback when the minimal size of PIP that is set by the app changes.
     */
    public void setOnMinimalSizeChangeCallback(@Nullable Runnable onMinimalSizeChangeCallback) {
        mOnMinimalSizeChangeCallback = onMinimalSizeChangeCallback;
    }

    /** Set a callback to be notified when the shelf visibility changes. */
    public void setOnShelfVisibilityChangeCallback(
            @Nullable TriConsumer<Boolean, Integer, Boolean> onShelfVisibilityChangeCallback) {
        mOnShelfVisibilityChangeCallback = onShelfVisibilityChangeCallback;
    }

    /**
     * Add a callback to watch out for PiP bounds. This is mostly used by SystemUI's
     * Back-gesture handler, to avoid conflicting with PiP when it's stashed.
     */
    public void addPipExclusionBoundsChangeCallback(
            @Nullable Consumer<Rect> onPipExclusionBoundsChangeCallback) {
        mOnPipExclusionBoundsChangeCallbacks.add(onPipExclusionBoundsChangeCallback);
        for (Consumer<Rect> callback : mOnPipExclusionBoundsChangeCallbacks) {
            callback.accept(getBounds());
        }
    }

    /**
     * Remove a callback that was previously added.
     */
    public void removePipExclusionBoundsChangeCallback(
            @Nullable Consumer<Rect> onPipExclusionBoundsChangeCallback) {
        mOnPipExclusionBoundsChangeCallbacks.remove(onPipExclusionBoundsChangeCallback);
    }

    /** Adds callback to listen on aspect ratio change. */
    public void addOnAspectRatioChangedCallback(
            @NonNull Consumer<Float> onAspectRatioChangedCallback) {
        if (!mOnAspectRatioChangedCallbacks.contains(onAspectRatioChangedCallback)) {
            mOnAspectRatioChangedCallbacks.add(onAspectRatioChangedCallback);
            onAspectRatioChangedCallback.accept(mAspectRatio);
        }
    }

    /** Removes callback to listen on aspect ratio change. */
    public void removeOnAspectRatioChangedCallback(
            @NonNull Consumer<Float> onAspectRatioChangedCallback) {
        if (mOnAspectRatioChangedCallbacks.contains(onAspectRatioChangedCallback)) {
            mOnAspectRatioChangedCallbacks.remove(onAspectRatioChangedCallback);
        }
    }

    public LauncherState getLauncherState() {
        return mLauncherState;
    }

    /** Source of truth for the current bounds of PIP that may be in motion. */
    public static class MotionBoundsState {
        /** The bounds used when PIP is in motion (e.g. during a drag or animation) */
        private final @NonNull Rect mBoundsInMotion = new Rect();
        /** The destination bounds to which PIP is animating. */
        private final @NonNull Rect mAnimatingToBounds = new Rect();

        /** Whether PIP is being dragged or animated (e.g. resizing, in fling, etc). */
        public boolean isInMotion() {
            return !mBoundsInMotion.isEmpty();
        }

        /** Set the temporary bounds used to represent the drag or animation bounds of PIP. */
        public void setBoundsInMotion(@NonNull Rect bounds) {
            mBoundsInMotion.set(bounds);
        }

        /** Set the bounds to which PIP is animating. */
        public void setAnimatingToBounds(@NonNull Rect bounds) {
            mAnimatingToBounds.set(bounds);
        }

        /** Called when all ongoing motion operations have ended. */
        public void onAllAnimationsEnded() {
            mBoundsInMotion.setEmpty();
        }

        /** Called when an ongoing physics animation has ended. */
        public void onPhysicsAnimationEnded() {
            mAnimatingToBounds.setEmpty();
        }

        /** Returns the motion bounds. */
        @NonNull
        public Rect getBoundsInMotion() {
            return mBoundsInMotion;
        }

        /** Returns the destination bounds to which PIP is currently animating. */
        @NonNull
        public Rect getAnimatingToBounds() {
            return mAnimatingToBounds;
        }

        void dump(PrintWriter pw, String prefix) {
            final String innerPrefix = prefix + "  ";
            pw.println(prefix + MotionBoundsState.class.getSimpleName());
            pw.println(innerPrefix + "mBoundsInMotion=" + mBoundsInMotion);
            pw.println(innerPrefix + "mAnimatingToBounds=" + mAnimatingToBounds);
        }
    }

    /** Data class for Launcher state. */
    public static final class LauncherState {
        private int mAppIconSizePx;

        public void setAppIconSizePx(int appIconSizePx) {
            mAppIconSizePx = appIconSizePx;
        }

        public int getAppIconSizePx() {
            return mAppIconSizePx;
        }

        void dump(PrintWriter pw, String prefix) {
            final String innerPrefix = prefix + "    ";
            pw.println(prefix + LauncherState.class.getSimpleName());
            pw.println(innerPrefix + "getAppIconSizePx=" + getAppIconSizePx());
        }
    }

    /**
     * Represents the state of pip to potentially restore upon reentry.
     */
    @VisibleForTesting
    public static final class PipReentryState {
        private static final String TAG = PipReentryState.class.getSimpleName();

        private final float mSnapFraction;
        private final float mBoundsScale;

        PipReentryState(float boundsScale, float snapFraction) {
            mBoundsScale = boundsScale;
            mSnapFraction = snapFraction;
        }

        public float getBoundsScale() {
            return mBoundsScale;
        }

        public float getSnapFraction() {
            return mSnapFraction;
        }

        void dump(PrintWriter pw, String prefix) {
            final String innerPrefix = prefix + "  ";
            pw.println(prefix + TAG);
            pw.println(innerPrefix + "mBoundsScale=" + mBoundsScale);
            pw.println(innerPrefix + "mSnapFraction=" + mSnapFraction);
        }
    }

    /** Dumps internal state. */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mBounds=" + mBounds);
        pw.println(innerPrefix + "mNormalBounds=" + mNormalBounds);
        pw.println(innerPrefix + "mExpandedBounds=" + mExpandedBounds);
        pw.println(innerPrefix + "mMovementBounds=" + mMovementBounds);
        pw.println(innerPrefix + "mNormalMovementBounds=" + mNormalMovementBounds);
        pw.println(innerPrefix + "mExpandedMovementBounds=" + mExpandedMovementBounds);
        pw.println(innerPrefix + "mLastPipComponentName=" + mLastPipComponentName);
        pw.println(innerPrefix + "mAspectRatio=" + mAspectRatio);
        pw.println(innerPrefix + "mStashedState=" + mStashedState);
        pw.println(innerPrefix + "mStashOffset=" + mStashOffset);
        pw.println(innerPrefix + "mIsImeShowing=" + mIsImeShowing);
        pw.println(innerPrefix + "mImeHeight=" + mImeHeight);
        pw.println(innerPrefix + "mIsShelfShowing=" + mIsShelfShowing);
        pw.println(innerPrefix + "mShelfHeight=" + mShelfHeight);
        pw.println(innerPrefix + "mHasUserMovedPip=" + mHasUserMovedPip);
        pw.println(innerPrefix + "mHasUserResizedPip=" + mHasUserResizedPip);
        pw.println(innerPrefix + "mMinSize=" + mMinSize);
        pw.println(innerPrefix + "mMaxSize=" + mMaxSize);
        pw.println(innerPrefix + "mBoundsScale" + mBoundsScale);
        if (mPipReentryState == null) {
            pw.println(innerPrefix + "mPipReentryState=null");
        } else {
            mPipReentryState.dump(pw, innerPrefix);
        }
        mLauncherState.dump(pw, innerPrefix);
        mMotionBoundsState.dump(pw, innerPrefix);
        mSizeSpecSource.dump(pw, innerPrefix);
    }
}
