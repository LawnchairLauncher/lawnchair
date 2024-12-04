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

import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CLOSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CUSTOM;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_CUSTOM_CLOSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_EXPAND_COLLAPSE;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_FULLSCREEN;
import static com.android.wm.shell.pip.tv.TvPipAction.ACTION_MOVE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static java.util.Collections.EMPTY_LIST;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.graphics.drawable.Icon;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PipMediaController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link TvPipActionsProvider}
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TvPipActionProviderTest extends ShellTestCase {
    private static final String TAG = TvPipActionProviderTest.class.getSimpleName();
    private TvPipActionsProvider mActionsProvider;

    @Mock
    private PipMediaController mMockPipMediaController;
    @Mock
    private TvPipActionsProvider.Listener mMockListener;
    @Mock
    private TvPipAction.SystemActionsHandler mMockSystemActionsHandler;
    @Mock
    private Icon mMockIcon;
    @Mock
    private PendingIntent mMockPendingIntent;

    private int mNumberOfRemoteActionsCreated = 0;

    private RemoteAction createRemoteAction() {
        final int identifier = mNumberOfRemoteActionsCreated++;
        return new RemoteAction(mMockIcon, "" + identifier, "" + identifier, mMockPendingIntent);
    }

    private List<RemoteAction> createRemoteActions(int numberOfActions) {
        List<RemoteAction> actions = new ArrayList<>();
        for (int i = 0; i < numberOfActions; i++) {
            actions.add(createRemoteAction());
        }
        return actions;
    }

    private void assertActionTypes(List<Integer> expected, List<Integer> actual) {
        assertEquals(getActionTypesStrings(expected), getActionTypesStrings(actual));
    }

    private static List<String> getActionTypesStrings(List<Integer> actionTypes) {
        return actionTypes.stream().map(a -> TvPipAction.getActionTypeString(a))
                .collect(Collectors.toList());
    }

    private List<Integer> getActionsTypes() {
        return mActionsProvider.getActionsList().stream().map(a -> a.getActionType())
                .collect(Collectors.toList());
    }

    @Before
    public void setUp() {
        assumeTelevision();
        MockitoAnnotations.initMocks(this);
        mActionsProvider = new TvPipActionsProvider(mContext, mMockPipMediaController,
                mMockSystemActionsHandler);
    }

    @Test
    public void defaultSystemActions_regularPip() {
        assertActionTypes(Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                          getActionsTypes());
    }

    @Test
    public void defaultSystemActions_expandedPip() {
        mActionsProvider.updateExpansionEnabled(true);
        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE, ACTION_EXPAND_COLLAPSE),
                getActionsTypes());
    }

    @Test
    public void expandedPip_enableExpansion_enable() {
        // PiP has expanded PiP disabled.
        mActionsProvider.addListener(mMockListener);
        mActionsProvider.updateExpansionEnabled(true);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE, ACTION_EXPAND_COLLAPSE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 1, /* updated= */ 0, /* index= */ 3);
    }

    @Test
    public void expandedPip_enableExpansion_disable() {
        mActionsProvider.updateExpansionEnabled(true);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.updateExpansionEnabled(false);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ -1, /* updated= */ 0, /* index= */ 3);
    }

    @Test
    public void expandedPip_enableExpansion_AlreadyEnabled() {
        mActionsProvider.addListener(mMockListener);
        mActionsProvider.updateExpansionEnabled(true);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE, ACTION_EXPAND_COLLAPSE),
                getActionsTypes());
    }

    private void check_expandedPip_updateExpansionState(
            boolean startExpansion, boolean endExpansion, boolean updateExpected) {

        mActionsProvider.updateExpansionEnabled(true);
        mActionsProvider.updatePipExpansionState(startExpansion);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.updatePipExpansionState(endExpansion);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE, ACTION_EXPAND_COLLAPSE),
                getActionsTypes());

        if (updateExpected) {
            verify(mMockListener).onActionsChanged(0, 1, 3);
        } else {
            verify(mMockListener, times(0))
                    .onActionsChanged(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void expandedPip_toggleExpansion_collapse() {
        check_expandedPip_updateExpansionState(
                /* startExpansion= */ true,
                /* endExpansion= */ false,
                /* updateExpected= */ true);
    }

    @Test
    public void expandedPip_toggleExpansion_expand() {
        check_expandedPip_updateExpansionState(
                /* startExpansion= */ false,
                /* endExpansion= */ true,
                /* updateExpected= */ true);
    }

    @Test
    public void expandedPiP_updateExpansionState_alreadyExpanded() {
        check_expandedPip_updateExpansionState(
                /* startExpansion= */ true,
                /* endExpansion= */ true,
                /* updateExpected= */ false);
    }

    @Test
    public void expandedPiP_updateExpansionState_alreadyCollapsed() {
        check_expandedPip_updateExpansionState(
                /* startExpansion= */ false,
                /* endExpansion= */ false,
                /* updateExpected= */ false);
    }

    @Test
    public void regularPiP_updateExpansionState_setCollapsed() {
        mActionsProvider.updatePipExpansionState(/* expanded= */ false);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.updatePipExpansionState(/* expanded= */ false);

        verify(mMockListener, times(0))
                .onActionsChanged(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void customActions_added() {
        mActionsProvider.addListener(mMockListener);

        mActionsProvider.setAppActions(createRemoteActions(2), null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 2, /* updated= */ 0, /* index= */ 2);
    }

    @Test
    public void customActions_replacedMore() {
        mActionsProvider.setAppActions(createRemoteActions(2), null);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(createRemoteActions(3), null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_CUSTOM, ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 1, /* updated= */ 2, /* index= */ 2);
    }

    @Test
    public void customActions_replacedLess() {
        mActionsProvider.setAppActions(createRemoteActions(2), null);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(EMPTY_LIST, null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ -2, /* updated= */ 0, /* index= */ 2);
    }

    @Test
    public void customCloseAdded() {
        List<RemoteAction> customActions = new ArrayList<>();
        mActionsProvider.setAppActions(customActions, null);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(customActions, createRemoteAction());

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CUSTOM_CLOSE, ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 0, /* updated= */ 1, /* index= */ 1);
    }

    @Test
    public void customClose_matchesOtherCustomAction() {
        List<RemoteAction> customActions = createRemoteActions(2);
        RemoteAction customClose = createRemoteAction();
        customActions.add(customClose);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(customActions, customClose);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CUSTOM_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 0, /* updated= */ 1, /* index= */ 1);
        verify(mMockListener).onActionsChanged(/* added= */ 2, /* updated= */ 0, /* index= */ 2);
    }

    @Test
    public void mediaActions_added_whileCustomActionsExist() {
        mActionsProvider.setAppActions(createRemoteActions(2), null);

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.onMediaActionsChanged(createRemoteActions(3));

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener, times(0)).onActionsChanged(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void customActions_removed_whileMediaActionsExist() {
        mActionsProvider.onMediaActionsChanged(createRemoteActions(2));
        mActionsProvider.setAppActions(createRemoteActions(3), null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_CUSTOM, ACTION_MOVE),
                getActionsTypes());

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(EMPTY_LIST, null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ -1, /* updated= */ 2, /* index= */ 2);
    }

    @Test
    public void customCloseOnly_mediaActionsShowing() {
        mActionsProvider.onMediaActionsChanged(createRemoteActions(2));

        mActionsProvider.addListener(mMockListener);
        mActionsProvider.setAppActions(EMPTY_LIST, createRemoteAction());

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CUSTOM_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
        verify(mMockListener).onActionsChanged(/* added= */ 0, /* updated= */ 1, /* index= */ 1);
    }

    @Test
    public void customActions_showDisabledActions() {
        List<RemoteAction> customActions = createRemoteActions(2);
        customActions.get(0).setEnabled(false);
        mActionsProvider.setAppActions(customActions, null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());
    }

    @Test
    public void mediaActions_hideDisabledActions() {
        List<RemoteAction> customActions = createRemoteActions(2);
        customActions.get(0).setEnabled(false);
        mActionsProvider.onMediaActionsChanged(customActions);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_MOVE),
                getActionsTypes());
    }

    @Test
    public void reset_mediaActions() {
        List<RemoteAction> customActions = createRemoteActions(2);
        customActions.get(0).setEnabled(false);
        mActionsProvider.onMediaActionsChanged(customActions);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_MOVE),
                getActionsTypes());

        mActionsProvider.reset();
        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
    }

    @Test
    public void reset_customActions() {
        List<RemoteAction> customActions = createRemoteActions(2);
        customActions.get(0).setEnabled(false);
        mActionsProvider.setAppActions(customActions, null);

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());

        mActionsProvider.reset();
        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
    }

    @Test
    public void reset_customClose() {
        mActionsProvider.setAppActions(EMPTY_LIST, createRemoteAction());

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CUSTOM_CLOSE, ACTION_MOVE),
                getActionsTypes());

        mActionsProvider.reset();
        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
    }

    @Test
    public void reset_All() {
        mActionsProvider.setAppActions(createRemoteActions(2), createRemoteAction());
        mActionsProvider.onMediaActionsChanged(createRemoteActions(3));

        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CUSTOM_CLOSE, ACTION_CUSTOM, ACTION_CUSTOM,
                    ACTION_MOVE),
                getActionsTypes());

        mActionsProvider.reset();
        assertActionTypes(
                Arrays.asList(ACTION_FULLSCREEN, ACTION_CLOSE, ACTION_MOVE),
                getActionsTypes());
    }

}

