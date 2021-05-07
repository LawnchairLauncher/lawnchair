/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep.interaction;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;

import java.net.URISyntaxException;

/**
 * A page shows after SUW flow to hint users to swipe up from the bottom of the screen to go home
 * for the gestural system navigation.
 */
public class AllSetActivity extends Activity {

    private static final String LOG_TAG = "AllSetActivity";
    private static final String URI_SYSTEM_NAVIGATION_SETTING =
            "#Intent;action=com.android.settings.SEARCH_RESULT_TRAMPOLINE;S.:settings:fragment_args_key=gesture_system_navigation_input_summary;S.:settings:show_fragment=com.android.settings.gestures.SystemNavigationGestureSettings;end";
    private static final String EXTRA_ACCENT_COLOR_DARK_MODE = "accent_color_dark_mode";
    private static final String EXTRA_ACCENT_COLOR_LIGHT_MODE = "accent_color_light_mode";

    private int mAccentColor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allset);
        setTitle(R.string.allset_title);

        final int mode =
                getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        mAccentColor = getIntent().getIntExtra(
                mode == Configuration.UI_MODE_NIGHT_YES
                        ? EXTRA_ACCENT_COLOR_DARK_MODE : EXTRA_ACCENT_COLOR_LIGHT_MODE,
                /* defValue= */ Color.BLACK);

        ((ImageView) findViewById(R.id.icon)).getDrawable().mutate().setTint(mAccentColor);

        TextView navigationSettings = findViewById(R.id.navigation_settings);
        navigationSettings.setMovementMethod(LinkMovementMethod.getInstance());
        AnnotationSpan.LinkInfo linkInfo = new AnnotationSpan.LinkInfo(
                AnnotationSpan.LinkInfo.DEFAULT_ANNOTATION,
                new AllSetLinkSpan(
                        /* context= */ this,
                        view -> {
                            try {
                                startActivityForResult(
                                        Intent.parseUri(URI_SYSTEM_NAVIGATION_SETTING, 0), 0);
                            } catch (URISyntaxException e) {
                                Log.e(LOG_TAG, "Failed to parse system nav settings intent", e);
                            }
                            finish();
                        }));
        navigationSettings.setText(
                AnnotationSpan.linkify(getText(R.string.allset_navigation_settings), linkInfo));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private final class AllSetLinkSpan extends AnnotationSpan {

        private final String mFontFamily;
        private final int mTextSize;

        AllSetLinkSpan(Context context, View.OnClickListener listener) {
            super(listener);
            TypedArray typedArray =
                    context.obtainStyledAttributes(R.style.TextAppearance_GestureTutorial_LinkText,
                            R.styleable.AllSetLinkSpan);
            mFontFamily = typedArray.getString(R.styleable.AllSetLinkSpan_android_fontFamily);
            mTextSize =
                    typedArray.getDimensionPixelSize(
                            R.styleable.AllSetLinkSpan_android_textSize, /* defValue= */ -1);
            typedArray.recycle();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(mAccentColor);
            ds.setTypeface(Typeface.create(mFontFamily, Typeface.NORMAL));
            ds.setUnderlineText(false);
            if (mTextSize != -1) {
                ds.setTextSize(mTextSize);
            }
        }
    }

}
