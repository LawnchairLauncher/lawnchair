/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.apps.nexuslauncher;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.override.CustomInfoProvider;
import com.android.launcher3.*;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.WidgetsBottomSheet;

public class CustomBottomSheet extends WidgetsBottomSheet {
    private FragmentManager mFragmentManager;
    private EditText mEditTitle;
    private String mPreviousTitle;
    private ItemInfo mItemInfo;
    private CustomInfoProvider<ItemInfo> mInfoProvider;

    public CustomBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
    }

    @Override
    public void populateAndShow(ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);
        mItemInfo = itemInfo;

        mInfoProvider = CustomInfoProvider.Companion.forItem(getContext(), mItemInfo);

        TextView title = findViewById(R.id.title);
        title.setText(itemInfo.title);
        if (itemInfo instanceof AppInfo || itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            ((PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs)).loadForApp(itemInfo);
        } else {
            mFragmentManager.beginTransaction()
                    .remove(mFragmentManager.findFragmentById(R.id.sheet_prefs))
                    .commitAllowingStateLoss();

            ((ViewGroup) findViewById(R.id.content)).removeView(findViewById(R.id.sheet_prefs));
        }

        if (itemInfo instanceof ItemInfoWithIcon) {
            ImageView icon = findViewById(R.id.icon);
            if (itemInfo instanceof ShortcutInfo && ((ShortcutInfo) itemInfo).customIcon != null) {
                icon.setImageBitmap(((ShortcutInfo) itemInfo).customIcon);
            } else {
                icon.setImageBitmap(((ItemInfoWithIcon) itemInfo).iconBitmap);
            }
            if (mInfoProvider != null) {
                icon.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LawnchairLauncher.Companion.getLauncher(getContext()).startEditIcon((ItemInfoWithIcon) mItemInfo);
                    }
                });
            }
        }
        if (mInfoProvider != null) {
            mPreviousTitle = mInfoProvider.getCustomTitle(mItemInfo);
            if (mPreviousTitle == null)
                mPreviousTitle = "";
            mEditTitle = findViewById(R.id.edit_title);
            mEditTitle.setHint(mInfoProvider.getDefaultTitle(mItemInfo));
            mEditTitle.setText(mPreviousTitle);
            mEditTitle.setVisibility(VISIBLE);
            title.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentById(R.id.sheet_prefs);
        if (pf != null) {
            mFragmentManager.beginTransaction().remove(pf).commitAllowingStateLoss();
        }
        if (mEditTitle != null) {
            String newTitle = mEditTitle.getText().toString();
            if (!newTitle.equals(mPreviousTitle)) {
                if (newTitle.equals(""))
                    newTitle = null;
                mInfoProvider.setTitle(mItemInfo, newTitle);
            }
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWidgetsBound() {
    }

    public static class PrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private final static String PREF_HIDE = "pref_app_hide";
        private SwitchPreference mPrefHide;

        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_edit_prefs);

            if (!Utilities.getLawnchairPrefs(getActivity()).getShowDebugInfo()) {
                getPreferenceScreen().removePreference(getPreferenceScreen().findPreference("debug"));
            } else {
                getPreferenceScreen().findPreference("componentName").setOnPreferenceClickListener(this);
            }
        }

        public void loadForApp(ItemInfo itemInfo) {
            mKey = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);

            Context context = getActivity();

            mPrefHide = (SwitchPreference) findPreference(PREF_HIDE);
            mPrefHide.setChecked(CustomAppFilter.isHiddenApp(context, mKey));
            mPrefHide.setOnPreferenceChangeListener(this);

            if (Utilities.getLawnchairPrefs(getActivity()).getShowDebugInfo()) {
                getPreferenceScreen().findPreference("componentName").setSummary(mKey.toString());
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean enabled = (boolean) newValue;
            Launcher launcher = Launcher.getLauncher(getActivity());
            switch (preference.getKey()) {
                case PREF_HIDE:
                    CustomAppFilter.setComponentNameState(launcher, mKey, enabled);
                    break;
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.debug_component_name), mKey.componentName.flattenToString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getActivity(), R.string.debug_component_name_copied, Toast.LENGTH_SHORT).show();
            return true;
        }
    }
}
