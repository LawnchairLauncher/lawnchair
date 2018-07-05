package com.google.android.apps.nexuslauncher.qsb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.animation.FloatPropertyCompat;
import android.support.animation.SpringAnimation;
import android.support.annotation.NonNull;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.util.Themes;

public class AllAppsQsbLayout extends AbstractQsbLayout implements SearchUiManager, WallpaperColorInfo.OnChangeListener {
    private AllAppsRecyclerView mRecyclerView;
    private FallbackAppsSearchView mFallback;
    private int mAlpha;
    private Bitmap mBitmap;
    private AlphabeticalAppsList mApps;
    private SpringAnimation mSpring;
    private float mStartY;

    public AllAppsQsbLayout(final Context context) {
        this(context, null);
    }

    public AllAppsQsbLayout(final Context context, final AttributeSet set) {
        this(context, set, 0);
    }

    public AllAppsQsbLayout(final Context context, final AttributeSet set, final int n) {
        super(context, set, n);
        mAlpha = 0;
        setOnClickListener(this);

        mStartY = getTranslationY();
        setTranslationY(Math.round(mStartY));
        mSpring = new SpringAnimation(this, new FloatPropertyCompat<AllAppsQsbLayout>("allAppsQsbLayoutSpringAnimation") {
            @Override
            public float getValue(AllAppsQsbLayout allAppsQsbLayout) {
                return allAppsQsbLayout.getTranslationY() + mStartY;
            }

            @Override
            public void setValue(AllAppsQsbLayout allAppsQsbLayout, float v) {
                allAppsQsbLayout.setTranslationY(Math.round(mStartY + v));
            }
        }, 0f);
    }

    public void addOnScrollRangeChangeListener(final SearchUiManager.OnScrollRangeChangeListener onScrollRangeChangeListener) {
        mActivity.getHotseat().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (mActivity.getDeviceProfile().isVerticalBarLayout()) {
                    onScrollRangeChangeListener.onScrollRangeChanged(bottom);
                } else {
                    onScrollRangeChangeListener.onScrollRangeChanged(bottom
                            - HotseatQsbWidget.getBottomMargin(mActivity)
                            - (((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin
                            + (int) getTranslationY() + getResources().getDimensionPixelSize(R.dimen.qsb_widget_height)));
                }
            }
        });
    }

    void useAlpha(int newAlpha) {
        int normalizedAlpha = Utilities.boundToRange(newAlpha, 0, 255);
        if (mAlpha != normalizedAlpha) {
            mAlpha = normalizedAlpha;
            invalidate();
        }
    }

    @Override
    protected int getWidth(final int n) {
        if (mActivity.getDeviceProfile().isVerticalBarLayout()) {
            return n - mRecyclerView.getPaddingLeft() - mRecyclerView.getPaddingRight();
        }
        CellLayout layout = mActivity.getHotseat().getLayout();
        return n - layout.getPaddingLeft() - layout.getPaddingRight();
    }

    protected void loadBottomMargin() {
    }

    public void draw(final Canvas canvas) {
        if (mAlpha > 0) {
            if (mBitmap == null) {
                mBitmap = createBitmap(getResources().getDimension(R.dimen.hotseat_qsb_scroll_shadow_blur_radius), getResources().getDimension(R.dimen.hotseat_qsb_scroll_key_shadow_offset), 0);
            }
            mShadowPaint.setAlpha(mAlpha);
            loadDimensions(mBitmap, canvas);
            mShadowPaint.setAlpha(255);
        }
        super.draw(canvas);
    }

    public void initialize(AlphabeticalAppsList appsList, AllAppsRecyclerView recyclerView) {
        mApps = appsList;

        recyclerView.setPadding(recyclerView.getPaddingLeft(),
                getLayoutParams().height / 2 + getResources().getDimensionPixelSize(R.dimen.all_apps_extra_search_padding),
                recyclerView.getPaddingRight(),
                recyclerView.getPaddingBottom());

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                useAlpha(((BaseRecyclerView) recyclerView).getCurrentScrollY());
            }
        });

        recyclerView.setVerticalFadingEdgeEnabled(true);

        mRecyclerView = recyclerView;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        int statusBarHeightId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        ((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin += getResources().getDimensionPixelSize(statusBarHeightId > 0 ?
                statusBarHeightId :
                R.dimen.status_bar_height);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WallpaperColorInfo instance = WallpaperColorInfo.getInstance(getContext());
        instance.addOnChangeListener(this);
        onExtractedColorsChanged(instance);
    }

    public void onClick(final View view) {
        super.onClick(view);
        if (view == this && (mFallback == null || mFallback.getVisibility() == View.GONE)) {
            if (Utilities.ATLEAST_OREO) {
                final ConfigBuilder config = new ConfigBuilder(this, true);
                if (mActivity.getGoogleNow().startSearch(config.build(), config.getExtras())) {
                    return;
                }
            }
            startAppsSearch();
        }
    }

    protected void onDetachedFromWindow() {
        WallpaperColorInfo.getInstance(getContext()).removeOnChangeListener(this);
        super.onDetachedFromWindow();
    }

    public void onExtractedColorsChanged(final WallpaperColorInfo wallpaperColorInfo) {
        int color = getResources().getColor(Themes.getAttrBoolean(mActivity, R.attr.isMainColorDark) ? R.color.qsb_background_drawer_dark : R.color.qsb_background_drawer_default);
        bz(ColorUtils.compositeColors(ColorUtils.compositeColors(color, Themes.getAttrColor(mActivity, R.attr.allAppsScrimColor)), wallpaperColorInfo.getMainColor()));
    }

    public void preDispatchKeyEvent(final KeyEvent keyEvent) {
    }

    @Override
    public void startAppsSearch() {
        if (mFallback == null) {
            mFallback = (FallbackAppsSearchView) mActivity.getLayoutInflater()
                    .inflate(R.layout.all_apps_google_search_fallback, this, false);
            mFallback.initialize(this, mApps, mRecyclerView);
            mFallback.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus && TextUtils.isEmpty(mFallback.getText())) {
                        mFallback.setVisibility(View.GONE);
                    }
                }
            });
            addView(mFallback);
        }
        mFallback.setVisibility(View.VISIBLE);
        mFallback.showKeyboard();
    }

    public void refreshSearchResult() {
        if (mFallback != null) {
            mFallback.refreshSearchResult();
        }
    }

    public void reset() {
        useAlpha(0);
        if (mFallback != null) {
            mFallback.clearSearchResult();
            setOnClickListener(this);
            removeView(mFallback);
            mFallback = null;
        }
    }

    @NonNull
    public SpringAnimation getSpringForFling() {
        return mSpring;
    }
}
