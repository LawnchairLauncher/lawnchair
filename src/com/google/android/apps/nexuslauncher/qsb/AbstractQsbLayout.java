package com.google.android.apps.nexuslauncher.qsb;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Process;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.LauncherLayoutChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.ShadowGenerator.Builder;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;

public abstract class AbstractQsbLayout extends FrameLayout implements LauncherLayoutChangeListener, OnClickListener, OnSharedPreferenceChangeListener {
    protected final NexusLauncherActivity mActivity;
    protected int mColor;
    protected View mMicIconView;
    private final RectF mDestRect;
    private final Rect mSrcRect;
    protected Bitmap mShadowBitmap;
    protected final Paint mShadowPaint;

    protected abstract int getWidth(int i);

    protected abstract void loadBottomMargin();

    public AbstractQsbLayout(Context context) {
        this(context, null);
    }

    public AbstractQsbLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbstractQsbLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSrcRect = new Rect();
        mDestRect = new RectF();
        mShadowPaint = new Paint(1);
        mColor = 0;
        mActivity = (NexusLauncherActivity) Launcher.getLauncher(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivity.getDeviceProfile().addLauncherLayoutChangedListener(this);
        loadAndGetPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    protected SharedPreferences loadAndGetPreferences() {
        mMicIconView = findViewById(R.id.mic_icon);
        mMicIconView.setOnClickListener(this);
        SharedPreferences devicePrefs = Utilities.getDevicePrefs(getContext());
        loadPreferences(devicePrefs);
        return devicePrefs;
    }

    @Override
    protected void onDetachedFromWindow() {
        this.mActivity.getDeviceProfile().removeLauncherLayoutChangedListener(this);
        Utilities.getDevicePrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    public void bz(int i) {
        if (mColor != i) {
            mColor = i;
            mShadowBitmap = null;
            invalidate();
        }
    }

    public void onLauncherLayoutChanged() {
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        loadBottomMargin();
        DeviceProfile deviceProfile = this.mActivity.getDeviceProfile();
        int bw = getWidth(MeasureSpec.getSize(widthMeasureSpec));
        int calculateCellWidth = DeviceProfile.calculateCellWidth(bw, deviceProfile.inv.numHotseatIcons);
        int round = Math.round(((float) deviceProfile.iconSizePx) * 0.92f);
        setMeasuredDimension(((bw - (calculateCellWidth - round)) + getPaddingLeft()) + getPaddingRight(), MeasureSpec.getSize(heightMeasureSpec));
        for (int childCount = getChildCount() - 1; childCount >= 0; childCount--) {
            View childAt = getChildAt(childCount);
            measureChildWithMargins(childAt, widthMeasureSpec, 0, heightMeasureSpec, 0);
            if (childAt.getMeasuredWidth() <= round) {
                LayoutParams layoutParams = (LayoutParams) childAt.getLayoutParams();
                bw = (round - childAt.getMeasuredWidth()) / 2;
                layoutParams.rightMargin = bw;
                layoutParams.leftMargin = bw;
            }
        }
    }

    public void draw(Canvas canvas) {
        if (mShadowBitmap == null) {
            int iconBitmapSize = LauncherAppState.getIDP(getContext()).iconBitmapSize;
            mShadowBitmap = createBitmap(((float) iconBitmapSize) * 0.010416667f, ((float) iconBitmapSize) * 0.020833334f, mColor);
        }
        loadDimensions(mShadowBitmap, canvas);
        super.draw(canvas);
    }

    protected void loadDimensions(Bitmap bitmap, Canvas canvas) {
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        int i = height + 20;
        int width = bitmap.getWidth();
        int height2 = bitmap.getHeight();
        mSrcRect.top = 0;
        mSrcRect.bottom = height2;
        mDestRect.top = (float) (getPaddingTop() - ((height2 - height) / 2));
        mDestRect.bottom = ((float) height2) + mDestRect.top;
        float f = (float) ((width - i) / 2);
        int i2 = width / 2;
        float paddingLeft = ((float) getPaddingLeft()) - f;
        drawWithDimensions(bitmap, canvas, 0, i2, paddingLeft, paddingLeft + ((float) i2));
        float width2 = ((float) (getWidth() - getPaddingRight())) + f;
        drawWithDimensions(bitmap, canvas, i2, width, width2 - ((float) i2), width2);
        Bitmap bitmap2 = bitmap;
        Canvas canvas2 = canvas;
        drawWithDimensions(bitmap2, canvas2, i2 - 5, i2 + 5, paddingLeft + ((float) i2), width2 - ((float) i2));
    }

    private void drawWithDimensions(Bitmap bitmap, Canvas canvas, int srcLeft, int srcRight, float destLeft, float destRight) {
        mSrcRect.left = srcLeft;
        mSrcRect.right = srcRight;
        mDestRect.left = destLeft;
        mDestRect.right = destRight;
        canvas.drawBitmap(bitmap, mSrcRect, mDestRect, mShadowPaint);
    }

    protected Bitmap createBitmap(float shadowBlur, float keyShadowDistance, int color) {
        int height = (getHeight() - getPaddingTop()) - getPaddingBottom();
        int i2 = height + 20;
        Builder builder = new Builder(color);
        builder.shadowBlur = shadowBlur;
        builder.keyShadowDistance = keyShadowDistance;
        builder.keyShadowAlpha = builder.ambientShadowAlpha;
        Bitmap createPill = builder.createPill(i2, height);
        if (Color.alpha(color) < 255) {
            Canvas canvas = new Canvas(createPill);
            Paint paint = new Paint();
            paint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
            canvas.drawRoundRect(builder.bounds, (float) (height / 2), (float) (height / 2), paint);
            paint.setXfermode(null);
            paint.setColor(color);
            canvas.drawRoundRect(builder.bounds, (float) (height / 2), (float) (height / 2), paint);
            canvas.setBitmap(null);
        }
        if (Utilities.ATLEAST_OREO) {
            return createPill.copy(Config.HARDWARE, false);
        }
        return createPill;
    }

    public void onClick(View view) {
        if (view == mMicIconView) {
            fallbackSearch("android.intent.action.VOICE_ASSIST");
        }
    }

    protected void fallbackSearch(String action) {
        final String GoogleQSB = "com.google.android.googlequicksearchbox";
        try {
            getContext().startActivity(new Intent(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(GoogleQSB));
        } catch (ActivityNotFoundException e) {
            try {
                getContext().getPackageManager().getPackageInfo(GoogleQSB, 0);
                LauncherAppsCompat.getInstance(getContext())
                        .showAppDetailsForProfile(new ComponentName(GoogleQSB, ".SearchActivity"), Process.myUserHandle());
            } catch (PackageManager.NameNotFoundException ignored) {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
            }
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("opa_enabled".equals(str)) {
            loadPreferences(sharedPreferences);
        }
    }

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mMicIconView.setVisibility(sharedPreferences.getBoolean("opa_enabled", true) ? View.GONE : View.VISIBLE);
        requestLayout();
    }
}