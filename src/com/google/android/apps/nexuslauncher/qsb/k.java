package com.google.android.apps.nexuslauncher.qsb;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;

@TargetApi(26)
public class k {
    private static k INSTANCE;
    Bitmap DX;
    private final ArrayList<o> mListeners = new ArrayList<>(2);

    public static k getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new k(context.getApplicationContext());
        }
        return INSTANCE;
    }

    private k(Context context) {

    }

    private void notifyListeners() {
        for (o listener : mListeners) {
            listener.dM();
        }
    }

    public final int getBackgroundColor() {
        // pixel_2017_qsb_background_color
        return 0x99FAFAFA;
    }

    public final int micOpacity() {
        // pixel_2018_qsb_mic_opacity
        return Color.alpha(getBackgroundColor());
    }

    public final float micStrokeWidth() {
        // pixel_2018_qsb_mic_stroke_width_dp
        return 0f;
    }

    public final String hintTextValue() {
        // pixel_2017_qsb_hint_text_value
        return "";
    }

    public final boolean useTwoBubbles() {
        // pixel_2018_qsb_use_two_bubbles
        return false;
    }

    public final boolean eg() {
        // pixel_2017_qsb_use_colored_g
        return false;
    }

    public final boolean hintIsForAssistant() {
        // pixel_2018_qsb_hint_is_for_assistant
        return false;
    }

    public final void a(o oVar) {
        this.mListeners.add(oVar);
    }

    public final void b(o oVar) {
        this.mListeners.remove(oVar);
    }
}
