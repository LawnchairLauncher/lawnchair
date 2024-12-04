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

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CLOSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CUSTOM;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CUSTOM_CLOSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_EXPAND_COLLAPSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_FULLSCREEN;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_MOVE;
import static com.android.wm.shell.pip.tv.TvPipController.ACTION_CLOSE_PIP;
import static com.android.wm.shell.pip.tv.TvPipController.ACTION_MOVE_PIP;
import static com.android.wm.shell.pip.tv.TvPipController.ACTION_TOGGLE_EXPANDED_PIP;
import static com.android.wm.shell.pip.tv.TvPipController.ACTION_TO_FULLSCREEN;

import android.annotation.NonNull;
import android.app.RemoteAction;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the system TvPipActions (fullscreen, close, move, expand/collapse),  and handles all the
 * changes to the actions, including the custom app actions and media actions. Other components can
 * listen to those changes.
 */
public class TvPipActionsProvider implements TvPipAction.SystemActionsHandler {
    private static final String TAG = TvPipActionsProvider.class.getSimpleName();

    private static final int CLOSE_ACTION_INDEX = 1;
    private static final int FIRST_CUSTOM_ACTION_INDEX = 2;

    private final List<Listener> mListeners = new ArrayList<>();
    private final TvPipAction.SystemActionsHandler mSystemActionsHandler;

    private final List<TvPipAction> mActionsList = new ArrayList<>();
    private final TvPipSystemAction mFullscreenAction;
    private final TvPipSystemAction mDefaultCloseAction;
    private final TvPipSystemAction mMoveAction;
    private final TvPipSystemAction mExpandCollapseAction;

    private final List<RemoteAction> mMediaActions = new ArrayList<>();
    private final List<RemoteAction> mAppActions = new ArrayList<>();

    public TvPipActionsProvider(Context context, PipMediaController pipMediaController,
            TvPipAction.SystemActionsHandler systemActionsHandler) {
        mSystemActionsHandler = systemActionsHandler;

        mFullscreenAction = new TvPipSystemAction(ACTION_FULLSCREEN, R.string.pip_fullscreen,
                R.drawable.pip_ic_fullscreen_white, ACTION_TO_FULLSCREEN, context,
                mSystemActionsHandler);
        mDefaultCloseAction = new TvPipSystemAction(ACTION_CLOSE, R.string.pip_close,
                R.drawable.pip_ic_close_white, ACTION_CLOSE_PIP, context, mSystemActionsHandler);
        mMoveAction = new TvPipSystemAction(ACTION_MOVE, R.string.pip_move,
                R.drawable.pip_ic_move_white, ACTION_MOVE_PIP, context, mSystemActionsHandler);
        mExpandCollapseAction = new TvPipSystemAction(ACTION_EXPAND_COLLAPSE, R.string.pip_collapse,
                R.drawable.pip_ic_collapse, ACTION_TOGGLE_EXPANDED_PIP, context,
                mSystemActionsHandler);
        initActions();

        pipMediaController.addActionListener(this::onMediaActionsChanged);
    }

    private void initActions() {
        mActionsList.add(mFullscreenAction);
        mActionsList.add(mDefaultCloseAction);
        mActionsList.add(mMoveAction);
    }

    @Override
    public void executeAction(@TvPipAction.ActionType int actionType) {
        if (mSystemActionsHandler != null) {
            mSystemActionsHandler.executeAction(actionType);
        }
    }

    private void notifyActionsChanged(int added, int changed, int startIndex) {
        for (Listener listener : mListeners) {
            listener.onActionsChanged(added, changed, startIndex);
        }
    }

    @VisibleForTesting(visibility = PACKAGE)
    public void setAppActions(@NonNull List<RemoteAction> appActions, RemoteAction closeAction) {
        // Update close action.
        mActionsList.set(CLOSE_ACTION_INDEX,
                closeAction == null ? mDefaultCloseAction
                        : new TvPipCustomAction(ACTION_CUSTOM_CLOSE, closeAction,
                                mSystemActionsHandler));
        notifyActionsChanged(/* added= */ 0, /* updated= */ 1, CLOSE_ACTION_INDEX);

        // Replace custom actions with new ones.
        mAppActions.clear();
        for (RemoteAction action : appActions) {
            if (action != null && !PipUtils.remoteActionsMatch(action, closeAction)) {
                // Only show actions that aren't duplicates of the custom close action.
                mAppActions.add(action);
            }
        }

        updateCustomActions(mAppActions);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void onMediaActionsChanged(List<RemoteAction> actions) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onMediaActionsChanged()", TAG);

        mMediaActions.clear();
        // Don't show disabled actions.
        for (RemoteAction remoteAction : actions) {
            if (remoteAction.isEnabled()) {
                mMediaActions.add(remoteAction);
            }
        }

        updateCustomActions(mMediaActions);
    }

    private void updateCustomActions(@NonNull List<RemoteAction> customActions) {
        List<RemoteAction> newCustomActions = customActions;
        if (newCustomActions == mMediaActions && !mAppActions.isEmpty()) {
            // Don't show the media actions while there are app actions.
            return;
        } else if (newCustomActions == mAppActions && mAppActions.isEmpty()) {
            // If all the app actions were removed, show the media actions.
            newCustomActions = mMediaActions;
        }

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: replaceCustomActions, count: %d", TAG, newCustomActions.size());
        int oldCustomActionsCount = 0;
        for (TvPipAction action : mActionsList) {
            if (action.getActionType() == ACTION_CUSTOM) {
                oldCustomActionsCount++;
            }
        }
        mActionsList.removeIf(tvPipAction -> tvPipAction.getActionType() == ACTION_CUSTOM);

        List<TvPipAction> actions = new ArrayList<>();
        for (RemoteAction action : newCustomActions) {
            actions.add(new TvPipCustomAction(ACTION_CUSTOM, action, mSystemActionsHandler));
        }
        mActionsList.addAll(FIRST_CUSTOM_ACTION_INDEX, actions);

        int added = newCustomActions.size() - oldCustomActionsCount;
        int changed = Math.min(newCustomActions.size(), oldCustomActionsCount);
        notifyActionsChanged(added, changed, FIRST_CUSTOM_ACTION_INDEX);
    }

    @VisibleForTesting(visibility = PACKAGE)
    public void updateExpansionEnabled(boolean enabled) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: updateExpansionState, enabled: %b", TAG, enabled);
        int actionIndex = mActionsList.indexOf(mExpandCollapseAction);
        boolean actionInList = actionIndex != -1;
        if (enabled && !actionInList) {
            mActionsList.add(mExpandCollapseAction);
            actionIndex = mActionsList.size() - 1;
        } else if (!enabled && actionInList) {
            mActionsList.remove(actionIndex);
        } else {
            return;
        }
        notifyActionsChanged(/* added= */ enabled ? 1 : -1, /* updated= */ 0, actionIndex);
    }

    @VisibleForTesting(visibility = PACKAGE)
    public void updatePipExpansionState(boolean expanded) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipExpansionToggled, expanded: %b", TAG, expanded);

        boolean changed = mExpandCollapseAction.update(
                expanded ? R.string.pip_collapse : R.string.pip_expand,
                expanded ? R.drawable.pip_ic_collapse : R.drawable.pip_ic_expand);
        if (changed) {
            notifyActionsChanged(/* added= */ 0, /* updated= */ 1,
                    mActionsList.indexOf(mExpandCollapseAction));
        }
    }

    void reset() {
        mActionsList.clear();
        mMediaActions.clear();
        mAppActions.clear();

        initActions();
    }

    List<TvPipAction> getActionsList() {
        return mActionsList;
    }

    @NonNull
    TvPipAction getCloseAction() {
        return mActionsList.get(CLOSE_ACTION_INDEX);
    }

    void addListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    /**
     * Returns the index of the first action of the given action type or -1 if none can be found.
     */
    int getFirstIndexOfAction(@TvPipAction.ActionType int actionType) {
        for (int i = 0; i < mActionsList.size(); i++) {
            if (mActionsList.get(i).getActionType() == actionType) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Allow components to listen to updates to the actions list, including where they happen so
     * that changes can be animated.
     */
    interface Listener {
        /**
         * Notifies the listener how many actions were added/removed or updated.
         *
         * @param added      can be positive (number of actions added), negative (number of actions
         *                   removed) or zero (the number of actions stayed the same).
         * @param updated    the number of actions that might have been updated and need to be
         *                   refreshed.
         * @param startIndex The index of the first updated action. The added/removed actions start
         *                   at (startIndex + updated).
         */
        void onActionsChanged(int added, int updated, int startIndex);
    }
}
