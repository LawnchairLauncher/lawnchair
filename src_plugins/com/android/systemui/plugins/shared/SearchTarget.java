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
package com.android.systemui.plugins.shared;

import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.List;

/**
 * Used to return all apps search targets.
 */
public class SearchTarget implements Comparable<SearchTarget> {

    private final String mItemId;
    private final String mItemType;
    private final float mScore;

    private final ComponentName mComponentName;
    private final UserHandle mUserHandle;
    private final List<ShortcutInfo> mShortcutInfos;
    //TODO: (sfufa) replace with a list of a custom type
    private final RemoteAction mRemoteAction;
    private final Bundle mExtras;

    private SearchTarget(String itemId, String itemType, float score,
            ComponentName componentName, UserHandle userHandle, List<ShortcutInfo> shortcutInfos,
            RemoteAction remoteAction, Bundle extras) {
        mItemId = itemId;
        mItemType = itemType;
        mScore = score;
        mComponentName = componentName;
        mUserHandle = userHandle;
        mShortcutInfos = shortcutInfos;
        mExtras = extras;
        mRemoteAction = remoteAction;
    }

    public String getItemId() {
        return mItemId;
    }

    public String getItemType() {
        return mItemType;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    public float getScore() {
        return mScore;
    }

    public List<ShortcutInfo> getShortcutInfos() {
        return mShortcutInfos;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public RemoteAction getRemoteAction() {
        return mRemoteAction;
    }

    @Override
    public int compareTo(SearchTarget o) {
        return Float.compare(o.mScore, mScore);
    }

    /**
     * A builder for {@link SearchTarget}
     */
    public static final class Builder {


        private String mItemId;

        private final String mItemType;
        private final float mScore;


        private ComponentName mComponentName;
        private UserHandle mUserHandle;
        private List<ShortcutInfo> mShortcutInfos;
        private Bundle mExtras;
        private RemoteAction mRemoteAction;

        public Builder(String itemType, float score) {
            this(itemType, score, null, null);
        }

        public Builder(String itemType, float score, ComponentName cn,
                UserHandle user) {
            mItemType = itemType;
            mScore = score;
            mComponentName = cn;
            mUserHandle = user;
        }

        public String getItemId() {
            return mItemId;
        }

        public float getScore() {
            return mScore;
        }

        public Builder setItemId(String itemId) {
            mItemId = itemId;
            return this;
        }

        public Builder setComponentName(ComponentName componentName) {
            mComponentName = componentName;
            return this;
        }

        public Builder setUserHandle(UserHandle userHandle) {
            mUserHandle = userHandle;
            return this;
        }

        public Builder setShortcutInfos(List<ShortcutInfo> shortcutInfos) {
            mShortcutInfos = shortcutInfos;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public Builder setRemoteAction(RemoteAction remoteAction) {
            mRemoteAction = remoteAction;
            return this;
        }

        /**
         * Builds a {@link SearchTarget}
         */
        public SearchTarget build() {
            if (mItemId == null) {
                throw new IllegalStateException("Item ID is required for building SearchTarget");
            }
            return new SearchTarget(mItemId, mItemType, mScore, mComponentName, mUserHandle,
                    mShortcutInfos,
                    mRemoteAction, mExtras);
        }
    }
}
