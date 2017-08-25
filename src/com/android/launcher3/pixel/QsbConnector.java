package com.android.launcher3.pixel;

import android.content.Intent;
import android.graphics.Canvas;

import com.android.launcher3.R;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.AttributeSet;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.animation.ObjectAnimator;
import android.util.Property;
import android.view.View;

public class QsbConnector extends View
{
    private static final Property alphaProperty;
    private int alpha;
    private ObjectAnimator revealAnimator;
    private final int qsbBackgroundColor;
    private final BroadcastReceiver packageChangeReceiver;

    static {
        alphaProperty = new AlphaPropertyOverride(Integer.class, "overlayAlpha");
    }

    public QsbConnector(final Context context) {
        this(context, null);
    }

    public QsbConnector(final Context context, final AttributeSet set) {
        this(context, set, 0);
    }

    public QsbConnector(final Context context, final AttributeSet set, final int n) {
        super(context, set, n);
        this.packageChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                retrieveGoogleQsbBackground();
            }
        };

        this.alpha = 0;
        this.qsbBackgroundColor = (this.getResources().getColor(R.color.qsb_background) & 0xFFFFFF);
    }

    private void stopRevealAnimation() {
        if (this.revealAnimator != null) {
            this.revealAnimator.end();
            this.revealAnimator = null;
        }
    }

    private void retrieveGoogleQsbBackground() {
        this.setBackground(getResources().getDrawable(R.drawable.bg_pixel_qsb_connector, getContext().getTheme()));
    }

    private void updateAlpha(final int m) {
        if (this.alpha != m) {
            this.alpha = m;
            this.invalidate();
        }
    }

    public void changeVisibility(final boolean makeVisible) {
        if (makeVisible) {
            this.stopRevealAnimation();
            this.updateAlpha(255);
            (this.revealAnimator = ObjectAnimator.ofInt(this, QsbConnector.alphaProperty, new int[] { 0 })).setInterpolator(new AccelerateDecelerateInterpolator());
            this.revealAnimator.start();
        }
        else {
            this.updateAlpha(0);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.retrieveGoogleQsbBackground();
        this.getContext().registerReceiver(this.packageChangeReceiver, Util.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED"));
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.getContext().unregisterReceiver(this.packageChangeReceiver);
    }

    protected void onDraw(final Canvas canvas) {
        if (this.alpha > 0) {
            canvas.drawColor(android.support.v4.graphics.ColorUtils.setAlphaComponent(this.qsbBackgroundColor, this.alpha));
        }
    }

    protected boolean onSetAlpha(final int n) {
        if (n == 0) {
            this.stopRevealAnimation();
        }
        return super.onSetAlpha(n);
    }

    static class AlphaPropertyOverride extends Property<QsbConnector, Integer>
    {
        AlphaPropertyOverride(final Class clazz, final String s) {
            super(clazz, s);
        }

        @Override
        public Integer get(final QsbConnector qsbConnector) {
            return qsbConnector.alpha;
        }

        @Override
        public void set(final QsbConnector qsbConnector, final Integer newAlpha) {
            qsbConnector.updateAlpha(newAlpha);
        }
    }
}
