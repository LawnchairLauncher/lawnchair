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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Handler;

import com.android.wm.shell.common.TvWindowMenuActionButton;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

abstract class TvPipAction {

    /**
     * Extras key for adding a boolean to the {@link Notification.Action} to differentiate custom
     * from system actions, most importantly to identify custom close actions.
     **/
    public static final String EXTRA_IS_PIP_CUSTOM_ACTION = "EXTRA_IS_PIP_CUSTOM_ACTION";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"ACTION_"}, value = {
            ACTION_FULLSCREEN,
            ACTION_CLOSE,
            ACTION_MOVE,
            ACTION_EXPAND_COLLAPSE,
            ACTION_CUSTOM,
            ACTION_CUSTOM_CLOSE
    })
    public @interface ActionType {
    }

    public static final int ACTION_FULLSCREEN = 0;
    public static final int ACTION_CLOSE = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_EXPAND_COLLAPSE = 3;
    public static final int ACTION_CUSTOM = 4;
    public static final int ACTION_CUSTOM_CLOSE = 5;

    @ActionType
    private final int mActionType;

    @NonNull
    private final SystemActionsHandler mSystemActionsHandler;

    TvPipAction(@ActionType int actionType, @NonNull SystemActionsHandler systemActionsHandler) {
        Objects.requireNonNull(systemActionsHandler);
        mActionType = actionType;
        mSystemActionsHandler = systemActionsHandler;
    }

    boolean isCloseAction() {
        return mActionType == ACTION_CLOSE || mActionType == ACTION_CUSTOM_CLOSE;
    }

    @ActionType
    int getActionType() {
        return mActionType;
    }

    static String getActionTypeString(@ActionType int actionType) {
        switch (actionType) {
            case ACTION_FULLSCREEN: return "ACTION_FULLSCREEN";
            case ACTION_CLOSE: return "ACTION_CLOSE";
            case ACTION_MOVE: return "ACTION_MOVE";
            case ACTION_EXPAND_COLLAPSE: return "ACTION_EXPAND_COLLAPSE";
            case ACTION_CUSTOM: return "ACTION_CUSTOM";
            case ACTION_CUSTOM_CLOSE: return "ACTION_CUSTOM_CLOSE";
            default:
                return "UNDEFINED";
        }
    }

    abstract void populateButton(@NonNull TvWindowMenuActionButton button, Handler mainHandler);

    abstract PendingIntent getPendingIntent();

    void executeAction() {
        mSystemActionsHandler.executeAction(mActionType);
    }

    abstract Notification.Action toNotificationAction(Context context);

    interface SystemActionsHandler {
        void executeAction(@TvPipAction.ActionType int actionType);
    }
}
