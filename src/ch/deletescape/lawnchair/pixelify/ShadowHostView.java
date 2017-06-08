package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Arrays;

import ch.deletescape.lawnchair.R;

public class ShadowHostView extends FrameLayout {
    private static final int bV = Math.round(38.25f);
    private static final int bW = Math.round(89.25f);
    private Bitmap bX;
    private final BlurMaskFilter bY;
    private final int bZ;
    private long[] ca;
    private Bitmap cb;
    private final int cc;
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
        this.mCanvas = new Canvas();
        this.mPaint = new Paint(3);
        setWillNotDraw(false);
        this.cc = getResources().getDimensionPixelSize(R.dimen.qsb_shadow_blur_radius);
        this.bZ = getResources().getDimensionPixelSize(R.dimen.qsb_key_shadow_offset);
        this.bY = new BlurMaskFilter((float) this.cc, Blur.NORMAL);
    }

    private boolean bH(C0280e c0280e) {
        long[] jArr = new long[]{c0280e.bS, (long) c0280e.bR, (long) c0280e.bQ.getLayoutId()};
        if (this.mView != null) {
            if (Arrays.equals(this.ca, jArr)) {
                try {
                    c0280e.bQ.reapply(getContext(), this.mView);
                    invalidate();
                    return true;
                } catch (Throwable e) {
                    Log.e("ShadowHostView", "View reapply failed", e);
                }
            }
            removeView(this.mView);
            this.mView = null;
        }
        try {
            this.mView = c0280e.bQ.apply(getContext(), this);
            bI(this.mView);
            this.ca = jArr;
            addView(this.mView);
            return true;
        } catch (Throwable e2) {
            Log.e("ShadowHostView", "View apply failed", e2);
            return false;
        }
    }

    private void bI(View view) {
        if (view instanceof TextView) {
            ((TextView) view).setShadowLayer(0.0f, 0.0f, 0.0f, 0);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int childCount = viewGroup.getChildCount() - 1; childCount >= 0; childCount--) {
                bI(viewGroup.getChildAt(childCount));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.mView != null && this.mView.getWidth() > 0 && this.mView.getHeight() > 0) {
            int width;
            int height;
            float left;
            float top;
            if (this.bX != null && this.bX.getHeight() == this.mView.getHeight()) {
                if (this.bX.getWidth() != this.mView.getWidth()) {
                }
                this.mCanvas.setBitmap(this.bX);
                this.mCanvas.drawColor(0x1000000, Mode.CLEAR);
                this.mView.draw(this.mCanvas);
                width = (this.bX.getWidth() + this.cc) + this.cc;
                height = (this.bX.getHeight() + this.cc) + this.cc;
                if (this.cb != null && this.cb.getWidth() == width) {
                    if (this.cb.getHeight() != height) {
                    }
                    this.mCanvas.setBitmap(this.cb);
                    this.mCanvas.drawColor(0x1000000, Mode.CLEAR);
                    this.mPaint.setMaskFilter(this.bY);
                    this.mPaint.setAlpha(100);
                    this.mCanvas.drawBitmap(this.bX, (float) this.cc, (float) this.cc, this.mPaint);
                    this.mCanvas.setBitmap(null);
                    this.mPaint.setMaskFilter(null);
                    left = (float) (this.mView.getLeft() - this.cc);
                    top = (float) (this.mView.getTop() - this.cc);
                    this.mPaint.setAlpha(bV);
                    canvas.drawBitmap(this.cb, left, top, this.mPaint);
                    this.mPaint.setAlpha(bW);
                    canvas.drawBitmap(this.cb, left, top + ((float) this.bZ), this.mPaint);
                }
                this.cb = Bitmap.createBitmap(width, height, Config.ALPHA_8);
                this.mCanvas.setBitmap(this.cb);
                this.mCanvas.drawColor(0x1000000, Mode.CLEAR);
                this.mPaint.setMaskFilter(this.bY);
                this.mPaint.setAlpha(100);
                this.mCanvas.drawBitmap(this.bX, (float) this.cc, (float) this.cc, this.mPaint);
                this.mCanvas.setBitmap(null);
                this.mPaint.setMaskFilter(null);
                left = (float) (this.mView.getLeft() - this.cc);
                top = (float) (this.mView.getTop() - this.cc);
                this.mPaint.setAlpha(bV);
                canvas.drawBitmap(this.cb, left, top, this.mPaint);
                this.mPaint.setAlpha(bW);
                canvas.drawBitmap(this.cb, left, top + ((float) this.bZ), this.mPaint);
            }
            this.bX = Bitmap.createBitmap(this.mView.getWidth(), this.mView.getHeight(), Config.ALPHA_8);
            this.mCanvas.setBitmap(this.bX);
            this.mCanvas.drawColor(0x1000000, Mode.CLEAR);
            this.mView.draw(this.mCanvas);
            width = (this.bX.getWidth() + this.cc) + this.cc;
            height = (this.bX.getHeight() + this.cc) + this.cc;
            if (this.cb.getHeight() != height) {
                this.cb = Bitmap.createBitmap(width, height, Config.ALPHA_8);
            }
            this.mCanvas.setBitmap(this.cb);
            this.mCanvas.drawColor(0x1000000, Mode.CLEAR);
            this.mPaint.setMaskFilter(this.bY);
            this.mPaint.setAlpha(100);
            this.mCanvas.drawBitmap(this.bX, (float) this.cc, (float) this.cc, this.mPaint);
            this.mCanvas.setBitmap(null);
            this.mPaint.setMaskFilter(null);
            left = (float) (this.mView.getLeft() - this.cc);
            top = (float) (this.mView.getTop() - this.cc);
            this.mPaint.setAlpha(bV);
            canvas.drawBitmap(this.cb, left, top, this.mPaint);
            this.mPaint.setAlpha(bW);
            canvas.drawBitmap(this.cb, left, top + ((float) this.bZ), this.mPaint);
        }
    }

    public static View bG(C0280e c0280e, ViewGroup viewGroup, View view) {
        if (c0280e == null || c0280e.bQ == null) {
            return null;
        }
        View view2;
        if (view instanceof ShadowHostView) {
            view2 = view;
        } else {
            view2 = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.shadow_host_view, viewGroup, false);
        }
        ShadowHostView view3 = (ShadowHostView) view2;
        if (!view3.bH(c0280e)) {
            view2 = null;
        }
        return view2;
    }
}