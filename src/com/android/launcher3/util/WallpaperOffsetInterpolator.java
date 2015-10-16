package com.android.launcher3.util;

import android.app.WallpaperManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Choreographer;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;

/**
 * Utility class to handle wallpaper scrolling along with workspace.
 */
public class WallpaperOffsetInterpolator implements Choreographer.FrameCallback {
    private static final String TAG = "WPOffsetInterpolator";
    private static final int ANIMATION_DURATION = 250;

    // Don't use all the wallpaper for parallax until you have at least this many pages
    private static final int MIN_PARALLAX_PAGE_SPAN = 3;

    private final Choreographer mChoreographer;
    private final Interpolator mInterpolator;
    private final WallpaperManager mWallpaperManager;
    private final Workspace mWorkspace;
    private final boolean mIsRtl;

    private IBinder mWindowToken;
    private boolean mWallpaperIsLiveWallpaper;
    private float mLastSetWallpaperOffsetSteps = 0;

    private float mFinalOffset = 0.0f;
    private float mCurrentOffset = 0.5f; // to force an initial update
    private boolean mWaitingForUpdate;

    private boolean mAnimating;
    private long mAnimationStartTime;
    private float mAnimationStartOffset;
    int mNumScreens;
    int mNumPagesForWallpaperParallax;

    public WallpaperOffsetInterpolator(Workspace workspace) {
        mChoreographer = Choreographer.getInstance();
        mInterpolator = new DecelerateInterpolator(1.5f);

        mWorkspace = workspace;
        mWallpaperManager = WallpaperManager.getInstance(workspace.getContext());
        mIsRtl = Utilities.isRtl(workspace.getResources());
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        updateOffset(false);
    }

    private void updateOffset(boolean force) {
        if (mWaitingForUpdate || force) {
            mWaitingForUpdate = false;
            if (computeScrollOffset() && mWindowToken != null) {
                try {
                    mWallpaperManager.setWallpaperOffsets(mWindowToken, getCurrX(), 0.5f);
                    setWallpaperOffsetSteps();
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Error updating wallpaper offset: " + e);
                }
            }
        }
    }

    public boolean computeScrollOffset() {
        final float oldOffset = mCurrentOffset;
        if (mAnimating) {
            long durationSinceAnimation = System.currentTimeMillis() - mAnimationStartTime;
            float t0 = durationSinceAnimation / (float) ANIMATION_DURATION;
            float t1 = mInterpolator.getInterpolation(t0);
            mCurrentOffset = mAnimationStartOffset +
                    (mFinalOffset - mAnimationStartOffset) * t1;
            mAnimating = durationSinceAnimation < ANIMATION_DURATION;
        } else {
            mCurrentOffset = mFinalOffset;
        }

        if (Math.abs(mCurrentOffset - mFinalOffset) > 0.0000001f) {
            scheduleUpdate();
        }
        if (Math.abs(oldOffset - mCurrentOffset) > 0.0000001f) {
            return true;
        }
        return false;
    }

    public float wallpaperOffsetForScroll(int scroll) {
        // TODO: do different behavior if it's  a live wallpaper?
        // Don't use up all the wallpaper parallax until you have at least
        // MIN_PARALLAX_PAGE_SPAN pages
        int numScrollingPages = getNumScreensExcludingEmptyAndCustom();
        int parallaxPageSpan;
        if (mWallpaperIsLiveWallpaper) {
            parallaxPageSpan = numScrollingPages - 1;
        } else {
            parallaxPageSpan = Math.max(MIN_PARALLAX_PAGE_SPAN, numScrollingPages - 1);
        }
        mNumPagesForWallpaperParallax = parallaxPageSpan;

        if (mWorkspace.getChildCount() <= 1) {
            if (mIsRtl) {
                return 1 - 1.0f/mNumPagesForWallpaperParallax;
            }
            return 0;
        }

        // Exclude the leftmost page
        int emptyExtraPages = numEmptyScreensToIgnore();
        int firstIndex = mWorkspace.numCustomPages();
        // Exclude the last extra empty screen (if we have > MIN_PARALLAX_PAGE_SPAN pages)
        int lastIndex = mWorkspace.getChildCount() - 1 - emptyExtraPages;
        if (mIsRtl) {
            int temp = firstIndex;
            firstIndex = lastIndex;
            lastIndex = temp;
        }

        int firstPageScrollX = mWorkspace.getScrollForPage(firstIndex);
        int scrollRange = mWorkspace.getScrollForPage(lastIndex) - firstPageScrollX;
        if (scrollRange == 0) {
            return 0;
        } else {
            // Sometimes the left parameter of the pages is animated during a layout transition;
            // this parameter offsets it to keep the wallpaper from animating as well
            int adjustedScroll =
                    scroll - firstPageScrollX - mWorkspace.getLayoutTransitionOffsetForPage(0);
            float offset = Math.min(1, adjustedScroll / (float) scrollRange);
            offset = Math.max(0, offset);

            // On RTL devices, push the wallpaper offset to the right if we don't have enough
            // pages (ie if numScrollingPages < MIN_PARALLAX_PAGE_SPAN)
            if (!mWallpaperIsLiveWallpaper && numScrollingPages < MIN_PARALLAX_PAGE_SPAN
                    && mIsRtl) {
                return offset * (parallaxPageSpan - numScrollingPages + 1) / parallaxPageSpan;
            }
            return offset * (numScrollingPages - 1) / parallaxPageSpan;
        }
    }

    private float wallpaperOffsetForCurrentScroll() {
        return wallpaperOffsetForScroll(mWorkspace.getScrollX());
    }

    private int numEmptyScreensToIgnore() {
        int numScrollingPages = mWorkspace.getChildCount() - mWorkspace.numCustomPages();
        if (numScrollingPages >= MIN_PARALLAX_PAGE_SPAN && mWorkspace.hasExtraEmptyScreen()) {
            return 1;
        } else {
            return 0;
        }
    }

    private int getNumScreensExcludingEmptyAndCustom() {
        return mWorkspace.getChildCount() - numEmptyScreensToIgnore() - mWorkspace.numCustomPages();
    }

    public void syncWithScroll() {
        float offset = wallpaperOffsetForCurrentScroll();
        setFinalX(offset);
        updateOffset(true);
    }

    public float getCurrX() {
        return mCurrentOffset;
    }

    public float getFinalX() {
        return mFinalOffset;
    }

    private void animateToFinal() {
        mAnimating = true;
        mAnimationStartOffset = mCurrentOffset;
        mAnimationStartTime = System.currentTimeMillis();
    }

    private void setWallpaperOffsetSteps() {
        // Set wallpaper offset steps (1 / (number of screens - 1))
        float xOffset = 1.0f / mNumPagesForWallpaperParallax;
        if (xOffset != mLastSetWallpaperOffsetSteps) {
            mWallpaperManager.setWallpaperOffsetSteps(xOffset, 1.0f);
            mLastSetWallpaperOffsetSteps = xOffset;
        }
    }

    public void setFinalX(float x) {
        scheduleUpdate();
        mFinalOffset = Math.max(0f, Math.min(x, 1.0f));
        if (getNumScreensExcludingEmptyAndCustom() != mNumScreens) {
            if (mNumScreens > 0) {
                // Don't animate if we're going from 0 screens
                animateToFinal();
            }
            mNumScreens = getNumScreensExcludingEmptyAndCustom();
        }
    }

    private void scheduleUpdate() {
        if (!mWaitingForUpdate) {
            mChoreographer.postFrameCallback(this);
            mWaitingForUpdate = true;
        }
    }

    public void jumpToFinal() {
        mCurrentOffset = mFinalOffset;
    }

    public void onResume() {
        mWallpaperIsLiveWallpaper = mWallpaperManager.getWallpaperInfo() != null;
        // Force the wallpaper offset steps to be set again, because another app might have changed
        // them
        mLastSetWallpaperOffsetSteps = 0f;
    }

    public void setWindowToken(IBinder token) {
        mWindowToken = token;
    }
}