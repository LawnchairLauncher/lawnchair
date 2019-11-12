/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2017 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.settings.ui;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.XmlRes;
import android.support.v14.preference.SwitchPreference;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceClickListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback;
import android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceRecyclerViewAccessibilityDelegate;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.support.v7.preference.internal.AbstractMultiSelectListPreference;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import ch.deletescape.lawnchair.FakeLauncherKt;
import ch.deletescape.lawnchair.FeedBridge;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.LawnchairPreferencesChangeCallback;
import ch.deletescape.lawnchair.LawnchairUtilsKt;
import ch.deletescape.lawnchair.adaptive.IconShapePreference;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.overrides.ThemedEditTextPreferenceDialogFragmentCompat;
import ch.deletescape.lawnchair.colors.overrides.ThemedListPreferenceDialogFragment;
import ch.deletescape.lawnchair.colors.overrides.ThemedMultiSelectListPreferenceDialogFragmentCompat;
import ch.deletescape.lawnchair.colors.preferences.ColorPickerPreference;
import ch.deletescape.lawnchair.gestures.ui.GesturePreference;
import ch.deletescape.lawnchair.gestures.ui.SelectGestureHandlerFragment;
import ch.deletescape.lawnchair.globalsearch.ui.SearchProviderPreference;
import ch.deletescape.lawnchair.globalsearch.ui.SelectSearchProviderFragment;
import ch.deletescape.lawnchair.iconpack.IconPackManager;
import ch.deletescape.lawnchair.preferences.ResumablePreference;
import ch.deletescape.lawnchair.preferences.SmartspaceEventProvidersFragment;
import ch.deletescape.lawnchair.preferences.SmartspaceEventProvidersPreference;
import ch.deletescape.lawnchair.settings.ui.search.SettingsSearchActivity;
import ch.deletescape.lawnchair.smartspace.OnboardingProvider;
import ch.deletescape.lawnchair.theme.ThemeOverride;
import ch.deletescape.lawnchair.theme.ThemeOverride.ThemeSet;
import ch.deletescape.lawnchair.views.SpringRecyclerView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.ContentWriter.CommitParams;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;
import com.google.android.apps.nexuslauncher.reflection.ReflectionClient;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings activity for Launcher.
 */
public class SettingsActivity extends SettingsBaseActivity implements
        OnPreferenceStartFragmentCallback, OnPreferenceDisplayDialogCallback,
        OnBackStackChangedListener, OnClickListener {

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";

    private static final String ICON_BADGING_PREFERENCE_KEY = "pref_icon_badging";
    /**
     * Hidden field Settings.Secure.NOTIFICATION_BADGING
     */
    public static final String NOTIFICATION_BADGING = "notification_badging";
    /**
     * Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
     */
    private static final String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String SHOW_ACTIONS_PREF = "pref_show_suggested_actions";
    public final static String HIDDEN_ACTIONS_PREF = "pref_hidden_prediction_action_set";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String FEED_THEME_PREF = "pref_feedTheme";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public final static String ALLOW_OVERLAP_PREF = "pref_allowOverlap";
    private final static String BRIDGE_TAG = "tag_bridge";
    private final static String RESET_HIDDEN_ACTIONS_PREF = "pref_reset_hidden_suggested_actions";

    public final static String EXTRA_TITLE = "title";

    public final static String EXTRA_FRAGMENT = "fragment";
    public final static String EXTRA_FRAGMENT_ARGS = "fragmentArgs";

    private boolean isSubSettings;
    protected boolean forceSubSettings = false;

    private boolean hasPreview = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        savedInstanceState = getRelaunchInstanceState(savedInstanceState);

        String fragmentName = getIntent().getStringExtra(EXTRA_FRAGMENT);
        int content = getIntent().getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        isSubSettings = content != 0 || fragmentName != null || forceSubSettings;
        hasPreview = getIntent().getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false);

        boolean showSearch = shouldShowSearch();

        super.onCreate(savedInstanceState);
        getDecorLayout().setHideToolbar(showSearch);
        getDecorLayout().setUseLargeTitle(shouldUseLargeTitle());
        setContentView(showSearch ? R.layout.activity_settings_home : R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment fragment = createLaunchFragment(getIntent());
            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, fragment)
                    .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        updateUpButton();

        if (showSearch) {
            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.setOnClickListener(this);
        }

        if (hasPreview) {
            overrideOpenAnim();
        }

        Utilities.getDevicePrefs(this).edit().putBoolean(OnboardingProvider.PREF_HAS_OPENED_SETTINGS, true).apply();
    }

    @Override
    public void finish() {
        super.finish();

        if (hasPreview) {
            overrideCloseAnim();
        }
    }

    protected Fragment createLaunchFragment(Intent intent) {
        CharSequence title = intent.getCharSequenceExtra(EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }
        String fragment = intent.getStringExtra(EXTRA_FRAGMENT);
        if (fragment != null) {
            return Fragment.instantiate(this, fragment, intent.getBundleExtra(EXTRA_FRAGMENT_ARGS));
        }
        int content = intent.getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        return content != 0
                ? SubSettingsFragment.newInstance(getIntent())
                : new LauncherSettingsFragment();
    }

    protected boolean shouldUseLargeTitle() {
        return !isSubSettings;
    }

    protected boolean shouldShowSearch() {
        return BuildConfig.FEATURE_SETTINGS_SEARCH && !isSubSettings;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (shouldShowSearch()) {
            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.getMenu().clear();
            LawnchairPreferences prefs = Utilities.getLawnchairPrefs(this);
            if (prefs.getEnableFools()) {
                toolbar.inflateMenu(R.menu.menu_toggle_fools);
                MenuItem foolsItem = toolbar.getMenu().findItem(R.id.action_toggle_fools);
                foolsItem.setTitle(prefs.getNoFools() ? "AFD / OFF" : "AFD / ON");
            }
            toolbar.inflateMenu(R.menu.menu_settings);
            ActionMenuView menuView = null;
            int count = toolbar.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof ActionMenuView) {
                    menuView = (ActionMenuView) child;
                    break;
                }
            }
            if (menuView != null) {
                menuView.getOverflowIcon()
                        .setTint(ColorEngine.getInstance(this).getAccent());
            }
            if (!BuildConfig.APPLICATION_ID.equals(resolveDefaultHome())) {
                toolbar.inflateMenu(R.menu.menu_change_default_home);
            }
            toolbar.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.action_toggle_fools:
                        prefs.beginBlockingEdit();
                        prefs.setNoFools(!prefs.getNoFools());
                        prefs.endBlockingEdit();
                    case R.id.action_change_default_home:
                        FakeLauncherKt.changeDefaultHome(this);
                        break;
                    case R.id.action_restart_lawnchair:
                        Utilities.killLauncher();
                        break;
                    case R.id.action_dev_options:
                        Intent intent = new Intent(this, SettingsActivity.class);
                        intent.putExtra(SettingsActivity.SubSettingsFragment.TITLE,
                                getString(R.string.developer_options_title));
                        intent.putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID,
                                R.xml.lawnchair_dev_options_preference);
                        intent.putExtra(SettingsBaseActivity.EXTRA_FROM_SETTINGS, true);
                        startActivity(intent);
                        break;
                    default:
                        return false;
                }
                return true;
            });
        }
    }

    private String resolveDefaultHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager()
                .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        } else {
            return null;
        }
    }

    @Override
    public void onColorChange(@NotNull ColorEngine.ResolveInfo resolveInfo) {
        super.onColorChange(resolveInfo);

        if (resolveInfo.getKey().equals(ColorEngine.Resolvers.ACCENT) && shouldShowSearch()) {
            Drawable search = getResources().getDrawable(R.drawable.ic_settings_search, null);
            search.setTint(resolveInfo.getColor());

            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.setNavigationIcon(search);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.search_action_bar) {
            startActivity(new Intent(this, SettingsSearchActivity.class));
        }
    }

    @NotNull
    @Override
    protected ThemeSet getThemeSet() {
        if (hasPreview) {
            return new ThemeOverride.SettingsTransparent();
        } else {
            return super.getThemeSet();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller,
            Preference preference) {
        Fragment fragment;
        if (preference instanceof SubPreference) {
            ((SubPreference) preference).start(this);
            return true;
        } else if (preference instanceof ColorPickerPreference) {
            ((ColorPickerPreference) preference).showDialog(getSupportFragmentManager());
            return true;
        } else {
            fragment = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        }
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).show(getSupportFragmentManager(), preference.getKey());
        } else {
            startFragment(this, preference.getFragment(), preference.getExtras(), preference.getTitle());
        }
        return true;
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentCompat caller,
            Preference pref) {
        if (ENABLE_MINUS_ONE_PREF.equals(pref.getKey())) {
            InstallFragment fragment = new InstallFragment();
            fragment.show(getSupportFragmentManager(), BRIDGE_TAG);
            return true;
        }
        return false;
    }

    private void updateUpButton() {
        updateUpButton(isSubSettings || getSupportFragmentManager().getBackStackEntryCount() != 0);
    }

    private void updateUpButton(boolean enabled) {
        if (getSupportActionBar() == null) {
            return;
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackStackChanged() {
        updateUpButton();
    }

    public abstract static class BaseFragment extends PreferenceFragmentCompat {

        private static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

        private HighlightablePreferenceGroupAdapter mAdapter;
        private boolean mPreferenceHighlighted = false;

        private RecyclerView.Adapter mCurrentRootAdapter;
        private boolean mIsDataSetObserverRegistered = false;
        private RecyclerView.AdapterDataObserver mDataSetObserver =
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount,
                            Object payload) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeInserted(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeRemoved(int positionStart, int itemCount) {
                        onDataSetChanged();
                    }

                    @Override
                    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                        onDataSetChanged();
                    }
                };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }
        }

        public void highlightPreferenceIfNeeded() {
            if (!isAdded()) {
                return;
            }
            if (mAdapter != null) {
                mAdapter.requestHighlight(Objects.requireNonNull(getView()), getListView());
            }
        }

        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                Bundle savedInstanceState) {
            RecyclerView recyclerView = (RecyclerView) inflater
                    .inflate(getRecyclerViewLayoutRes(), parent, false);
            if (recyclerView instanceof SpringRecyclerView) {
                ((SpringRecyclerView) recyclerView).setShouldTranslateSelf(false);
            }

            recyclerView.setLayoutManager(onCreateLayoutManager());
            recyclerView.setAccessibilityDelegateCompat(
                    new PreferenceRecyclerViewAccessibilityDelegate(recyclerView));

            return recyclerView;
        }

        abstract protected int getRecyclerViewLayoutRes();

        @Override
        public void setDivider(Drawable divider) {
            super.setDivider(null);
        }

        @Override
        public void setDividerHeight(int height) {
            super.setDividerHeight(0);
        }

        @Override
        protected Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
            final Bundle arguments = getActivity().getIntent().getExtras();
            mAdapter = new HighlightablePreferenceGroupAdapter(preferenceScreen,
                    arguments == null
                            ? null : arguments.getString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY),
                    mPreferenceHighlighted);
            return mAdapter;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            if (mAdapter != null) {
                outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mAdapter.isHighlightRequested());
            }
        }

        protected void onDataSetChanged() {
            highlightPreferenceIfNeeded();
        }

        public int getInitialExpandedChildCount() {
            return -1;
        }

        @Override
        public void onResume() {
            super.onResume();
            highlightPreferenceIfNeeded();

            dispatchOnResume(getPreferenceScreen());
        }

        public void dispatchOnResume(PreferenceGroup group) {
            int count = group.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference preference = group.getPreference(i);

                if (preference instanceof ResumablePreference) {
                    ((ResumablePreference) preference).onResume();
                }

                if (preference instanceof PreferenceGroup) {
                    dispatchOnResume((PreferenceGroup) preference);
                }
            }
        }


        @Override
        protected void onBindPreferences() {
            registerObserverIfNeeded();
        }

        @Override
        protected void onUnbindPreferences() {
            unregisterObserverIfNeeded();
        }

        public void registerObserverIfNeeded() {
            if (!mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                }
                mCurrentRootAdapter = getListView().getAdapter();
                mCurrentRootAdapter.registerAdapterDataObserver(mDataSetObserver);
                mIsDataSetObserverRegistered = true;
                onDataSetChanged();
            }
        }

        public void unregisterObserverIfNeeded() {
            if (mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                    mCurrentRootAdapter = null;
                }
                mIsDataSetObserverRegistered = false;
            }
        }

        void onPreferencesAdded(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);

                if (preference instanceof ControlledPreference) {
                    PreferenceController controller = ((ControlledPreference) preference)
                            .getController();
                    if (controller != null) {
                        if (!controller.onPreferenceAdded(preference)) {
                            i--;
                            continue;
                        }
                    }
                }

                if (preference instanceof PreferenceGroup) {
                    onPreferencesAdded((PreferenceGroup) preference);
                }

            }
        }
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends BaseFragment {

        private boolean mShowDevOptions;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mShowDevOptions = Utilities.getLawnchairPrefs(getActivity()).getDeveloperOptionsEnabled();
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.lawnchair_preferences);
            onPreferencesAdded(getPreferenceScreen());
        }

        @Override
        public void onResume() {
            super.onResume();
            boolean dev = Utilities.getLawnchairPrefs(getActivity()).getDeveloperOptionsEnabled();
            if (dev != mShowDevOptions) {
                getActivity().recreate();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference.getKey() != null && "about".equals(preference.getKey())) {
                startActivity(new Intent(getActivity(), SettingsAboutActivity.class));
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return BuildConfig.FEATURE_SETTINGS_SEARCH ? R.layout.preference_home_recyclerview
                    : R.layout.preference_dialog_recyclerview;
        }
    }

    public static class SubSettingsFragment extends BaseFragment implements
            Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        public static final String TITLE = "title";
        public static final String CONTENT_RES_ID = "content_res_id";
        public static final String HAS_PREVIEW = "has_preview";

        private IconBadgingObserver mIconBadgingObserver;

        private Context mContext;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mContext = getActivity();

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            if (getContent() == R.xml.lawnchair_desktop_preferences) {
                if (getResources().getBoolean(R.bool.notification_badging_enabled)) {
                    ButtonPreference iconBadgingPref =
                            (ButtonPreference) findPreference(ICON_BADGING_PREFERENCE_KEY);
                    // Listen to system notification badge settings while this UI is active.
                    mIconBadgingObserver = new IconBadgingObserver(
                            iconBadgingPref, getActivity().getContentResolver(),
                            getFragmentManager());
                    mIconBadgingObserver
                            .register(NOTIFICATION_BADGING, NOTIFICATION_ENABLED_LISTENERS);
                }
            } else if (getContent() == R.xml.lawnchair_theme_preferences) {
                Preference resetIconsPreference = findPreference("pref_resetCustomIcons");
                resetIconsPreference.setOnPreferenceClickListener(preference -> {
                    new SettingsActivity.ResetIconsConfirmation()
                            .show(getFragmentManager(), "reset_icons");
                    return true;
                });
            } else if (getContent() == R.xml.lawnchair_app_drawer_preferences) {
                SwitchPreference showActionsPref = (SwitchPreference) findPreference(SHOW_ACTIONS_PREF);
                Preference resetHiddenActionsPref = findPreference(RESET_HIDDEN_ACTIONS_PREF);
                resetHiddenActionsPref.setEnabled(showActionsPref.isChecked());
                showActionsPref.setOnPreferenceClickListener(preference -> {
                    resetHiddenActionsPref.setEnabled(((SwitchPreference) preference).isChecked());
                    return false;
                });
                resetHiddenActionsPref.setOnPreferenceClickListener(preference -> {
                    Utilities.getLawnchairPrefs(mContext).getSharedPrefs().edit()
                            .remove(HIDDEN_ACTIONS_PREF).apply();
                    return true;
                });
                findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
            } else if (getContent() == R.xml.lawnchair_dev_options_preference) {
                findPreference("kill").setOnPreferenceClickListener(this);
                findPreference("addSettingsShortcut").setOnPreferenceClickListener(this);
                findPreference("currentWeatherProvider").setSummary(
                        Utilities.getLawnchairPrefs(mContext).getWeatherProvider());
                findPreference("appInfo").setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(getContent());
            onPreferencesAdded(getPreferenceScreen());
        }

        private int getContent() {
            return getArguments().getInt(CONTENT_RES_ID);
        }

        @Override
        public void onResume() {
            super.onResume();
            setActivityTitle();

            if (getContent() == R.xml.lawnchair_integration_preferences) {
                SwitchPreference minusOne = (SwitchPreference) findPreference(
                        ENABLE_MINUS_ONE_PREF);
                if (minusOne != null && !FeedBridge.Companion.getInstance(getActivity())
                        .isInstalled()) {
                    minusOne.setChecked(false);
                }
            }
        }

        protected void setActivityTitle() {
            getActivity().setTitle(getArguments().getString(TITLE));
        }

        @Override
        public void onDestroy() {
            if (mIconBadgingObserver != null) {
                mIconBadgingObserver.unregister();
                mIconBadgingObserver = null;
            }
            super.onDestroy();
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            final DialogFragment f;
            if (preference instanceof GridSizePreference) {
                f = GridSizeDialogFragmentCompat.Companion.newInstance(preference.getKey());
            } else if (preference instanceof SingleDimensionGridSizePreference) {
                f = SingleDimensionGridSizeDialogFragmentCompat.Companion
                        .newInstance(preference.getKey());
            } else if (preference instanceof GesturePreference) {
                f = SelectGestureHandlerFragment.Companion
                        .newInstance((GesturePreference) preference);
            } else if (preference instanceof SearchProviderPreference) {
                f = SelectSearchProviderFragment.Companion
                        .newInstance((SearchProviderPreference) preference);
            } else if (preference instanceof PreferenceDialogPreference) {
                f = PreferenceScreenDialogFragment.Companion
                        .newInstance((PreferenceDialogPreference) preference);
            } else if (preference instanceof IconShapePreference) {
                f = ((IconShapePreference) preference).createDialogFragment();
            } else if (preference instanceof ListPreference) {
                Log.d("success", "onDisplayPreferenceDialog: yay");
                f = ThemedListPreferenceDialogFragment.Companion.newInstance(preference.getKey());
            } else if (preference instanceof EditTextPreference) {
                f = ThemedEditTextPreferenceDialogFragmentCompat.Companion
                        .newInstance(preference.getKey());
            } else if (preference instanceof AbstractMultiSelectListPreference) {
                f = ThemedMultiSelectListPreferenceDialogFragmentCompat.Companion
                        .newInstance(preference.getKey());
            } else if (preference instanceof SmartspaceEventProvidersPreference) {
                f = SmartspaceEventProvidersFragment.Companion.newInstance(preference.getKey());
            } else if (preference instanceof WeatherIconPackPreference) {
                f = WeatherIconPackDialogFragment.Companion.newInstance();
            } else {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        }

        public static SubSettingsFragment newInstance(SubPreference preference) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, (String) preference.getTitle());
            b.putInt(CONTENT_RES_ID, preference.getContent());
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(Intent intent) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, intent.getStringExtra(TITLE));
            b.putInt(CONTENT_RES_ID, intent.getIntExtra(CONTENT_RES_ID, 0));
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(String title, @XmlRes int content) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            switch (preference.getKey()) {
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        ReflectionClient.getInstance(getContext()).setEnabled(true);
                        return true;
                    }
                    SuggestionConfirmationFragment confirmationFragment = new SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;
                case ENABLE_MINUS_ONE_PREF:
                    if (FeedBridge.Companion.getInstance(getActivity()).isInstalled()) {
                        return true;
                    }
                    FragmentManager fm = getFragmentManager();
                    if (fm.findFragmentByTag(BRIDGE_TAG) == null) {
                        InstallFragment fragment = new InstallFragment();
                        fragment.show(fm, BRIDGE_TAG);
                    }
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case "kill":
                    Utilities.killLauncher();
                    break;
                case "addSettingsShortcut":
                    Utilities.pinSettingsShortcut(getActivity());
                    break;
                case "appInfo":
                    ComponentName componentName = new ComponentName(getActivity(),
                            LawnchairLauncher.class);
                    LauncherAppsCompat.getInstance(getContext())
                            .showAppDetailsForProfile(componentName,
                                    android.os.Process.myUserHandle());
                    break;
                case "screenshot":
                    final Context context = getActivity();
                    LawnchairLauncher.Companion.takeScreenshot(getActivity(), new Handler(),
                            new Function1<Uri, Unit>() {
                                @Override
                                public Unit invoke(Uri uri) {
                                    try {
                                        Bitmap bitmap = MediaStore.Images.Media
                                                .getBitmap(context.getContentResolver(), uri);
                                        ImageView imageView = new ImageView(context);
                                        imageView.setImageBitmap(bitmap);
                                        new AlertDialog.Builder(context)
                                                .setTitle("Screenshot")
                                                .setView(imageView)
                                                .show();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return null;
                                }
                            });
                    break;
            }
            return false;
        }

        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_insettable_recyclerview;
        }
    }

    public static class DialogSettingsFragment extends SubSettingsFragment {

        @Override
        protected void setActivityTitle() {

        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_dialog_recyclerview;
        }

        public static DialogSettingsFragment newInstance(String title, @XmlRes int content) {
            DialogSettingsFragment fragment = new DialogSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements
            DialogInterface.OnClickListener {

        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragmentCompat) {
                Preference preference = ((PreferenceFragmentCompat) getTargetFragment())
                        .findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
            ReflectionClient.getInstance(getContext()).setEnabled(false);
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }


        @Override
        public void onStart() {
            super.onStart();
            LawnchairUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }
    }

    /**
     * Content observer which listens for system badging setting changes, and updates the launcher
     * badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends SettingsObserver.Secure
            implements Preference.OnPreferenceClickListener {

        private final ButtonPreference mBadgingPref;
        private final ContentResolver mResolver;
        private final FragmentManager mFragmentManager;
        private boolean serviceEnabled = true;

        public IconBadgingObserver(ButtonPreference badgingPref, ContentResolver resolver,
                FragmentManager fragmentManager) {
            super(resolver);
            mBadgingPref = badgingPref;
            mResolver = resolver;
            mFragmentManager = fragmentManager;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            int summary = enabled ? R.string.icon_badging_desc_on : R.string.icon_badging_desc_off;

            if (enabled) {
                // Check if the listener is enabled or not.
                String enabledListeners =
                        Settings.Secure.getString(mResolver, NOTIFICATION_ENABLED_LISTENERS);
                ComponentName myListener =
                        new ComponentName(mBadgingPref.getContext(), NotificationListener.class);
                serviceEnabled = enabledListeners != null &&
                        (enabledListeners.contains(myListener.flattenToString()) ||
                                enabledListeners.contains(myListener.flattenToShortString()));
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access;
                }
            }
            mBadgingPref.setWidgetFrameVisible(!serviceEnabled);
            mBadgingPref.setOnPreferenceClickListener(
                    serviceEnabled && Utilities.ATLEAST_OREO ? null : this);
            mBadgingPref.setSummary(summary);

        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (!Utilities.ATLEAST_OREO && serviceEnabled) {
                ComponentName cn = new ComponentName(preference.getContext(),
                        NotificationListener.class);
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(":settings:fragment_args_key", cn.flattenToString());
                preference.getContext().startActivity(intent);
            } else {
                new SettingsActivity.NotificationAccessConfirmation()
                        .show(mFragmentManager, "notification_access");
            }
            return true;
        }
    }

    public static class NotificationAccessConfirmation
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                    context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.title_missing_notification_access)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.title_change_settings, this)
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            LawnchairUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(":settings:fragment_args_key", cn.flattenToString());
            getActivity().startActivity(intent);
        }
    }

    public static class ResetIconsConfirmation
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.reset_custom_icons)
                    .setMessage(R.string.reset_custom_icons_confirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, this)
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            LawnchairUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            Context context = getContext();

            // Clear custom app icons
            LawnchairPreferences prefs = Utilities.getLawnchairPrefs(context);
            Set<ComponentKey> toUpdateSet = prefs.getCustomAppIcon().toMap().keySet();
            prefs.beginBlockingEdit();
            prefs.getCustomAppIcon().clear();
            prefs.endBlockingEdit();

            // Clear custom shortcut icons
            ContentWriter writer = new ContentWriter(context, new CommitParams(null, null));
            writer.put(Favorites.CUSTOM_ICON, (byte[]) null);
            writer.put(Favorites.CUSTOM_ICON_ENTRY, (String) null);
            writer.commit();

            // Reload changes
            LawnchairUtilsKt.reloadIconsFromComponents(context, toUpdateSet);
            LawnchairPreferencesChangeCallback prefsCallback = prefs.getOnChangeCallback();
            if (prefsCallback != null) {
                prefsCallback.reloadAll();
            }
        }
    }

    public static class InstallFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.bridge_missing_title)
                    .setMessage(R.string.bridge_missing_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.get_lawnfeed, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Utilities.openURLinBrowser(getContext(), BuildConfig.BRIDGE_DOWNLOAD_URL);
                        }
                    })
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            LawnchairUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }
    }

    public static void startFragment(Context context, String fragment, int title) {
        startFragment(context, fragment, null, context.getString(title));
    }

    public static void startFragment(Context context, String fragment, @Nullable Bundle args) {
        startFragment(context, fragment, args, null);
    }

    public static void startFragment(Context context, String fragment, @Nullable Bundle args,
            @Nullable CharSequence title) {
        context.startActivity(createFragmentIntent(context, fragment, args, title));
    }

    @NotNull
    private static Intent createFragmentIntent(Context context, String fragment,
            @Nullable Bundle args, @Nullable CharSequence title) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA_FRAGMENT, fragment);
        intent.putExtra(EXTRA_FRAGMENT_ARGS, args);
        if (title != null) {
            intent.putExtra(EXTRA_TITLE, title);
        }
        return intent;
    }
}