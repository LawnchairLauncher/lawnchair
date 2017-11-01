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
    private static final int MIN_PARALLAX_PAGE_SPAN = 4;

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
    private boolean mLockedToDefaultPage;

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

    /**
     * Locks the wallpaper offset to the offset in the default state of Launcher.
     */
    public void setLockToDefaultPage(boolean lockToDefaultPage) {
        mLockedToDefaultPage = lockToDefaultPage;
    }

    public boolean isLockedToDefaultPage() {
        return mLockedToDefaultPage;
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

    /**
     * TODO: do different behavior if it's  a live wallpaper?
     */
    public float wallpaperOffsetForScroll(int scroll) {
        // To match the default wallpaper behavior in the system, we default to either the left
        // or right edge on initialization
        int numScrollingPages = getNumScreensExcludingEmptyAndCustom();
        if (mLockedToDefaultPage || numScrollingPages <= 1) {
            return mIsRtl ? 1f : 0f;
        }

        // Distribute the wallpaper parallax over a minimum of MIN_PARALLAX_PAGE_SPAN workspace
        // screens, not including the custom screen, and empty screens (if > MIN_PARALLAX_PAGE_SPAN)
        if (mWallpaperIsLiveWallpaper) {
            mNumPagesForWallpaperParallax = numScrollingPages;
        } else {
            mNumPagesForWallpaperParallax = Math.max(MIN_PARALLAX_PAGE_SPAN, numScrollingPages);
        }

        // Offset by the custom screen
        int leftPageIndex;
        int rightPageIndex;
        if (mIsRtl) {
            rightPageIndex = mWorkspace.numCustomPages();
            leftPageIndex = rightPageIndex + numScrollingPages - 1;
        } else {
            leftPageIndex = mWorkspace.numCustomPages();
            rightPageIndex = leftPageIndex + numScrollingPages - 1;
        }

        // Calculate the scroll range
        int leftPageScrollX = mWorkspace.getScrollForPage(leftPageIndex);
        int rightPageScrollX = mWorkspace.getScrollForPage(rightPageIndex);
        int scrollRange = rightPageScrollX - leftPageScrollX;
        if (scrollRange == 0) {
            return 0f;
        }

        // Sometimes the left parameter of the pages is animated during a layout transition;
        // this parameter offsets it to keep the wallpaper from animating as well
        int adjustedScroll = scroll - leftPageScrollX -
                mWorkspace.getLayoutTransitionOffsetForPage(0);
        float offset = Utilities.boundToRange((float) adjustedScroll / scrollRange, 0f, 1f);

        // The offset is now distributed 0..1 between the left and right pages that we care about,
        // so we just map that between the pages that we are using for parallax
        float rtlOffset = 0;
        if (mIsRtl) {
            // In RTL, the pages are right aligned, so adjust the offset from the end
            rtlOffset = (float) ((mNumPagesForWallpaperParallax - 1) - (numScrollingPages - 1)) /
                    (mNumPagesForWallpaperParallax - 1);
        }
        return rtlOffset + offset *
                ((float) (numScrollingPages - 1) / (mNumPagesForWallpaperParallax - 1));
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
        float xOffset = 1.0f / (mNumPagesForWallpaperParallax - 1);
        if (xOffset != mLastSetWallpaperOffsetSteps) {
            mWallpaperManager.setWallpaperOffsetSteps(xOffset, 1.0f);
            mLastSetWallpaperOffsetSteps = xOffset;
        }
    }

    public void setFinalX(float x) {
        scheduleUpdate();
        mFinalOffset = Math.max(0f, Math.min(x, 1f));
        if (getNumScreensExcludingEmptyAndCustom() != mNumScreens) {
            if (mNumScreens > 0 && Float.compare(mCurrentOffset, mFinalOffset) != 0) {
                // Don't animate if we're going from 0 screens, or if the final offset is the same
                // as the current offset
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