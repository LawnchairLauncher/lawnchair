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

package com.android.wm.shell.bubbles;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.LocusId;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Pair;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.BubbleData.TimeSource;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;
import com.android.wm.shell.common.bubbles.BubbleBarUpdate;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests operations and the resulting state managed by BubbleData.
 * <p>
 * After each operation to verify, {@link #verifyUpdateReceived()} ensures the listener was called
 * and captures the Update object received there.
 * <p>
 * Other methods beginning with 'assert' access the captured update object and assert on specific
 * aspects of it.
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class BubbleDataTest extends ShellTestCase {

    private BubbleEntry mEntryA1;
    private BubbleEntry mEntryA2;
    private BubbleEntry mEntryA3;
    private BubbleEntry mEntryB1;
    private BubbleEntry mEntryB2;
    private BubbleEntry mEntryB3;
    private BubbleEntry mEntryC1;
    private BubbleEntry mEntryInterruptive;
    private BubbleEntry mEntryDismissed;
    private BubbleEntry mEntryLocusId;

    private Bubble mBubbleA1;
    private Bubble mBubbleA2;
    private Bubble mBubbleA3;
    private Bubble mBubbleB1;
    private Bubble mBubbleB2;
    private Bubble mBubbleB3;
    private Bubble mBubbleC1;
    private Bubble mBubbleInterruptive;
    private Bubble mBubbleDismissed;
    private Bubble mBubbleLocusId;
    private Bubble mAppBubble;

    private BubbleData mBubbleData;
    private TestableBubblePositioner mPositioner;

    @Mock
    private TimeSource mTimeSource;
    @Mock
    private BubbleData.Listener mListener;
    @Mock
    private PendingIntent mExpandIntent;
    @Mock
    private PendingIntent mDeleteIntent;
    @Mock
    private BubbleLogger mBubbleLogger;
    @Mock
    private BubbleEducationController mEducationController;
    @Mock
    private ShellExecutor mMainExecutor;

    @Captor
    private ArgumentCaptor<BubbleData.Update> mUpdateCaptor;

    @Mock
    private Bubbles.BubbleMetadataFlagListener mBubbleMetadataFlagListener;

    @Mock
    private Bubbles.PendingIntentCanceledListener mPendingIntentCanceledListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mEntryA1 = createBubbleEntry(1, "a1", "package.a", null);
        mEntryA2 = createBubbleEntry(1, "a2", "package.a", null);
        mEntryA3 = createBubbleEntry(1, "a3", "package.a", null);
        mEntryB1 = createBubbleEntry(1, "b1", "package.b", null);
        mEntryB2 = createBubbleEntry(1, "b2", "package.b", null);
        mEntryB3 = createBubbleEntry(11, "b3", "package.b", null);
        mEntryC1 = createBubbleEntry(11, "c1", "package.c", null);

        NotificationListenerService.Ranking ranking =
                mock(NotificationListenerService.Ranking.class);
        when(ranking.isTextChanged()).thenReturn(true);
        mEntryInterruptive = createBubbleEntry(1, "interruptive", "package.d", ranking);
        mBubbleInterruptive = new Bubble(mEntryInterruptive, mBubbleMetadataFlagListener, null,
                mMainExecutor);

        mEntryDismissed = createBubbleEntry(1, "dismissed", "package.d", null);
        mBubbleDismissed = new Bubble(mEntryDismissed, mBubbleMetadataFlagListener, null,
                mMainExecutor);

        mEntryLocusId = createBubbleEntry(1, "keyLocus", "package.e", null,
                new LocusId("locusId1"));
        mBubbleLocusId = new Bubble(mEntryLocusId,
                mBubbleMetadataFlagListener,
                null /* pendingIntentCanceledListener */,
                mMainExecutor);

        mBubbleA1 = new Bubble(mEntryA1,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleA2 = new Bubble(mEntryA2,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleA3 = new Bubble(mEntryA3,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleB1 = new Bubble(mEntryB1,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleB2 = new Bubble(mEntryB2,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleB3 = new Bubble(mEntryB3,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);
        mBubbleC1 = new Bubble(mEntryC1,
                mBubbleMetadataFlagListener,
                mPendingIntentCanceledListener,
                mMainExecutor);

        Intent appBubbleIntent = new Intent(mContext, BubblesTestActivity.class);
        appBubbleIntent.setPackage(mContext.getPackageName());
        mAppBubble = Bubble.createAppBubble(
                appBubbleIntent,
                new UserHandle(1),
                mock(Icon.class),
                mMainExecutor);

        mPositioner = new TestableBubblePositioner(mContext,
                mContext.getSystemService(WindowManager.class));
        mBubbleData = new BubbleData(getContext(), mBubbleLogger, mPositioner, mEducationController,
                mMainExecutor);

        // Used by BubbleData to set lastAccessedTime
        when(mTimeSource.currentTimeMillis()).thenReturn(1000L);
        mBubbleData.setTimeSource(mTimeSource);

        // Assert baseline starting state
        assertThat(mBubbleData.hasBubbles()).isFalse();
        assertThat(mBubbleData.isExpanded()).isFalse();
        assertThat(mBubbleData.getSelectedBubble()).isNull();
    }

    @Test
    public void testAddBubble() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryA1, 1000);

        // Verify
        verifyUpdateReceived();
        assertBubbleAdded(mBubbleA1);
        assertSelectionChangedTo(mBubbleA1);
    }

    @Test
    public void testAddAppBubble_setsTime() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        assertThat(mAppBubble.getLastActivity()).isEqualTo(0);
        setCurrentTime(1000);
        mBubbleData.notificationEntryUpdated(mAppBubble, true /* suppressFlyout*/,
                false /* showInShade */);

        // Verify
        assertThat(mBubbleData.getBubbleInStackWithKey(mAppBubble.getKey())).isEqualTo(mAppBubble);
        assertThat(mAppBubble.getLastActivity()).isEqualTo(1000);
    }

    @Test
    public void testRemoveBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);

        // Verify
        verifyUpdateReceived();
        assertBubbleRemoved(mBubbleA1, Bubbles.DISMISS_USER_GESTURE);
    }

    @Test
    public void ifSuppress_hideFlyout() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleC1, /* suppressFlyout */ true, /* showInShade */
                true);

        // Verify
        verifyUpdateReceived();
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.addedBubble.showFlyout()).isFalse();
    }

    @Test
    public void ifInterruptiveAndNotSuppressed_thenShowFlyout() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleInterruptive,
                false /* suppressFlyout */, true  /* showInShade */);

        // Verify
        verifyUpdateReceived();
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.addedBubble.showFlyout()).isTrue();
    }

    @Test
    public void sameUpdate_InShade_thenHideFlyout() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleC1, false /* suppressFlyout */,
                true /* showInShade */);
        verifyUpdateReceived();

        mBubbleData.notificationEntryUpdated(mBubbleC1, false /* suppressFlyout */,
                true /* showInShade */);
        verifyUpdateReceived();

        // Verify
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.updatedBubble.showFlyout()).isFalse();
    }

    @Test
    public void sameUpdate_NotInShade_NotVisuallyInterruptive_dontShowFlyout() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleDismissed, false /* suppressFlyout */,
                true /* showInShade */);
        verifyUpdateReceived();

        // Suppress the notif / make it look dismissed
        mBubbleDismissed.setSuppressNotification(true);

        mBubbleData.notificationEntryUpdated(mBubbleDismissed, false /* suppressFlyout */,
                true /* showInShade */);
        verifyUpdateReceived();

        // Verify
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.updatedBubble.showFlyout()).isFalse();
    }

    //
    // Overflow
    //

    /**
     * Verifies that when the bubble stack reaches its maximum, the oldest bubble is overflowed.
     */
    @Test
    public void testOverflow_add_stackAtMaxBubbles_overflowsOldest() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        sendUpdatedEntryAtTime(mEntryB1, 4000);
        sendUpdatedEntryAtTime(mEntryB2, 5000);
        mBubbleData.setListener(mListener);

        sendUpdatedEntryAtTime(mEntryC1, 6000);
        verifyUpdateReceived();
        assertBubbleRemoved(mBubbleA1, Bubbles.DISMISS_AGED);
        assertOverflowChangedTo(ImmutableList.of(mBubbleA1));

        Bubble bubbleA1 = mBubbleData.getOrCreateBubble(mEntryA1, null /* persistedBubble */);
        bubbleA1.markUpdatedAt(7000L);
        mBubbleData.notificationEntryUpdated(bubbleA1, false /* suppressFlyout*/,
                true /* showInShade */);
        verifyUpdateReceived();
        assertBubbleRemoved(mBubbleA2, Bubbles.DISMISS_AGED);
        assertOverflowChangedTo(ImmutableList.of(mBubbleA2));
    }

    /**
     * Verifies that once the number of overflowed bubbles reaches its maximum, the oldest
     * overflow bubble is removed.
     */
    @Test
    public void testOverflow_maxReached_bubbleRemoved() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        mBubbleData.setListener(mListener);

        mBubbleData.setMaxOverflowBubbles(1);
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertOverflowChangedTo(ImmutableList.of(mBubbleA1));

        // Overflow max of 1 is reached; A1 is oldest, so it gets removed
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertOverflowChangedTo(ImmutableList.of(mBubbleA2));
    }

    /**
     * Verifies that overflow bubbles are canceled on notif entry removal.
     */
    @Test
    public void testOverflow_notifCanceled_removesOverflowBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        sendUpdatedEntryAtTime(mEntryB1, 4000);
        sendUpdatedEntryAtTime(mEntryB2, 5000);
        sendUpdatedEntryAtTime(mEntryB3, 6000); // [A2, A3, B1, B2, B3], overflow: [A1]
        sendUpdatedEntryAtTime(mEntryC1, 7000); // [A3, B1, B2, B3, C1], overflow: [A2, A1]
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_NOTIF_CANCEL);
        verifyUpdateReceived();
        assertOverflowChangedTo(ImmutableList.of(mBubbleA2));

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_GROUP_CANCELLED);
        verifyUpdateReceived();
        assertOverflowChangedTo(ImmutableList.of());
    }

    /**
     * Verifies that the update shouldn't show the user education, if the education is not required
     */
    @Test
    public void test_shouldNotShowEducation() {
        // Setup
        when(mEducationController.shouldShowStackEducation(any())).thenReturn(false);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleA1, /* suppressFlyout */ true, /* showInShade */
                true);

        // Verify
        verifyUpdateReceived();
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.shouldShowEducation).isFalse();
    }

    /**
     * Verifies that the update should show the user education, if the education is required
     */
    @Test
    public void test_shouldShowEducation() {
        // Setup
        when(mEducationController.shouldShowStackEducation(any())).thenReturn(true);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleA1, /* suppressFlyout */ true, /* showInShade */
                true);

        // Verify
        verifyUpdateReceived();
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.shouldShowEducation).isTrue();
    }

    /**
     * Verifies that the update shouldn't show the user education, if the education is required but
     * the bubble should auto-expand
     */
    @Test
    public void test_shouldShowEducation_shouldAutoExpand() {
        // Setup
        when(mEducationController.shouldShowStackEducation(any())).thenReturn(true);
        mBubbleData.setListener(mListener);
        mBubbleA1.setShouldAutoExpand(true);

        // Test
        mBubbleData.notificationEntryUpdated(mBubbleA1, /* suppressFlyout */ true, /* showInShade */
                true);

        // Verify
        verifyUpdateReceived();
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.shouldShowEducation).isFalse();
    }

    // COLLAPSED / ADD

    /**
     * Verifies that new bubbles insert to the left when collapsed.
     * <p>
     * Placement within the list is based on {@link Bubble#getLastActivity()}, descending
     * order (with most recent first).
     */
    @Test
    public void test_collapsed_addBubble() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        verifyUpdateReceived();
        assertOrderNotChanged();

        sendUpdatedEntryAtTime(mEntryB1, 2000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleB1, mBubbleA1);

        sendUpdatedEntryAtTime(mEntryB2, 3000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleB2, mBubbleB1, mBubbleA1);

        sendUpdatedEntryAtTime(mEntryA2, 4000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA2, mBubbleB2, mBubbleB1, mBubbleA1);
    }

    /**
     * Verifies that new bubbles become the selected bubble when they appear when the stack is in
     * the collapsed state.
     *
     * @see #test_collapsed_updateBubble_selectionChanges()
     */
    @Test
    public void test_collapsed_addBubble_selectionChanges() {
        // Setup
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA1);

        sendUpdatedEntryAtTime(mEntryB1, 2000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleB1);

        sendUpdatedEntryAtTime(mEntryB2, 3000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleB2);

        sendUpdatedEntryAtTime(mEntryA2, 4000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA2);
    }

    // COLLAPSED / REMOVE

    /**
     * Verifies order of bubbles after a removal.
     */
    @Test
    public void test_collapsed_removeBubble_sort() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        // TODO: this should fail if things work as I expect them to?
        assertOrderChangedTo(mBubbleB2, mBubbleB1, mBubbleA1);
    }

    /**
     * Verifies that onOrderChanged is not called when a bubble is removed if the removal does not
     * cause other bubbles to change position.
     */
    @Test
    public void test_collapsed_removeOldestBubble_doesNotCallOnOrderChanged() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertOrderNotChanged();
    }

    /**
     * Verifies that when the selected bubble is removed with the stack in the collapsed state,
     * the selection moves to the next most-recently updated bubble.
     */
    @Test
    public void test_collapsed_removeBubble_selectionChanges() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_NOTIF_CANCEL);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleB2);
    }

    // COLLAPSED / UPDATE

    /**
     * Verifies that bubble ordering changes with updates while the stack is in the
     * collapsed state.
     */
    @Test
    public void test_collapsed_updateBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryB1, 5000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleB1, mBubbleA2, mBubbleB2, mBubbleA1);

        sendUpdatedEntryAtTime(mEntryA1, 6000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA1, mBubbleB1, mBubbleA2, mBubbleB2);
    }

    /**
     * Verifies that selection tracks the most recently updated bubble while in the collapsed state.
     */
    @Test
    public void test_collapsed_updateBubble_selectionChanges() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryB1, 5000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleB1);

        sendUpdatedEntryAtTime(mEntryA1, 6000);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA1);
    }

    /**
     * Verifies that when a non visually interruptive update occurs, that the selection does not
     * change.
     */
    @Test
    public void test_notVisuallyInterruptive_updateBubble_selectionDoesntChange() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryB2, 3000);
        sendUpdatedEntryAtTime(mEntryA2, 4000); // [A2, B2, B1, A1]
        mBubbleData.setListener(mListener);

        assertThat(mBubbleData.getSelectedBubble()).isEqualTo(mBubbleA2);

        // Test
        sendUpdatedEntryAtTime(mEntryB1, 5000, false /* isVisuallyInterruptive */);
        assertThat(mBubbleData.getSelectedBubble()).isEqualTo(mBubbleA2);
    }

    /**
     * Verifies that a request to expand the stack has no effect if there are no bubbles.
     */
    @Test
    public void test_collapsed_expansion_whenEmpty_doesNothing() {
        assertThat(mBubbleData.hasBubbles()).isFalse();
        mBubbleData.setListener(mListener);

        changeExpandedStateAtTime(true, 2000L);
        verifyZeroInteractions(mListener);
    }

    /**
     * Verifies that removing the last bubble clears the selected bubble and collapses the stack.
     */
    @Test
    public void test_collapsed_removeLastBubble_clearsSelectedBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);

        // Verify the selection was cleared.
        verifyUpdateReceived();
        assertThat(mBubbleData.isExpanded()).isFalse();
        assertThat(mBubbleData.getSelectedBubble()).isNull();
    }

    // EXPANDED / ADD / UPDATE

    /**
     * Verifies that bubbles are added at the front of the stack.
     * <p>
     * Placement within the list is based on {@link Bubble#getLastActivity()}, descending
     * order (with most recent first).
     *
     * @see #test_collapsed_addBubble()
     */
    @Test
    public void test_expanded_addBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryB1, 3000); // [B1, A2, A1]
        changeExpandedStateAtTime(true, 4000L); // B1 marked updated at 4000L
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryC1, 4000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleC1, mBubbleB1, mBubbleA2, mBubbleA1);
    }

    /**
     * Verifies that updates to bubbles while expanded do not result in any change to sorting
     * of bubbles.
     */
    @Test
    public void test_expanded_updateBubble_noChanges() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryB1, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000); // [B2, B1, A2, A1]
        changeExpandedStateAtTime(true, 5000L);
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryA1, 4000);
        verifyUpdateReceived();
        assertOrderNotChanged();
    }

    /**
     * Verifies that updates to bubbles while expanded do not result in any change to selection.
     *
     * @see #test_collapsed_addBubble_selectionChanges()
     */
    @Test
    public void test_expanded_updateBubble_noSelectionChanges() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryB1, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000); // [B2, B1, A2, A1]
        changeExpandedStateAtTime(true, 5000L);
        mBubbleData.setListener(mListener);

        // Test
        sendUpdatedEntryAtTime(mEntryA1, 6000);
        verifyUpdateReceived();
        assertOrderNotChanged();

        sendUpdatedEntryAtTime(mEntryA2, 7000);
        verifyUpdateReceived();
        assertOrderNotChanged();

        sendUpdatedEntryAtTime(mEntryB1, 8000);
        verifyUpdateReceived();
        assertOrderNotChanged();
    }

    // EXPANDED / REMOVE

    /**
     * Verifies that removing a bubble while expanded does not result in reordering of bubbles.
     *
     * @see #test_collapsed_addBubble()
     */
    @Test
    public void test_expanded_removeBubble() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000); // [B2, A2, B1, A1]
        changeExpandedStateAtTime(true, 5000L);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryB2.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA2, mBubbleB1, mBubbleA1);
    }

    /**
     * Verifies that removing the selected bubble while expanded causes another bubble to become
     * selected. The replacement selection is the bubble which appears at the same index as the
     * previous one, or the previous index if this was the last position.
     *
     * @see #test_collapsed_addBubble()
     */
    @Test
    public void test_expanded_removeBubble_selectionChanges_whenSelectedRemoved() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000);
        changeExpandedStateAtTime(true, 5000L);
        mBubbleData.setSelectedBubble(mBubbleA2);  // [B2, A2^, B1, A1]
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleB1);

        mBubbleData.dismissBubbleWithKey(mEntryB1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA1);
    }

    @Test
    public void test_expandAndCollapse_callsOnExpandedChanged() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        mBubbleData.setListener(mListener);

        // Test
        changeExpandedStateAtTime(true, 3000L);
        verifyUpdateReceived();
        assertExpandedChangedTo(true);

        changeExpandedStateAtTime(false, 4000L);
        verifyUpdateReceived();
        assertExpandedChangedTo(false);
    }

    /**
     * Verifies that transitions between the collapsed and expanded state maintain sorting and
     * grouping rules.
     * <p>
     * While collapsing, sorting is applied since no sorting happens while expanded. The resulting
     * state is the new expanded ordering. This state is saved and restored if possible when next
     * expanded.
     * <p>
     * When the stack transitions to the collapsed state, the selected bubble is brought to the top.
     * <p>
     * When the stack transitions back to the expanded state, this new order is kept as is.
     */
    @Test
    public void test_expansionChanges() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000);
        changeExpandedStateAtTime(true, 5000L); // [B2=4000, A2=3000, B1=2000, A1=1000]
        sendUpdatedEntryAtTime(mEntryB1, 6000); // [B2=4000, A2=3000, B1=6000, A1=1000]
        setCurrentTime(7000);
        mBubbleData.setSelectedBubble(mBubbleA2);
        mBubbleData.setListener(mListener);
        assertThat(mBubbleData.getBubbles()).isEqualTo(
                ImmutableList.of(mBubbleB2, mBubbleA2, mBubbleB1, mBubbleA1));

        // Test

        // At this point, B1 has been updated but sorting has not been changed because the
        // stack is expanded. When next collapsed, sorting will be applied and saved, just prior
        // to moving the selected bubble to the top (first).
        //
        // In this case, the expected re-expand state will be: [A2^, B1, B2, A1]
        //
        // collapse -> selected bubble (A2) moves first.
        changeExpandedStateAtTime(false, 8000L);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA2, mBubbleB1, mBubbleB2, mBubbleA1);
    }

    /**
     * When a change occurs while collapsed (any update, add, remove), the previous expanded
     * order becomes invalidated, the stack is resorted and will reflect that when next expanded.
     */
    @Test
    public void test_expansionChanges_withUpdatesWhileCollapsed() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryB1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        sendUpdatedEntryAtTime(mEntryB2, 4000);
        changeExpandedStateAtTime(true, 5000L); // [B2=4000, A2=3000,  B1=2000, A1=1000]
        sendUpdatedEntryAtTime(mEntryB1, 6000); // [B2=4000, A2=3000,  B1=6000, A1=1000]
        setCurrentTime(7000);
        mBubbleData.setSelectedBubble(mBubbleA2); // [B2, A2^, B1, A1]
        mBubbleData.setListener(mListener);

        // Test

        // At this point, B1 has been updated but sorting has not been changed because the
        // stack is expanded. When next collapsed, sorting will be applied and saved, just prior
        // to moving the selected bubble to the top (first).
        //
        // In this case, the expected re-expand state will be: [A2^, B1, B2, A1]
        //
        // That state is restored as long as no changes occur (add/remove/update) while in
        // the collapsed state.
        //
        // collapse -> selected bubble (A2) moves first.
        changeExpandedStateAtTime(false, 8000L);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA2, mBubbleB1, mBubbleB2, mBubbleA1);

        // An update occurs, which causes sorting, and this invalidates the previously saved order.
        sendUpdatedEntryAtTime(mEntryA1, 9000);
        verifyUpdateReceived();
        assertOrderChangedTo(mBubbleA1, mBubbleA2, mBubbleB1, mBubbleB2);

        // No order changes when expanding because the new sorted order remains.
        changeExpandedStateAtTime(true, 10000L);
        verifyUpdateReceived();
        assertOrderNotChanged();
    }

    @Test
    public void test_expanded_removeLastBubble_collapsesIfOverflowNotEmpty() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        changeExpandedStateAtTime(true, 2000);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertThat(mBubbleData.getOverflowBubbles().size()).isGreaterThan(0);
        assertExpandedChangedTo(false);
    }

    @Test
    public void test_expanded_removeLastBubble_collapsesIfOverflowEmpty() {
        // Setup
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        changeExpandedStateAtTime(true, 2000);
        mBubbleData.setListener(mListener);

        // Test
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_NO_BUBBLE_UP);
        verifyUpdateReceived();
        assertThat(mBubbleData.getOverflowBubbles()).isEmpty();
        assertExpandedChangedTo(false);
    }

    @Test
    public void test_addToOverflow_doesntAllowDupes() {
        assertEquals(0, mBubbleData.getOverflowBubbles().size());
        mBubbleData.overflowBubble(Bubbles.DISMISS_AGED, mBubbleA1);
        mBubbleData.overflowBubble(Bubbles.DISMISS_AGED, mBubbleA1);
        mBubbleData.overflowBubble(Bubbles.DISMISS_AGED, mBubbleA1);
        assertEquals(1, mBubbleData.getOverflowBubbles().size());
    }

    @Test
    public void test_onMaxBubblesChanged_notExpanded() {
        mBubbleData.setListener(mListener);
        mPositioner.setMaxBubbles(5);
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        sendUpdatedEntryAtTime(mEntryB1, 4000);
        sendUpdatedEntryAtTime(mEntryB2, 5000);
        mBubbleData.setExpanded(false);
        reset(mListener);

        mPositioner.setMaxBubbles(3);
        mBubbleData.onMaxBubblesChanged();
        verifyUpdateReceived();

        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.removedBubbles.get(0)).isEqualTo(
                Pair.create(mBubbleA1, Bubbles.DISMISS_AGED));
        assertThat(update.removedBubbles.get(1)).isEqualTo(
                Pair.create(mBubbleA2, Bubbles.DISMISS_AGED));

        assertNotNull(mBubbleData.getOverflowBubbleWithKey(mBubbleA1.getKey()));
        assertNotNull(mBubbleData.getOverflowBubbleWithKey(mBubbleA2.getKey()));
    }

    @Test
    public void test_onMaxBubblesChanged_expanded() {
        mBubbleData.setListener(mListener);
        mPositioner.setMaxBubbles(5);
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryA3, 3000);
        sendUpdatedEntryAtTime(mEntryB1, 4000);
        sendUpdatedEntryAtTime(mEntryB2, 5000);
        mBubbleData.setExpanded(true);
        reset(mListener);

        mPositioner.setMaxBubbles(3);
        mBubbleData.onMaxBubblesChanged();
        verify(mListener, never()).applyUpdate(any());

        mBubbleData.setExpanded(false);
        verifyUpdateReceived();

        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.removedBubbles.get(0)).isEqualTo(
                Pair.create(mBubbleA1, Bubbles.DISMISS_AGED));
        assertThat(update.removedBubbles.get(1)).isEqualTo(
                Pair.create(mBubbleA2, Bubbles.DISMISS_AGED));

        assertNotNull(mBubbleData.getOverflowBubbleWithKey(mBubbleA1.getKey()));
        assertNotNull(mBubbleData.getOverflowBubbleWithKey(mBubbleA2.getKey()));
    }

    /**
     * Verifies that after the stack is collapsed with the overflow selected, it will select
     * the top bubble upon next expansion.
     */
    @Test
    public void test_collapseWithOverflowSelected_nextExpansion() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        mBubbleData.setExpanded(true);

        mBubbleData.setListener(mListener);

        // Select the overflow
        mBubbleData.setShowingOverflow(true);
        mBubbleData.setSelectedBubble(mBubbleData.getOverflow());
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleData.getOverflow());

        // Collapse
        mBubbleData.setExpanded(false);
        verifyUpdateReceived();
        assertSelectionNotChanged();

        // Expand (here we should select the new bubble)
        mBubbleData.setExpanded(true);
        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA2);
    }

    /**
      * - have a maxed out bubble stack & all of the bubbles have been recently accessed
      * - bubble a notification that was posted before any of those bubbles were accessed
      * => that bubble should be added
     *
      */
    @Test
    public void test_addOldNotifWithNewerBubbles() {
        sendUpdatedEntryAtTime(mEntryA1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        sendUpdatedEntryAtTime(mEntryA3, 4000);
        sendUpdatedEntryAtTime(mEntryB1, 5000);
        sendUpdatedEntryAtTime(mEntryB2, 6000);

        mBubbleData.setListener(mListener);
        sendUpdatedEntryAtTime(mEntryB3, 1000 /* postTime */, 7000 /* currentTime */);
        verifyUpdateReceived();

        // B3 is in the stack
        assertThat(mBubbleData.getBubbleInStackWithKey(mBubbleB3.getKey())).isNotNull();
        // A1 is the oldest so it's in the overflow
        assertThat(mBubbleData.getOverflowBubbleWithKey(mEntryA1.getKey())).isNotNull();
        assertOrderChangedTo(mBubbleB3, mBubbleB2, mBubbleB1, mBubbleA3, mBubbleA2);
    }

    /**
     * There is one bubble in the stack. If a task matching the locusId becomes visible, suppress
     * the bubble. If it is hidden, unsuppress the bubble.
     */
    @Test
    public void test_onLocusVisibilityChanged_singleBubble() {
        sendUpdatedEntryAtTime(mEntryLocusId, 1000);
        mBubbleData.setListener(mListener);

        // Suppress the bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), true /* visible */);
        verifyUpdateReceived();
        assertBubbleSuppressed(mBubbleLocusId);
        assertOrderNotChanged();
        assertBubbleListContains(/* empty list */);

        // Unsuppress the bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), false /* visible */);
        verifyUpdateReceived();
        assertBubbleUnsuppressed(mBubbleLocusId);
        assertOrderNotChanged();
        assertBubbleListContains(mBubbleLocusId);
    }

    /**
     * Bubble stack has multiple bubbles. Suppress bubble based on matching locusId. Suppressed
     * bubble is at the top.
     *
     * When suppressed:
     * - hide bubble
     * - update order
     * - update selection
     *
     * When unsuppressed:
     * - show bubble
     * - update order
     * - update selection
     */
    @Test
    public void test_onLocusVisibilityChanged_multipleBubbles_suppressTopBubble() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        sendUpdatedEntryAtTime(mEntryLocusId, 3000);
        mBubbleData.setListener(mListener);

        // Suppress bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), true /* visible */);
        verifyUpdateReceived();
        assertBubbleSuppressed(mBubbleLocusId);
        assertSelectionChangedTo(mBubbleA2);
        assertOrderChangedTo(mBubbleA2, mBubbleA1);

        // Unsuppress bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), false /* visible */);
        verifyUpdateReceived();
        assertBubbleUnsuppressed(mBubbleLocusId);
        assertSelectionChangedTo(mBubbleLocusId);
        assertOrderChangedTo(mBubbleLocusId, mBubbleA2, mBubbleA1);
    }

    /**
     * Bubble stack has multiple bubbles. Suppress bubble based on matching locusId. Suppressed
     * bubble is not at the top.
     *
     * When suppressed:
     * - hide suppressed bubble
     * - do not update order
     * - do not update selection
     *
     * When unsuppressed:
     * - show bubble
     * - do not update order
     * - do not update selection
     */
    @Test
    public void test_onLocusVisibilityChanged_multipleBubbles_suppressStackedBubble() {
        sendUpdatedEntryAtTime(mEntryLocusId, 1000);
        sendUpdatedEntryAtTime(mEntryA1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        mBubbleData.setListener(mListener);

        // Suppress bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), true /* visible */);
        verifyUpdateReceived();
        assertBubbleSuppressed(mBubbleLocusId);
        assertSelectionNotChanged();
        assertBubbleListContains(mBubbleA2, mBubbleA1);

        // Unsuppress bubble
        mBubbleData.onLocusVisibilityChanged(100, mEntryLocusId.getLocusId(), false /* visible */);
        verifyUpdateReceived();
        assertBubbleUnsuppressed(mBubbleLocusId);
        assertSelectionNotChanged();
        assertBubbleListContains(mBubbleA2, mBubbleA1, mBubbleLocusId);
    }

    @Test
    public void test_removeBubblesForUser() {
        // A is user 1
        sendUpdatedEntryAtTime(mEntryA1, 2000);
        sendUpdatedEntryAtTime(mEntryA2, 3000);
        // B & C belong to user 11
        sendUpdatedEntryAtTime(mEntryB3, 4000);
        sendUpdatedEntryAtTime(mEntryC1, 5000);
        mBubbleData.setListener(mListener);

        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertOverflowChangedTo(ImmutableList.of(mBubbleA1));
        assertBubbleListContains(mBubbleC1, mBubbleB3, mBubbleA2);

        // Remove all the A bubbles
        mBubbleData.removeBubblesForUser(1);
        verifyUpdateReceived();

        // Verify the update has the removals.
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.removedBubbles.get(0)).isEqualTo(
                Pair.create(mBubbleA2, Bubbles.DISMISS_USER_ACCOUNT_REMOVED));
        assertThat(update.removedBubbles.get(1)).isEqualTo(
                Pair.create(mBubbleA1, Bubbles.DISMISS_USER_ACCOUNT_REMOVED));

        // Verify no A bubbles in active or overflow.
        assertBubbleListContains(mBubbleC1, mBubbleB3);
        assertOverflowChangedTo(ImmutableList.of());
    }

    @Test
    public void test_removeAppBubble_overflows() {
        String appBubbleKey = mAppBubble.getKey();
        mBubbleData.notificationEntryUpdated(mAppBubble, true /* suppressFlyout*/,
                false /* showInShade */);
        assertThat(mBubbleData.getBubbleInStackWithKey(appBubbleKey)).isEqualTo(mAppBubble);

        mBubbleData.dismissBubbleWithKey(appBubbleKey, Bubbles.DISMISS_USER_GESTURE);

        assertThat(mBubbleData.getOverflowBubbleWithKey(appBubbleKey)).isEqualTo(mAppBubble);
        assertThat(mBubbleData.getBubbleInStackWithKey(appBubbleKey)).isNull();
    }

    @Test
    public void test_getInitialStateForBubbleBar_includesInitialBubblesAndPosition() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        mPositioner.setBubbleBarLocation(BubbleBarLocation.LEFT);

        BubbleBarUpdate update = mBubbleData.getInitialStateForBubbleBar();
        assertThat(update.currentBubbleList).hasSize(2);
        assertThat(update.currentBubbleList.get(0).getKey()).isEqualTo(mEntryA2.getKey());
        assertThat(update.currentBubbleList.get(1).getKey()).isEqualTo(mEntryA1.getKey());
        assertThat(update.bubbleBarLocation).isEqualTo(BubbleBarLocation.LEFT);
        assertThat(update.expandedChanged).isFalse();
        assertThat(update.selectedBubbleKey).isEqualTo(mEntryA2.getKey());
    }

    @Test
    public void test_getInitialStateForBubbleBar_includesExpandedState() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        mPositioner.setBubbleBarLocation(BubbleBarLocation.LEFT);
        mBubbleData.setExpanded(true);

        BubbleBarUpdate update = mBubbleData.getInitialStateForBubbleBar();
        assertThat(update.currentBubbleList).hasSize(2);
        assertThat(update.currentBubbleList.get(0).getKey()).isEqualTo(mEntryA2.getKey());
        assertThat(update.currentBubbleList.get(1).getKey()).isEqualTo(mEntryA1.getKey());
        assertThat(update.bubbleBarLocation).isEqualTo(BubbleBarLocation.LEFT);
        assertThat(update.expandedChanged).isTrue();
        assertThat(update.expanded).isTrue();
        assertThat(update.selectedBubbleKey).isEqualTo(mEntryA2.getKey());
    }

    @Test
    public void setSelectedBubbleAndExpandStack() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 2000);
        mBubbleData.setListener(mListener);

        mBubbleData.setSelectedBubbleAndExpandStack(mBubbleA1);

        verifyUpdateReceived();
        assertSelectionChangedTo(mBubbleA1);
        assertExpandedChangedTo(true);
    }

    @Test
    public void testShowOverflowChanged_hasOverflowBubbles() {
        assertThat(mBubbleData.getOverflowBubbles()).isEmpty();
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        mBubbleData.setListener(mListener);

        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertThat(mUpdateCaptor.getValue().showOverflowChanged).isTrue();
        assertThat(mBubbleData.getOverflowBubbles()).isNotEmpty();
    }

    @Test
    public void testShowOverflowChanged_false_hasOverflowBubbles() {
        assertThat(mBubbleData.getOverflowBubbles()).isEmpty();
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        sendUpdatedEntryAtTime(mEntryA2, 1000);
        mBubbleData.setListener(mListener);

        // First overflowed causes change event
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertThat(mUpdateCaptor.getValue().showOverflowChanged).isTrue();
        assertThat(mBubbleData.getOverflowBubbles()).isNotEmpty();

        // Second overflow does not
        mBubbleData.dismissBubbleWithKey(mEntryA2.getKey(), Bubbles.DISMISS_USER_GESTURE);
        verifyUpdateReceived();
        assertThat(mUpdateCaptor.getValue().showOverflowChanged).isFalse();
    }

    @Test
    public void testShowOverflowChanged_noOverflowBubbles() {
        sendUpdatedEntryAtTime(mEntryA1, 1000);
        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_USER_GESTURE);
        assertThat(mBubbleData.getOverflowBubbles()).isNotEmpty();
        mBubbleData.setListener(mListener);

        mBubbleData.dismissBubbleWithKey(mEntryA1.getKey(), Bubbles.DISMISS_NOTIF_CANCEL);

        verifyUpdateReceived();
        assertThat(mUpdateCaptor.getValue().showOverflowChanged).isTrue();
        assertThat(mBubbleData.getOverflowBubbles()).isEmpty();
    }

    private void verifyUpdateReceived() {
        verify(mListener).applyUpdate(mUpdateCaptor.capture());
        reset(mListener);
    }

    private void assertBubbleAdded(Bubble expected) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("addedBubble").that(update.addedBubble).isEqualTo(expected);
    }

    private void assertBubbleRemoved(Bubble expected, @Bubbles.DismissReason int reason) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("removedBubbles").that(update.removedBubbles)
                .isEqualTo(ImmutableList.of(Pair.create(expected, reason)));
    }

    private void assertOrderNotChanged() {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("orderChanged").that(update.orderChanged).isFalse();
    }

    private void assertOrderChangedTo(Bubble... order) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("orderChanged").that(update.orderChanged).isTrue();
        assertWithMessage("bubble order").that(update.bubbles)
                .isEqualTo(ImmutableList.copyOf(order));
    }

    private void assertSelectionNotChanged() {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("selectionChanged").that(update.selectionChanged).isFalse();
    }

    private void assertSelectionChangedTo(BubbleViewProvider bubble) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("selectionChanged").that(update.selectionChanged).isTrue();
        assertWithMessage("selectedBubble").that(update.selectedBubble).isEqualTo(bubble);
    }

    private void assertSelectionCleared() {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("selectionChanged").that(update.selectionChanged).isTrue();
        assertWithMessage("selectedBubble").that(update.selectedBubble).isNull();
    }

    private void assertExpandedChangedTo(boolean expected) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("expandedChanged").that(update.expandedChanged).isTrue();
        assertWithMessage("expanded").that(update.expanded).isEqualTo(expected);
    }

    private void assertOverflowChangedTo(ImmutableList<Bubble> bubbles) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertThat(update.overflowBubbles).isEqualTo(bubbles);
    }

    private void assertBubbleListContains(Bubble... bubbles) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("bubbleList").that(update.bubbles).containsExactlyElementsIn(bubbles);
    }

    private void assertBubbleSuppressed(Bubble expected) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("suppressedBubble").that(update.suppressedBubble).isEqualTo(expected);
    }

    private void assertBubbleUnsuppressed(Bubble expected) {
        BubbleData.Update update = mUpdateCaptor.getValue();
        assertWithMessage("unsuppressedBubble").that(update.unsuppressedBubble).isEqualTo(expected);
    }

    private BubbleEntry createBubbleEntry(int userId, String notifKey, String packageName,
            NotificationListenerService.Ranking ranking) {
        return createBubbleEntry(userId, notifKey, packageName, ranking, 1000, null);
    }

    private BubbleEntry createBubbleEntry(int userId, String notifKey, String packageName,
            NotificationListenerService.Ranking ranking, LocusId locusId) {
        return createBubbleEntry(userId, notifKey, packageName, ranking, 1000, locusId);
    }

    private void setPostTime(BubbleEntry entry, long postTime) {
        when(entry.getStatusBarNotification().getPostTime()).thenReturn(postTime);
    }

    /**
     * No ExpandableNotificationRow is required to test BubbleData. This setup is all that is
     * required for BubbleData functionality and verification. NotificationTestHelper is used only
     * as a convenience to create a Notification w/BubbleMetadata.
     */
    private BubbleEntry createBubbleEntry(int userId, String notifKey, String packageName,
            NotificationListenerService.Ranking ranking, long postTime,
            LocusId locusId) {
        // BubbleMetadata
        Notification.BubbleMetadata bubbleMetadata = new Notification.BubbleMetadata.Builder(
                mExpandIntent, Icon.createWithResource("", 0))
                .setDeleteIntent(mDeleteIntent)
                .setSuppressableBubble(true)
                .build();
        // Notification -> BubbleMetadata
        Notification notification = mock(Notification.class);
        when(notification.getBubbleMetadata()).thenReturn(bubbleMetadata);
        when(notification.getLocusId()).thenReturn(locusId);

        // Notification -> extras
        notification.extras = new Bundle();

        // StatusBarNotification
        StatusBarNotification sbn = mock(StatusBarNotification.class);
        when(sbn.getKey()).thenReturn(notifKey);
        when(sbn.getUser()).thenReturn(new UserHandle(userId));
        when(sbn.getPackageName()).thenReturn(packageName);
        when(sbn.getPostTime()).thenReturn(postTime);
        when(sbn.getNotification()).thenReturn(notification);

        // NotificationEntry -> StatusBarNotification -> Notification -> BubbleMetadata
        return new BubbleEntry(sbn, ranking, true, false, false, false);
    }

    private void setCurrentTime(long time) {
        when(mTimeSource.currentTimeMillis()).thenReturn(time);
    }

    private void sendUpdatedEntryAtTime(BubbleEntry entry, long postTime) {
        setCurrentTime(postTime);
        sendUpdatedEntryAtTime(entry, postTime, true /* isTextChanged */);
    }

    private void sendUpdatedEntryAtTime(BubbleEntry entry, long postTime, long currentTime) {
        setCurrentTime(currentTime);
        sendUpdatedEntryAtTime(entry, postTime, true /* isTextChanged */);
    }

    private void sendUpdatedEntryAtTime(BubbleEntry entry, long postTime,
            boolean textChanged) {
        setPostTime(entry, postTime);
        // BubbleController calls this:
        Bubble b = mBubbleData.getOrCreateBubble(entry, null /* persistedBubble */);
        b.setTextChangedForTest(textChanged);
        // And then this
        mBubbleData.notificationEntryUpdated(b, false /* suppressFlyout*/,
                true /* showInShade */);
    }

    private void changeExpandedStateAtTime(boolean shouldBeExpanded, long time) {
        setCurrentTime(time);
        mBubbleData.setExpanded(shouldBeExpanded);
    }
}
