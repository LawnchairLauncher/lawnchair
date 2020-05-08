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

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;

import android.content.Intent;
import android.os.Process;
import android.text.TextUtils;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderNameInfo;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
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

    public static final String EXTRA_FOLDER_SUGGESTIONS = "suggest";

    public int options;

    public Intent suggestedFolderNames;

    // When title changes, previous title is stored.
    // Primarily used for logging purpose.
    public CharSequence previousTitle;

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
        return super.dumpProperties()
                + " manuallyTypedTitle=" + hasOption(FLAG_MANUAL_FOLDER_NAME);
    }

    @Override
    public LauncherAtom.ItemInfo buildProto(FolderInfo fInfo) {
        return getDefaultItemInfoBuilder()
            .setFolderIcon(LauncherAtom.FolderIcon.newBuilder().setCardinality(contents.size()))
            .setContainerInfo(getContainerInfo())
            .build();
    }

    @Override
    public ItemInfo makeShallowCopy() {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.copyFrom(this);
        folderInfo.contents = this.contents;
        return folderInfo;
    }

    /**
     * Returns {@link LauncherAtom.FolderIcon} wrapped as {@link LauncherAtom.ItemInfo} for logging
     * into Westworld.
     *
     */
    public LauncherAtom.ItemInfo getFolderIconAtom() {
        LauncherAtom.ToState toFolderLabelState = getToFolderLabelState();
        LauncherAtom.FolderIcon.Builder folderIconBuilder = LauncherAtom.FolderIcon.newBuilder()
                .setCardinality(contents.size())
                .setFromState(getFromFolderLabelState())
                .setToState(toFolderLabelState);
        if (toFolderLabelState.toString().startsWith("TO_SUGGESTION")) {
            folderIconBuilder.setLabel(title.toString());
        }
        return getDefaultItemInfoBuilder()
                .setFolderIcon(folderIconBuilder)
                .setContainerInfo(getContainerInfo())
                .build();
    }

    /**
     * Returns index of the accepted suggestion.
     */
    public OptionalInt getAcceptedSuggestionIndex() {
        String newLabel = checkNotNull(title,
                "Expected valid folder label, but found null").toString();
        return getSuggestedLabels()
                .map(suggestionsArray ->
                        IntStream.range(0, suggestionsArray.length)
                                .filter(
                                        index -> !isEmpty(suggestionsArray[index])
                                                && newLabel.equalsIgnoreCase(
                                                suggestionsArray[index]))
                                .sequential()
                                .findFirst()
                ).orElse(OptionalInt.empty());

    }

    private LauncherAtom.ToState getToFolderLabelState() {
        if (title == null) {
            return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
        }

        if (title.equals(previousTitle)) {
            return LauncherAtom.ToState.UNCHANGED;
        }

        if (!FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_SUGGESTIONS_DISABLED
                    : LauncherAtom.ToState.TO_EMPTY_WITH_SUGGESTIONS_DISABLED;
        }

        Optional<String[]> suggestedLabels = getSuggestedLabels();
        boolean isEmptySuggestions = suggestedLabels
                .map(labels -> stream(labels).allMatch(TextUtils::isEmpty))
                .orElse(true);
        if (isEmptySuggestions) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_EMPTY_SUGGESTIONS
                    : LauncherAtom.ToState.TO_EMPTY_WITH_EMPTY_SUGGESTIONS;
        }

        boolean hasValidPrimary = suggestedLabels
                .map(labels -> !isEmpty(labels[0]))
                .orElse(false);
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

    private LauncherAtom.FromState getFromFolderLabelState() {
        return previousTitle == null
                ? LauncherAtom.FromState.FROM_STATE_UNSPECIFIED
                : previousTitle.toString().isEmpty()
                ? LauncherAtom.FromState.FROM_EMPTY
                : hasOption(FLAG_MANUAL_FOLDER_NAME)
                ? LauncherAtom.FromState.FROM_CUSTOM
                : LauncherAtom.FromState.FROM_SUGGESTED;
    }

    private Optional<String[]> getSuggestedLabels() {
        return ofNullable(suggestedFolderNames)
                .map(folderNames ->
                        (FolderNameInfo[])
                                folderNames.getParcelableArrayExtra(EXTRA_FOLDER_SUGGESTIONS))
                .map(folderNameInfoArray ->
                        stream(folderNameInfoArray)
                                .filter(Objects::nonNull)
                                .map(FolderNameInfo::getLabel)
                                .filter(Objects::nonNull)
                                .map(CharSequence::toString)
                                .toArray(String[]::new));
    }
}
