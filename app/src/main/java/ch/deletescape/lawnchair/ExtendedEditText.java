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
package ch.deletescape.lawnchair;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;


/**
 * The edit text that reports back when the back key has been pressed.
 */
public class ExtendedEditText extends EditText {

    private boolean mShowImeAfterFirstLayout;
    private int mHintTextColor;

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
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            return mBackKeyListener != null && mBackKeyListener.onBackKey();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        // We don't want this view to interfere with Launcher own drag and drop.
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mShowImeAfterFirstLayout) {
            // soft input only shows one frame after the layout of the EditText happens,
            post(new Runnable() {
                @Override
                public void run() {
                    showSoftInput();
                    mShowImeAfterFirstLayout = false;
                }
            });
        }
    }

    public void dispatchBackKey() {
        ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(), 0);
        if (this.mBackKeyListener != null) {
            this.mBackKeyListener.onBackKey();
        }
    }

    public void showKeyboard() {
        mShowImeAfterFirstLayout = !showSoftInput();
    }

    private boolean showSoftInput() {
        return requestFocus() &&
                ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

    }

    public void setCursorColor(@ColorInt int color) {
        try {
            // Get the cursor resource id
            Field field = TextView.class.getDeclaredField("mCursorDrawableRes");
            field.setAccessible(true);
            int drawableResId = field.getInt(this);

            // Get the editor
            field = TextView.class.getDeclaredField("mEditor");
            field.setAccessible(true);
            Object editor = field.get(this);

            // Get the drawable and set a color filter
            Drawable drawable = ContextCompat.getDrawable(getContext(), drawableResId);
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            Drawable[] drawables = {drawable, drawable};

            // Set the drawables
            field = editor.getClass().getDeclaredField("mCursorDrawable");
            field.setAccessible(true);
            field.set(editor, drawables);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) {
            if (getCurrentHintTextColor() != Color.TRANSPARENT) {
                mHintTextColor = getCurrentHintTextColor();
                setHintTextColor(Color.TRANSPARENT);
            }
        } else {
            setHintTextColor(mHintTextColor);
        }
    }
}
