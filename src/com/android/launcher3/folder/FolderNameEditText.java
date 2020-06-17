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
package com.android.launcher3.folder;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.ExtendedEditText;

import java.util.List;

/**
 * Handles additional edit text functionality to better support folder name suggestion.
 * First, makes suggestion to the InputMethodManager via {@link #displayCompletions(List)}
 * Second, intercepts whether user accepted the suggestion or manually edited their
 * folder names.
 */
public class FolderNameEditText extends ExtendedEditText {
    private static final String TAG = "FolderNameEditText";
    private static final boolean DEBUG = false;

    private boolean mEnteredCompose = false;

    public FolderNameEditText(Context context) {
        super(context);
    }

    public FolderNameEditText(Context context, AttributeSet attrs) {
        // ctor chaining breaks the touch handling
        super(context, attrs);
    }

    public FolderNameEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection con = super.onCreateInputConnection(outAttrs);
        FolderNameEditTextInputConnection connectionWrapper =
                new FolderNameEditTextInputConnection(con, true);
        return connectionWrapper;
    }

    /**
     * Send strings in @param suggestList to the IME to show up as suggestions.
     */
    public void displayCompletions(List<String> suggestList) {
        int cnt = Math.min(suggestList.size(), FolderNameProvider.SUGGEST_MAX);
        CompletionInfo[] cInfo = new CompletionInfo[cnt];
        for (int i = 0; i < cnt; i++) {
            cInfo[i] = new CompletionInfo(i, i, suggestList.get(i));
        }
        // post it to future frame so that onSelectionChanged, onFocusChanged, all other
        // TextView flag change and IME animation has settled. Ideally, there should be IMM
        // callback to notify when the IME animation and state handling is finished.
        postDelayed(() -> getContext().getSystemService(InputMethodManager.class)
                .displayCompletions(this, cInfo), 40 /* 2~3 frame delay */);
    }

    /**
     * Within 's', the 'count' characters beginning at 'start' have just replaced
     * old text 'before'
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String reason = "unknown";
        if (start == 0 && count == 0 && before > 0) {
            reason = "suggestion was rejected";
            mEnteredCompose = true;
        }
        if (DEBUG) {
            Log.d(TAG, "onTextChanged " + start + "," + before + "," + count
                    + ", " + reason);
        }
    }

    @Override
    public void onCommitCompletion(CompletionInfo text) {
        setText(text.getText());
        setSelection(text.getText().length());
        mEnteredCompose = false;
    }

    protected void setEnteredCompose(boolean value) {
        mEnteredCompose = value;
    }

    private class FolderNameEditTextInputConnection extends InputConnectionWrapper {

        FolderNameEditTextInputConnection(InputConnection target, boolean mutable) {
            super(target, mutable);
        }

        @Override
        public boolean setComposingText(CharSequence cs, int newCursorPos) {
            mEnteredCompose = true;
            return super.setComposingText(cs, newCursorPos);
        }
    }
}
