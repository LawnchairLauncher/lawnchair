/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.views;

import static com.android.launcher3.Utilities.EXTRA_WALLPAPER_FLAVOR;
import static com.android.launcher3.Utilities.EXTRA_WALLPAPER_OFFSET;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.widget.WidgetsFullSheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup shown on long pressing an empty space in launcher
 */
public class OptionsPopupView extends ArrowPopup
        implements OnClickListener, OnLongClickListener {

    private final ArrayMap<View, OptionItem> mItemMap = new ArrayMap<>();
    private RectF mTargetRect;

    public OptionsPopupView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OptionsPopupView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onClick(View view) {
        handleViewClick(view);
    }

    @Override
    public boolean onLongClick(View view) {
        return handleViewClick(view);
    }

    private boolean handleViewClick(View view) {
        OptionItem item = mItemMap.get(view);
        if (item == null) {
            return false;
        }
        if (item.mEventId.getId() > 0) {
            mLauncher.getStatsLogManager().logger().log(item.mEventId);
        }
        if (item.mClickListener.onLongClick(view)) {
            close(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (getPopupContainer().isEventOverView(this, ev)) {
            return false;
        }
        close(true);
        return true;
    }

    @Override
    public void logActionCommand(int command) {
        // TODO:
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_OPTIONS_POPUP) != 0;
    }

    @Override
    protected void getTargetObjectLocation(Rect outPos) {
        mTargetRect.roundOut(outPos);
    }

    public static void show(Launcher launcher, RectF targetRect, List<OptionItem> items) {
        OptionsPopupView popup = (OptionsPopupView) launcher.getLayoutInflater()
                .inflate(R.layout.longpress_options_menu, launcher.getDragLayer(), false);
        popup.mTargetRect = targetRect;

        for (OptionItem item : items) {
            DeepShortcutView view =
                    (DeepShortcutView) popup.inflateAndAdd(R.layout.system_shortcut, popup);
            view.getIconView().setBackgroundResource(item.mIconRes);
            view.getBubbleText().setText(item.mLabelRes);
            view.setDividerVisibility(View.INVISIBLE);
            view.setOnClickListener(popup);
            view.setOnLongClickListener(popup);
            popup.mItemMap.put(view, item);
        }
        popup.reorderAndShow(popup.getChildCount());
    }

    @VisibleForTesting
    public static ArrowPopup getOptionsPopup(Launcher launcher) {
        return launcher.findViewById(R.id.deep_shortcuts_container);
    }

    public static void showDefaultOptions(Launcher launcher, float x, float y) {
        float halfSize = launcher.getResources().getDimension(R.dimen.options_menu_thumb_size) / 2;
        if (x < 0 || y < 0) {
            x = launcher.getDragLayer().getWidth() / 2;
            y = launcher.getDragLayer().getHeight() / 2;
        }
        RectF target = new RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize);

        ArrayList<OptionItem> options = new ArrayList<>();
        int resString = Utilities.existsStyleWallpapers(launcher) ?
                R.string.styles_wallpaper_button_text : R.string.wallpaper_button_text;
        int resDrawable = Utilities.existsStyleWallpapers(launcher) ?
                R.drawable.ic_palette : R.drawable.ic_wallpaper;
        options.add(new OptionItem(resString, resDrawable,
                IGNORE,
                OptionsPopupView::startWallpaperPicker));
        if (!WidgetsModel.GO_DISABLE_WIDGETS) {
            options.add(new OptionItem(R.string.widget_button_text, R.drawable.ic_widget,
                    LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS,
                    OptionsPopupView::onWidgetsClicked));
        }
        options.add(new OptionItem(R.string.settings_button_text, R.drawable.ic_setting,
                LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
                OptionsPopupView::startSettings));

        show(launcher, target, options);
    }

    public static boolean onWidgetsClicked(View view) {
        return openWidgets(Launcher.getLauncher(view.getContext())) != null;
    }

    /** Returns WidgetsFullSheet that was opened, or null if nothing was opened. */
    @Nullable
    public static WidgetsFullSheet openWidgets(Launcher launcher) {
        if (launcher.getPackageManager().isSafeMode()) {
            Toast.makeText(launcher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            return null;
        } else {
            return WidgetsFullSheet.show(launcher, true /* animated */);
        }
    }

    public static boolean startSettings(View view) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: startSettings");
        Launcher launcher = Launcher.getLauncher(view.getContext());
        launcher.startActivity(new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(launcher.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }

    /**
     * Event handler for the wallpaper picker button that appears after a long press
     * on the home screen.
     */
    public static boolean startWallpaperPicker(View v) {
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!Utilities.isWallpaperAllowed(launcher)) {
            Toast.makeText(launcher, R.string.msg_disabled_by_admin, Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_WALLPAPER_OFFSET,
                        launcher.getWorkspace().getWallpaperOffsetForCenterPage());
        if (!Utilities.existsStyleWallpapers(launcher)) {
            intent.putExtra(EXTRA_WALLPAPER_FLAVOR, "wallpaper_only");
        } else {
            intent.putExtra(EXTRA_WALLPAPER_FLAVOR, "focus_wallpaper");
            intent.setComponent(new ComponentName(
                    launcher.getString(R.string.wallpaper_picker_package),
                    "com.android.customization.picker.CustomizationPickerActivity"));
        }
        String pickerPackage = launcher.getString(R.string.wallpaper_picker_package);
        if (!TextUtils.isEmpty(pickerPackage)) {
            intent.setPackage(pickerPackage);
        }
        return launcher.startActivitySafely(v, intent, dummyInfo(intent), null);
    }

    static WorkspaceItemInfo dummyInfo(Intent intent) {
        WorkspaceItemInfo dummyInfo = new WorkspaceItemInfo();
        dummyInfo.intent = intent;
        dummyInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
        dummyInfo.container = LauncherSettings.Favorites.CONTAINER_SETTINGS;
        return dummyInfo;
    }

    public static class OptionItem {

        private final int mLabelRes;
        private final int mIconRes;
        private final EventEnum mEventId;
        private final OnLongClickListener mClickListener;

        public OptionItem(int labelRes, int iconRes, EventEnum eventId,
                OnLongClickListener clickListener) {
            mLabelRes = labelRes;
            mIconRes = iconRes;
            mEventId = eventId;
            mClickListener = clickListener;
        }
    }
}
