/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SETTINGS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WALLPAPERS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.LauncherSettings.Favorites.EXTENDED_CONTAINERS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_TASK;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.CONTAINER_NOT_SET;
import static com.android.launcher3.shortcuts.ShortcutKey.EXTRA_SHORTCUT_ID;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Animation;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Workspace;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.AllAppsContainer;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictionContainer;
import com.android.launcher3.logger.LauncherAtom.SettingsContainer;
import com.android.launcher3.logger.LauncherAtom.Shortcut;
import com.android.launcher3.logger.LauncherAtom.ShortcutsContainer;
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer;
import com.android.launcher3.logger.LauncherAtom.WallpapersContainer;
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.SettingsCache;

import java.util.Optional;

/**
 * Represents an item in the launcher.
 */
public class ItemInfo {

    public static final boolean DEBUG = false;
    public static final int NO_ID = -1;
    // An id that doesn't match any item, including predicted apps with have an id=NO_ID
    public static final int NO_MATCHING_ID = Integer.MIN_VALUE;

    /** Hidden field Settings.Secure.NAV_BAR_KIDS_MODE */
    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor("nav_bar_kids_mode");

    /**
     * The id in the settings database for this item
     */
    public int id = NO_ID;

    /**
     * One of {@link Favorites#ITEM_TYPE_APPLICATION},
     * {@link Favorites#ITEM_TYPE_DEEP_SHORTCUT}
     * {@link Favorites#ITEM_TYPE_FOLDER},
     * {@link Favorites#ITEM_TYPE_APP_PAIR},
     * {@link Favorites#ITEM_TYPE_APPWIDGET} or
     * {@link Favorites#ITEM_TYPE_CUSTOM_APPWIDGET}.
     */
    public int itemType;

    /**
     * One of {@link Animation#DEFAULT},
     * {@link Animation#VIEW_BACKGROUND}.
     */
    public int animationType = Animation.DEFAULT;

    /**
     * The id of the container that holds this item. For the desktop, this will be
     * {@link Favorites#CONTAINER_DESKTOP}. For the all applications folder it
     * will be {@link #NO_ID} (since it is not stored in the settings DB). For user folders
     * it will be the id of the folder.
     */
    public int container = NO_ID;

    /**
     * Indicates the screen in which the shortcut appears if the container types is
     * {@link Favorites#CONTAINER_DESKTOP}. (i.e., ignore if the container type is
     * {@link Favorites#CONTAINER_HOTSEAT})
     */
    public int screenId = -1;

    /**
     * Indicates the X position of the associated cell.
     */
    public int cellX = -1;

    /**
     * Indicates the Y position of the associated cell.
     */
    public int cellY = -1;

    /**
     * Indicates the X cell span.
     */
    public int spanX = 1;

    /**
     * Indicates the Y cell span.
     */
    public int spanY = 1;

    /**
     * Indicates the minimum X cell span.
     */
    public int minSpanX = 1;

    /**
     * Indicates the minimum Y cell span.
     */
    public int minSpanY = 1;

    /**
     * Indicates the position in an ordered list.
     */
    public int rank = 0;

    /**
     * Title of the item
     */
    @Nullable
    public CharSequence title;

    /**
     * Content description of the item.
     */
    @Nullable
    public CharSequence contentDescription;

    /**
     * When the instance is created using {@link #copyFrom}, this field is used to keep track of
     * original {@link ComponentName}.
     */
    @Nullable
    private ComponentName mComponentName;

    @NonNull
    public UserHandle user;

    public ItemInfo() {
        user = Process.myUserHandle();
    }

    protected ItemInfo(@NonNull final ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(@NonNull final ItemInfo info) {
        id = info.id;
        title = info.title;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        minSpanX = info.minSpanX;
        minSpanY = info.minSpanY;
        rank = info.rank;
        screenId = info.screenId;
        itemType = info.itemType;
        animationType = info.animationType;
        container = info.container;
        user = info.user;
        contentDescription = info.contentDescription;
        mComponentName = info.getTargetComponent();
    }

    @Nullable
    public Intent getIntent() {
        return null;
    }

    @Nullable
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(mComponentName);
    }

    @Nullable
    public final ComponentKey getComponentKey() {
        ComponentName targetComponent = getTargetComponent();
        return targetComponent == null ? null : new ComponentKey(targetComponent, user);
    }

    /**
     * Returns this item's package name.
     *
     * Prioritizes the component package name, then uses the intent package name as a fallback.
     * This ensures deep shortcuts are supported.
     */
    @Nullable
    public String getTargetPackage() {
        ComponentName component = getTargetComponent();
        Intent intent = getIntent();

        return component != null
                ? component.getPackageName()
                : intent != null
                        ? intent.getPackage()
                        : null;
    }

    public void writeToValues(@NonNull final ContentWriter writer) {
        writer.put(LauncherSettings.Favorites.ITEM_TYPE, itemType)
                .put(LauncherSettings.Favorites.CONTAINER, container)
                .put(LauncherSettings.Favorites.SCREEN, screenId)
                .put(LauncherSettings.Favorites.CELLX, cellX)
                .put(LauncherSettings.Favorites.CELLY, cellY)
                .put(LauncherSettings.Favorites.SPANX, spanX)
                .put(LauncherSettings.Favorites.SPANY, spanY)
                .put(LauncherSettings.Favorites.RANK, rank);
    }

    public void readFromValues(@NonNull final ContentValues values) {
        itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        container = values.getAsInteger(LauncherSettings.Favorites.CONTAINER);
        screenId = values.getAsInteger(LauncherSettings.Favorites.SCREEN);
        cellX = values.getAsInteger(LauncherSettings.Favorites.CELLX);
        cellY = values.getAsInteger(LauncherSettings.Favorites.CELLY);
        spanX = values.getAsInteger(LauncherSettings.Favorites.SPANX);
        spanY = values.getAsInteger(LauncherSettings.Favorites.SPANY);
        rank = values.getAsInteger(LauncherSettings.Favorites.RANK);
    }

    /**
     * Write the fields of this item to the DB
     */
    public void onAddToDatabase(@NonNull final ContentWriter writer) {
        if (Workspace.EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
            // We should never persist an item on the extra empty screen.
            throw new RuntimeException("Screen id should not be extra empty screen: " + screenId);
        }

        writeToValues(writer);
        writer.put(LauncherSettings.Favorites.PROFILE_ID, user);
    }

    @Override
    @NonNull
    public final String toString() {
        return getClass().getSimpleName() + "(" + dumpProperties() + ")";
    }

    @NonNull
    protected String dumpProperties() {
        return "id=" + id
                + " type=" + LauncherSettings.Favorites.itemTypeToString(itemType)
                + " container=" + getContainerInfo()
                + " targetComponent=" + getTargetComponent()
                + " screen=" + screenId
                + " cell(" + cellX + "," + cellY + ")"
                + " span(" + spanX + "," + spanY + ")"
                + " minSpan(" + minSpanX + "," + minSpanY + ")"
                + " rank=" + rank
                + " user=" + user
                + " title=" + title;
    }

    /**
     * Whether this item is disabled.
     */
    public boolean isDisabled() {
        return false;
    }

    public int getViewId() {
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        // This cast is safe as long as the id < 0x00FFFFFF
        // Since we jail all the dynamically generated views, there should be no clashes
        // with any other views.
        return id;
    }

    /**
     * Returns if an Item is a predicted item
     */
    public boolean isPredictedItem() {
        return container == CONTAINER_HOTSEAT_PREDICTION || container == CONTAINER_PREDICTION;
    }

    /**
     * Returns if an Item is in the hotseat.
     */
    public boolean isInHotseat() {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    /**
     * Returns whether this item should use the background animation.
     */
    public boolean shouldUseBackgroundAnimation() {
        return animationType == LauncherSettings.Animation.VIEW_BACKGROUND;
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     */
    @NonNull
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     * @param fInfo
     */
    @NonNull
    public LauncherAtom.ItemInfo buildProto(@Nullable final FolderInfo fInfo) {
        LauncherAtom.ItemInfo.Builder itemBuilder = getDefaultItemInfoBuilder();
        Optional<ComponentName> nullableComponent = Optional.ofNullable(getTargetComponent());
        switch (itemType) {
            case ITEM_TYPE_APPLICATION:
                itemBuilder
                        .setApplication(nullableComponent
                                .map(component -> LauncherAtom.Application.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setPackageName(component.getPackageName()))
                                .orElse(LauncherAtom.Application.newBuilder()));
                break;
            case ITEM_TYPE_DEEP_SHORTCUT:
                itemBuilder
                        .setShortcut(nullableComponent
                                .map(component -> {
                                    Shortcut.Builder lsb = Shortcut.newBuilder()
                                            .setShortcutName(component.flattenToShortString());
                                    Optional.ofNullable(getIntent())
                                            .map(i -> i.getStringExtra(EXTRA_SHORTCUT_ID))
                                            .ifPresent(lsb::setShortcutId);
                                    return lsb;
                                })
                                .orElse(LauncherAtom.Shortcut.newBuilder()));
                break;
            case ITEM_TYPE_APPWIDGET:
                itemBuilder
                        .setWidget(nullableComponent
                                .map(component -> LauncherAtom.Widget.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setPackageName(component.getPackageName()))
                                .orElse(LauncherAtom.Widget.newBuilder())
                                .setSpanX(spanX)
                                .setSpanY(spanY));
                break;
            case ITEM_TYPE_TASK:
                itemBuilder
                        .setTask(nullableComponent
                                .map(component -> LauncherAtom.Task.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setIndex(screenId))
                                .orElse(LauncherAtom.Task.newBuilder()));
                break;
            default:
                break;
        }
        if (fInfo != null) {
            LauncherAtom.FolderContainer.Builder folderBuilder =
                    LauncherAtom.FolderContainer.newBuilder();
            folderBuilder.setGridX(cellX).setGridY(cellY).setPageIndex(screenId);

            switch (fInfo.container) {
                case CONTAINER_HOTSEAT:
                case CONTAINER_HOTSEAT_PREDICTION:
                    folderBuilder.setHotseat(LauncherAtom.HotseatContainer.newBuilder()
                            .setIndex(fInfo.screenId));
                    break;
                case CONTAINER_DESKTOP:
                    folderBuilder.setWorkspace(LauncherAtom.WorkspaceContainer.newBuilder()
                            .setPageIndex(fInfo.screenId)
                            .setGridX(fInfo.cellX).setGridY(fInfo.cellY));
                    break;
            }
            itemBuilder.setContainerInfo(ContainerInfo.newBuilder().setFolder(folderBuilder));
        } else {
            ContainerInfo containerInfo = getContainerInfo();
            if (!containerInfo.getContainerCase().equals(CONTAINER_NOT_SET)) {
                itemBuilder.setContainerInfo(containerInfo);
            }
        }
        return itemBuilder.build();
    }

    @NonNull
    protected LauncherAtom.ItemInfo.Builder getDefaultItemInfoBuilder() {
        LauncherAtom.ItemInfo.Builder itemBuilder = LauncherAtom.ItemInfo.newBuilder();
        itemBuilder.setIsWork(!Process.myUserHandle().equals(user));
        SettingsCache settingsCache = SettingsCache.INSTANCE.getNoCreate();
        boolean isKidsMode = settingsCache != null && settingsCache.getValue(NAV_BAR_KIDS_MODE, 0);
        itemBuilder.setIsKidsMode(isKidsMode);
        itemBuilder.setRank(rank);
        return itemBuilder;
    }

    /**
     * Returns {@link ContainerInfo} used when logging this item.
     */
    @NonNull
    public ContainerInfo getContainerInfo() {
        switch (container) {
            case CONTAINER_HOTSEAT:
                return ContainerInfo.newBuilder()
                        .setHotseat(LauncherAtom.HotseatContainer.newBuilder().setIndex(screenId))
                        .build();
            case CONTAINER_HOTSEAT_PREDICTION:
                return ContainerInfo.newBuilder().setPredictedHotseatContainer(
                        LauncherAtom.PredictedHotseatContainer.newBuilder().setIndex(screenId))
                        .build();
            case CONTAINER_DESKTOP:
                return ContainerInfo.newBuilder()
                        .setWorkspace(
                                LauncherAtom.WorkspaceContainer.newBuilder()
                                        .setGridX(cellX)
                                        .setGridY(cellY)
                                        .setPageIndex(screenId))
                        .build();
            case CONTAINER_ALL_APPS:
                return ContainerInfo.newBuilder()
                        .setAllAppsContainer(
                                AllAppsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_WIDGETS_TRAY:
                return ContainerInfo.newBuilder()
                        .setWidgetsContainer(
                                LauncherAtom.WidgetsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_PREDICTION:
                return ContainerInfo.newBuilder()
                        .setPredictionContainer(PredictionContainer.getDefaultInstance())
                        .build();
            case CONTAINER_SHORTCUTS:
                return ContainerInfo.newBuilder()
                        .setShortcutsContainer(ShortcutsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_SETTINGS:
                return ContainerInfo.newBuilder()
                        .setSettingsContainer(SettingsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_TASKSWITCHER:
                return ContainerInfo.newBuilder()
                        .setTaskSwitcherContainer(TaskSwitcherContainer.getDefaultInstance())
                        .build();
            case CONTAINER_WALLPAPERS:
                return ContainerInfo.newBuilder()
                        .setWallpapersContainer(WallpapersContainer.getDefaultInstance())
                        .build();
            default:
                if (container <= EXTENDED_CONTAINERS) {
                    return ContainerInfo.newBuilder()
                            .setExtendedContainers(getExtendedContainer())
                            .build();
                }
        }
        return ContainerInfo.getDefaultInstance();
    }

    /**
     * Returns non-AOSP container wrapped by {@link ExtendedContainers} object. Should be overridden
     * by build variants.
     */
    @NonNull
    protected ExtendedContainers getExtendedContainer() {
        return ExtendedContainers.getDefaultInstance();
    }

    /**
     * Returns shallow copy of the object.
     */
    @NonNull
    public ItemInfo makeShallowCopy() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.copyFrom(this);
        return itemInfo;
    }

    /**
     * Sets the title of the item and writes to DB model if needed.
     */
    public void setTitle(@Nullable final CharSequence title,
            @Nullable final ModelWriter modelWriter) {
        this.title = title;
    }
}
