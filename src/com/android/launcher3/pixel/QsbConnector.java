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
    private static final Property l;
    private int m;
    private ObjectAnimator n;
    private final int o;
    private final BroadcastReceiver p;

    static {
        l = new g(Integer.class, "overlayAlpha");
    }

    public QsbConnector(final Context context) {
        this(context, null);
    }

    public QsbConnector(final Context context, final AttributeSet set) {
        this(context, set, 0);
    }

    public QsbConnector(final Context context, final AttributeSet set, final int n) {
        super(context, set, n);
        this.p = new h(this);
        this.m = 0;
        this.o = (this.getResources().getColor(R.color.qsb_background) & 0xFFFFFF);
    }

    private void h() {
        if (this.n != null) {
            this.n.end();
            this.n = null;
        }
    }

    private void i() {
        Drawable drawable = null;
        try {
            final Context context = this.getContext();
            final Resources resourcesForApplication = context.getPackageManager().getResourcesForApplication("com.google.android.googlequicksearchbox");
            final int identifier = resourcesForApplication.getIdentifier("bg_pixel_qsb_connector", "drawable", "com.google.android.googlequicksearchbox");
            if (identifier == 0) {
                return;
            }
            final Context context2 = this.getContext();
            try {
                drawable = resourcesForApplication.getDrawable(identifier, context2.getTheme());
                if (drawable == null) {
                    this.setBackgroundResource(R.color.qsb_connector);
                    return;
                }
            }
            catch (Exception ex) {
                Log.d("QsbConnector", "Error loading connector background", ex);
            }
        }
        catch (Exception ex3) {}
        this.setBackground(drawable);
    }

    private void j(final int m) {
        if (this.m != m) {
            this.m = m;
            this.invalidate();
        }
    }

    public void g(final boolean b) {
        if (b) {
            this.h();
            this.j(255);
            (this.n = ObjectAnimator.ofInt(this, QsbConnector.l, new int[] { 0 })).setInterpolator(new AccelerateDecelerateInterpolator());
            this.n.start();
        }
        else {
            this.j(0);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.i();
        this.getContext().registerReceiver(this.p, Util.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED"));
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.getContext().unregisterReceiver(this.p);
    }

    protected void onDraw(final Canvas canvas) {
        if (this.m > 0) {
            canvas.drawColor(android.support.v4.graphics.ColorUtils.setAlphaComponent(this.o, this.m));
        }
    }

    protected boolean onSetAlpha(final int n) {
        if (n == 0) {
            this.h();
        }
        return super.onSetAlpha(n);
    }

    static class g extends Property<QsbConnector, Integer>
    {
        g(final Class clazz, final String s) {
            super(clazz, s);
        }

        @Override
        public Integer get(final QsbConnector qsbConnector) {
            return qsbConnector.m;
        }

        @Override
        public void set(final QsbConnector qsbConnector, final Integer n) {
            qsbConnector.j(n);
        }
    }

    static class h extends BroadcastReceiver
    {
        private QsbConnector ab;

        h(QsbConnector ab) {
            this.ab = ab;
        }

        public void onReceive(Context context, Intent intent) {
            this.ab.i();
        }
    }
}
