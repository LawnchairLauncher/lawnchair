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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.bubbles.BubbleInfo;

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
    private ShellExecutor mMainExecutor;

    private BubbleEntry mBubbleEntry;
    private Bundle mExtras;
    private Bubble mBubble;

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
        when(mSbn.getNotification()).thenReturn(mNotif);
        when(mNotif.getBubbleMetadata()).thenReturn(metadata);
        when(mSbn.getKey()).thenReturn("mock");
        mBubbleEntry = new BubbleEntry(mSbn, null, true, false, false, false);
        mBubble = new Bubble(mBubbleEntry, mBubbleMetadataFlagListener, null, mMainExecutor);
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
        assertThat(mBubble.showInShade()).isTrue();

        mBubble.setSuppressNotification(true);

        assertThat(mBubble.showInShade()).isFalse();

        verify(mBubbleMetadataFlagListener).onBubbleMetadataFlagChanged(mBubble);
    }

    @Test
    public void testBubbleMetadataFlagListener_noChange_doesntNotify() {
        assertThat(mBubble.showInShade()).isTrue();

        mBubble.setSuppressNotification(false);

        verify(mBubbleMetadataFlagListener, never()).onBubbleMetadataFlagChanged(any());
    }

    @Test
    public void testBubbleIsConversation_hasConversationShortcut() {
        Bubble bubble = createBubbleWithShortcut();
        assertThat(bubble.getShortcutInfo()).isNotNull();
        assertThat(bubble.isConversation()).isTrue();
    }

    @Test
    public void testBubbleIsConversation_hasNoShortcut() {
        Bubble bubble = new Bubble(mBubbleEntry, mBubbleMetadataFlagListener, null, mMainExecutor);
        assertThat(bubble.getShortcutInfo()).isNull();
        assertThat(bubble.isConversation()).isFalse();
    }

    @Test
    public void testBubbleAsBubbleBarBubble_withShortcut() {
        Bubble bubble = createBubbleWithShortcut();
        BubbleInfo bubbleInfo = bubble.asBubbleBarBubble();

        assertThat(bubble.getShortcutInfo()).isNotNull();
        assertThat(bubbleInfo.getShortcutId()).isNotNull();
        assertThat(bubbleInfo.getShortcutId()).isEqualTo(bubble.getShortcutId());
        assertThat(bubbleInfo.getKey()).isEqualTo(bubble.getKey());
        assertThat(bubbleInfo.getUserId()).isEqualTo(bubble.getUser().getIdentifier());
        assertThat(bubbleInfo.getPackageName()).isEqualTo(bubble.getPackageName());
    }

    @Test
    public void testBubbleAsBubbleBarBubble_withoutShortcut() {
        Intent intent = new Intent(mContext, BubblesTestActivity.class);
        intent.setPackage(mContext.getPackageName());
        Bubble bubble = Bubble.createAppBubble(intent, new UserHandle(1 /* userId */),
                null /* icon */, mMainExecutor);
        BubbleInfo bubbleInfo = bubble.asBubbleBarBubble();

        assertThat(bubble.getShortcutInfo()).isNull();
        assertThat(bubbleInfo.getShortcutId()).isNull();
        assertThat(bubbleInfo.getKey()).isEqualTo(bubble.getKey());
        assertThat(bubbleInfo.getUserId()).isEqualTo(bubble.getUser().getIdentifier());
        assertThat(bubbleInfo.getPackageName()).isEqualTo(bubble.getPackageName());
    }

    private Bubble createBubbleWithShortcut() {
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext)
                .setId("mockShortcutId")
                .build();
        return new Bubble("mockKey", shortcutInfo, 10, Resources.ID_NULL,
                "mockTitle", 0 /* taskId */, "mockLocus", true /* isDismissible */,
                mMainExecutor, mBubbleMetadataFlagListener);
    }
}
