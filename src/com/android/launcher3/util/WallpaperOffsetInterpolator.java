package com.android.launcher3.util;

import static android.content.Intent.ACTION_WALLPAPER_CHANGED;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.Interpolator;

import androidx.annotation.AnyThread;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;

/**
 * Utility class to handle wallpaper scrolling along with workspace.
 */
public class WallpaperOffsetInterpolator {

    private static final int[] sTempInt = new int[2];
    private static final String TAG = "WPOffsetInterpolator";
    private static final int ANIMATION_DURATION = 250;

    // Don't use all the wallpaper for parallax until you have at least this many pages
    private static final int MIN_PARALLAX_PAGE_SPAN = 4;

    private final SimpleBroadcastReceiver mWallpaperChangeReceiver =
            new SimpleBroadcastReceiver(i -> onWallpaperChanged());
    private final Workspace<?> mWorkspace;
    private final boolean mIsRtl;
    private final Handler mHandler;

    private boolean mRegistered = false;
    private IBinder mWindowToken;
    private boolean mWallpaperIsLiveWallpaper;

    private boolean mLockedToDefaultPage;
    private int mNumScreens;

    public WallpaperOffsetInterpolator(Workspace<?> workspace) {
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
    private void wallpaperOffsetForScroll(int scroll, int numScrollableScreens, final int[] out) {
        out[1] = 1;

        // To match the default wallpaper behavior in the system, we default to either the left
        // or right edge on initialization
        if (mLockedToDefaultPage || numScrollableScreens <= 1) {
            out[0] =  mIsRtl ? 1 : 0;
            return;
        }

        // Distribute the wallpaper parallax over a minimum of MIN_PARALLAX_PAGE_SPAN workspace
        // screens, not including the custom screen, and empty screens (if > MIN_PARALLAX_PAGE_SPAN)
        int numScreensForWallpaperParallax = mWallpaperIsLiveWallpaper ? numScrollableScreens :
                        Math.max(MIN_PARALLAX_PAGE_SPAN, numScrollableScreens);

        // Offset by the custom screen

        // Don't confuse screens & pages in this function. In a phone UI, we often use screens &
        // pages interchangeably. However, in a n-panels UI, where n > 1, the screen in this class
        // means the scrollable screen. Each screen can consist of at most n panels.
        // Each panel has at most 1 page. Take 5 pages in 2 panels UI as an example, the Workspace
        // looks as follow:
        //
        // S: scrollable screen, P: page, <E>: empty
        //   S0        S1         S2
        // _______   _______   ________
        // |P0|P1|   |P2|P3|   |P4|<E>|
        // ¯¯¯¯¯¯¯   ¯¯¯¯¯¯¯   ¯¯¯¯¯¯¯¯
        int endIndex = getNumPagesExcludingEmpty() - 1;
        final int leftPageIndex = mIsRtl ? endIndex : 0;
        final int rightPageIndex = mIsRtl ? 0 : endIndex;

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
        out[1] = (numScreensForWallpaperParallax - 1) * scrollRange;

        // The offset is now distributed 0..1 between the left and right pages that we care about,
        // so we just map that between the pages that we are using for parallax
        int rtlOffset = 0;
        if (mIsRtl) {
            // In RTL, the pages are right aligned, so adjust the offset from the end
            rtlOffset = out[1] - (numScrollableScreens - 1) * scrollRange;
        }
        out[0] = rtlOffset + adjustedScroll * (numScrollableScreens - 1);
    }

    public float wallpaperOffsetForScroll(int scroll) {
        wallpaperOffsetForScroll(scroll, getNumScrollableScreensExcludingEmpty(), sTempInt);
        return ((float) sTempInt[0]) / sTempInt[1];
    }

    /**
     * Returns the number of screens that can be scrolled.
     *
     * <p>In an usual phone UI, the number of scrollable screens is equal to the number of
     * CellLayouts because each screen has exactly 1 CellLayout.
     *
     * <p>In a n-panels UI, a screen shows n panels. Each panel has at most 1 CellLayout. Take
     * 2-panels UI as an example: let's say there are 5 CellLayouts in the Workspace. the number of
     * scrollable screens will be 3 = ⌈5 / 2⌉.
     */
    private int getNumScrollableScreensExcludingEmpty() {
        float numOfPages = getNumPagesExcludingEmpty();
        return (int) Math.ceil(numOfPages / mWorkspace.getPanelCount());
    }

    /**
     * Returns the number of non-empty pages in the Workspace.
     *
     * <p>If a user starts dragging on the rightmost (or leftmost in RTL), an empty CellLayout is
     * added to the Workspace. This empty CellLayout add as a hover-over target for adding a new
     * page. To avoid janky motion effect, we ignore this empty CellLayout.
     */
    private int getNumPagesExcludingEmpty() {
        int numOfPages = mWorkspace.getChildCount();
        if (numOfPages >= MIN_PARALLAX_PAGE_SPAN && mWorkspace.hasExtraEmptyScreens()) {
            return numOfPages - mWorkspace.getPanelCount();
        } else {
            return numOfPages;
        }
    }

    public void syncWithScroll() {
        int numScreens = getNumScrollableScreensExcludingEmpty();
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

    /** Returns the number of pages used for the wallpaper parallax. */
    public int getNumPagesForWallpaperParallax() {
        if (mWallpaperIsLiveWallpaper) {
            return mNumScreens;
        } else {
            return Math.max(MIN_PARALLAX_PAGE_SPAN, mNumScreens);
        }
    }

    @AnyThread
    private void updateOffset() {
        Message.obtain(mHandler, MSG_SET_NUM_PARALLAX, getNumPagesForWallpaperParallax(), 0,
                mWindowToken).sendToTarget();
    }

    public void jumpToFinal() {
        Message.obtain(mHandler, MSG_JUMP_TO_FINAL, mWindowToken).sendToTarget();
    }

    public void setWindowToken(IBinder token) {
        mWindowToken = token;
        if (mWindowToken == null && mRegistered) {
            mWallpaperChangeReceiver.unregisterReceiverSafely(mWorkspace.getContext());
            mRegistered = false;
        } else if (mWindowToken != null && !mRegistered) {
            mWallpaperChangeReceiver.register(mWorkspace.getContext(), ACTION_WALLPAPER_CHANGED);
            onWallpaperChanged();
            mRegistered = true;
        }
    }

    private void onWallpaperChanged() {
        UI_HELPER_EXECUTOR.execute(() -> {
            // Updating the boolean on a background thread is fine as the assignments are atomic
            mWallpaperIsLiveWallpaper = WallpaperManager.getInstance(mWorkspace.getContext())
                    .getWallpaperInfo() != null;
            updateOffset();
        });
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
            mInterpolator = Interpolators.DECELERATE_1_5;
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