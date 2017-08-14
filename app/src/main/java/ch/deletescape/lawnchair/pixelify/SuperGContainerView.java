package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.LauncherRootView;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.blur.BlurDrawable;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.util.TransformingTouchDelegate;

public class SuperGContainerView extends BaseQsbView {
    private static final Rect sTempRect = new Rect();
    private final TransformingTouchDelegate bz;
    private int mLeft;
    private int mTop;
    private int mBlurTranslationX;
    private int mBlurTranslationY;
    private final boolean mBlurEnabled;
    private BlurDrawable mBlurDrawable;
    private Runnable mUpdatePosition = new Runnable() {
        @Override
        public void run() {
            int left = 0, top = 0;
            View view = mQsbView;
            while (!(view instanceof LauncherRootView)) {
                if (view == null) {
                    break;
                }
                left += view.getLeft();
                top += view.getTop();
                view = (View) view.getParent();
            }
            mLeft = left;
            mTop = top;
            updateBlur();
        }
    };


    public SuperGContainerView(Context context) {
        this(context, null);
    }

    public SuperGContainerView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public SuperGContainerView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        if (Utilities.getPrefs(getContext()).getUseFullWidthSearchBar()) {
            bz = null;
        } else {
            bz = new TransformingTouchDelegate(this);
        }
        mBlurEnabled = BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_QSB);
        if (mBlurEnabled) {
            mBlurDrawable = mLauncher.getBlurWallpaperProvider().createDrawable(100, false);
        }
    }

    @Override
    protected void setupViews() {
        super.setupViews();
        if (mBlurEnabled) {
            mQsbView.setBackground(mBlurDrawable);
            mQsbView.setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        if (Utilities.getPrefs(getContext()).getUseWhiteGoogleIcon() &&
                (mBlurEnabled || FeatureFlags.INSTANCE.useDarkTheme(FeatureFlags.DARK_QSB))) {
            ((ImageView) findViewById(R.id.g_icon)).setColorFilter(Color.WHITE);
            if (Utilities.getPrefs(getContext()).getShowVoiceSearchButton()) {
                ((ImageView) findViewById(R.id.mic_icon)).setColorFilter(Color.WHITE);
            }
        }
    }

    @Override
    public void applyVoiceSearchPreference() {
        if (!Utilities.getPrefs(getContext()).getUseFullWidthSearchBar()) {
            super.applyVoiceSearchPreference();
            if (bz != null) {
                bz.setDelegateView(mQsbView);
            }
        }
    }

    @Override
    protected int getQsbView(boolean withMic) {
        if (bz != null) {
            float f;
            if (withMic) {
                f = 0.0f;
            } else {
                f = getResources().getDimension(R.dimen.qsb_touch_extension);
            }
            bz.extendTouchBounds(f);
        }

        return withMic ? R.layout.qsb_with_mic : R.layout.qsb_without_mic;
    }

    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Utilities.getPrefs(getContext()).getShowPixelBar()) {
            return;
        }
        if (bz != null) {
            mLauncher.getWorkspace().findViewById(R.id.workspace_blocked_row).setTouchDelegate(bz);
        }
        if (mBlurEnabled)
            mBlurDrawable.startListening();
    }

    @Override
    protected void onMeasure(int i, int i2) {
        LayoutParams layoutParams;
        int i4 = -getResources().getDimensionPixelSize(R.dimen.qsb_overlap_margin);
        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        Rect workspacePadding = deviceProfile.getWorkspacePadding(sTempRect);
        int size = MeasureSpec.getSize(i) - i4;
        int i5 = (size - workspacePadding.left) - workspacePadding.right;
        size = DeviceProfile.calculateCellWidth(i5, deviceProfile.inv.numColumnsOriginal) * deviceProfile.inv.numColumnsOriginal;
        i4 += workspacePadding.left + ((i5 - size) / 2);
        int oldSize = size;
        size = i4;
        if (mQsbView != null) {
            layoutParams = (LayoutParams) mQsbView.getLayoutParams();
            layoutParams.width = oldSize / deviceProfile.inv.numColumnsOriginal;
            if (showMic) {
                layoutParams.width = Math.max(layoutParams.width, getResources().getDimensionPixelSize(R.dimen.qsb_min_width_with_mic));
            }
            layoutParams.setMarginStart(size);
            layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
        }
        if (qsbConnector != null) {
            layoutParams = (LayoutParams) qsbConnector.getLayoutParams();
            layoutParams.width = size + (layoutParams.height / 2);
        }
        super.onMeasure(i, i2);
    }


    @Override
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        if (bz != null && mQsbView != null && Utilities.getPrefs(getContext()).getShowPixelBar()) {
            int i5 = 0;
            if (Utilities.isRtl(getResources())) {
                i5 = mQsbView.getLeft() - mLauncher.getDeviceProfile().getWorkspacePadding(sTempRect).left;
            }
            bz.setBounds(i5, mQsbView.getTop(), mQsbView.getWidth() + i5, mQsbView.getBottom());
        }
        if (!mBlurEnabled) return;
        post(mUpdatePosition);
    }

    private void updateBlur() {
        if (!mBlurEnabled) return;
        mBlurDrawable.setTranslation(mTop - mBlurTranslationY);
        mBlurDrawable.setOverscroll(mLeft - mBlurTranslationX);
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        if (deviceProfile.isLandscape || deviceProfile.isTablet) return;
        int height = mQsbView.getHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            rect.right = height + getRight();
        } else {
            rect.left = -height;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        return bz == null && super.dispatchTouchEvent(motionEvent);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBlurEnabled)
            mBlurDrawable.stopListening();
    }

    @Override
    public void translateBlurX(int translationX) {
        if (!mBlurEnabled) return;
        mBlurTranslationX = translationX;
        updateBlur();
    }

    @Override
    public void translateBlurY(int translationY) {
        if (!mBlurEnabled) return;
        mBlurTranslationY = translationY;
        updateBlur();
    }
}