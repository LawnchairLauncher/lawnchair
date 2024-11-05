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

package com.android.wm.shell.compatui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

/**
 * Tests for {@link RestartDialogLayout}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:RestartDialogLayoutTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class RestartDialogLayoutTest extends ShellTestCase  {

    @Mock private Runnable mDismissCallback;
    @Mock private Consumer<Boolean> mRestartCallback;

    private RestartDialogLayout mLayout;
    private View mDismissButton;
    private View mRestartButton;
    private View mDialogContainer;
    private CheckBox mDontRepeatCheckBox;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLayout = (RestartDialogLayout)
                LayoutInflater.from(mContext).inflate(R.layout.letterbox_restart_dialog_layout,
                        null);
        mDismissButton = mLayout.findViewById(R.id.letterbox_restart_dialog_dismiss_button);
        mRestartButton = mLayout.findViewById(R.id.letterbox_restart_dialog_restart_button);
        mDialogContainer = mLayout.findViewById(R.id.letterbox_restart_dialog_container);
        mDontRepeatCheckBox = mLayout.findViewById(R.id.letterbox_restart_dialog_checkbox);
        mLayout.setDismissOnClickListener(mDismissCallback);
        mLayout.setRestartOnClickListener(mRestartCallback);
    }

    @Test
    public void testOnFinishInflate() {
        assertEquals(mLayout.getDialogContainerView(),
                mLayout.findViewById(R.id.letterbox_restart_dialog_container));
        assertEquals(mLayout.getDialogTitle(),
                mLayout.findViewById(R.id.letterbox_restart_dialog_title));
        assertEquals(mLayout.getBackgroundDimDrawable(), mLayout.getBackground());
        assertEquals(mLayout.getBackground().getAlpha(), 0);
    }

    @Test
    public void testOnDismissButtonClicked() {
        assertTrue(mDismissButton.performClick());

        verify(mDismissCallback).run();
    }

    @Test
    public void testOnRestartButtonClickedWithoutCheckbox() {
        mDontRepeatCheckBox.setChecked(false);
        assertTrue(mRestartButton.performClick());

        verify(mRestartCallback).accept(false);
    }

    @Test
    public void testOnRestartButtonClickedWithCheckbox() {
        mDontRepeatCheckBox.setChecked(true);
        assertTrue(mRestartButton.performClick());

        verify(mRestartCallback).accept(true);
    }

    @Test
    public void testOnBackgroundClickedDoesntDismiss() {
        assertFalse(mLayout.performClick());

        verify(mDismissCallback, never()).run();
    }

    @Test
    public void testOnDialogContainerClicked() {
        assertTrue(mDialogContainer.performClick());

        verify(mDismissCallback, never()).run();
        verify(mRestartCallback, never()).accept(anyBoolean());
    }

    @Test
    public void testSetDismissOnClickListenerNull() {
        mLayout.setDismissOnClickListener(null);

        assertFalse(mDismissButton.performClick());
        assertFalse(mLayout.performClick());
        assertTrue(mDialogContainer.performClick());

        verify(mDismissCallback, never()).run();
    }

    @Test
    public void testSetRestartOnClickListenerNull() {
        mLayout.setRestartOnClickListener(null);

        assertFalse(mRestartButton.performClick());
        assertFalse(mLayout.performClick());
        assertTrue(mDialogContainer.performClick());

        verify(mRestartCallback, never()).accept(anyBoolean());
    }

}
