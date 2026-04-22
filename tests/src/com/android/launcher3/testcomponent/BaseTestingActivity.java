/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.testcomponent;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Base activity with utility methods to help automate testing.
 */
public class BaseTestingActivity extends Activity implements View.OnClickListener {

    public static final String SUFFIX_COMMAND = "-command";
    public static final String EXTRA_METHOD = "method";
    public static final String EXTRA_PARAM = "param_";

    private static final int MARGIN_DP = 20;

    private final String mAction = this.getClass().getName();

    private LinearLayout mView;
    private int mMargin;

    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            handleCommand(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMargin = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, MARGIN_DP, getResources().getDisplayMetrics()));
        mView = new LinearLayout(this);
        mView.setPadding(mMargin, mMargin, mMargin, mMargin);
        mView.setOrientation(LinearLayout.VERTICAL);
        mView.setBackgroundColor(Color.BLUE);
        setContentView(mView);

        registerReceiver(
                mCommandReceiver,
                new IntentFilter(mAction + SUFFIX_COMMAND),
                RECEIVER_EXPORTED);
    }

    protected void addButton(String title, String method) {
        Button button = new Button(this);
        button.setText(title);
        button.setTag(method);
        button.setOnClickListener(this);

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = mMargin;
        mView.addView(button, lp);
    }

    protected void addEditor(String initText, String hint, boolean requestIme) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(initText);

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = mMargin;
        mView.addView(editText, lp);
        if (requestIme) {
            editText.requestFocus();
            mView.getWindowInsetsController().show(WindowInsets.Type.ime());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sendBroadcast(new Intent(mAction).putExtra(Intent.EXTRA_INTENT, getIntent()));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mCommandReceiver);
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        handleCommand(new Intent().putExtra(EXTRA_METHOD, (String) view.getTag()));
    }

    private void handleCommand(Intent cmd) {
        String methodName = cmd.getStringExtra(EXTRA_METHOD);
        try {
            Method method = null;
            for (Method m : this.getClass().getDeclaredMethods()) {
                if (methodName.equals(m.getName()) &&
                        !Modifier.isStatic(m.getModifiers()) &&
                        Modifier.isPublic(m.getModifiers())) {
                    method = m;
                    break;
                }
            }
            Object[] args = new Object[method.getParameterTypes().length];
            Bundle extras = cmd.getExtras();
            for (int i = 0; i < args.length; i++) {
                args[i] = extras.get(EXTRA_PARAM + i);
            }
            method.invoke(this, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Intent getCommandIntent(Class<?> clazz, String method) {
        return new Intent(clazz.getName() + SUFFIX_COMMAND)
                .putExtra(EXTRA_METHOD, method);
    }
}
