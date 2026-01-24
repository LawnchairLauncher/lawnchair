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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.bubbles.BubbleInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class BubbleTest extends ShellTestCase {
    @Mock
    private Notification mNotif;
    @Mock
    private StatusBarNotification mSbn;
    @Mock
    private NotificationListenerService.Ranking mRanking;
    @Mock
    private ShellExecutor mMainExecutor;
    @Mock
    private ShellExecutor mBgExecutor;

    private Bundle mExtras;

    // This entry / bubble are set up with PendingIntent / Icon API for chat
    private BubbleEntry mBubbleEntry;
    private Bubble mChatBubble;

    @Mock
    private Bubbles.BubbleMetadataFlagListener mBubbleMetadataFlagListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mExtras = new Bundle();
        mNotif.extras = mExtras;

        Intent target = new Intent(mContext, BubblesTestActivity.class);
        Notification.BubbleMetadata metadata = new Notification.BubbleMetadata.Builder(
                PendingIntent.getActivity(mContext, 0, target, PendingIntent.FLAG_MUTABLE),
                Icon.createWithResource(mContext, R.drawable.bubble_ic_create_bubble))
                .build();
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext)
                .setId("shortcutId")
                .build();
        when(mSbn.getNotification()).thenReturn(mNotif);
        when(mNotif.getBubbleMetadata()).thenReturn(metadata);
        when(mSbn.getKey()).thenReturn("mock");
        when(mRanking.getConversationShortcutInfo()).thenReturn(shortcutInfo);

        mBubbleEntry = new BubbleEntry(mSbn, mRanking, true, false, false, false);
        mChatBubble = new Bubble(mBubbleEntry, mBubbleMetadataFlagListener, null, mMainExecutor,
                mBgExecutor);
    }

    @Test
    public void testGetUpdateMessage_default() {
        final String msg = "Hello there!";
        doReturn(Notification.Style.class).when(mNotif).getNotificationStyle();
        mExtras.putCharSequence(Notification.EXTRA_TEXT, msg);
        assertEquals(msg, Bubble.extractFlyoutMessage(mBubbleEntry).message);
    }

    @Test
    public void testGetUpdateMessage_bigText() {
        final String msg = "A big hello there!";
        doReturn(Notification.BigTextStyle.class).when(mNotif).getNotificationStyle();
        mExtras.putCharSequence(Notification.EXTRA_TEXT, "A small hello there.");
        mExtras.putCharSequence(Notification.EXTRA_BIG_TEXT, msg);

        // Should be big text, not the small text.
        assertEquals(msg, Bubble.extractFlyoutMessage(mBubbleEntry).message);
    }

    @Test
    public void testGetUpdateMessage_media() {
        doReturn(Notification.MediaStyle.class).when(mNotif).getNotificationStyle();

        // Media notifs don't get update messages.
        assertNull(Bubble.extractFlyoutMessage(mBubbleEntry).message);
    }

    @Test
    public void testGetUpdateMessage_inboxStyle() {
        doReturn(Notification.InboxStyle.class).when(mNotif).getNotificationStyle();
        mExtras.putCharSequenceArray(
                Notification.EXTRA_TEXT_LINES,
                new CharSequence[]{
                        "How do you feel about tests?",
                        "They're okay, I guess.",
                        "I hate when they're flaky.",
                        "Really? I prefer them that way."});

        // Should be the last one only.
        assertEquals("Really? I prefer them that way.",
                Bubble.extractFlyoutMessage(mBubbleEntry).message);
    }

    @Test
    public void testGetUpdateMessage_messagingStyle() {
        doReturn(Notification.MessagingStyle.class).when(mNotif).getNotificationStyle();
        mExtras.putParcelableArray(
                Notification.EXTRA_MESSAGES,
                new Bundle[]{
                        new Notification.MessagingStyle.Message(
                                "Hello", 0, "Josh").toBundle(),
                        new Notification.MessagingStyle.Message(
                                "Oh, hello!", 0, "Mady").toBundle()});

        // Should be the last one only.
        assertEquals("Oh, hello!", Bubble.extractFlyoutMessage(mBubbleEntry).message);
        assertEquals("Mady", Bubble.extractFlyoutMessage(mBubbleEntry).senderName);
    }

    @Test
    public void testBubbleMetadataFlagListener_change_notified() {
        assertThat(mChatBubble.showInShade()).isTrue();

        mChatBubble.setSuppressNotification(true);

        assertThat(mChatBubble.showInShade()).isFalse();

        verify(mBubbleMetadataFlagListener).onBubbleMetadataFlagChanged(mChatBubble);
    }

    @Test
    public void testBubbleMetadataFlagListener_noChange_doesntNotify() {
        assertThat(mChatBubble.showInShade()).isTrue();

        mChatBubble.setSuppressNotification(false);

        verify(mBubbleMetadataFlagListener, never()).onBubbleMetadataFlagChanged(any());
    }

    @Test
    public void testBubbleType_conversationShortcut() {
        Bubble bubble = createChatBubble(true /* useShortcut */);
        assertThat(bubble.isChat()).isTrue();
    }

    @Test
    public void testBubbleType_conversationPendingIntent() {
        Bubble bubble = createChatBubble(false /* useShortcut */);
        assertThat(bubble.isChat()).isTrue();
    }

    @Test
    public void testBubbleType_note() {
        Bubble bubble = Bubble.createNotesBubble(createIntent(), UserHandle.of(0),
                mock(Icon.class),
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isNote()).isTrue();
    }

    @Test
    public void testBubbleType_shortcut() {
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext)
                .setId("mockShortcutId")
                .build();
        Bubble bubble = Bubble.createShortcutBubble(shortcutInfo, mMainExecutor, mBgExecutor);
        assertThat(bubble.isShortcut()).isTrue();
    }

    @Test
    public void testBubbleType_intent() {
        Bubble bubble = Bubble.createAppBubble(createIntent(), UserHandle.of(0),
                mock(Icon.class),
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isApp()).isTrue();
    }

    @Test
    public void testBubbleType_taskId() {
        TaskInfo info = mock(TaskInfo.class);
        ComponentName componentName = mock(ComponentName.class);
        when(componentName.getPackageName()).thenReturn(mContext.getPackageName());
        info.taskId = 1;
        info.baseActivity = componentName;
        info.baseIntent = createIntent();
        Bubble bubble = Bubble.createTaskBubble(info, UserHandle.of(0),
                mock(Icon.class),
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isApp()).isTrue();
        assertThat(bubble.getIntent()).isNotNull();
    }

    @Test
    public void testShowAppBadge_chat() {
        Bubble bubble = createChatBubble(true /* useShortcut */);
        assertThat(bubble.isChat()).isTrue();
        assertThat(bubble.showAppBadge()).isTrue();
    }

    @Test
    public void testShowAppBadge_note() {
        Bubble bubble = Bubble.createNotesBubble(createIntent(), UserHandle.of(0),
                mock(Icon.class),
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isNote()).isTrue();
        assertThat(bubble.showAppBadge()).isTrue();
    }

    @Test
    public void testShowAppBadge_app() {
        Bubble bubble = Bubble.createAppBubble(createIntent(), UserHandle.of(0),
                mock(Icon.class),
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isApp()).isTrue();
        assertThat(bubble.showAppBadge()).isFalse();
    }

    @Test
    public void testShowAppBadge_shortcut() {
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext)
                .setId("mockShortcutId")
                .build();
        Bubble bubble = Bubble.createShortcutBubble(shortcutInfo,
                mMainExecutor, mBgExecutor);
        assertThat(bubble.isShortcut()).isTrue();
        assertThat(bubble.showAppBadge()).isTrue();
    }

    @Test
    public void testBubbleAsBubbleBarBubble_withShortcut() {
        Bubble bubble = createChatBubble(true /* useShortcut */);
        BubbleInfo bubbleInfo = bubble.asBubbleBarBubble();

        assertThat(bubble.getShortcutInfo()).isNotNull();
        assertThat(bubbleInfo.getShortcutId()).isNotNull();
        assertThat(bubbleInfo.getShortcutId()).isEqualTo(bubble.getShortcutId());
        assertThat(bubbleInfo.getKey()).isEqualTo(bubble.getKey());
        assertThat(bubbleInfo.getUserId()).isEqualTo(bubble.getUser().getIdentifier());
        assertThat(bubbleInfo.getPackageName()).isEqualTo(bubble.getPackageName());
    }

    @Test
    public void testBubbleAsBubbleBarBubble_withIntent() {
        Intent intent = new Intent(mContext, BubblesTestActivity.class);
        intent.setPackage(mContext.getPackageName());
        Bubble bubble = Bubble.createAppBubble(intent, new UserHandle(1 /* userId */),
                null /* icon */, mMainExecutor, mBgExecutor);
        BubbleInfo bubbleInfo = bubble.asBubbleBarBubble();

        assertThat(bubble.getShortcutInfo()).isNull();
        assertThat(bubbleInfo.getShortcutId()).isNull();
        assertThat(bubbleInfo.getKey()).isEqualTo(bubble.getKey());
        assertThat(bubbleInfo.getUserId()).isEqualTo(bubble.getUser().getIdentifier());
        assertThat(bubbleInfo.getPackageName()).isEqualTo(bubble.getPackageName());
    }

    @Test
    public void testCleanupTaskView() {
        // Create a bubble with a task id
        TaskInfo info = mock(TaskInfo.class);
        info.taskId = 123;
        info.baseActivity = new ComponentName(mContext, "SomeActivity");
        Bubble bubble = Bubble.createTaskBubble(info, new UserHandle(1),
                null /* icon */, mMainExecutor, mBgExecutor);
        assertThat(bubble.getTaskId()).isEqualTo(123);

        bubble.cleanupTaskView();
        assertThat(bubble.getTaskId()).isEqualTo(INVALID_TASK_ID);
    }

    private Intent createIntent() {
        Intent intent = new Intent(mContext, BubblesTestActivity.class);
        intent.setPackage(mContext.getPackageName());
        return intent;
    }

    private Bubble createChatBubble(boolean useShortcut) {
        if (useShortcut) {
            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext)
                    .setId("mockShortcutId")
                    .build();
            return new Bubble("mockKey", shortcutInfo, 10, Resources.ID_NULL,
                    "mockTitle", 0 /* taskId */, "mockLocus", true /* isDismissible */,
                    mMainExecutor, mBgExecutor, mBubbleMetadataFlagListener);
        } else {
            return new Bubble(mBubbleEntry, mBubbleMetadataFlagListener, null, mMainExecutor,
                    mBgExecutor);
        }
    }
}
