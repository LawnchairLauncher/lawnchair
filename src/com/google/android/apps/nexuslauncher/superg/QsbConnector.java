package com.google.android.apps.nexuslauncher.superg;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.android.launcher3.R;

public class QsbConnector extends View  {
    private static final Property sAlphaProperty = new Property<QsbConnector, Integer>(Integer.class, "overlayAlpha") {
        @Override
        public Integer get(QsbConnector qsbConnector) {
            return qsbConnector.mForegroundAlpha;
        }

        @Override
        public void set(QsbConnector qsbConnector, Integer newAlpha) {
            qsbConnector.updateAlpha(newAlpha);
        }
    };

    private int mForegroundAlpha;
    private ObjectAnimator mRevealAnimator;
    private final int mForegroundColor;

    public QsbConnector(Context context) {
        this(context, null);
    }

    public QsbConnector(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QsbConnector(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mForegroundAlpha = 0;
        mForegroundColor = getResources().getColor(R.color.qsb_background) & 0xFFFFFF;

        setBackground(getResources().getDrawable(R.drawable.bg_pixel_qsb_connector, getContext().getTheme()));
    }

    private void stopRevealAnimation() {
        if (mRevealAnimator != null) {
            mRevealAnimator.end();
            mRevealAnimator = null;
        }
    }

    private void updateAlpha(int alpha) {
        if (mForegroundAlpha != alpha) {
            mForegroundAlpha = alpha;
            invalidate();
        }
    }

    public void changeVisibility(boolean makeVisible) {
        if (makeVisible) {
            stopRevealAnimation();
            updateAlpha(255);
            mRevealAnimator = ObjectAnimator.ofInt(this, QsbConnector.sAlphaProperty, 0);
            mRevealAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            mRevealAnimator.start();
        } else {
            updateAlpha(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mForegroundAlpha > 0) {
            canvas.drawColor(ColorUtils.setAlphaComponent(mForegroundColor, mForegroundAlpha));
        }
    }

    @Override
    protected boolean onSetAlpha(final int alpha) {
        if (alpha == 0) {
            stopRevealAnimation();
        }
        return super.onSetAlpha(alpha);
    }
}