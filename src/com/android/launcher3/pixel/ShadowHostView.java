package com.android.launcher3.pixel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.launcher3.R;

public class ShadowHostView extends FrameLayout {
    private static final int cf = 38;
    private static final int cg = 89;
    private Bitmap ch;
    private final BlurMaskFilter blurMaskFilter; //ci
    private final int cj;
    private Bitmap ck;
    private final int cl;
    private final Canvas mCanvas;
    private final Paint mPaint;
    private View mView;

    public ShadowHostView(Context context) {
        this(context, null);
    }

    public ShadowHostView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ShadowHostView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mCanvas = new Canvas();
        mPaint = new Paint(3);
        setWillNotDraw(false);
        cl = getResources().getDimensionPixelSize(R.dimen.qsb_shadow_blur_radius);
        cj = getResources().getDimensionPixelSize(R.dimen.qsb_key_shadow_offset);
        blurMaskFilter = new BlurMaskFilter((float) cl, Blur.NORMAL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int n =  android.graphics.Color.BLACK;
        if (this.mView != null && this.mView.getWidth() > 0 && this.mView.getHeight() > 0) {
            if (this.ch == null || this.ch.getHeight() != this.mView.getHeight() || this.ch.getWidth() != this.mView.getWidth()) {
                this.ch = Bitmap.createBitmap(this.mView.getWidth(), this.mView.getHeight(), Bitmap.Config.ALPHA_8);
            }
            this.mCanvas.setBitmap(this.ch);
            this.mCanvas.drawColor(n, PorterDuff.Mode.CLEAR);
            this.mView.draw(this.mCanvas);
            final int n2 = this.ch.getWidth() + this.cl + this.cl;
            final int n3 = this.ch.getHeight() + this.cl + this.cl;
            if (this.ck == null || this.ck.getWidth() != n2 || this.ck.getHeight() != n3) {
                this.ck = Bitmap.createBitmap(n2, n3, Bitmap.Config.ALPHA_8);
            }
            this.mCanvas.setBitmap(this.ck);
            this.mCanvas.drawColor(n, PorterDuff.Mode.CLEAR);
            this.mPaint.setMaskFilter(blurMaskFilter);
            this.mPaint.setAlpha(100);
            this.mCanvas.drawBitmap(this.ch, (float)this.cl, (float)this.cl, this.mPaint);
            this.mCanvas.setBitmap(null);
            this.mPaint.setMaskFilter(null);
            final float n4 = this.mView.getLeft() - this.cl;
            final float n5 = this.mView.getTop() - this.cl;
            this.mPaint.setAlpha(ShadowHostView.cf);
            canvas.drawBitmap(this.ck, n4, n5, this.mPaint);
            this.mPaint.setAlpha(ShadowHostView.cg);
            canvas.drawBitmap(this.ck, n4, n5 + this.cj, this.mPaint);
        }
    }
    public static View getView(final RemoteViews remoteViews, final ViewGroup viewGroup, final View view) {
        if (remoteViews == null) {
            return null;
        }
        ShadowHostView shadowHostView;
        if (view instanceof ShadowHostView) {
            shadowHostView = (ShadowHostView) view;
        }
        else {
            shadowHostView = (ShadowHostView) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.shadow_host_view, viewGroup, false);
        }
        if (!shadowHostView.applyView(remoteViews)) {
            shadowHostView = null;
        }

        return shadowHostView;
    }

    private boolean applyView(final RemoteViews remoteViews) {
        final boolean b = true;
        if (this.mView != null) {
            try {
                final Context context = this.getContext();
                try {
                    remoteViews.reapply(context, this.mView);
                    this.invalidate();
                    return b;
                }
                catch (RuntimeException ex) {
                    Log.e("ShadowHostView", "View reapply failed", ex);
                    this.removeView(this.mView);
                    this.mView = null;
                }
            }
            catch (RuntimeException ex3) {}
        }
        try {
            this.Dim(this.mView = remoteViews.apply(this.getContext(), this));
            this.addView(this.mView);
            return b;
        }
        catch (RuntimeException ex2) {
            Log.e("ShadowHostView", "View apply failed", ex2);
            return false;
        }
    }

    private void Dim(final View view) {
        if (view instanceof TextView) {
            ((TextView)view).setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        }
        else if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup)view;
            for (int i = viewGroup.getChildCount() - 1; i >= 0; --i) {
                this.Dim(viewGroup.getChildAt(i));
            }
        }
    }
}
