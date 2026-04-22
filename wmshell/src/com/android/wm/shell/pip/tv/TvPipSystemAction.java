/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import static android.app.Notification.Action.SEMANTIC_ACTION_DELETE;
import static android.app.Notification.Action.SEMANTIC_ACTION_NONE;

import android.annotation.DrawableRes;
import android.annotation.NonNull;
import android.annotation.StringRes;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Handler;

import com.android.wm.shell.common.TvWindowMenuActionButton;

/**
 * A TvPipAction for actions that the system provides, i.e. fullscreen, default close, move,
 * expand/collapse.
 */
public class TvPipSystemAction extends TvPipAction {

    @StringRes
    private int mTitleResource;
    @DrawableRes
    private int mIconResource;

    private final PendingIntent mBroadcastIntent;

    TvPipSystemAction(@ActionType int actionType, @StringRes int title, @DrawableRes int icon,
            String broadcastAction, @NonNull Context context,
            SystemActionsHandler systemActionsHandler) {
        super(actionType, systemActionsHandler);
        update(title, icon);
        mBroadcastIntent = TvPipNotificationController.createPendingIntent(context,
                broadcastAction);
    }

    /**
     * @return true if the title and/or icon were changed.
     */
    boolean update(@StringRes int title, @DrawableRes int icon) {
        boolean changed = title != mTitleResource || icon != mIconResource;
        mTitleResource = title;
        mIconResource = icon;
        return changed;
    }

    void populateButton(@NonNull TvWindowMenuActionButton button, Handler mainHandler) {
        button.setTextAndDescription(mTitleResource);
        button.setImageResource(mIconResource);
        button.setEnabled(true);
        button.setIsCustomCloseAction(false);
    }

    PendingIntent getPendingIntent() {
        return mBroadcastIntent;
    }

    @Override
    Notification.Action toNotificationAction(Context context) {
        Notification.Action.Builder builder = new Notification.Action.Builder(
                Icon.createWithResource(context, mIconResource),
                context.getString(mTitleResource),
                mBroadcastIntent);

        builder.setSemanticAction(isCloseAction()
                ? SEMANTIC_ACTION_DELETE : SEMANTIC_ACTION_NONE);
        builder.setContextual(true);
        return builder.build();
    }

}
