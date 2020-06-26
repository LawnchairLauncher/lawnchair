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

import static android.text.TextUtils.isEmpty;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.logger.LauncherAtom.Attribute.EMPTY_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.MANUAL_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.SUGGESTED_LABEL;
import static com.android.launcher3.userevent.LauncherLogProto.Target.FromFolderLabelState.FROM_CUSTOM;
import static com.android.launcher3.userevent.LauncherLogProto.Target.FromFolderLabelState.FROM_EMPTY;
import static com.android.launcher3.userevent.LauncherLogProto.Target.FromFolderLabelState.FROM_FOLDER_LABEL_STATE_UNSPECIFIED;
import static com.android.launcher3.userevent.LauncherLogProto.Target.FromFolderLabelState.FROM_SUGGESTED;

import android.os.Process;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.Attribute;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.userevent.LauncherLogProto;
import com.android.launcher3.userevent.LauncherLogProto.Target;
import com.android.launcher3.userevent.LauncherLogProto.Target.FromFolderLabelState;
import com.android.launcher3.userevent.LauncherLogProto.Target.ToFolderLabelState;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.stream.IntStream;


/**
 * Represents a folder containing shortcuts or apps.
 */
public class FolderInfo extends ItemInfo {

    public static final int NO_FLAGS = 0x00000000;

    /**
     * The folder is locked in sorted mode
     */
    public static final int FLAG_ITEMS_SORTED = 0x00000001;

    /**
     * It is a work folder
     */
    public static final int FLAG_WORK_FOLDER = 0x00000002;

    /**
     * The multi-page animation has run for this folder
     */
    public static final int FLAG_MULTI_PAGE_ANIMATION = 0x00000004;

    public static final int FLAG_MANUAL_FOLDER_NAME = 0x00000008;

    /**
     * Different states of folder label.
     */
    public enum LabelState {
        // Folder's label is not yet assigned( i.e., title == null). Eligible for auto-labeling.
        UNLABELED(Attribute.UNLABELED),

        // Folder's label is empty(i.e., title == ""). Not eligible for auto-labeling.
        EMPTY(EMPTY_LABEL),

        // Folder's label is one of the non-empty suggested values.
        SUGGESTED(SUGGESTED_LABEL),

        // Folder's label is non-empty, manually entered by the user
        // and different from any of suggested values.
        MANUAL(MANUAL_LABEL);

        private final LauncherAtom.Attribute mLogAttribute;

        LabelState(Attribute logAttribute) {
            this.mLogAttribute = logAttribute;
        }
    }

    public static final String EXTRA_FOLDER_SUGGESTIONS = "suggest";

    public int options;

    public FolderNameInfos suggestedFolderNames;

    /**
     * The apps and shortcuts
     */
    public ArrayList<WorkspaceItemInfo> contents = new ArrayList<>();

    private ArrayList<FolderListener> mListeners = new ArrayList<>();

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = Process.myUserHandle();
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(WorkspaceItemInfo item, boolean animate) {
        add(item, contents.size(), animate);
    }

    /**
     * Add an app or shortcut for a specified rank.
     */
    public void add(WorkspaceItemInfo item, int rank, boolean animate) {
        rank = Utilities.boundToRange(rank, 0, contents.size());
        contents.add(rank, item);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onAdd(item, rank);
        }
        itemsChanged(animate);
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(WorkspaceItemInfo item, boolean animate) {
        contents.remove(item);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onRemove(item);
        }
        itemsChanged(animate);
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.TITLE, title)
                .put(LauncherSettings.Favorites.OPTIONS, options);
    }

    public void addListener(FolderListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(FolderListener listener) {
        mListeners.remove(listener);
    }

    public void itemsChanged(boolean animate) {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onItemsChanged(animate);
        }
    }

    public interface FolderListener {
        public void onAdd(WorkspaceItemInfo item, int rank);
        public void onRemove(WorkspaceItemInfo item);
        public void onItemsChanged(boolean animate);
    }

    public boolean hasOption(int optionFlag) {
        return (options & optionFlag) != 0;
    }

    /**
     * @param option flag to set or clear
     * @param isEnabled whether to set or clear the flag
     * @param writer if not null, save changes to the db.
     */
    public void setOption(int option, boolean isEnabled, ModelWriter writer) {
        int oldOptions = options;
        if (isEnabled) {
            options |= option;
        } else {
            options &= ~option;
        }
        if (writer != null && oldOptions != options) {
            writer.updateItemInDatabase(this);
        }
    }

    @Override
    protected String dumpProperties() {
        return String.format("%s; labelState=%s", super.dumpProperties(), getLabelState());
    }

    @Override
    public LauncherAtom.ItemInfo buildProto(FolderInfo fInfo) {
        return getDefaultItemInfoBuilder()
                .setFolderIcon(LauncherAtom.FolderIcon.newBuilder().setCardinality(contents.size()))
                .setRank(rank)
                .setAttribute(getLabelState().mLogAttribute)
                .setContainerInfo(getContainerInfo())
                .build();
    }

    @Override
    public void setTitle(@Nullable CharSequence title, ModelWriter modelWriter) {
        // Updating label from null to empty is considered as false touch.
        // Retaining null title(ie., UNLABELED state) allows auto-labeling when new items added.
        if (isEmpty(title) && this.title == null) {
            return;
        }

        // Updating title to same value does not change any states.
        if (title != null && title.equals(this.title)) {
            return;
        }

        this.title = title;
        LabelState newLabelState =
                title == null ? LabelState.UNLABELED
                        : title.length() == 0 ? LabelState.EMPTY :
                                getAcceptedSuggestionIndex().isPresent() ? LabelState.SUGGESTED
                                        : LabelState.MANUAL;

        if (newLabelState.equals(LabelState.MANUAL)) {
            options |= FLAG_MANUAL_FOLDER_NAME;
        } else {
            options &= ~FLAG_MANUAL_FOLDER_NAME;
        }
        if (modelWriter != null) {
            modelWriter.updateItemInDatabase(this);
        }
    }

    /**
     * Returns current state of the current folder label.
     */
    public LabelState getLabelState() {
        return title == null ? LabelState.UNLABELED
                : title.length() == 0 ? LabelState.EMPTY :
                        hasOption(FLAG_MANUAL_FOLDER_NAME) ? LabelState.MANUAL
                                : LabelState.SUGGESTED;
    }

    @Override
    public ItemInfo makeShallowCopy() {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.copyFrom(this);
        folderInfo.contents = this.contents;
        return folderInfo;
    }

    /**
     * Returns {@link LauncherAtom.FolderIcon} wrapped as {@link LauncherAtom.ItemInfo} for logging.
     */
    @Override
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Returns index of the accepted suggestion.
     */
    public OptionalInt getAcceptedSuggestionIndex() {
        String newLabel = checkNotNull(title,
                "Expected valid folder label, but found null").toString();
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return OptionalInt.empty();
        }
        CharSequence[] labels = suggestedFolderNames.getLabels();
        return IntStream.range(0, labels.length)
                .filter(index -> !isEmpty(labels[index])
                        && newLabel.equalsIgnoreCase(
                        labels[index].toString()))
                .sequential()
                .findFirst();
    }

    /**
     * Returns {@link FromState} based on current {@link #title}.
     */
    public LauncherAtom.FromState getFromLabelState() {
        switch (getLabelState()){
            case EMPTY:
                return LauncherAtom.FromState.FROM_EMPTY;
            case MANUAL:
                return LauncherAtom.FromState.FROM_CUSTOM;
            case SUGGESTED:
                return LauncherAtom.FromState.FROM_SUGGESTED;
            case UNLABELED:
            default:
                return LauncherAtom.FromState.FROM_STATE_UNSPECIFIED;
        }
    }

    /**
     * Returns {@link ToState} based on current {@link #title}.
     */
    public LauncherAtom.ToState getToLabelState() {
        if (title == null) {
            return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
        }

        if (!FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_SUGGESTIONS_DISABLED
                    : LauncherAtom.ToState.TO_EMPTY_WITH_SUGGESTIONS_DISABLED;
        }

        // TODO: if suggestedFolderNames is null then it infrastructure issue, not
        // ranking issue. We should log these appropriately.
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_EMPTY_SUGGESTIONS
                    : LauncherAtom.ToState.TO_EMPTY_WITH_EMPTY_SUGGESTIONS;
        }

        boolean hasValidPrimary = suggestedFolderNames != null && suggestedFolderNames.hasPrimary();
        if (title.length() == 0) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_EMPTY_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_EMPTY_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        OptionalInt accepted_suggestion_index = getAcceptedSuggestionIndex();
        if (!accepted_suggestion_index.isPresent()) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        switch (accepted_suggestion_index.getAsInt()) {
            case 0:
                return LauncherAtom.ToState.TO_SUGGESTION0;
            case 1:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION1_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION1_WITH_EMPTY_PRIMARY;
            case 2:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION2_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION2_WITH_EMPTY_PRIMARY;
            case 3:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION3_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION3_WITH_EMPTY_PRIMARY;
            default:
                // fall through
        }
        return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
    }

    /**
     * Returns {@link LauncherLogProto.LauncherEvent} to log current folder label info.
     *
     * @deprecated This method is used only for validation purpose and soon will be removed.
     */
    @Deprecated
    public LauncherLogProto.LauncherEvent getFolderLabelStateLauncherEvent(FromState fromState,
            ToState toState) {
        return LauncherLogProto.LauncherEvent.newBuilder()
                .setAction(LauncherLogProto.Action
                        .newBuilder()
                        .setType(LauncherLogProto.Action.Type.SOFT_KEYBOARD))
                .addSrcTarget(Target
                        .newBuilder()
                        .setType(Target.Type.ITEM)
                        .setItemType(LauncherLogProto.ItemType.EDITTEXT)
                        .setFromFolderLabelState(convertFolderLabelState(fromState))
                        .setToFolderLabelState(convertFolderLabelState(toState)))
                .addSrcTarget(Target.newBuilder()
                        .setType(Target.Type.CONTAINER)
                        .setContainerType(LauncherLogProto.ContainerType.FOLDER)
                        .setPageIndex(screenId)
                        .setGridX(cellX)
                        .setGridY(cellY)
                        .setCardinality(contents.size()))
                .addSrcTarget(newParentContainerTarget())
                .build();
    }

    /**
     * @deprecated This method is used only for validation purpose and soon will be removed.
     */
    @Deprecated
    private Target.Builder newParentContainerTarget() {
        Target.Builder builder = Target.newBuilder().setType(Target.Type.CONTAINER);
        switch (container) {
            case CONTAINER_HOTSEAT:
                return builder.setContainerType(LauncherLogProto.ContainerType.HOTSEAT);
            case CONTAINER_DESKTOP:
                return builder.setContainerType(LauncherLogProto.ContainerType.WORKSPACE);
            default:
                throw new AssertionError(String
                        .format("Expected container to be either %s or %s but found %s.",
                                CONTAINER_HOTSEAT,
                                CONTAINER_DESKTOP,
                                container));
        }
    }

    /**
     * @deprecated This method is used only for validation purpose and soon will be removed.
     */
    @Deprecated
    private static FromFolderLabelState convertFolderLabelState(FromState fromState) {
        switch (fromState) {
            case FROM_EMPTY:
                return FROM_EMPTY;
            case FROM_SUGGESTED:
                return FROM_SUGGESTED;
            case FROM_CUSTOM:
                return FROM_CUSTOM;
            default:
                return FROM_FOLDER_LABEL_STATE_UNSPECIFIED;
        }
    }

    /**
     * @deprecated This method is used only for validation purpose and soon will be removed.
     */
    @Deprecated
    private static ToFolderLabelState convertFolderLabelState(ToState toState) {
        switch (toState) {
            case UNCHANGED:
                return ToFolderLabelState.UNCHANGED;
            case TO_SUGGESTION0:
                return ToFolderLabelState.TO_SUGGESTION0_WITH_VALID_PRIMARY;
            case TO_SUGGESTION1_WITH_VALID_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION1_WITH_VALID_PRIMARY;
            case TO_SUGGESTION1_WITH_EMPTY_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION1_WITH_EMPTY_PRIMARY;
            case TO_SUGGESTION2_WITH_VALID_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION2_WITH_VALID_PRIMARY;
            case TO_SUGGESTION2_WITH_EMPTY_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION2_WITH_EMPTY_PRIMARY;
            case TO_SUGGESTION3_WITH_VALID_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION3_WITH_VALID_PRIMARY;
            case TO_SUGGESTION3_WITH_EMPTY_PRIMARY:
                return ToFolderLabelState.TO_SUGGESTION3_WITH_EMPTY_PRIMARY;
            case TO_EMPTY_WITH_VALID_PRIMARY:
                return ToFolderLabelState.TO_EMPTY_WITH_VALID_PRIMARY;
            case TO_EMPTY_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY:
                return ToFolderLabelState.TO_EMPTY_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
            case TO_EMPTY_WITH_EMPTY_SUGGESTIONS:
                return ToFolderLabelState.TO_EMPTY_WITH_EMPTY_SUGGESTIONS;
            case TO_EMPTY_WITH_SUGGESTIONS_DISABLED:
                return ToFolderLabelState.TO_EMPTY_WITH_SUGGESTIONS_DISABLED;
            case TO_CUSTOM_WITH_VALID_PRIMARY:
                return ToFolderLabelState.TO_CUSTOM_WITH_VALID_PRIMARY;
            case TO_CUSTOM_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY:
                return ToFolderLabelState.TO_CUSTOM_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
            case TO_CUSTOM_WITH_EMPTY_SUGGESTIONS:
                return ToFolderLabelState.TO_CUSTOM_WITH_EMPTY_SUGGESTIONS;
            case TO_CUSTOM_WITH_SUGGESTIONS_DISABLED:
                return ToFolderLabelState.TO_CUSTOM_WITH_SUGGESTIONS_DISABLED;
            default:
                return ToFolderLabelState.TO_FOLDER_LABEL_STATE_UNSPECIFIED;
        }
    }
}
