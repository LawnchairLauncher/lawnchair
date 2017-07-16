package ch.deletescape.lawnchair.iconpack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

public class CustomIconDrawable extends Drawable {

    private final Context mContext;
    private final IconPack mIconPack;
    private final Resources mResources;
    private final Drawable mOriginalIcon;
    private Drawable mIconBack = null;
    private Drawable mIconUpon = null;
    private Bitmap mIconMask = null;
    private float mScale = 1f;

    public CustomIconDrawable(Context context, IconPack iconPack, LauncherActivityInfoCompat info)
            throws PackageManager.NameNotFoundException {
        mContext = context;
        mIconPack = iconPack;
        mResources = context.getPackageManager().getResourcesForApplication(iconPack.getPackageName());
        mOriginalIcon = info.getIcon(DisplayMetrics.DENSITY_XXXHIGH);

        if (iconPack.getIconBack() != null) {
            mIconBack = getDrawable(iconPack.getIconBack());
        }
        if (iconPack.getIconUpon() != null) {
            mIconUpon = getDrawable(iconPack.getIconUpon());
        }
        if (iconPack.getIconMask() != null) {
            mIconMask = BitmapFactory.decodeResource(mResources, getIconRes(iconPack.getIconMask()));
        }

        mScale = iconPack.getScale();
    }

    private Drawable getDrawable(String name) {
        try {
            return mResources.getDrawable(getIconRes(name));
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private int getIconRes(String name) {
        return mResources.getIdentifier(name, "drawable", mIconPack.getPackageName());
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = canvas.getWidth(), height = canvas.getHeight();

        // draw iconBack
        if (mIconBack != null) {
            mIconBack.setBounds(0, 0, width, height);
            mIconBack.draw(canvas);
        }

        // mask the original icon to iconMask and then draw it
        Drawable maskedIcon = getMaskedIcon(width, height);
        maskedIcon.setBounds(0, 0, width, height);
        maskedIcon.draw(canvas);

        // draw iconUpon
        if (mIconUpon != null) {
            mIconUpon.setBounds(0, 0, width, height);
            mIconUpon.draw(canvas);
        }
    }

    private Drawable getMaskedIcon(int width, int height) {
        Bitmap bitmap =  Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas();
        canvas.setBitmap(bitmap);

        float scaledWidth = width * mScale, scaledHeight = height * mScale;
        float horizontalPadding = (width - scaledWidth) / 2;
        float verticalPadding = (height - scaledHeight) / 2;

        mOriginalIcon.setBounds((int) horizontalPadding, (int) verticalPadding,
                (int) (scaledWidth + horizontalPadding), (int) (scaledHeight + horizontalPadding));
        mOriginalIcon.draw(canvas);

        if (mIconMask != null) {
            Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            Bitmap scaledMask = Bitmap.createScaledBitmap(mIconMask, width, height, false);
            canvas.drawBitmap(scaledMask, 0, 0, clearPaint);
        }

        return new BitmapDrawable(mContext.getResources(), bitmap);
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
