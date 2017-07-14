package ch.deletescape.lawnchair.badge;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.graphics.IconPalette;
import ch.deletescape.lawnchair.graphics.ShadowGenerator;

public class BadgeRenderer {
    private final Paint mBackgroundPaint = new Paint(3);
    private final Context mContext;
    private final IconPalette mIconPalette;
    private final IconDrawer mLargeIconDrawer;
    private final int mOffset;
    private final int mSize;
    private final IconDrawer mSmallIconDrawer;
    private final Bitmap mBackgroundWithShadow;

    private class IconDrawer {
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
    }

    public BadgeRenderer(Context context, int i) {
        mContext = context;
        Resources resources = context.getResources();
        mSize = (int) (((float) i) * 0.38f);
        mOffset = (int) (((float) i) * 0.02f);
        mLargeIconDrawer = new IconDrawer(resources.getDimensionPixelSize(R.dimen.badge_small_padding));
        mSmallIconDrawer = new IconDrawer(resources.getDimensionPixelSize(R.dimen.badge_large_padding));
        mIconPalette = IconPalette.fromDominantColor(Utilities.getDynamicBadgeColor(context));
        mBackgroundWithShadow = ShadowGenerator.createPillWithShadow(-1, mSize, mSize);
    }

    public void draw(Canvas canvas, BadgeInfo badgeInfo, Rect rect, float f, Point point) {
        draw(canvas, badgeInfo, rect, f, point, mIconPalette);
    }

    public void draw(Canvas canvas, BadgeInfo badgeInfo, Rect rect, float f, Point point, IconPalette iconPalette) {
        IconDrawer iconDrawer = (badgeInfo == null || !badgeInfo.isIconLarge()) ? mSmallIconDrawer : mLargeIconDrawer;
        if (badgeInfo != null) {
            badgeInfo.getNotificationIconForBadge(mContext, iconPalette.backgroundColor, mSize, iconDrawer.mPadding);
        }
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        f *= 0.6f;
        canvas.translate((float) ((rect.right - (mSize / 2)) + Math.min(mOffset, point.x)), (float) ((rect.top + (mSize / 2)) - Math.min(mOffset, point.y)));
        canvas.scale(f, f);
        mBackgroundPaint.setColorFilter(iconPalette.backgroundColorMatrixFilter);
        int length = mBackgroundWithShadow.getHeight();
        mBackgroundPaint.setColorFilter(iconPalette.saturatedBackgroundColorMatrixFilter);
        canvas.drawBitmap(mBackgroundWithShadow, (float) ((-length) / 2), (float) ((-length) / 2), mBackgroundPaint);
        canvas.restore();
    }
}