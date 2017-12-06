package ch.deletescape.lawnchair.graphics;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class FastScrollThumbDrawable extends Drawable {
    private static final Matrix sMatrix = new Matrix();
    private final boolean mIsRtl;
    private final Paint mPaint;
    private final Path mPath = new Path();

    public FastScrollThumbDrawable(Paint paint, boolean z) {
        this.mPaint = paint;
        this.mIsRtl = z;
    }

    public void getOutline(Outline outline) {
        if (this.mPath.isConvex()) {
            outline.setConvexPath(this.mPath);
        }
    }

    protected void onBoundsChange(Rect rect) {
        this.mPath.reset();
        float height = ((float) rect.height()) * 0.5f;
        float f = 2.0f * height;
        float f2 = height / 5.0f;
        this.mPath.addRoundRect((float) rect.left, (float) rect.top, ((float) rect.left) + f, f + ((float) rect.top), new float[]{height, height, height, height, f2, f2, height, height}, Direction.CCW);
        sMatrix.setRotate(-45.0f, ((float) rect.left) + height, ((float) rect.top) + height);
        if (this.mIsRtl) {
            sMatrix.postTranslate((float) rect.width(), 0.0f);
            sMatrix.postScale(-1.0f, 1.0f, (float) rect.width(), 0.0f);
        }
        this.mPath.transform(sMatrix);
    }

    public void draw(Canvas canvas) {
        canvas.drawPath(this.mPath, this.mPaint);
    }

    public void setAlpha(int i) {
    }

    public void setColorFilter(ColorFilter colorFilter) {
    }

    public int getOpacity() {
        return -3;
    }
}