/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3;

import static com.android.launcher3.logging.KeyboardStateManager.KeyboardState.SHOW;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.android.launcher3.views.ActivityContext;

import java.util.HashSet;
import java.util.Set;


/**
 * The edit text that reports back when the back key has been pressed.
 * Note: AppCompatEditText doesn't fully support #displayCompletions and #onCommitCompletion
 */
public class ExtendedEditText extends EditText {
    private static final String TAG = "ExtendedEditText";

    private final Set<OnFocusChangeListener> mOnFocusChangeListeners = new HashSet<>();

    private boolean mForceDisableSuggestions = false;

    /**
     * Implemented by listeners of the back key.
     */
    public interface OnBackKeyListener {
        boolean onBackKey();
    }

    private OnBackKeyListener mBackKeyListener;

    public ExtendedEditText(Context context) {
        // ctor chaining breaks the touch handling
        super(context);
    }

    public ExtendedEditText(Context context, AttributeSet attrs) {
        // ctor chaining breaks the touch handling
        super(context, attrs);
    }

    public ExtendedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBackKeyListener(OnBackKeyListener listener) {
        mBackKeyListener = listener;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // If this is a back key, propagate the key back to the listener
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
                && mBackKeyListener != null) {
            return mBackKeyListener.onBackKey();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        // We don't want this view to interfere with Launcher own drag and drop.
        return false;
    }

    /**
     * Synchronously shows the soft input method.
     *
     * @return true if the keyboard is shown correctly and focus is given to this view.
     */
    public boolean showKeyboard() {
        onKeyboardShown();
        return requestFocus() && showSoftInputInternal();
    }

    /**
     * Requests the framework to show the keyboard in order to ensure that an already registered
     * controlled keyboard animation is triggered correctly.
     * Must NEVER be called in any other case than to trigger a pre-registered controlled animation.
     */
    public void requestShowKeyboardForControlledAnimation() {
        // We don't log the keyboard state, as that must happen only after the controlled animation
        // has completed.
        // We also must not request focus, as this triggers unwanted side effects.
        showSoftInputInternal();
    }

    public void hideKeyboard() {
        hideKeyboard(/* clearFocus= */ true);
    }

    public void hideKeyboard(boolean clearFocus) {
        ActivityContext.lookupContext(getContext()).hideKeyboard();
        if (clearFocus) {
            clearFocus();
        }
    }

    protected void onKeyboardShown() {
        ActivityContext.lookupContext(getContext()).getStatsLogManager()
                .keyboardStateManager().setKeyboardState(SHOW);
    }

    private boolean showSoftInputInternal() {
        boolean result = false;
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            result = imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        } else {
            Log.w(TAG, "Failed to retrieve InputMethodManager from the system.");
        }
        return result;
    }

    public void dispatchBackKey() {
        hideKeyboard();
        if (mBackKeyListener != null) {
            mBackKeyListener.onBackKey();
        }
    }

    /**
     * Set to true when you want isSuggestionsEnabled to return false.
     * Use this to disable the red underlines that appear under typos when suggestions is enabled.
     */
    public void forceDisableSuggestions(boolean forceDisableSuggestions) {
        mForceDisableSuggestions = forceDisableSuggestions;
    }

    @Override
    public boolean isSuggestionsEnabled() {
        return !mForceDisableSuggestions && super.isSuggestionsEnabled();
    }

    public void reset() {
        if (!TextUtils.isEmpty(getText())) {
            setText("");
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        // With hardware keyboard, there is a possibility that the user types before edit
        // text is visible during the transition.
        // So move the cursor to the end of the text.
        setSelection(getText().length());
    }

    /**
     * This method should be preferred to {@link #setOnFocusChangeListener(OnFocusChangeListener)},
     * as it allows for multiple listeners from different sources.
     */
    public void addOnFocusChangeListener(OnFocusChangeListener listener) {
        mOnFocusChangeListeners.add(listener);
    }

    /**
     * Removes the given listener from the set of registered focus listeners, or does nothing if it
     * wasn't registered in the first place.
     */
    public void removeOnFocusChangeListener(OnFocusChangeListener listener) {
        mOnFocusChangeListeners.remove(listener);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        for (OnFocusChangeListener listener : mOnFocusChangeListeners) {
            listener.onFocusChange(this, focused);
        }
    }

    /**
     * Save the input, suggestion, hint states when it's on focus, and set to unfocused states.
     */
    public void saveFocusedStateAndUpdateToUnfocusedState() {}

    /**
     * Restore to the previous saved focused state.
     */
    public void restoreToFocusedState() {}
}
