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
package com.android.launcher3.model.data;

import android.app.RemoteAction;
import android.os.Process;

/**
 * Represents a launchable {@link RemoteAction}
 */
public class RemoteActionItemInfo extends ItemInfoWithIcon {

    private final RemoteAction mRemoteAction;
    private final String mToken;
    private final boolean mShouldStart;

    public RemoteActionItemInfo(RemoteAction remoteAction, String token, boolean shouldStart) {
        mShouldStart = shouldStart;
        mToken = token;
        mRemoteAction = remoteAction;
        title = remoteAction.getTitle();
        user = Process.myUserHandle();
    }

    public RemoteActionItemInfo(RemoteActionItemInfo info) {
        super(info);
        this.mShouldStart = info.mShouldStart;
        this.mRemoteAction = info.mRemoteAction;
        this.mToken = info.mToken;
    }

    @Override
    public ItemInfoWithIcon clone() {
        return new RemoteActionItemInfo(this);
    }

    public RemoteAction getRemoteAction() {
        return mRemoteAction;
    }

    public String getToken() {
        return mToken;
    }

    /**
     * Getter method for mShouldStart
     */
    public boolean shouldStartInLauncher() {
        return mShouldStart;
    }

    public boolean isEscapeHatch() {
        return mToken.contains("item_type:[ESCAPE_HATCH]");
    }
}
