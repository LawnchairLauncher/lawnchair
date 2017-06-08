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
    private static final Property bu = new C0284i(Integer.class, "overlayAlpha");
    private int bv;
    private ObjectAnimator bw;
    private final int bx;
    private final BroadcastReceiver by;

    public QsbConnector(Context context) {
        this(context, null);
    }

    public QsbConnector(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public QsbConnector(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.by = new C0285j(this);
        this.bv = 0;
        this.bx = getResources().getColor(R.color.qsb_background) & 16777215;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        be();
        getContext().registerReceiver(this.by, C0330a.ca("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED"));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(this.by);
    }

    private void be() {
        Drawable drawable = null;
        try {
            Resources resourcesForApplication = getContext().getPackageManager().getResourcesForApplication("com.google.android.googlequicksearchbox");
            int identifier = resourcesForApplication.getIdentifier("bg_pixel_qsb_connector", "drawable", "com.google.android.googlequicksearchbox");
            if (identifier != 0) {
                drawable = resourcesForApplication.getDrawable(identifier, getContext().getTheme());
            }
        } catch (Throwable e) {
            Log.d("QsbConnector", "Error loading connector background", e);
        }
        if (drawable == null) {
            setBackgroundResource(R.color.qsb_connector);
        } else {
            setBackground(drawable);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.bv > 0) {
            canvas.drawColor(ColorUtils.setAlphaComponent(this.bx, this.bv));
        }
    }

    @Override
    protected boolean onSetAlpha(int i) {
        if (i == 0) {
            bd();
        }
        return super.onSetAlpha(i);
    }

    public void bc(boolean z) {
        if (z) {
            bd();
            bf(255);
            this.bw = ObjectAnimator.ofInt(this, bu, new int[]{0});
            this.bw.setInterpolator(new AccelerateDecelerateInterpolator());
            this.bw.start();
            return;
        }
        bf(0);
    }

    private void bd() {
        if (this.bw != null) {
            this.bw.end();
            this.bw = null;
        }
    }

    private void bf(int i) {
        if (this.bv != i) {
            this.bv = i;
            invalidate();
        }
    }

    final class C0285j extends BroadcastReceiver {
        final /* synthetic */ QsbConnector co;

        C0285j(QsbConnector qsbConnector) {
            this.co = qsbConnector;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            this.co.be();
        }
    }

    final static class C0284i extends Property {
        C0284i(Class cls, String str) {
            super(cls, str);
        }

        @Override
        public /* bridge */ /* synthetic */ Object get(Object obj) {
            return bR((QsbConnector) obj);
        }

        public Integer bR(QsbConnector qsbConnector) {
            return Integer.valueOf(qsbConnector.bv);
        }

        @Override
        public /* bridge */ /* synthetic */ void set(Object obj, Object obj2) {
            bS((QsbConnector) obj, (Integer) obj2);
        }

        public void bS(QsbConnector qsbConnector, Integer num) {
            qsbConnector.bf(num.intValue());
        }
    }
}