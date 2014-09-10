package com.android.launcher3.list;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.OverviewSettingsPanel;
import com.android.launcher3.AppsCustomizePagedView;
import com.android.launcher3.R;

import com.android.launcher3.settings.SettingsProvider;
import android.view.View.OnClickListener;
import android.content.SharedPreferences;

public class SettingsPinnedHeaderAdapter extends PinnedHeaderListAdapter {
    private static final int PARTITION_TAG = 0;
    private static final int POSITION_TAG = 1;

    private Launcher mLauncher;
    private Context mContext;

    class SettingsPosition {
        int partition = 0;
        int position = 0;

        SettingsPosition (int partition, int position) {
            this.partition = partition;
            this.position = position;
        }
    }

    public SettingsPinnedHeaderAdapter(Context context) {
        super(context);
        mLauncher = (Launcher) context;
        mContext = context;
    }

    private String[] mHeaders;
    public int mPinnedHeaderCount;

    public void setHeaders(String[] headers) {
        this.mHeaders = headers;
    }

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
                                 ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.settings_pane_list_header, null);
    }

    @Override
    protected void bindHeaderView(View view, int partition, Cursor cursor) {
        TextView textView = (TextView) view.findViewById(R.id.item_name);
        textView.setText(mHeaders[partition]);
        textView.setTypeface(textView.getTypeface(), Typeface.BOLD);

        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
                           ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.settings_pane_list_item, null);
    }

    @Override
    protected void bindView(View v, int partition, Cursor cursor, int position) {
        TextView text = (TextView)v.findViewById(R.id.item_name);
        String title = cursor.getString(1);
        text.setText(title);

        v.setTag(new SettingsPosition(partition, position));

        Resources res = mLauncher.getResources();

        boolean current = false;
        String state = "";

        switch (partition) {
            case OverviewSettingsPanel.HOME_SETTINGS_POSITION:
                switch (position) {
                    case 0:
                        current = mLauncher.isSearchBarEnabled();
                        state = current ? res.getString(R.string.setting_state_on)
                                : res.getString(R.string.setting_state_off);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    case 1:
                        updateSearchPanelItem(v);
                        break;
                    case 2:
                        state = mLauncher.getWorkspaceTransitionEffect();
                        state = mapEffectToValue(state);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    case 3:
                        current = mLauncher.shouldHideWorkspaceIconLables();
                        state = current ? res.getString(R.string.icon_labels_hide)
                                : res.getString(R.string.icon_labels_show);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    case 4:
                        current = SettingsProvider.getBoolean(mContext,
                                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_WALLPAPER_SCROLL,
                                R.bool.preferences_interface_homescreen_scrolling_wallpaper_scroll_default);
                        state = current ? res.getString(R.string.setting_state_on)
                                : res.getString(R.string.setting_state_off);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    case 5:
                        updateDynamicGridSizeSettingsItem(v);
                        break;
                    default:
                        ((TextView) v.findViewById(R.id.item_state)).setText("");
                }
                break;
            case OverviewSettingsPanel.DRAWER_SETTINGS_POSITION:
                switch (position) {
                    case 0:
                        state = mLauncher.getAppsCustomizeTransitionEffect();
                        state = mapEffectToValue(state);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    case 1:
                        updateDrawerSortSettingsItem(v);
                        break;
                    case 2:
                        current = SettingsProvider.getBoolean(mContext,
                                SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                                R.bool.preferences_interface_drawer_hide_icon_labels_default);
                        state = current ? res.getString(R.string.icon_labels_hide)
                                : res.getString(R.string.icon_labels_show);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    default:
                        ((TextView) v.findViewById(R.id.item_state)).setText("");
                }
                break;
            default:
                switch (position) {
                    case 0:
                        current = SettingsProvider.getBoolean(mContext,
                                SettingsProvider.SETTINGS_UI_GENERAL_ICONS_LARGE,
                                R.bool.preferences_interface_general_icons_large_default);
                        state = current ? res.getString(R.string.setting_state_on)
                                : res.getString(R.string.setting_state_off);
                        ((TextView) v.findViewById(R.id.item_state)).setText(state);
                        break;
                    default:
                        ((TextView) v.findViewById(R.id.item_state)).setText("");
                }
        }

        v.setOnClickListener(mSettingsItemListener);
    }

    @Override
    public View getPinnedHeaderView(int viewIndex, View convertView, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.settings_pane_list_header, parent, false);
        view.setFocusable(false);
        view.setEnabled(false);
        bindHeaderView(view, viewIndex, null);
        return view;
    }

    @Override
    public int getPinnedHeaderCount() {
        return mPinnedHeaderCount;
    }

    public void updateDrawerSortSettingsItem(View v) {
        String state = "";
        switch (mLauncher.getAppsCustomizeContentSortMode()) {
            case Title:
                state = mLauncher.getResources().getString(R.string.sort_mode_title);
                break;
            case LaunchCount:
                state = mLauncher.getResources().getString(
                        R.string.sort_mode_launch_count);
                break;
            case InstallTime:
                state = mLauncher.getResources().getString(
                        R.string.sort_mode_install_time);
                break;
        }
        ((TextView) v.findViewById(R.id.item_state)).setText(state);
    }

    public void updateSearchPanelItem(View v) {
        String state = "";
        switch (mLauncher.getCustomContentMode()) {
            case DISABLED:
                state = mLauncher.getResources().getString(
                        R.string.setting_state_off);
                break;
            case GEL:
                state = mLauncher.getResources().getString(R.string.search_panel_gel);
                break;
            default:
                state = mLauncher.getResources().getString(
                        R.string.search_panel_custom_home);
                break;
        }
        ((TextView) v.findViewById(R.id.item_state)).setText(state);
    }

    public void updateDynamicGridSizeSettingsItem(View v) {
        DeviceProfile.GridSize gridSize = DeviceProfile.GridSize.getModeForValue(
                SettingsProvider.getIntCustomDefault(mLauncher,
                SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, 0));
        String state = "";

        switch (gridSize) {
            case Comfortable:
                state = mLauncher.getResources().getString(R.string.grid_size_comfortable);
                break;
            case Cozy:
                state = mLauncher.getResources().getString(R.string.grid_size_cozy);
                break;
            case Condensed:
                state = mLauncher.getResources().getString(R.string.grid_size_condensed);
                break;
            case Custom:
                int rows = SettingsProvider.getIntCustomDefault(mLauncher,
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, 0);
                int columns = SettingsProvider.getIntCustomDefault(mLauncher,
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, 0);
                state = rows + " " + "\u00d7" + " " + columns;
                break;
        }
        ((TextView) v.findViewById(R.id.item_state)).setText(state);
    }

    private String mapEffectToValue(String effect) {
        final String[] titles = mLauncher.getResources().getStringArray(
                R.array.transition_effect_entries);
        final String[] values = mLauncher.getResources().getStringArray(
                R.array.transition_effect_values);

        int length = values.length;
        for (int i = 0; i < length; i++) {
            if (effect.equals(values[i])) {
                return titles[i];
            }
        }
        return "";
    }

    OnClickListener mSettingsItemListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            String value = ((TextView) v.findViewById(R.id.item_name)).getText().toString();
            Resources res = mLauncher.getResources();

            int partition = ((SettingsPosition) v.getTag()).partition;
            int position = ((SettingsPosition) v.getTag()).position;

            switch (partition) {
                case OverviewSettingsPanel.HOME_SETTINGS_POSITION:
                    switch (position) {
                        case 0:
                            onSettingsBooleanChanged(v,
                                    SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH,
                                    R.bool.preferences_interface_homescreen_search_default);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                        case 1:
                            onClickSearchPanelButton();
                            boolean customContentEnabled = mLauncher.getCustomContentMode()
                                    != Launcher.CustomContentMode.DISABLED;
                            mLauncher.getWorkspace().updatePageScrollForCustomPage(
                                    customContentEnabled);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                        case 2:
                            mLauncher.onClickTransitionEffectButton(v, false);
                            break;
                        case 3:
                            onIconLabelsBooleanChanged(v,
                                    SettingsProvider.SETTINGS_UI_HOMESCREEN_HIDE_ICON_LABELS,
                                    R.bool.preferences_interface_homescreen_hide_icon_labels_default);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                        case 4:
                            onSettingsBooleanChanged(v,
                                    SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_WALLPAPER_SCROLL,
                                    R.bool.preferences_interface_homescreen_scrolling_wallpaper_scroll_default);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                        case 5:
                            mLauncher.onClickDynamicGridSizeButton();
                            break;

                    }
                    break;
                case OverviewSettingsPanel.DRAWER_SETTINGS_POSITION:
                    switch (position) {
                        case 0:
                            mLauncher.onClickTransitionEffectButton(v, true);
                            break;
                        case 1:
                            onClickSortButton();
                            break;
                        case 2:
                            onIconLabelsBooleanChanged(v,
                                    SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                                    R.bool.preferences_interface_drawer_hide_icon_labels_default);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                    }
                    break;
                default:
                    switch (position) {
                        case 0:
                            onSettingsBooleanChanged(v,
                                    SettingsProvider.SETTINGS_UI_GENERAL_ICONS_LARGE,
                                    R.bool.preferences_interface_general_icons_large_default);
                            mLauncher.setUpdateDynamicGrid();
                            break;
                        case 1:
                            Intent intent = new Intent();
                            intent.setClassName(OverviewSettingsPanel.ANDROID_SETTINGS,
                                    OverviewSettingsPanel.ANDROID_PROTECTED_APPS);
                            mLauncher.startActivity(intent);
                            break;
                    }
            }

            View defaultHome = mLauncher.findViewById(R.id.default_home_screen_panel);
            defaultHome.setVisibility(getCursor(0).getCount() > 1 ? View.VISIBLE : View.GONE);
        }
    };

    private void onSettingsBooleanChanged(View v, String key, int res) {
        boolean current = SettingsProvider.getBoolean(
                mContext, key, res);

        // Set new state
        SharedPreferences sharedPref = SettingsProvider
                .get(mContext);
        sharedPref.edit().putBoolean(key, !current).commit();
        sharedPref.edit()
                .putBoolean(SettingsProvider.SETTINGS_CHANGED, true)
                .commit();

        String state = current ? mLauncher.getResources().getString(
                R.string.setting_state_off) : mLauncher.getResources().getString(
                R.string.setting_state_on);
        ((TextView) v.findViewById(R.id.item_state)).setText(state);
    }

    private void onIconLabelsBooleanChanged(View v, String key, int res) {
        boolean current = SettingsProvider.getBoolean(
                mContext, key, res);

        // Set new state
        SharedPreferences sharedPref = SettingsProvider
                .get(mContext);
        sharedPref.edit().putBoolean(key, !current).commit();
        sharedPref.edit()
                .putBoolean(SettingsProvider.SETTINGS_CHANGED, true)
                .commit();

        String state = current ? mLauncher.getResources().getString(
                R.string.icon_labels_show) : mLauncher.getResources().getString(
                R.string.icon_labels_hide);
        ((TextView) v.findViewById(R.id.item_state)).setText(state);
    }

    private void onClickSortButton() {
        int sort = SettingsProvider.getIntCustomDefault(mLauncher,
                SettingsProvider.SETTINGS_UI_DRAWER_SORT_MODE, 0);

        sort = (sort + 1) % AppsCustomizePagedView.SortMode.values().length;
        mLauncher.getAppsCustomizeContent().setSortMode(
                AppsCustomizePagedView.SortMode.getModeForValue(sort));

        SettingsProvider.putInt(mLauncher, SettingsProvider.SETTINGS_UI_DRAWER_SORT_MODE, sort);

        notifyDataSetChanged();
    }

    private void onClickSearchPanelButton() {
        int searchPanelVal = SettingsProvider.getIntCustomDefault(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH_PANEL_LEFT,
                Launcher.CustomContentMode.DISABLED.getValue());

        Launcher.CustomContentMode nextCCMode =
            Launcher.CustomContentMode.getModeForValue(searchPanelVal + 1);
        if(nextCCMode == Launcher.CustomContentMode.GEL && !mLauncher.isGelIntegrationSupported()) {
            // GEL is not supported, skip that option
            searchPanelVal++;
        }

        searchPanelVal = (searchPanelVal + 1) % Launcher.CustomContentMode.values().length;
        mLauncher.setCustomContentMode(Launcher.CustomContentMode.getModeForValue(searchPanelVal));

        SettingsProvider.putInt(mLauncher,
                                SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH_PANEL_LEFT,
                                searchPanelVal);

        notifyDataSetChanged();
    }
}
