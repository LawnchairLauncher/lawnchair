package com.google.android.apps.nexuslauncher.qsb;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;

public class HotseatQsbWidget extends AbstractQsbLayout {
    private boolean mIsDefaultLiveWallpaper;
    private boolean mGoogleHasFocus;
    private AnimatorSet mAnimatorSet;
    private boolean mSearchRequested;
    private final BroadcastReceiver mBroadcastReceiver;

    public HotseatQsbWidget(Context context) {
        this(context, null);
    }

    public HotseatQsbWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HotseatQsbWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setGoogleColored();
            }
        };
        mIsDefaultLiveWallpaper = isDefaultLiveWallpaper();
        setColors();
        setOnClickListener(this);
    }

    static int getBottomMargin(Launcher launcher) {
        Rect insets = launcher.getDragLayer().getInsets();
        Resources res = launcher.getResources();
        return res.getDimensionPixelSize(R.dimen.qsb_hotseat_bottom_margin) + (insets.bottom == 0 ?
                res.getDimensionPixelSize(R.dimen.qsb_hotseat_bottom_margin_hw) :
                insets.bottom);
    }

    private void setColors() {
        View.inflate(new ContextThemeWrapper(getContext(), mIsDefaultLiveWallpaper ? R.style.HotseatQsbTheme_Colored : R.style.HotseatQsbTheme), R.layout.qsb_hotseat_content, this);
        bz(mIsDefaultLiveWallpaper ? 0xCCFFFFFF : 0x99FAFAFA);
    }

    private void openQSB() {
        mSearchRequested = false;
        playAnimation(mGoogleHasFocus = true, true);
    }

    private void closeQSB(boolean longDuration) {
        mSearchRequested = false;
        if (mGoogleHasFocus) {
            playAnimation(mGoogleHasFocus = false, longDuration);
        }
    }

    private Intent getSearchIntent() {
        int[] array = new int[2];
        getLocationInWindow(array);
        Rect rect = new Rect(0, 0, getWidth(), getHeight());
        rect.offset(array[0], array[1]);
        rect.inset(getPaddingLeft(), getPaddingTop());
        return ConfigBuilder.getSearchIntent(rect, findViewById(R.id.g_icon), mMicIconView);
    }

    private void setGoogleColored() {
        if (mIsDefaultLiveWallpaper != isDefaultLiveWallpaper()) {
            mIsDefaultLiveWallpaper ^= true;
            removeAllViews();
            setColors();
            loadAndGetPreferences();
        }
    }

    private void playQsbAnimation() {
        if (hasWindowFocus()) {
            mSearchRequested = true;
        } else {
            openQSB();
        }
    }

    private boolean isDefaultLiveWallpaper() {
        WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(getContext()).getWallpaperInfo();
        return wallpaperInfo != null && wallpaperInfo.getComponent().flattenToString().equals(getContext().getString(R.string.default_live_wallpaper));
    }

    private void doOnClick() {
        final ConfigBuilder f = new ConfigBuilder(this, false);
        if (mActivity.getGoogleNow().startSearch(f.build(), f.getExtras())) {
            SharedPreferences devicePrefs = Utilities.getDevicePrefs(getContext());
            devicePrefs.edit().putInt("key_hotseat_qsb_tap_count", devicePrefs.getInt("key_hotseat_qsb_tap_count", 0) + 1).apply();
            playQsbAnimation();
        } else {
            getContext().sendOrderedBroadcast(getSearchIntent(), null,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.e("HotseatQsbSearch", getResultCode() + " " + getResultData());
                            if (getResultCode() == 0) {
                                fallbackSearch("com.google.android.googlequicksearchbox.TEXT_ASSIST");
                            } else {
                                playQsbAnimation();
                            }
                        }
                    }, null, 0, null, null);
        }
    }

    private void playAnimation(boolean hideWorkspace, boolean longDuration) {
        if (mAnimatorSet != null) {
            mAnimatorSet.cancel();
        }
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                if (animation == mAnimatorSet) {
                    mAnimatorSet = null;
                }
            }
        });

        DragLayer dragLayer = mActivity.getDragLayer();
        float[] alphaValues = new float[1];
        float[] translationValues = new float[1];
        if (hideWorkspace) {
            alphaValues[0] = 0.0f;
            mAnimatorSet.play(ObjectAnimator.ofFloat(dragLayer, View.ALPHA, alphaValues));

            translationValues[0] = -mActivity.getHotseat().getHeight() / 2;
            ObjectAnimator ofFloat = ObjectAnimator.ofFloat(dragLayer, View.TRANSLATION_Y, translationValues);
            ofFloat.setInterpolator(new AccelerateInterpolator());
            mAnimatorSet.play(ofFloat);
        } else {
            alphaValues[0] = 1.0f;
            mAnimatorSet.play(ObjectAnimator.ofFloat(dragLayer, View.ALPHA, alphaValues));

            translationValues[0] = 0.0f;
            ObjectAnimator ofFloat = ObjectAnimator.ofFloat(dragLayer, View.TRANSLATION_Y, translationValues);
            ofFloat.setInterpolator(new DecelerateInterpolator());
            mAnimatorSet.play(ofFloat);
        }
        mAnimatorSet.setDuration(200L);
        mAnimatorSet.start();
        if (!longDuration) {
            mAnimatorSet.end();
        }
    }

    protected int getWidth(final int n) {
        CellLayout layout = mActivity.getHotseat().getLayout();
        return n - layout.getPaddingLeft() - layout.getPaddingRight();
    }

    protected void loadBottomMargin() {
        ((MarginLayoutParams) getLayoutParams()).bottomMargin = getBottomMargin(mActivity);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().registerReceiver(mBroadcastReceiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
    }

    public void onClick(View view) {
        super.onClick(view);
        if (view == this) {
            doOnClick();
        }
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mBroadcastReceiver);
    }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus && mSearchRequested) {
            openQSB();
        } else if (hasWindowFocus) {
            closeQSB(true);
        }
    }

    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        closeQSB(false);
    }
}
