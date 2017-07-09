package ch.deletescape.lawnchair.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.blur.BlurDrawable;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;

public class AllAppsBackground extends View {

    private final Paint mScrimPaint;
    private final Path mScrimPath;
    private final BlurDrawable mBlurDrawable;
    private final boolean mBlurEnabled;
    private float mStatusBarHeight;
    private boolean mShowingScrim;

    public AllAppsBackground(Context context) {
        this(context, null, 0);
    }

    public AllAppsBackground(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsBackground(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int scrimColor = ContextCompat.getColor(context, R.color.all_apps_statusbar_color);

        mScrimPaint = new Paint();
        mScrimPaint.setColor(scrimColor);
        mScrimPath = new Path();

        mBlurEnabled = BlurWallpaperProvider.isEnabled();

        mBlurDrawable = LauncherAppState.getInstance().getLauncher().getBlurWallpaperProvider().createDrawable();
        setBackground(mBlurDrawable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBlurDrawable.startListening();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBlurDrawable.stopListening();
    }

    public void setStatusBarHeight(float height) {
        mStatusBarHeight = height;
        boolean shouldShowScrim = mStatusBarHeight > 0;
        if (mShowingScrim || shouldShowScrim) {
            invalidate();
        }
        mShowingScrim = shouldShowScrim;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mStatusBarHeight <= 0) return;

        int width = getMeasuredWidth();

        mScrimPath.reset();
        mScrimPath.moveTo(0, 0);
        mScrimPath.lineTo(width, 0);
        mScrimPath.lineTo(width, mStatusBarHeight);
        mScrimPath.lineTo(0, mStatusBarHeight);

        canvas.drawPath(mScrimPath, mScrimPaint);
    }

    public void setWallpaperTranslation(float translation) {
        setBackground(mBlurDrawable);
        mBlurDrawable.setTranslation(translation);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (mBlurEnabled) {
            mBlurDrawable.setOverlayColor(color);
        } else {
            super.setBackgroundColor(color);
        }
    }
}
