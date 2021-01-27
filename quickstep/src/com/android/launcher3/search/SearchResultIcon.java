/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.search;

import static com.android.launcher3.model.data.SearchActionItemInfo.FLAG_BADGE_WITH_PACKAGE;
import static com.android.launcher3.model.data.SearchActionItemInfo.FLAG_PRIMARY_ICON_FROM_TITLE;
import static com.android.launcher3.search.SearchTargetUtil.BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.app.search.ResultType;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtomExtensions.DeviceSearchResultContainer;
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.SearchActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Themes;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link BubbleTextView} representing a single cell result in AllApps
 */
public class SearchResultIcon extends BubbleTextView implements
        SearchTargetHandler, View.OnClickListener,
        View.OnLongClickListener {

    //Play store thumbnail process workaround
    private final Rect mTempRect = new Rect();
    private final Paint mIconPaint = new Paint();
    private static final int BITMAP_CROP_MASK_COLOR = 0xff424242;

    private final Launcher mLauncher;

    private String mTargetId;
    private Consumer<ItemInfoWithIcon> mOnItemInfoChanged;


    public SearchResultIcon(Context context) {
        this(context, null, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(getContext());
    }

    private boolean mLongPressSupported;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setLongPressTimeoutFactor(1f);
        setOnFocusChangeListener(mLauncher.getFocusHandler());
        setOnClickListener(this);
        setOnLongClickListener(this);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                mLauncher.getDeviceProfile().allAppsCellHeightPx));
    }

    /**
     * Applies {@link SearchTarget} to view. registers a consumer after a corresponding
     * {@link ItemInfoWithIcon} is created
     */
    public void apply(SearchTarget searchTarget, List<SearchTarget> inlineItems,
            Consumer<ItemInfoWithIcon> cb) {
        mOnItemInfoChanged = cb;
        apply(searchTarget, inlineItems);
    }

    @Override
    public void apply(SearchTarget parentTarget, List<SearchTarget> children) {
        mTargetId = parentTarget.getId();
        if (parentTarget.getShortcutInfo() != null) {
            prepareUsingShortcutInfo(parentTarget.getShortcutInfo());
            mLongPressSupported = true;
        } else if (parentTarget.getSearchAction() != null) {
            prepareUsingSearchAction(parentTarget);
            mLongPressSupported = false;
        } else {
            String className = parentTarget.getExtras().getString(SearchTargetUtil.EXTRA_CLASS);
            prepareUsingApp(new ComponentName(parentTarget.getPackageName(), className),
                    parentTarget.getUserHandle());
            mLongPressSupported = true;
        }
    }

    private void prepareUsingSearchAction(SearchTarget searchTarget) {
        SearchAction searchAction = searchTarget.getSearchAction();
        Bundle extras = searchAction.getExtras();

        SearchActionItemInfo itemInfo = new SearchActionItemInfo(searchAction.getIcon(),
                searchTarget.getPackageName(), searchTarget.getUserHandle(),
                searchAction.getTitle()) {
            // Workaround to log ItemInfo with DeviceSearchResultContainer without
            // updating ItemInfo.container field.
            @Override
            protected ContainerInfo getContainerInfo() {
                return buildDeviceSearchResultContainer();
            }
        };
        itemInfo.setIntent(searchAction.getIntent());
        itemInfo.setPendingIntent(searchAction.getPendingIntent());

        //TODO: remove this after flags are introduced in SearchAction. Settings results require
        // startActivityForResult
        boolean isSettingsResult = searchTarget.getResultType() == ResultType.SETTING;
        if ((extras != null && extras.getBoolean(
                SearchTargetUtil.BUNDLE_EXTRA_SHOULD_START_FOR_RESULT))
                || isSettingsResult) {
            itemInfo.setFlags(SearchActionItemInfo.FLAG_SHOULD_START_FOR_RESULT);
        } else if (extras != null && extras.getBoolean(
                SearchTargetUtil.BUNDLE_EXTRA_SHOULD_START)) {
            itemInfo.setFlags(SearchActionItemInfo.FLAG_SHOULD_START);
        }
        if (extras != null && extras.getBoolean(
                SearchTargetUtil.BUNDLE_EXTRA_BADGE_WITH_PACKAGE)) {
            itemInfo.setFlags(FLAG_BADGE_WITH_PACKAGE);
        }
        if (extras != null && extras.getBoolean(BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE)) {
            itemInfo.setFlags(FLAG_PRIMARY_ICON_FROM_TITLE);
        }

        notifyItemInfoChanged(itemInfo);
        LauncherAppState appState = LauncherAppState.getInstance(mLauncher);
        MODEL_EXECUTOR.post(() -> {
            try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
                Icon icon = searchTarget.getSearchAction().getIcon();
                BitmapInfo pkgBitmap = getPackageBitmap(appState, searchTarget);
                if (itemInfo.hasFlags(FLAG_PRIMARY_ICON_FROM_TITLE)) {
                    // create a bitmap with first char if FLAG_PRIMARY_ICON_FROM_TITLE is set
                    itemInfo.bitmap = li.createIconBitmap(String.valueOf(itemInfo.title.charAt(0)),
                            pkgBitmap.color);
                } else if (icon == null) {
                    // Use default icon from package name
                    itemInfo.bitmap = pkgBitmap;
                } else {
                    boolean isPlayResult = searchTarget.getResultType() == ResultType.PLAY;
                    if (isPlayResult) {
                        Bitmap b = getPlayResultBitmap(searchAction.getIcon());
                        itemInfo.bitmap = b == null
                                ? BitmapInfo.LOW_RES_INFO : BitmapInfo.fromBitmap(b);
                    } else {
                        itemInfo.bitmap = li.createBadgedIconBitmap(icon.loadDrawable(getContext()),
                                itemInfo.user, false);
                    }
                }

                // badge with package name
                if (itemInfo.hasFlags(FLAG_BADGE_WITH_PACKAGE) && itemInfo.bitmap != pkgBitmap) {
                    itemInfo.bitmap = li.badgeBitmap(itemInfo.bitmap.icon, pkgBitmap);
                }
            }
            MAIN_EXECUTOR.post(() -> applyFromSearchActionItemInfo(itemInfo));
        });
    }

    private static BitmapInfo getPackageBitmap(LauncherAppState appState, SearchTarget target) {
        PackageItemInfo pkgInfo = new PackageItemInfo(target.getPackageName());
        pkgInfo.user = target.getUserHandle();
        appState.getIconCache().getTitleAndIconForApp(pkgInfo, false);
        return pkgInfo.bitmap;
    }

    private Bitmap getPlayResultBitmap(Icon icon) {
        try {
            int iconSize = getIconSize();
            URL url = new URL(icon.getUri().toString());
            URLConnection con = url.openConnection();
            con.addRequestProperty("Cache-Control", "max-age: 0");
            con.setUseCaches(true);
            Bitmap bitmap = BitmapFactory.decodeStream(con.getInputStream());
            return getRoundedBitmap(Bitmap.createScaledBitmap(bitmap, iconSize, iconSize, false));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bitmap getRoundedBitmap(Bitmap bitmap) {
        final int iconSize = bitmap.getWidth();
        final float radius = Themes.getDialogCornerRadius(getContext());

        return BitmapRenderer.createHardwareBitmap(iconSize, iconSize, (canvas) -> {
            mTempRect.set(0, 0, iconSize, iconSize);
            final RectF rectF = new RectF(mTempRect);

            mIconPaint.setAntiAlias(true);
            mIconPaint.reset();
            canvas.drawARGB(0, 0, 0, 0);
            mIconPaint.setColor(BITMAP_CROP_MASK_COLOR);
            canvas.drawRoundRect(rectF, radius, radius, mIconPaint);

            mIconPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, mTempRect, mTempRect, mIconPaint);
        });
    }

    private void prepareUsingApp(ComponentName componentName, UserHandle userHandle) {
        AllAppsStore appsStore = mLauncher.getAppsView().getAppsStore();
        AppInfo appInfo = new AppInfo(
                appsStore.getApp(new ComponentKey(componentName, userHandle))) {
            // Workaround to log ItemInfo with DeviceSearchResultContainer without
            // updating ItemInfo.container field.
            @Override
            protected ContainerInfo getContainerInfo() {
                return buildDeviceSearchResultContainer();
            }
        };

        if (appInfo == null) {
            setVisibility(GONE);
            return;
        }
        applyFromApplicationInfo(appInfo);
        notifyItemInfoChanged(appInfo);
    }

    private void prepareUsingShortcutInfo(ShortcutInfo shortcutInfo) {
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(shortcutInfo, getContext()) {
            // Workaround to log ItemInfo with DeviceSearchResultContainer without
            // updating ItemInfo.container field.
            @Override
            protected ContainerInfo getContainerInfo() {
                return buildDeviceSearchResultContainer();
            }
        };
        notifyItemInfoChanged(workspaceItemInfo);
        LauncherAppState launcherAppState = LauncherAppState.getInstance(getContext());
        MODEL_EXECUTOR.execute(() -> {
            launcherAppState.getIconCache().getShortcutIcon(workspaceItemInfo, shortcutInfo);
            MAIN_EXECUTOR.post(() -> applyFromWorkspaceItem(workspaceItemInfo));
        });
    }

    @Override
    public boolean quickSelect() {
        this.performClick();
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LAUNCH_KEYBOARD_FOCUS);
        return true;
    }

    @Override
    public void onClick(View view) {
        ItemClickHandler.INSTANCE.onClick(view);
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LAUNCH_TOUCH);
    }

    @Override
    public boolean onLongClick(View view) {
        if (!mLongPressSupported) {
            return false;
        }
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LONGPRESS);
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(this);
    }


    private void notifyItemInfoChanged(ItemInfoWithIcon itemInfoWithIcon) {
        if (mOnItemInfoChanged != null) {
            mOnItemInfoChanged.accept(itemInfoWithIcon);
            mOnItemInfoChanged = null;
        }
    }

    private static ContainerInfo buildDeviceSearchResultContainer() {
        return ContainerInfo.newBuilder().setExtendedContainers(
                ExtendedContainers
                        .newBuilder()
                        .setDeviceSearchResultContainer(
                                DeviceSearchResultContainer
                                        .newBuilder()))
                .build();
    }
}
