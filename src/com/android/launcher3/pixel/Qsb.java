package com.android.launcher3.pixel;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.PackageManagerHelper;


public abstract class Qsb extends FrameLayout implements View.OnClickListener {
    private static final String TEXT_ASSIST = "com.google.android.googlequicksearchbox.TEXT_ASSIST"; //bB
    private final ArgbEvaluator mArgbEvaluator = new ArgbEvaluator(); //mArgbEvaluator
    private ObjectAnimator mObjectAnimator; //getInstanceUI
    protected View mQsbView; //bF
    private float qsbButtonElevation; //bG
    protected QsbConnector qsbConnector; //bH
    private final Interpolator mADInterpolator = new AccelerateDecelerateInterpolator(); //bI
    private ObjectAnimator elevationAnimator; //bJ
    protected boolean qsbHidden; //bM
    private int mQsbViewId = 0; //bN
    protected final Launcher mLauncher; //getView
    private boolean windowHasFocus; //bP

    protected abstract int getQsbView(boolean withMic);

    public Qsb(Context paramContext, AttributeSet paramAttributeSet, int paramInt)
    {
        super(paramContext, paramAttributeSet, paramInt);
        mLauncher = Launcher.getLauncher(paramContext);
    }

    public void applyOpaPreference() { //bm
        int qsbView = getQsbView(false);
        if (qsbView != mQsbViewId) {
            mQsbViewId = qsbView;
            if (mQsbView != null) {
                removeView(mQsbView);
            }
            mQsbView = LayoutInflater.from(getContext()).inflate(mQsbViewId, this, false);
            qsbButtonElevation = (float) getResources().getDimensionPixelSize(R.dimen.qsb_button_elevation);
            addView(mQsbView);
            mObjectAnimator = ObjectAnimator.ofFloat(mQsbView, "elevation", 0.0f, qsbButtonElevation).setDuration(200L);
            mObjectAnimator.setInterpolator(mADInterpolator);
            if (qsbHidden) {
                hideQsbImmediately();
            }
            mQsbView.setOnClickListener(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyOpaPreference();
        applyMinusOnePreference();
        applyVisibility();
    }

    private void applyMinusOnePreference() { //bh
        if (qsbConnector != null) {
            removeView(qsbConnector);
            qsbConnector = null;
        }

        if (!mLauncher.useVerticalBarLayout()) {
            addView(qsbConnector = (QsbConnector) mLauncher.getLayoutInflater().inflate(R.layout.qsb_connector, this, false), 0);
            /*final int color = getResources().getColor(R.color.qsb_connector);
            final int color2 = getResources().getColor(R.color.qsb_background);
            final ColorDrawable background = new ColorDrawable(color);
            qsbConnector.setBackground(background);
            (elevationAnimator = ObjectAnimator.ofObject(background, "color", mArgbEvaluator, color2, color)).setDuration(200L);
            elevationAnimator.setInterpolator(mADInterpolator);*/
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void onClick(View paramView)
    {
        getContext().sendOrderedBroadcast(bm("com.google.nexuslauncher.FAST_TEXT_SEARCH"), null, new FastTextSearchReceiver(this), null, 0, null, null);
    }

    private Intent bm(String str) {
        int[] iArr = new int[2];
        mQsbView.getLocationOnScreen(iArr);
        Rect rect = new Rect(iArr[0], iArr[1], iArr[0] + mQsbView.getWidth(), iArr[1] + mQsbView.getHeight());
        Intent intent = new Intent(str);
        setGoogleAnimationStart(rect, intent);
        intent.setSourceBounds(rect);
        return intent.putExtra("source_round_left", true).putExtra("source_round_right", true).putExtra("source_logo_offset", MidLocation(findViewById(R.id.g_icon), rect)).setPackage("com.google.android.googlequicksearchbox").addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private Point MidLocation(View view, Rect rect) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        Point point = new Point();
        point.x = (location[0] - rect.left) + (view.getWidth() / 2);
        point.y = (location[1] - rect.top) + (view.getHeight() / 2);
        return point;
    }

    protected void setGoogleAnimationStart(Rect rect, Intent intent) {
    }

    private void loadWindowFocus() { //bj
        if (hasWindowFocus()) {
            windowHasFocus = true;
        } else {
            hideQsbImmediately();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean newWindowHasFocus) {
        super.onWindowFocusChanged(newWindowHasFocus);
        if (!newWindowHasFocus && windowHasFocus) {
            hideQsbImmediately();
        } else if (newWindowHasFocus && !windowHasFocus) {
            changeVisibility(true);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int paramInt)
    {
        super.onWindowVisibilityChanged(paramInt);
        changeVisibility(false);
    }

    private void hideQsbImmediately() { //bb
        windowHasFocus = false;
        qsbHidden = true;
        if (mQsbView != null) {
            mQsbView.setAlpha(0.0f);
            if (elevationAnimator != null && elevationAnimator.isRunning()) {
                elevationAnimator.end();
            }
        }
        if (qsbConnector != null) {
            if (mObjectAnimator != null && mObjectAnimator.isRunning()) {
                mObjectAnimator.end();
            }
            qsbConnector.setAlpha(0.0f);
        }
    }

    private void changeVisibility(boolean makeVisible) { //bc
        windowHasFocus = false;
        if (qsbHidden) {
            qsbHidden = false;
            if (mQsbView != null) {
                mQsbView.setAlpha(1.0f);
                if (elevationAnimator != null) {
                    elevationAnimator.start();
                    if (!makeVisible) {
                        elevationAnimator.end();
                    }
                }
            }
            if (qsbConnector != null) {
                qsbConnector.setAlpha(1.0f);
                qsbConnector.changeVisibility(makeVisible);
            }
        }
    }

    private void startQsbActivity(String str) { //bl
        try {
            getContext().startActivity(new Intent(str).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK).setPackage("com.google.android.googlequicksearchbox"));
        } catch (ActivityNotFoundException e) {
            Log.e("SuperQsb", "ActivityNotFound");
        }
    }

    private void applyVisibility() { //bg
        int visibility = PackageManagerHelper.isAppEnabled(getContext().getPackageManager(), "com.google.android.googlequicksearchbox") ? View.VISIBLE : View.GONE;

        if (mQsbView != null) {
            mQsbView.setVisibility(visibility);
        }
        if (qsbConnector != null) {
            qsbConnector.setVisibility(visibility);
        }
    }

    final class FastTextSearchReceiver extends BroadcastReceiver {
        final Qsb cq;

        FastTextSearchReceiver(Qsb qsbView) {
            cq = qsbView;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() == 0) {
                //Animation not allowed, start the normal search bar
                cq.startQsbActivity(Qsb.TEXT_ASSIST);
            } else {
                cq.loadWindowFocus();
            }
        }
    }
}
