package ch.deletescape.lawnchair.badge;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.SparseArray;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.graphics.ShadowGenerator;

public class BadgeRenderer {
    private final Paint mBackgroundPaint = new Paint(3);
    private final SparseArray mBackgroundsWithShadow;
    private final int mCharSize;
    private final Context mContext;
    private final IconPalette mIconPalette;
    private final IconDrawer mLargeIconDrawer;
    private final int mOffset;
    private final int mSize;
    private final IconDrawer mSmallIconDrawer;
    private final int mStackOffsetX;
    private final int mStackOffsetY;
    private final int mTextHeight;
    private final Paint mTextPaint = new Paint(1);

    class IconDrawer {
        private final Bitmap mCircleClipBitmap;
        private final int mPadding;
        private final Paint mPaint = new Paint(7);

        public IconDrawer(int i) {
            mPadding = i;
            mCircleClipBitmap = Bitmap.createBitmap(BadgeRenderer.this.mSize, BadgeRenderer.this.mSize, Config.ALPHA_8);
            Canvas canvas = new Canvas();
            canvas.setBitmap(mCircleClipBitmap);
            canvas.drawCircle((float) (BadgeRenderer.this.mSize / 2), (float) (BadgeRenderer.this.mSize / 2), (float) ((BadgeRenderer.this.mSize / 2) - i), mPaint);
        }

        public void drawIcon(Shader shader, Canvas canvas) {
            mPaint.setShader(shader);
            canvas.drawBitmap(mCircleClipBitmap, (float) ((-BadgeRenderer.this.mSize) / 2), (float) ((-BadgeRenderer.this.mSize) / 2), mPaint);
            mPaint.setShader(null);
        }
    }

    public BadgeRenderer(Context context, int i) {
        mContext = context;
        Resources resources = context.getResources();
        mSize = (int) (((float) i) * 0.38f);
        mCharSize = (int) (((float) i) * 0.12f);
        mOffset = (int) (((float) i) * 0.02f);
        mStackOffsetX = (int) (((float) i) * 0.05f);
        mStackOffsetY = (int) (((float) i) * 0.06f);
        mTextPaint.setTextSize(((float) i) * 0.26f);
        mTextPaint.setTextAlign(Align.CENTER);
        mLargeIconDrawer = new IconDrawer(resources.getDimensionPixelSize(R.dimen.badge_small_padding));
        mSmallIconDrawer = new IconDrawer(resources.getDimensionPixelSize(R.dimen.badge_large_padding));
        Rect rect = new Rect();
        mTextPaint.getTextBounds("0", 0, 1, rect);
        mTextHeight = rect.height();
        mBackgroundsWithShadow = new SparseArray(3);
        mIconPalette = IconPalette.fromDominantColor(context.getResources().getColor(R.color.badge_color));
    }

    public void draw(Canvas canvas, BadgeInfo badgeInfo, Rect rect, float f, Point point) {
        String str;
        mTextPaint.setColor(mIconPalette.textColor);
        IconDrawer iconDrawer = (badgeInfo == null || !badgeInfo.isIconLarge()) ? mSmallIconDrawer : mLargeIconDrawer;
        if (badgeInfo != null) {
            badgeInfo.getNotificationIconForBadge(mContext, mIconPalette.backgroundColor, mSize, iconDrawer.mPadding);
        }
        if (badgeInfo == null) {
            str = "0";
        } else {
            str = String.valueOf(badgeInfo.getNotificationCount());
        }
        int length = str.length();
        int i = mSize;
        Bitmap bitmap = (Bitmap) mBackgroundsWithShadow.get(length);
        if (bitmap == null) {
            bitmap = ShadowGenerator.createPillWithShadow(-1, i, mSize);
            mBackgroundsWithShadow.put(length, bitmap);
        }
        canvas.save(1);
        f *= 0.6f;
        canvas.translate((float) ((rect.right - (i / 2)) + Math.min(mOffset, point.x)), (float) ((rect.top + (mSize / 2)) - Math.min(mOffset, point.y)));
        canvas.scale(f, f);
        mBackgroundPaint.setColorFilter(mIconPalette.backgroundColorMatrixFilter);
        length = bitmap.getHeight();
        mBackgroundPaint.setColorFilter(mIconPalette.saturatedBackgroundColorMatrixFilter);
        canvas.drawBitmap(bitmap, (float) ((-length) / 2), (float) ((-length) / 2), mBackgroundPaint);
        canvas.restore();
    }
}