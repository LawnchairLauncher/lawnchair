package ch.deletescape.lawnchair.pixelify;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import ch.deletescape.lawnchair.R;

public class QsbConnector extends View {
    private static final Property alphaProperty = new AlphaPropertyOverride(Integer.class, "overlayAlpha");
    private int alpha;
    private final BroadcastReceiver packageChangeReceiver;
    private final int qsbBackgroundColor;
    private ObjectAnimator revealAnimator;

    static class AlphaPropertyOverride extends Property<QsbConnector, Integer> {
        AlphaPropertyOverride(Class clazz, String s) {
            super(clazz, s);
        }

        public Integer get(QsbConnector qsbConnector) {
            return Integer.valueOf(qsbConnector.alpha);
        }

        public void set(QsbConnector qsbConnector, Integer newAlpha) {
            qsbConnector.updateAlpha(newAlpha.intValue());
        }
    }

    public QsbConnector(Context context) {
        this(context, null);
    }

    public QsbConnector(Context context, AttributeSet set) {
        this(context, set, 0);
    }

    public QsbConnector(Context context, AttributeSet set, int n) {
        super(context, set, n);
        this.packageChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                QsbConnector.this.retrieveGoogleQsbBackground();
            }
        };
        this.alpha = 0;
        this.qsbBackgroundColor = getResources().getColor(R.color.qsb_background) & ViewCompat.MEASURED_SIZE_MASK;
    }

    private void stopRevealAnimation() {
        if (this.revealAnimator != null) {
            this.revealAnimator.end();
            this.revealAnimator = null;
        }
    }

    private void retrieveGoogleQsbBackground() {
        setBackground(getResources().getDrawable(R.drawable.bg_pixel_qsb_connector, getContext().getTheme()));
    }

    private void updateAlpha(int m) {
        if (this.alpha != m) {
            this.alpha = m;
            invalidate();
        }
    }

    public void changeVisibility(boolean makeVisible) {
        if (makeVisible) {
            stopRevealAnimation();
            updateAlpha(255);
            ObjectAnimator ofInt = ObjectAnimator.ofInt(this, alphaProperty, new int[]{0});
            this.revealAnimator = ofInt;
            ofInt.setInterpolator(new AccelerateDecelerateInterpolator());
            this.revealAnimator.start();
            return;
        }
        updateAlpha(0);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        retrieveGoogleQsbBackground();
        getContext().registerReceiver(this.packageChangeReceiver, Util.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED"));
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(this.packageChangeReceiver);
    }

    protected void onDraw(Canvas canvas) {
        if (this.alpha > 0) {
            canvas.drawColor(ColorUtils.setAlphaComponent(this.qsbBackgroundColor, this.alpha));
        }
    }

    protected boolean onSetAlpha(int n) {
        if (n == 0) {
            stopRevealAnimation();
        }
        return super.onSetAlpha(n);
    }
}
