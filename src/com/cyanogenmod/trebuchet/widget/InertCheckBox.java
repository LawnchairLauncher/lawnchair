package com.cyanogenmod.trebuchet.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.CheckBox;


// CheckBox that does not react to any user event in order to let the container handle them.
public class InertCheckBox extends CheckBox {

    @SuppressWarnings("unused")
    public InertCheckBox(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public InertCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressWarnings("unused")
    public InertCheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        // Make the checkbox not respond to any user event
        return false;
    }
}