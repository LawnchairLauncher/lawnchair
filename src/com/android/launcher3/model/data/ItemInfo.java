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
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SEARCH_RESULTS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SETTINGS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_TASK;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.CONTAINER_NOT_SET;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Workspace;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.AllAppsContainer;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictionContainer;
import com.android.launcher3.logger.LauncherAtom.SearchResultContainer;
import com.android.launcher3.logger.LauncherAtom.SettingsContainer;
import com.android.launcher3.logger.LauncherAtom.ShortcutsContainer;
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ContentWriter;

import java.util.Optional;

/**
 * Represents an item in the launcher.
 */
public class ItemInfo {

    public static final boolean DEBUG = true;
    public static final int NO_ID = -1;

    /**
     * The id in the settings database for this item
     */
    public int id = NO_ID;

    /**
     * One of {@link Favorites#ITEM_TYPE_APPLICATION},
     * {@link Favorites#ITEM_TYPE_SHORTCUT},
     * {@link Favorites#ITEM_TYPE_DEEP_SHORTCUT}
     * {@link Favorites#ITEM_TYPE_FOLDER},
     * {@link Favorites#ITEM_TYPE_APPWIDGET} or
     * {@link Favorites#ITEM_TYPE_CUSTOM_APPWIDGET}.
     */
    public int itemType;

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
    public CharSequence title;

    /**
     * Content description of the item.
     */
    public CharSequence contentDescription;

    /**
     * When the instance is created using {@link #copyFrom}, this field is used to keep track of
     * original {@link ComponentName}.
     */
    private ComponentName mComponentName;

    public UserHandle user;

    public ItemInfo() {
        user = Process.myUserHandle();
    }

    protected ItemInfo(ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(ItemInfo info) {
        id = info.id;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        rank = info.rank;
        screenId = info.screenId;
        itemType = info.itemType;
        container = info.container;
        user = info.user;
        contentDescription = info.contentDescription;
        mComponentName = info.getTargetComponent();
    }

    public Intent getIntent() {
        return null;
    }

    @Nullable
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(mComponentName);
    }

    public void writeToValues(ContentWriter writer) {
        writer.put(LauncherSettings.Favorites.ITEM_TYPE, itemType)
                .put(LauncherSettings.Favorites.CONTAINER, container)
                .put(LauncherSettings.Favorites.SCREEN, screenId)
                .put(LauncherSettings.Favorites.CELLX, cellX)
                .put(LauncherSettings.Favorites.CELLY, cellY)
                .put(LauncherSettings.Favorites.SPANX, spanX)
                .put(LauncherSettings.Favorites.SPANY, spanY)
                .put(LauncherSettings.Favorites.RANK, rank);
    }

    public void readFromValues(ContentValues values) {
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
    public void onAddToDatabase(ContentWriter writer) {
        if (screenId == Workspace.EXTRA_EMPTY_SCREEN_ID) {
            // We should never persist an item on the extra empty screen.
            throw new RuntimeException("Screen id should not be EXTRA_EMPTY_SCREEN_ID");
        }

        writeToValues(writer);
        writer.put(LauncherSettings.Favorites.PROFILE_ID, user);
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "(" + dumpProperties() + ")";
    }

    protected String dumpProperties() {
        return "id=" + id
                + " type=" + LauncherSettings.Favorites.itemTypeToString(itemType)
                + " container=" + LauncherSettings.Favorites.containerToString(container)
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
     * Can be overridden by inherited classes to fill in {@link LauncherAtom.ItemInfo}
     */
    public void setItemBuilder(LauncherAtom.ItemInfo.Builder builder) {
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     */
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     */
    public LauncherAtom.ItemInfo buildProto(FolderInfo fInfo) {
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
            case ITEM_TYPE_SHORTCUT:
                itemBuilder
                        .setShortcut(nullableComponent
                                .map(component -> LauncherAtom.Shortcut.newBuilder()
                                        .setShortcutName(component.flattenToShortString()))
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
                        .setTask(LauncherAtom.Task.newBuilder()
                                .setComponentName(getTargetComponent().flattenToShortString())
                                .setIndex(screenId));
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

    LauncherAtom.ItemInfo.Builder getDefaultItemInfoBuilder() {
        LauncherAtom.ItemInfo.Builder itemBuilder = LauncherAtom.ItemInfo.newBuilder();
        itemBuilder.setIsWork(user != Process.myUserHandle());
        return itemBuilder;
    }

    protected ContainerInfo getContainerInfo() {
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
            case CONTAINER_SEARCH_RESULTS:
                return ContainerInfo.newBuilder()
                        .setSearchResultContainer(SearchResultContainer.getDefaultInstance())
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

        }
        return ContainerInfo.getDefaultInstance();
    }

    /**
     * Returns shallow copy of the object.
     */
    public ItemInfo makeShallowCopy() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.copyFrom(this);
        return itemInfo;
    }

    /**
     * Sets the title of the item and writes to DB model if needed.
     */
    public void setTitle(CharSequence title, ModelWriter modelWriter) {
        this.title = title;
    }
}
