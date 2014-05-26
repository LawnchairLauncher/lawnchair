package com.android.launcher3.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.OverviewSettingsPanel;
import com.android.launcher3.R;
import com.android.launcher3.settings.SettingsProvider;
import android.view.View.OnClickListener;
import android.content.SharedPreferences;

public class SettingsPinnedHeaderAdapter extends PinnedHeaderListAdapter {
    private Launcher mLauncher;
    private Context mContext;

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

        Resources res = mLauncher.getResources();

        if (title.equals(res
                .getString(R.string.home_screen_search_text))) {
            boolean current = mLauncher.shouldShowSearchBar();
            String state = current ? res.getString(
                    R.string.setting_state_on) : res.getString(
                    R.string.setting_state_off);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        } else if (title.equals(res
                .getString(R.string.drawer_sorting_text))) {
            updateDrawerSortSettingsItem(v);
        } else if (title.equals(res
                .getString(R.string.drawer_scroll_effect_text)) &&
                partition == OverviewSettingsPanel.DRAWER_SETTINGS_POSITION) {
            String state = mLauncher.getAppsCustomizeTransitionEffect();
            state = mapEffectToValue(state);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        } else if (title.equals(res
                .getString(R.string.page_scroll_effect_text)) &&
                partition == OverviewSettingsPanel.HOME_SETTINGS_POSITION) {
            String state = mLauncher.getWorkspaceTransitionEffect();
            state = mapEffectToValue(state);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        } else if (title.equals(res
                .getString(R.string.larger_icons_text))) {
            boolean current = SettingsProvider
                    .getBoolean(
                            mContext,
                            SettingsProvider.SETTINGS_UI_GENERAL_ICONS_LARGE,
                            R.bool.preferences_interface_general_icons_large_default);
            String state = current ? res.getString(
                    R.string.setting_state_on) : res.getString(
                    R.string.setting_state_off);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        } else if (title.equals(res
                .getString(R.string.hide_icon_labels)) &&
                partition == OverviewSettingsPanel.HOME_SETTINGS_POSITION) {
            boolean current = mLauncher.shouldHideWorkspaceIconLables();
            String state = current ? res.getString(
                    R.string.setting_state_on) : res.getString(
                    R.string.setting_state_off);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        } else if (title.equals(res
                .getString(R.string.hide_icon_labels)) &&
                partition == OverviewSettingsPanel.DRAWER_SETTINGS_POSITION) {
            boolean current = SettingsProvider
                    .getBoolean(
                            mContext,
                            SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                            R.bool.preferences_interface_drawer_hide_icon_labels_default);
            String state = current ? res.getString(
                    R.string.setting_state_on) : res.getString(
                    R.string.setting_state_off);
            ((TextView) v.findViewById(R.id.item_state)).setText(state);
        }

        v.setTag(partition);
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

            // Handle toggles or launch pickers
            if (value.equals(res
                    .getString(R.string.home_screen_search_text))) {
                onSettingsBooleanChanged(
                        v,
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH,
                        R.bool.preferences_interface_homescreen_search_default);
                mLauncher.updateDynamicGrid();
            } else if (value.equals(res
                    .getString(R.string.drawer_sorting_text))) {
                mLauncher.onClickSortModeButton(v);
            } else if (value.equals(res
                    .getString(R.string.drawer_scroll_effect_text)) &&
                    ((Integer)v.getTag() == OverviewSettingsPanel.DRAWER_SETTINGS_POSITION)) {
                mLauncher.onClickTransitionEffectButton(v, true);
            } else if (value.equals(res
                    .getString(R.string.page_scroll_effect_text)) &&
                    ((Integer)v.getTag() == OverviewSettingsPanel.HOME_SETTINGS_POSITION)) {
                mLauncher.onClickTransitionEffectButton(v, false);
            } else if (value.equals(res
                    .getString(R.string.larger_icons_text))) {
                onSettingsBooleanChanged(
                        v,
                        SettingsProvider.SETTINGS_UI_GENERAL_ICONS_LARGE,
                        R.bool.preferences_interface_general_icons_large_default);
                mLauncher.updateDynamicGrid();
            } else if (value.equals(res
                    .getString(R.string.hide_icon_labels)) &&
                    ((Integer)v.getTag() == OverviewSettingsPanel.HOME_SETTINGS_POSITION)) {
                onSettingsBooleanChanged(
                        v,
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_HIDE_ICON_LABELS,
                        R.bool.preferences_interface_homescreen_hide_icon_labels_default);
                mLauncher.updateDynamicGrid();
            } else if (value.equals(res
                    .getString(R.string.hide_icon_labels)) &&
                    ((Integer)v.getTag() == OverviewSettingsPanel.DRAWER_SETTINGS_POSITION)) {
                onSettingsBooleanChanged(
                        v,
                        SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                        R.bool.preferences_interface_drawer_hide_icon_labels_default);
                mLauncher.updateDynamicGrid();
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
}
