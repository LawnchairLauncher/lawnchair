/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.keyguard;

import android.annotation.CallSuper;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * A View similar to a textView which contains password text and can animate when the text is
 * changed
 */
public abstract class BasePasswordTextView extends FrameLayout {
    private String mText = "";
    private UserActivityListener mUserActivityListener;
    protected boolean mIsPinHinting;
    protected PinShapeInput mPinShapeInput;
    protected boolean mShowPassword = true;
    protected boolean mUsePinShapes = false;
    protected static final char DOT = '\u2022';

    /** Listens to user activities like appending, deleting and resetting PIN text */
    public interface UserActivityListener {

        /** Listens to user activities. */
        void onUserActivity();
    }

    public BasePasswordTextView(Context context) {
        this(context, null);
    }

    public BasePasswordTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BasePasswordTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BasePasswordTextView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected abstract PinShapeInput inflatePinShapeInput(boolean isPinHinting);

    protected abstract boolean shouldSendAccessibilityEvent();

    protected void onAppend(char c, int newLength) {}

    protected void onDelete(int index) {}

    protected void onReset(boolean animated) {}

    @CallSuper
    protected void onUserActivity() {
        if (mUserActivityListener != null) {
            mUserActivityListener.onUserActivity();
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Appends a PIN text */
    public void append(char c) {
        CharSequence textbefore = getTransformedText();

        mText = mText + c;
        int newLength = mText.length();
        onAppend(c, newLength);

        if (mPinShapeInput != null) {
            mPinShapeInput.append();
        }

        onUserActivity();

        sendAccessibilityEventTypeViewTextChanged(textbefore, textbefore.length(), 0, 1);
    }

    /** Sets a listener who is notified on user activity */
    public void setUserActivityListener(UserActivityListener userActivityListener) {
        mUserActivityListener = userActivityListener;
    }

    /** Deletes the last PIN text */
    public void deleteLastChar() {
        int length = mText.length();
        if (length > 0) {
            CharSequence textbefore = getTransformedText();

            mText = mText.substring(0, length - 1);
            onDelete(length - 1);

            if (mPinShapeInput != null) {
                mPinShapeInput.delete();
            }

            sendAccessibilityEventTypeViewTextChanged(textbefore, textbefore.length() - 1, 1, 0);
        }
        onUserActivity();
    }

    /** Gets entered PIN text */
    public String getText() {
        return mText;
    }

    /** Gets a transformed text for accessibility event. Called before text changed. */
    protected CharSequence getTransformedText() {
        return String.valueOf(DOT).repeat(mText.length());
    }

    /** Gets a transformed text for accessibility event. Called after text changed. */
    protected CharSequence getTransformedText(int fromIndex, int removedCount, int addedCount) {
        return getTransformedText();
    }

    /** Reset PIN text without error */
    public void reset(boolean animated, boolean announce) {
        reset(false /* error */, animated, announce);
    }

    /** Reset PIN text */
    public void reset(boolean error, boolean animated, boolean announce) {
        CharSequence textbefore = getTransformedText();

        mText = "";

        onReset(animated);
        if (animated) {
            onUserActivity();
        }

        if (mPinShapeInput != null) {
            if (error) {
                mPinShapeInput.resetWithError();
            } else {
                mPinShapeInput.reset();
            }
        }

        if (announce) {
            sendAccessibilityEventTypeViewTextChanged(textbefore, 0, textbefore.length(), 0);
        }
    }

    void sendAccessibilityEventTypeViewTextChanged(
            CharSequence beforeText, int fromIndex, int removedCount, int addedCount) {
        if (AccessibilityManager.getInstance(mContext).isEnabled()
                && shouldSendAccessibilityEvent()) {
            AccessibilityEvent event =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
            event.setFromIndex(fromIndex);
            event.setRemovedCount(removedCount);
            event.setAddedCount(addedCount);
            event.setBeforeText(beforeText);
            CharSequence transformedText = getTransformedText(fromIndex, removedCount, addedCount);
            if (!TextUtils.isEmpty(transformedText)) {
                event.getText().add(transformedText);
            }
            event.setPassword(true);
            sendAccessibilityEventUnchecked(event);
        }
    }

    /** Sets whether to use pin shapes. */
    public void setUsePinShapes(boolean usePinShapes) {
        mUsePinShapes = usePinShapes;
    }

    /** Determines whether AutoConfirmation feature is on. */
    public void setIsPinHinting(boolean isPinHinting) {
        // Do not reinflate the view if we are using the same one.
        if (mPinShapeInput != null && mIsPinHinting == isPinHinting) {
            return;
        }
        mIsPinHinting = isPinHinting;

        if (mPinShapeInput != null) {
            removeView(mPinShapeInput.getView());
            mPinShapeInput = null;
        }

        mPinShapeInput = inflatePinShapeInput(isPinHinting);
        addView(mPinShapeInput.getView());
    }

    /** Controls whether the last entered digit is briefly shown after being entered */
    public void setShowPassword(boolean enabled) {
        mShowPassword = enabled;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);

        event.setClassName(EditText.class.getName());
        event.setPassword(true);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setClassName(EditText.class.getName());
        info.setPassword(true);
        info.setText(getTransformedText());
        info.setSelected(false);
        info.setEditable(true);

        info.setInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
}
