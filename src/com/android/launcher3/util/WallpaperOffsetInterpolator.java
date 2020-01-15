package com.android.launcher3.util;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.Interpolator;

import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.Interpolators;

/**
 * Utility class to handle wallpaper scrolling along with workspace.
 */
public class WallpaperOffsetInterpolator extends BroadcastReceiver {

    private static final int[] sTempInt = new int[2];
    private static final String TAG = "WPOffsetInterpolator";
    private static final int ANIMATION_DURATION = 250;

    // Don't use all the wallpaper for parallax until you have at least this many pages
    private static final int MIN_PARALLAX_PAGE_SPAN = 4;

    private final Workspace mWorkspace;
    private final boolean mIsRtl;
    private final Handler mHandler;

    private boolean mRegistered = false;
    private IBinder mWindowToken;
    private boolean mWallpaperIsLiveWallpaper;

    private boolean mLockedToDefaultPage;
    private int mNumScreens;

    public WallpaperOffsetInterpolator(Workspace workspace) {
        mWorkspace = workspace;
        mIsRtl = Utilities.isRtl(workspace.getResources());
        mHandler = new OffsetHandler(workspace.getContext());
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

    /**
     * Computes the wallpaper offset as an int ratio (out[0] / out[1])
     *
     * TODO: do different behavior if it's  a live wallpaper?
     */
    private void wallpaperOffsetForScroll(int scroll, int numScrollingPages, final int[] out) {
        out[1] = 1;

        // To match the default wallpaper behavior in the system, we default to either the left
        // or right edge on initialization
        if (mLockedToDefaultPage || numScrollingPages <= 1) {
            out[0] =  mIsRtl ? 1 : 0;
            return;
        }

        // Distribute the wallpaper parallax over a minimum of MIN_PARALLAX_PAGE_SPAN workspace
        // screens, not including the custom screen, and empty screens (if > MIN_PARALLAX_PAGE_SPAN)
        int numPagesForWallpaperParallax = mWallpaperIsLiveWallpaper ? numScrollingPages :
                        Math.max(MIN_PARALLAX_PAGE_SPAN, numScrollingPages);

        // Offset by the custom screen
        int leftPageIndex;
        int rightPageIndex;
        if (mIsRtl) {
            rightPageIndex = 0;
            leftPageIndex = rightPageIndex + numScrollingPages - 1;
        } else {
            leftPageIndex = 0;
            rightPageIndex = leftPageIndex + numScrollingPages - 1;
        }

        // Calculate the scroll range
        int leftPageScrollX = mWorkspace.getScrollForPage(leftPageIndex);
        int rightPageScrollX = mWorkspace.getScrollForPage(rightPageIndex);
        int scrollRange = rightPageScrollX - leftPageScrollX;
        if (scrollRange <= 0) {
            out[0] = 0;
            return;
        }

        // Sometimes the left parameter of the pages is animated during a layout transition;
        // this parameter offsets it to keep the wallpaper from animating as well
        int adjustedScroll = scroll - leftPageScrollX -
                mWorkspace.getLayoutTransitionOffsetForPage(0);
        adjustedScroll = Utilities.boundToRange(adjustedScroll, 0, scrollRange);
        out[1] = (numPagesForWallpaperParallax - 1) * scrollRange;

        // The offset is now distributed 0..1 between the left and right pages that we care about,
        // so we just map that between the pages that we are using for parallax
        int rtlOffset = 0;
        if (mIsRtl) {
            // In RTL, the pages are right aligned, so adjust the offset from the end
            rtlOffset = out[1] - (numScrollingPages - 1) * scrollRange;
        }
        out[0] = rtlOffset + adjustedScroll * (numScrollingPages - 1);
    }

    public float wallpaperOffsetForScroll(int scroll) {
        wallpaperOffsetForScroll(scroll, getNumScreensExcludingEmpty(), sTempInt);
        return ((float) sTempInt[0]) / sTempInt[1];
    }

    private int getNumScreensExcludingEmpty() {
        int numScrollingPages = mWorkspace.getChildCount();
        if (numScrollingPages >= MIN_PARALLAX_PAGE_SPAN && mWorkspace.hasExtraEmptyScreen()) {
            return numScrollingPages - 1;
        } else {
            return numScrollingPages;
        }
    }

    public void syncWithScroll() {
        int numScreens = getNumScreensExcludingEmpty();
        wallpaperOffsetForScroll(mWorkspace.getScrollX(), numScreens, sTempInt);
        Message msg = Message.obtain(mHandler, MSG_UPDATE_OFFSET, sTempInt[0], sTempInt[1],
                mWindowToken);
        if (numScreens != mNumScreens) {
            if (mNumScreens > 0) {
                // Don't animate if we're going from 0 screens
                msg.what = MSG_START_ANIMATION;
            }
            mNumScreens = numScreens;
            updateOffset();
        }
        msg.sendToTarget();
    }

    private void updateOffset() {
        int numPagesForWallpaperParallax;
        if (mWallpaperIsLiveWallpaper) {
            numPagesForWallpaperParallax = mNumScreens;
        } else {
            numPagesForWallpaperParallax = Math.max(MIN_PARALLAX_PAGE_SPAN, mNumScreens);
        }
        Message.obtain(mHandler, MSG_SET_NUM_PARALLAX, numPagesForWallpaperParallax, 0,
                mWindowToken).sendToTarget();
    }

    public void jumpToFinal() {
        Message.obtain(mHandler, MSG_JUMP_TO_FINAL, mWindowToken).sendToTarget();
    }

    public void setWindowToken(IBinder token) {
        mWindowToken = token;
        if (mWindowToken == null && mRegistered) {
            mWorkspace.getContext().unregisterReceiver(this);
            mRegistered = false;
        } else if (mWindowToken != null && !mRegistered) {
            mWorkspace.getContext()
                    .registerReceiver(this, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
            onReceive(mWorkspace.getContext(), null);
            mRegistered = true;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mWallpaperIsLiveWallpaper =
                WallpaperManager.getInstance(mWorkspace.getContext()).getWallpaperInfo() != null;
        updateOffset();
    }

    private static final int MSG_START_ANIMATION = 1;
    private static final int MSG_UPDATE_OFFSET = 2;
    private static final int MSG_APPLY_OFFSET = 3;
    private static final int MSG_SET_NUM_PARALLAX = 4;
    private static final int MSG_JUMP_TO_FINAL = 5;

    private static class OffsetHandler extends Handler {

        private final Interpolator mInterpolator;
        private final WallpaperManager mWM;

        private float mCurrentOffset = 0.5f; // to force an initial update
        private boolean mAnimating;
        private long mAnimationStartTime;
        private float mAnimationStartOffset;

        private float mFinalOffset;
        private float mOffsetX;

        public OffsetHandler(Context context) {
            super(UI_HELPER_EXECUTOR.getLooper());
            mInterpolator = Interpolators.DEACCEL_1_5;
            mWM = WallpaperManager.getInstance(context);
        }

        @Override
        public void handleMessage(Message msg) {
            final IBinder token = (IBinder) msg.obj;
            if (token == null) {
                return;
            }

            switch (msg.what) {
                case MSG_START_ANIMATION: {
                    mAnimating = true;
                    mAnimationStartOffset = mCurrentOffset;
                    mAnimationStartTime = msg.getWhen();
                    // Follow through
                }
                case MSG_UPDATE_OFFSET:
                    mFinalOffset = ((float) msg.arg1) / msg.arg2;
                    // Follow through
                case MSG_APPLY_OFFSET: {
                    float oldOffset = mCurrentOffset;
                    if (mAnimating) {
                        long durationSinceAnimation = SystemClock.uptimeMillis()
                                - mAnimationStartTime;
                        float t0 = durationSinceAnimation / (float) ANIMATION_DURATION;
                        float t1 = mInterpolator.getInterpolation(t0);
                        mCurrentOffset = mAnimationStartOffset +
                                (mFinalOffset - mAnimationStartOffset) * t1;
                        mAnimating = durationSinceAnimation < ANIMATION_DURATION;
                    } else {
                        mCurrentOffset = mFinalOffset;
                    }

                    if (Float.compare(mCurrentOffset, oldOffset) != 0) {
                        setOffsetSafely(token);
                        // Force the wallpaper offset steps to be set again, because another app
                        // might have changed them
                        mWM.setWallpaperOffsetSteps(mOffsetX, 1.0f);
                    }
                    if (mAnimating) {
                        // If we are animating, keep updating the offset
                        Message.obtain(this, MSG_APPLY_OFFSET, token).sendToTarget();
                    }
                    return;
                }
                case MSG_SET_NUM_PARALLAX: {
                    // Set wallpaper offset steps (1 / (number of screens - 1))
                    mOffsetX = 1.0f / (msg.arg1 - 1);
                    mWM.setWallpaperOffsetSteps(mOffsetX, 1.0f);
                    return;
                }
                case MSG_JUMP_TO_FINAL: {
                    if (Float.compare(mCurrentOffset, mFinalOffset) != 0) {
                        mCurrentOffset = mFinalOffset;
                        setOffsetSafely(token);
                    }
                    mAnimating = false;
                    return;
                }
            }
        }

        private void setOffsetSafely(IBinder token) {
            try {
                mWM.setWallpaperOffsets(token, mCurrentOffset, 0.5f);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error updating wallpaper offset: " + e);
            }
        }
    }
}