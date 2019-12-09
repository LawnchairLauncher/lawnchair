package com.google.android.apps.nexuslauncher.qsb;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.ColorEngine.ResolveInfo;
import ch.deletescape.lawnchair.colors.ColorEngine.Resolvers;
import ch.deletescape.lawnchair.globalsearch.SearchProvider;
import ch.deletescape.lawnchair.globalsearch.SearchProviderController;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.PackageManagerHelper;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class HotseatQsbWidget extends AbstractQsbLayout implements o,
        LawnchairPreferences.OnPreferenceChangeListener, ColorEngine.OnColorChangeListener {

    public static final String KEY_DOCK_COLORED_GOOGLE = "pref_dockColoredGoogle";
    public static final String KEY_DOCK_SEARCHBAR = "pref_dockSearchBar";
    public static final String KEY_DOCK_HIDE = "pref_hideHotseat";
    private boolean mIsGoogleColored;
    private final k Ds;
    private boolean widgetMode;

    static /* synthetic */ void a(HotseatQsbWidget hotseatQsbWidget) {
        if (hotseatQsbWidget.mIsGoogleColored != hotseatQsbWidget.isGoogleColored()) {
            hotseatQsbWidget.mIsGoogleColored = !hotseatQsbWidget.mIsGoogleColored;
            hotseatQsbWidget.dM();
        }
    }

    public HotseatQsbWidget(Context context) {
        this(context, null);
    }

    public HotseatQsbWidget(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public HotseatQsbWidget(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.Ds = k.getInstance(context);
        setOnClickListener(this);
    }

    public void setWidgetMode(boolean widgetMode) {
        this.widgetMode = widgetMode;
    }

    protected void onAttachedToWindow() {
        Utilities.getLawnchairPrefs(getContext())
                .addOnPreferenceChangeListener(this, KEY_DOCK_COLORED_GOOGLE, KEY_DOCK_SEARCHBAR);
        ColorEngine.getInstance(getContext())
                .addColorChangeListeners(this, Resolvers.HOTSEAT_QSB_BG);
        dW();
        super.onAttachedToWindow();
        this.Ds.a((o) this);
        dH();
        setOnFocusChangeListener(this.mActivity.mFocusHandler);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Utilities.getLawnchairPrefs(getContext())
                .removeOnPreferenceChangeListener(this, KEY_DOCK_COLORED_GOOGLE,
                        KEY_DOCK_SEARCHBAR);
        ColorEngine.getInstance(getContext())
                .removeColorChangeListeners(this, Resolvers.HOTSEAT_QSB_BG);
        this.Ds.b(this);
    }

    @Override
    public void onColorChange(@NotNull ResolveInfo resolveInfo) {
        if (resolveInfo.getKey().equals(Resolvers.HOTSEAT_QSB_BG)) {
            ay(resolveInfo.getColor());
            az(ColorUtils.setAlphaComponent(Dc, Ds.micOpacity()));
        }
    }

    @Override
    public void onValueChanged(@NotNull String key, @NotNull LawnchairPreferences prefs,
            boolean force) {
        if (key.equals(KEY_DOCK_COLORED_GOOGLE)) {
            mIsGoogleColored = isGoogleColored();
            dM();
        } else if (!widgetMode && (key.equals(KEY_DOCK_SEARCHBAR) || key.equals(KEY_DOCK_HIDE))) {
            boolean visible = prefs.getDockSearchBar() && !prefs.getDockHide();
            setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected Drawable getIcon() {
        return getIcon(mIsGoogleColored);
    }

    @Override
    protected Drawable getMicIcon() {
        return getMicIcon(mIsGoogleColored);
    }

    public final void dM() {
        removeAllViews();
        setColors();
        dW();
        dy();
        dH();
    }

    private void dW() {
        y(false);
    }

    private void y(boolean z) {
        View findViewById = findViewById(R.id.g_icon);
        if (findViewById != null) {
            findViewById.setAlpha(1.0f);
        }
    }

    protected void onWindowVisibilityChanged(int i) {
        super.onWindowVisibilityChanged(i);
        if (i != 0) {
            y(false);
        }
    }

    public final int dL() {
        return 0;
    }

    private void setColors() {
        View.inflate(new ContextThemeWrapper(getContext(),
                        mIsGoogleColored ? R.style.HotseatQsbTheme_Colored : R.style.HotseatQsbTheme),
                R.layout.qsb_hotseat_content, this);
        /*int colorRes;
        if (isDarkBar()) {
            colorRes = R.color.qsb_background_hotseat_dark;
        } else {
            colorRes = mIsGoogleColored ? R.color.qsb_background_hotseat_white : R.color.qsb_background_hotseat_default;
        }
        ay(getResources().getColor(colorRes));
        az(ColorUtils.setAlphaComponent(Dc, Ds.micOpacity()));*/
    }

    public boolean isGoogleColored() {
        return isGoogleColored(getContext());
    }

    public static boolean isGoogleColored(Context context) {
        if (Utilities.getLawnchairPrefs(context).getDockColoredGoogle()) {
            return true;
        }
        WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(context).getWallpaperInfo();
        return wallpaperInfo != null && wallpaperInfo.getComponent().flattenToString()
                .equals(context.getString(R.string.default_live_wallpaper));
    }

    protected final int aA(int i) {
        if (widgetMode) {
            return i;
        }
        View view = this.mActivity.getHotseat().getLayout();
        return (i - view.getPaddingLeft()) - view.getPaddingRight();
    }

    private void doOnClick() {
        SearchProviderController controller = SearchProviderController.Companion
                .getInstance(mActivity);
        if (controller.isGoogle()) {
            startGoogleSearch();
        } else {
            controller.getSearchProvider().startSearch(intent -> {
                mActivity.openQsb();
                getContext().startActivity(intent, ActivityOptionsCompat.makeClipRevealAnimation(this, 0, 0, getWidth(), getHeight()).toBundle());
                return null;
            });
        }
    }

    private void startGoogleSearch() {
        final ConfigBuilder f = new ConfigBuilder(this, false);
        if (!forceFallbackSearch() && mActivity.getGoogleNow()
                .startSearch(f.build(), f.getExtras())) {
            SharedPreferences devicePrefs = Utilities.getDevicePrefs(getContext());
            devicePrefs.edit().putInt("key_hotseat_qsb_tap_count",
                    devicePrefs.getInt("key_hotseat_qsb_tap_count", 0) + 1).apply();
            mActivity.playQsbAnimation();
        } else {
            getContext().sendOrderedBroadcast(getSearchIntent(), null,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (getResultCode() == 0) {
                                fallbackSearch(
                                        "com.google.android.googlequicksearchbox.TEXT_ASSIST");
                            } else {
                                mActivity.playQsbAnimation();
                            }
                        }
                    }, null, 0, null, null);
        }
    }

    private boolean forceFallbackSearch() {
        return !PackageManagerHelper.isAppEnabled(getContext().getPackageManager(),
                "com.google.android.apps.nexuslauncher", 0);
    }

    @Override
    protected void noGoogleAppSearch() {
        final Intent searchIntent = new Intent("com.google.android.apps.searchlite.WIDGET_ACTION")
                .setComponent(ComponentName.unflattenFromString(
                        "com.google.android.apps.searchlite/.ui.SearchActivity"))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("showKeyboard", true)
                .putExtra("contentType", 12);

        final Context context = getContext();
        final PackageManager pm = context.getPackageManager();

        if (pm.queryIntentActivities(searchIntent, 0).isEmpty()) {
            try {
                context.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
                mActivity.openQsb();
            } catch (ActivityNotFoundException ignored) {
                try {
                    getContext().getPackageManager().getPackageInfo(GOOGLE_QSB, 0);
                    LauncherAppsCompat.getInstance(getContext())
                            .showAppDetailsForProfile(
                                    new ComponentName(GOOGLE_QSB, ".SearchActivity"),
                                    Process.myUserHandle());
                } catch (PackageManager.NameNotFoundException ignored2) {
                }
            }
        } else {
            mActivity.openQsb().addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    context.startActivity(searchIntent);
                }
            });
        }
    }

    public final boolean dI() {
        return false;
    }

    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        setTranslationY((float) (-getBottomMargin(this.mActivity, widgetMode)));
    }

    public void setInsets(Rect rect) {
        super.setInsets(rect);
        if (!widgetMode) {
            setVisibility(
                    mActivity.getDeviceProfile().isVerticalBarLayout() ? View.GONE : View.VISIBLE);
        }
    }

    public void onClick(View view) {
        super.onClick(view);
        if (view == this) {
            startSearch("", this.Di);
        }
    }

    protected final Intent createSettingsBroadcast() {
        SearchProviderController controller = SearchProviderController.Companion.getInstance(mActivity);
        SearchProvider provider = controller.getSearchProvider();
        if (provider.isBroadcast()) {
            Intent intent = provider.getSettingsIntent();
            List queryBroadcastReceivers = getContext().getPackageManager()
                    .queryBroadcastReceivers(intent, 0);
            if (!(queryBroadcastReceivers == null || queryBroadcastReceivers.isEmpty())) {
                return intent;
            }
        }
        return null;
    }

    protected final Intent createSettingsIntent() {
        SearchProviderController controller = SearchProviderController.Companion.getInstance(mActivity);
        SearchProvider provider = controller.getSearchProvider();
        return provider.isBroadcast() ? null : provider.getSettingsIntent();
    }

    public final void l(String str) {
        startSearch(str, 0);
    }

    private Intent getSearchIntent() {
        int[] array = new int[2];
        getLocationInWindow(array);
        Rect rect = new Rect(0, 0, getWidth(), getHeight());
        rect.offset(array[0], array[1]);
        rect.inset(getPaddingLeft(), getPaddingTop());
        return ConfigBuilder.getSearchIntent(rect, findViewById(R.id.g_icon), mMicIconView);
    }

    @Override
    public final void startSearch(String str, int i) {
        doOnClick();
    }

    public static int getBottomMargin(Launcher launcher, boolean widgetMode) {
        DeviceProfile profile = launcher.getDeviceProfile();
        Resources resources = launcher.getResources();
        int resBottomMargin = resources.getDimensionPixelSize(R.dimen.hotseat_qsb_bottom_margin);

        if (widgetMode) {
            int cellHeight = profile.getCellSize().y;
            int widgetHeight = resources.getDimensionPixelSize(R.dimen.qsb_widget_height);
            int bottomMargin = Math.round((cellHeight - widgetHeight) / 2.0f);
            return Math.max(bottomMargin, resBottomMargin);
        }

        Rect rect = profile.getInsets();
        int minBottom = rect.bottom + resBottomMargin;
        Rect hotseatLayoutPadding = profile.getHotseatLayoutPadding();

        int hotseatTop = profile.hotseatBarSizePx + rect.bottom;
        int hotseatIconsTop = hotseatTop - hotseatLayoutPadding.top;

        float f = ((hotseatIconsTop - hotseatLayoutPadding.bottom) + (profile.iconSizePx * 0.92f)) / 2.0f;
        float f2 = ((float) rect.bottom) * 0.67f;
        int bottomMargin = Math.round(f2 + (
                ((((((float) hotseatTop) - f2) - f) - resources
                        .getDimension(R.dimen.qsb_widget_height))
                        - ((float) profile.verticalDragHandleSizePx)) / 2.0f));

        return Math.max(minBottom, bottomMargin);
    }

    @Nullable
    @Override
    protected String getClipboardText() {
        return null;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        mActivity.findViewById(R.id.scrim_view).invalidate();
    }
}
