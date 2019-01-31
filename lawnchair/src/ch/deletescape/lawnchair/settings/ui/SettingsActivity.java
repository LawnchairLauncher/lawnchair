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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceRecyclerViewAccessibilityDelegate;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.support.v7.preference.internal.AbstractMultiSelectListPreference;
import android.support.v7.view.menu.BaseMenuPresenter;
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
import ch.deletescape.lawnchair.LawnchairUtilsKt;
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
import ch.deletescape.lawnchair.settings.ui.search.SettingsSearchActivity;
import ch.deletescape.lawnchair.theme.ThemeOverride;
import ch.deletescape.lawnchair.theme.ThemeOverride.ThemeSet;
import ch.deletescape.lawnchair.views.SpringRecyclerView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;
import com.google.android.apps.nexuslauncher.reflection.ReflectionClient;
import java.io.IOException;
import java.util.Objects;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

/**
 * Settings activity for Launcher.
 */
public class SettingsActivity extends SettingsBaseActivity implements
        OnPreferenceStartFragmentCallback, OnBackStackChangedListener, OnClickListener {

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
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String FEED_THEME_PREF = "pref_feedTheme";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    private final static String BRIDGE_TAG = "tag_bridge";

    private boolean isSubSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        savedInstanceState = getRelaunchInstanceState(savedInstanceState);

        int content = getIntent().getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        isSubSettings = content != 0;

        boolean showSearch = shouldShowSearch();

        super.onCreate(savedInstanceState);
        getDecorLayout().setHideToolbar(showSearch);
        getDecorLayout().setUseLargeTitle(shouldUseLargeTitle());
        setContentView(showSearch ? R.layout.activity_settings_home : R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment fragment = createLaunchFragment(content);
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
    }

    protected Fragment createLaunchFragment(int content) {
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
            toolbar.inflateMenu(R.menu.menu_restart_lawnchair);
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
                    case R.id.action_change_default_home:
                        FakeLauncherKt.changeDefaultHome(this);
                        break;
                    case R.id.action_restart_lawnchair:
                        Utilities.killLauncher();
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
    public void onColorChange(String resolver, int color, int foregroundColor) {
        super.onColorChange(resolver, color, foregroundColor);

        if (resolver.equals(ColorEngine.Resolvers.ACCENT) && shouldShowSearch()) {
            Drawable search = getResources().getDrawable(R.drawable.ic_settings_search, null);
            search.setTint(color);

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
        if (getIntent().getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false)) {
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
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            setTitle(preference.getTitle());
            transaction
                    .setCustomAnimations(R.animator.fly_in, R.animator.fade_out, R.animator.fade_in,
                            R.animator.fly_out);
            transaction.replace(R.id.content, fragment);
            transaction.addToBackStack("PreferenceFragment");
            transaction.commit();
        }
        return true;
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
            SpringRecyclerView recyclerView = (SpringRecyclerView) inflater
                    .inflate(getRecyclerViewLayoutRes(), parent, false);
            recyclerView.setShouldTranslateSelf(false);

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

                if (preference instanceof PreferenceGroup) {
                    onPreferencesAdded((PreferenceGroup) preference);
                    continue;
                }

                if (!(preference instanceof ControlledPreference)) {
                    continue;
                }

                PreferenceController controller = ((ControlledPreference) preference)
                        .getController();
                if (controller != null) {
                    if (!controller.onPreferenceAdded(preference)) {
                        i--;
                    }
                }
            }
        }
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends BaseFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
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
            getActivity().setTitle(R.string.derived_app_name);
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
                    : R.layout.preference_spring_recyclerview;
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
                IconPackManager ipm = IconPackManager.Companion.getInstance(mContext);
                Preference packMaskingPreference = findPreference("pref_iconPackMasking");
                PreferenceGroup parent = packMaskingPreference.getParent();
                Utilities.getLawnchairPrefs(mContext).addOnPreferenceChangeListener(
                        "pref_iconPacks", (key, prefs, force) -> {
                            if (!ipm.maskSupported()) {
                                parent.removePreference(packMaskingPreference);
                            } else if (!force) {
                                parent.addPreference(packMaskingPreference);
                            }
                        });
            } else if (getContent() == R.xml.lawnchair_app_drawer_preferences) {
                findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
            } else if (getContent() == R.xml.lawnchair_dev_options_preference) {
                findPreference("kill").setOnPreferenceClickListener(this);
                findPreference("crashLauncher").setOnPreferenceClickListener(this);
                findPreference("addSettingsShortcut").setOnPreferenceClickListener(this);
                findPreference("currentWeatherProvider").setSummary(
                        Utilities.getLawnchairPrefs(mContext).getWeatherProvider());
                findPreference("appInfo").setOnPreferenceClickListener(this);
                findPreference("screenshot").setOnPreferenceClickListener(this);
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
            getActivity().setTitle(getArguments().getString(TITLE));

            if (getContent() == R.xml.lawnchair_desktop_preferences) {
                SwitchSubPreference minusOne = (SwitchSubPreference) findPreference(
                        ENABLE_MINUS_ONE_PREF);
                if (minusOne != null && !FeedBridge.Companion.getInstance(getActivity())
                        .isInstalled()) {
                    minusOne.setChecked(false);
                }
            }
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
            } else if (preference instanceof ListPreference) {
                Log.d("success", "onDisplayPreferenceDialog: yay");
                f = ThemedListPreferenceDialogFragment.Companion.newInstance(preference.getKey());
            } else if (preference instanceof EditTextPreference) {
                f = ThemedEditTextPreferenceDialogFragmentCompat.Companion
                        .newInstance(preference.getKey());
            } else if (preference instanceof AbstractMultiSelectListPreference) {
                f = ThemedMultiSelectListPreferenceDialogFragmentCompat.Companion
                        .newInstance(preference.getKey());
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
                case "crashLauncher":
                    throw new RuntimeException("Triggered from developer options");
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

    public static class InstallFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.bridge_missing_title)
                    .setMessage(R.string.bridge_missing_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            LawnchairUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }
    }
}