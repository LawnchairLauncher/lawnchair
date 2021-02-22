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

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.gestures.BlankGestureHandler;
import ch.deletescape.lawnchair.gestures.GestureHandler;
import ch.deletescape.lawnchair.gestures.ui.LauncherGesturePreference;
import ch.deletescape.lawnchair.override.CustomInfoProvider;
import ch.deletescape.lawnchair.preferences.MultiSelectTabPreference;
import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.widget.WidgetsBottomSheet;

public class CustomBottomSheet extends WidgetsBottomSheet {
    private FragmentManager mFragmentManager;
    private EditText mEditTitle;
    private String mPreviousTitle;
    private ItemInfo mItemInfo;
    private CustomInfoProvider<ItemInfo> mInfoProvider;

    private boolean mForceOpen;

    public CustomBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = mLauncher.getFragmentManager();
    }

    @Override
    public void populateAndShow(ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);
        mItemInfo = itemInfo;

        mInfoProvider = CustomInfoProvider.Companion.forItem(getContext(), mItemInfo);

        TextView title = findViewById(R.id.title);
        title.setText(itemInfo.title);
        ((PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs)).loadForApp(itemInfo,
                this::setForceOpen, this::unsetForceOpen, this::reopen);

        boolean allowTitleEdit = true;

        if (itemInfo instanceof ItemInfoWithIcon || mInfoProvider.supportsIcon()) {
            ImageView icon = findViewById(R.id.icon);
            if (itemInfo instanceof ShortcutInfo && ((ShortcutInfo) itemInfo).customIcon != null) {
                icon.setImageBitmap(((ShortcutInfo) itemInfo).customIcon);
            } else if (itemInfo instanceof  ItemInfoWithIcon) {
                icon.setImageBitmap(((ItemInfoWithIcon) itemInfo).iconBitmap);
            } else if (itemInfo instanceof FolderInfo) {
                FolderInfo folderInfo = (FolderInfo) itemInfo;
                icon.setImageDrawable(folderInfo.getIcon(mLauncher));
                // Drawer folder
                if (folderInfo.container == ItemInfo.NO_ID) {
                    // TODO: Allow editing title for drawer folder & sync with group backend
                    allowTitleEdit = false;
                }
            }
            if (mInfoProvider != null) {
                LawnchairLauncher launcher = LawnchairLauncher.Companion.getLauncher(getContext());
                icon.setOnClickListener(v -> {
                    ItemInfo editItem;
                    if (mItemInfo instanceof FolderInfo && ((FolderInfo) mItemInfo).isCoverMode()) {
                        editItem = ((FolderInfo) mItemInfo).getCoverInfo();
                    } else {
                        editItem = mItemInfo;
                    }
                    CustomInfoProvider editProvider
                            = CustomInfoProvider.Companion.forItem(getContext(), editItem);
                    if (editProvider != null) {
                        launcher.startEditIcon(editItem, editProvider);
                    }
                });
            }
        }
        if (mInfoProvider != null && allowTitleEdit) {
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
    protected void handleClose(boolean animate, long defaultDuration) {
        if (mForceOpen) return;
        super.handleClose(animate, defaultDuration);
    }

    private void setForceOpen() {
        mForceOpen = true;
    }

    private void unsetForceOpen() {
        mForceOpen = false;
    }

    private void reopen() {
        mForceOpen = false;
        mIsOpen = true;
        mLauncher.getDragLayer().onViewAdded(this);
    }

    @Override
    protected void onWidgetsBound() {
    }

    public static class PrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private final static String PREF_HIDE = "pref_app_hide";
        private final static String PREF_HIDE_BADGE = "pref_badge_hide";
        private final static String PREF_HIDE_FROM_PREDICTIONS = "pref_app_prediction_hide";
        private final static boolean HIDE_PREDICTION_OPTION = true;
        public final static int requestCode = "swipeUp".hashCode() & 65535;

        private SwitchPreference mPrefHidePredictions;
        private LauncherGesturePreference mSwipeUpPref;
        private MultiSelectTabPreference mTabsPref;
        private SwitchPreference mPrefCoverMode;
        private SwitchPreference mPrefHideBadge;
        private LawnchairPreferences prefs;

        private ComponentKey mKey;

        private ItemInfo itemInfo;
        private GestureHandler previousHandler;
        private GestureHandler selectedHandler;
        private Runnable setForceOpen;
        private Runnable unsetForceOpen;
        private Runnable reopen;

        private CustomInfoProvider mProvider;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_edit_prefs);
        }

        public void loadForApp(ItemInfo info, Runnable setForceOpen, Runnable unsetForceOpen, Runnable reopen) {
            itemInfo = info;
            this.setForceOpen = setForceOpen;
            this.unsetForceOpen = unsetForceOpen;
            this.reopen = reopen;

            mProvider = CustomInfoProvider.Companion.forItem(getActivity(), info);

            Context context = getActivity();
            boolean isApp = itemInfo instanceof AppInfo || itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

            PreferenceScreen screen = getPreferenceScreen();
            prefs = Utilities.getLawnchairPrefs(getActivity());
            mSwipeUpPref = (LauncherGesturePreference) screen.findPreference("pref_swipe_up_gesture");
            mTabsPref = (MultiSelectTabPreference) screen.findPreference("pref_show_in_tabs");
            if (!(itemInfo instanceof FolderInfo)) {
                mKey = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);
            }
            SwitchPreference mPrefHide = (SwitchPreference) findPreference(PREF_HIDE);
            mPrefHideBadge = (SwitchPreference) findPreference(PREF_HIDE_BADGE);
            mPrefCoverMode = (SwitchPreference) findPreference("pref_cover_mode");

            if (isApp) {
                mPrefHide.setChecked(CustomAppFilter.isHiddenApp(context, mKey));
                mPrefHide.setOnPreferenceChangeListener(this);
            } else {
                screen.removePreference(mPrefHide);
            }

            if (!isApp || !prefs.getDrawerTabs().isEnabled()) {
                screen.removePreference(mTabsPref);
            } else {
                mTabsPref.setComponentKey(mKey);
                mTabsPref.loadSummary();
            }

            if (mProvider != null && mProvider.supportsSwipeUp(itemInfo)) {
                String previousSwipeUpAction = mProvider.getSwipeUpAction(itemInfo);
                mSwipeUpPref.setValue(previousSwipeUpAction);
                mSwipeUpPref.setOnSelectHandler(gestureHandler -> {
                    onSelectHandler(gestureHandler);
                    return null;
                });
            } else {
                getPreferenceScreen().removePreference(mSwipeUpPref);
            }

            if (mProvider != null && mProvider.supportsBadgeVisible(itemInfo)) {
                boolean badgeVisible = mProvider.getBadgeVisible(itemInfo);
                mPrefHideBadge.setChecked(!badgeVisible);
            } else {
                screen.removePreference(mPrefHideBadge);
            }

            if (mPrefHidePredictions != null) {
                mPrefHidePredictions.setChecked(CustomAppPredictor.isHiddenApp(context, mKey));
                mPrefHidePredictions.setOnPreferenceChangeListener(this);
            }

            if (prefs.getShowDebugInfo() && mKey != null && mKey.componentName != null) {
                Preference componentPref = getPreferenceScreen().findPreference("componentName");
                Preference versionPref  = getPreferenceScreen().findPreference("versionName");

                componentPref.setOnPreferenceClickListener(this);
                versionPref.setOnPreferenceClickListener(this);
                componentPref.setSummary(mKey.toString());
                versionPref.setSummary(new PackageManagerHelper(context).getPackageVersion(mKey.componentName.getPackageName()));
            } else {
                getPreferenceScreen().removePreference(getPreferenceScreen().findPreference("debug"));
            }

            mPrefHidePredictions = (SwitchPreference) getPreferenceScreen()
                    .findPreference(PREF_HIDE_FROM_PREDICTIONS);
            if ((!prefs.getShowPredictions() || HIDE_PREDICTION_OPTION)
                    && mPrefHidePredictions != null) {
                getPreferenceScreen().removePreference(mPrefHidePredictions);
            }

            if (itemInfo instanceof FolderInfo) {
                mPrefCoverMode.setChecked(((FolderInfo) itemInfo).isCoverMode());
            } else {
                getPreferenceScreen().removePreference(mPrefCoverMode);
            }

            // TODO: Add link to edit bottom sheet for drawer folder
        }

        private void onSelectHandler(GestureHandler handler) {
            previousHandler = selectedHandler;
            selectedHandler = handler;
            if (handler.getConfigIntent() != null) {
                setForceOpen.run();
                startActivityForResult(handler.getConfigIntent(), PrefsFragment.requestCode);
            } else {
                updatePref();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == PrefsFragment.requestCode) {
                if (resultCode == Activity.RESULT_OK) {
                    selectedHandler.onConfigResult(data);
                    updatePref();
                } else {
                    selectedHandler = previousHandler;
                }
                reopen.run();
            } else {
                unsetForceOpen.run();
            }
        }

        private void updatePref() {
            if (mProvider != null && selectedHandler != null) {
                setForceOpen.run();
                String stringValue;
                if (selectedHandler instanceof BlankGestureHandler) {
                    stringValue = null;
                } else {
                    stringValue = selectedHandler.toString();
                }

                Dialog dialog = mSwipeUpPref.getDialog();
                if (dialog != null) {
                    dialog.dismiss();
                }
                mSwipeUpPref.setValue(stringValue);
                unsetForceOpen.run();
            }
        }

        @SuppressWarnings({"ConstantConditions", "unchecked"})
        @Override
        public void onDetach() {
            super.onDetach();

            if (mProvider != null && selectedHandler != null) {
                String stringValue = selectedHandler.toString();

                CustomInfoProvider provider = CustomInfoProvider.Companion.forItem(getActivity(), itemInfo);
                provider.setSwipeUpAction(itemInfo, stringValue);
            }

            if (mProvider != null && mProvider.supportsBadgeVisible(itemInfo)) {
                CustomInfoProvider provider = CustomInfoProvider.Companion.forItem(getActivity(), itemInfo);
                boolean badgeHidden = mPrefHideBadge.isChecked();
                provider.setBadgeVisible(itemInfo, !badgeHidden);
            }

            if (mTabsPref.getEdited()) {
                prefs.getDrawerTabs().saveToJson();
            }

            if (itemInfo instanceof FolderInfo) {
                FolderInfo folderInfo = (FolderInfo) itemInfo;
                boolean coverEnabled = mPrefCoverMode.isChecked();
                if (folderInfo.isCoverMode() != coverEnabled) {
                    Launcher launcher = Launcher.getLauncher(getActivity());
                    folderInfo.setCoverMode(coverEnabled, launcher.getModelWriter());
                    folderInfo.onIconChanged();
                }
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
                case PREF_HIDE_FROM_PREDICTIONS:
                    CustomAppPredictor.setComponentNameState(launcher, mKey, enabled);
                    break;
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case "componentName":
                case "versionName":
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(getString(R.string.debug_component_name), preference.getSummary());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getActivity(), R.string.debug_component_name_copied, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    public static void show(Launcher launcher, ItemInfo itemInfo) {
        CustomBottomSheet cbs = (CustomBottomSheet) launcher.getLayoutInflater()
                .inflate(R.layout.app_edit_bottom_sheet, launcher.getDragLayer(), false);
        cbs.populateAndShow(itemInfo);
    }
}
