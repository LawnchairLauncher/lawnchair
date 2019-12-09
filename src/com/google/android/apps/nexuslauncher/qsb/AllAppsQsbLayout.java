package com.google.android.apps.nexuslauncher.qsb;

import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_ICON_PARAMS;
import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER;
import static com.android.launcher3.LauncherState.HOTSEAT_SEARCH_BOX;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.ColorEngine.OnColorChangeListener;
import ch.deletescape.lawnchair.colors.ColorEngine.ResolveInfo;
import ch.deletescape.lawnchair.colors.ColorEngine.Resolvers;
import ch.deletescape.lawnchair.globalsearch.SearchProvider;
import ch.deletescape.lawnchair.globalsearch.SearchProviderController;
import ch.deletescape.lawnchair.globalsearch.providers.AppSearchSearchProvider;
import ch.deletescape.lawnchair.globalsearch.providers.web.WebSearchProvider;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.util.PackageManagerHelper;
import com.google.android.apps.nexuslauncher.search.SearchThread;
import org.jetbrains.annotations.NotNull;

public class AllAppsQsbLayout extends AbstractQsbLayout implements SearchUiManager, o,
        OnColorChangeListener, OnIDPChangeListener {

    private final k Ds;
    private final int Dt;
    private int mShadowAlpha;
    private Bitmap Dv;
    private boolean mUseFallbackSearch;
    private FallbackAppsSearchView mFallback;
    public float Dy;
    private TextView mHint;
    private AllAppsContainerView mAppsView;
    boolean mDoNotRemoveFallback;
    private LawnchairPreferences prefs;
    private int mForegroundColor;

    private boolean widgetMode;

    private final boolean mLowPerformanceMode;

    private final int mTopAdjusting;
    private final int mVerticalOffset;

    public AllAppsQsbLayout(Context context) {
        this(context, null);
    }

    public AllAppsQsbLayout(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public AllAppsQsbLayout(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mShadowAlpha = 0;
        setOnClickListener(this);
        this.Ds = k.getInstance(context);
        this.Dt = getResources().getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting);
        this.Dy = getResources().getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset);
        setClipToPadding(false);
        prefs = LawnchairPreferences.Companion.getInstanceNoCreate();

        mLowPerformanceMode = prefs.getLowPerformanceMode();
        mTopAdjusting = getResources().getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting);
        mVerticalOffset = getResources().getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        mHint = findViewById(R.id.qsb_hint);
    }

    @Override
    public float getScrollRangeDelta(Rect insets) {
        DeviceProfile wallpaperDeviceProfile = mActivity.getWallpaperDeviceProfile();
        int i = (wallpaperDeviceProfile.hotseatBarSizePx - wallpaperDeviceProfile.hotseatCellHeightPx) - getLayoutParams().height;
        int i2 = insets.bottom;
        return (float) (((getLayoutParams().height + Math.max(-mVerticalOffset, insets.top - mTopAdjusting)) + mVerticalOffset) + (i2 + ((int) (((float) (i - i2)) * 0.45f))));
    }

    public void setWidgetMode(boolean enable) {
        widgetMode = enable;
        dz();
        setContentVisibility(HOTSEAT_SEARCH_BOX, NO_ANIM_PROPERTY_SETTER, LINEAR);
    }

    public void setInsets(Rect rect) {
        c(Utilities.getDevicePrefs(getContext()));
        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        mlp.topMargin = getTopMargin(rect);
        requestLayout();
    }

    public int getTopMargin(Rect rect) {
        return Math.max(Math.round(-this.Dy), rect.top - this.Dt);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ColorEngine.getInstance(getContext()).addColorChangeListeners(this,
                Resolvers.HOTSEAT_QSB_BG, Resolvers.ALLAPPS_QSB_BG);
        LauncherAppState.getIDP(getContext()).addOnChangeListener(this);
        dN();
        Ds.a(this);
    }

    @Override
    public void onColorChange(@NotNull ResolveInfo resolveInfo) {
        if (resolveInfo.getKey().equals(Resolvers.ALLAPPS_QSB_BG)) {
            mForegroundColor = resolveInfo.getForegroundColor();
            setAllAppsBgColor(resolveInfo.getColor());
            az(mAllAppsBgColor);
        } else if (resolveInfo.getKey().equals(Resolvers.HOTSEAT_QSB_BG)) {
            setHotseatBgColor(resolveInfo.getColor());
        }
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        if ((changeFlags & CHANGE_FLAG_ICON_PARAMS) != 0) {
            mAllAppsShadowBitmap = mHotseatShadowBitmap = mBubbleShadowBitmap = mClearBitmap = null;
            dH();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals("pref_allAppsGoogleSearch")) {
            loadPreferences(sharedPreferences);
        }
    }

    @Override
    protected Drawable getIcon(boolean colored) {
        if (prefs.getAllAppsGlobalSearch()) {
            return super.getIcon(colored);
        } else {
            return new AppSearchSearchProvider(getContext()).getIcon(colored);
        }
    }

    @Override
    protected Drawable getHotseatIcon(boolean colored) {
        return super.getIcon(colored);
    }

    @Override
    protected boolean logoCanOpenFeed() {
        return super.logoCanOpenFeed() && prefs.getAllAppsGlobalSearch();
    }

    protected void onDetachedFromWindow() {
        ColorEngine.getInstance(getContext()).removeColorChangeListeners(this,
                Resolvers.HOTSEAT_QSB_BG, Resolvers.ALLAPPS_QSB_BG);
        LauncherAppState.getIDP(getContext()).removeOnChangeListener(this);
        super.onDetachedFromWindow();
        Ds.b(this);
    }

    protected final int aA(int i) {
        if (widgetMode) {
            return i;
        }
        if (mActivity.getDeviceProfile().isVerticalBarLayout()) {
            return (i - this.mAppsView.getActiveRecyclerView().getPaddingLeft()) - this.mAppsView
                    .getActiveRecyclerView().getPaddingRight();
        }
        Rect padding = mActivity.getDeviceProfile().getHotseatLayoutPadding();
        return (i - padding.left) - padding.right;
    }

    public final void initialize(AllAppsContainerView allAppsContainerView) {
        this.mAppsView = allAppsContainerView;
        int i = 0;
        mAppsView.addElevationController(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                setShadowAlpha(((BaseRecyclerView) recyclerView).getCurrentScrollY());
            }
        });
        mAppsView.setRecyclerViewVerticalFadingEdgeEnabled(!mLowPerformanceMode);
    }

    public final void dM() {
        dN();
        invalidate();
    }

    private void dN() {
        az(this.mAllAppsBgColor);
        h(this.Ds.micStrokeWidth());
        this.Dh = this.Ds.hintIsForAssistant();
        mUseTwoBubbles = useTwoBubbles();
        setHintText(this.Ds.hintTextValue(), this.mHint);
        dH();
    }

    public void onClick(View view) {
        super.onClick(view);
        if (view == this) {
            startSearch("", this.Di);
        }
    }

    public final void l(String str) {
        startSearch(str, 0);
    }

    @Override
    public void startSearch() {
        post(() -> startSearch("", Di));
    }

    @Override
    public final void startSearch(String str, int i) {
        if (mHotseatProgress < 0.5) {
            startDrawerSearch(str, i);
        } else {
            startHotseatSearch();
        }
    }

    private void startDrawerSearch(String str, int i) {
        SearchProviderController controller = SearchProviderController.Companion
                .getInstance(getContext());
        SearchProvider provider = controller.getSearchProvider();
        if (shouldUseFallbackSearch(provider)) {
            searchFallback(str);
        } else if (controller.isGoogle()) {
            final ConfigBuilder f = new ConfigBuilder(this, true);
            if (!getLauncher().getGoogleNow().startSearch(f.build(), f.getExtras())) {
                searchFallback(str);
                if (mFallback != null) {
                    mFallback.setHint(null);
                }
            }
        } else {
            provider.startSearch(intent -> {
                getLauncher().startActivity(intent);
                return null;
            });
        }
    }

    private void startHotseatSearch() {
        SearchProviderController controller = SearchProviderController.Companion
                .getInstance(getContext());
        if (controller.isGoogle()) {
            startGoogleSearch();
        } else {
            controller.getSearchProvider().startSearch(intent -> {
                getLauncher().openQsb();
                getContext().startActivity(intent, ActivityOptionsCompat
                        .makeClipRevealAnimation(this, 0, 0, getWidth(), getHeight()).toBundle());
                return null;
            });
        }
    }

    private void startGoogleSearch() {
        final ConfigBuilder f = new ConfigBuilder(this, false);
        if (!forceFallbackSearch() && getLauncher().getGoogleNow()
                .startSearch(f.build(), f.getExtras())) {
            SharedPreferences devicePrefs = Utilities.getDevicePrefs(getContext());
            devicePrefs.edit().putInt("key_hotseat_qsb_tap_count",
                    devicePrefs.getInt("key_hotseat_qsb_tap_count", 0) + 1).apply();
            getLauncher().playQsbAnimation();
        } else {
            getContext().sendOrderedBroadcast(getSearchIntent(), null,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (getResultCode() == 0) {
                                fallbackSearch(
                                        "com.google.android.googlequicksearchbox.TEXT_ASSIST");
                            } else {
                                getLauncher().playQsbAnimation();
                            }
                        }
                    }, null, 0, null, null);
        }
    }

    private boolean forceFallbackSearch() {
        return !PackageManagerHelper.isAppEnabled(getContext().getPackageManager(),
                "com.google.android.apps.nexuslauncher", 0);
    }

    private Intent getSearchIntent() {
        int[] array = new int[2];
        getLocationInWindow(array);
        Rect rect = new Rect(0, 0, getWidth(), getHeight());
        rect.offset(array[0], array[1]);
        rect.inset(getPaddingLeft(), getPaddingTop());
        return ConfigBuilder.getSearchIntent(rect, findViewById(R.id.g_icon), mMicIconView);
    }

    private boolean shouldUseFallbackSearch() {
        SearchProviderController controller = SearchProviderController.Companion
                .getInstance(getContext());
        SearchProvider provider = controller.getSearchProvider();
        return shouldUseFallbackSearch(provider);
    }

    private boolean shouldUseFallbackSearch(SearchProvider provider) {
        return !Utilities
                .getLawnchairPrefs(getContext()).getAllAppsGlobalSearch()
                || provider instanceof AppSearchSearchProvider
                || provider instanceof WebSearchProvider;
    }

    public void searchFallback(String query) {
        ensureFallbackView();
        mFallback.setText(query);
        mFallback.showKeyboard();
    }

    public final void resetSearch() {
        setShadowAlpha(0);
        if (mUseFallbackSearch) {
            resetFallbackView();
        } else if (!mDoNotRemoveFallback) {
            removeFallbackView();
        }
    }

    private void ensureFallbackView() {
        if (mFallback == null) {
            setOnClickListener(null);
            mFallback = (FallbackAppsSearchView) getLauncher().getLayoutInflater()
                    .inflate(R.layout.all_apps_google_search_fallback, this, false);
            AllAppsContainerView allAppsContainerView = this.mAppsView;
            mFallback.DJ = this;
            mFallback.mApps = allAppsContainerView.getApps();
            mFallback.mAppsView = allAppsContainerView;
            mFallback.DI.initialize(new SearchThread(mFallback.getContext()), mFallback,
                    Launcher.getLauncher(mFallback.getContext()), mFallback);
            addView(this.mFallback);
            mFallback.setTextColor(mForegroundColor);
        }
    }

    private void removeFallbackView() {
        if (mFallback != null) {
            mFallback.clearSearchResult();
            setOnClickListener(this);
            removeView(mFallback);
            mFallback = null;
        }
    }

    private void resetFallbackView() {
        if (mFallback != null) {
            mFallback.reset();
            mFallback.clearSearchResult();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View view = (View) getParent();
        if (!widgetMode) {
            setTranslationX((float) ((view.getPaddingLeft() + (
                    (((view.getWidth() - view.getPaddingLeft()) - view.getPaddingRight()) - (right
                            - left))
                            / 2)) - left));
        }
        int containerTopMargin = 0;
        if (!prefs.getAllAppsSearch()) {
            MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
            containerTopMargin = -(mlp.topMargin + mlp.height);
        }
        offsetTopAndBottom((int) Dy - containerTopMargin);
    }

    @Override
    protected void drawQsb(@NonNull Canvas canvas) {
        if (this.mShadowAlpha > 0) {
            if (this.Dv == null) {
                this.Dv = createShadowBitmap(
                        getResources().getDimension(R.dimen.hotseat_qsb_scroll_shadow_blur_radius),
                        getResources().getDimension(R.dimen.hotseat_qsb_scroll_key_shadow_offset),
                        0, true);
            }
            this.mShadowHelper.paint.setAlpha(this.mShadowAlpha);
            a(this.Dv, canvas);
            this.mShadowHelper.paint.setAlpha(255);
        }
        super.drawQsb(canvas);
    }

    final void setShadowAlpha(int i) {
        i = Utilities.boundToRange(i, 0, 255);
        if (this.mShadowAlpha != i) {
            this.mShadowAlpha = i;
            invalidate();
        }
    }

    protected final boolean dK() {
        if (this.mFallback != null) {
            return false;
        }
        return super.dK();
    }

    protected final void c(SharedPreferences sharedPreferences) {
        if (mUseFallbackSearch) {
            removeFallbackView();
            this.mUseFallbackSearch = false;
            if (this.mUseFallbackSearch) {
                ensureFallbackView();
            }
        }
    }

    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void preDispatchKeyEvent(KeyEvent keyEvent) {

    }

    @Nullable
    @Override
    protected String getClipboardText() {
        return shouldUseFallbackSearch() ? super.getClipboardText() : null;
    }

    @Override
    protected void clearMainPillBg(Canvas canvas) {
        if (!mLowPerformanceMode && mClearBitmap != null) {
            if (mHotseatProgress < 1) {
                drawPill(mClearShadowHelper, mClearBitmap, canvas);
            }
        }
    }

    @Override
    protected void clearPillBg(Canvas canvas, int left, int top, int right) {
        if (!mLowPerformanceMode && mClearBitmap != null) {
            mClearShadowHelper.draw(mClearBitmap, canvas, left, top, right);
        }
    }

    @Override
    public void setContentVisibility(int visibleElements, PropertySetter setter,
            Interpolator interpolator) {
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        boolean hotseatQsbEnabled = prefs.getDockSearchBar() || widgetMode;
        boolean drawerQsbEnabled = prefs.getAllAppsSearch();
        boolean hotseatQsbVisible = (visibleElements & HOTSEAT_SEARCH_BOX) != 0;
        boolean drawerQsbVisible = (visibleElements & ALL_APPS_HEADER) != 0;
        boolean qsbVisible = (hotseatQsbEnabled && hotseatQsbVisible) || (drawerQsbEnabled && drawerQsbVisible);
        float hotseatProgress, micProgress;
        if (!hotseatQsbEnabled) {
            hotseatProgress = 0;
        } else if (!drawerQsbEnabled) {
            hotseatProgress = 1;
        } else {
            hotseatProgress = (visibleElements & ALL_APPS_CONTENT) != 0 ? 0 : 1;
        }
        if (prefs.getAllAppsGlobalSearch()) {
            micProgress = 1f;
        } else {
            micProgress = hotseatProgress;
        }
        setter.setFloat(this, HOTSEAT_PROGRESS, hotseatProgress, LINEAR);
        setter.setViewAlpha(this, qsbVisible ? 1 : 0, interpolator);
        setter.setViewAlpha(mLogoIconView, 1 - hotseatProgress, interpolator);
        setter.setViewAlpha(mHotseatLogoIconView, hotseatProgress, interpolator);
        setter.setViewAlpha(mMicIconView, micProgress, interpolator);
        if (mMicIconView != null) {
            mMicIconView.setVisibility(micProgress > 0 ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
