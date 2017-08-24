package com.android.launcher3.pixel;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

import java.util.Locale;

public class DateWidgetView extends LinearLayout implements TextWatcher {
    private String text = "";
    private float dateText1TextSize;
    private DoubleShadowTextClock dateText1;
    private DoubleShadowTextClock dateText2;
    private int width = 0;

    public DateWidgetView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        dateText1 = findViewById(R.id.date_text1);
        dateText1TextSize = dateText1.getTextSize();
        dateText1.addTextChangedListener(this);
        dateText1.setFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd"));
        dateText2 = findViewById(R.id.date_text2);
        dateText2.setFormat(getContext().getString(R.string.week_day_format, "EEEE", "yyyy"));
        init();
    }

    private void init() {
        Locale locale = Locale.getDefault();
        if (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            Paint paint = dateText1.getPaint();
            Rect rect = new Rect();
            paint.getTextBounds("x", 0, 1, rect);
            int height = rect.height();
            dateText2.setPadding(0, 0, 0, ((int) (Math.abs(paint.getFontMetrics().ascent) - ((float) height))) / 2);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
        int size = MeasureSpec.getSize(widthMeasureSpec) / deviceProfile.inv.numColumns;
        int marginEnd = (size - deviceProfile.iconSizePx) / 2;
        width = (deviceProfile.inv.numColumns - Math.max(1, (int) Math.ceil((double) (getResources().getDimension(R.dimen.qsb_min_width_with_mic) / ((float) size))))) * size;
        text = "";
        update();
        setMarginEnd(dateText1, marginEnd);
        setMarginEnd(dateText2, marginEnd);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void setMarginEnd(View view, int marginEnd) {
        LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
        layoutParams.setMarginEnd(marginEnd);
        layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        update();
    }

    private void update() {
        if (width > 0) {
            String dateText1Text = dateText1.getText().toString();
            if (!text.equals(dateText1Text)) {
                text = dateText1Text;
                if (!dateText1Text.isEmpty()) {
                    TextPaint paint = dateText1.getPaint();
                    float textSize = paint.getTextSize();
                    float size = dateText1TextSize;
                    for (int i = 0; i < 10; i++) {
                        paint.setTextSize(size);
                        float measureText = paint.measureText(dateText1Text);
                        if (measureText <= ((float) width)) {
                            break;
                        }
                        size = (size * ((float) width)) / measureText;
                    }
                    if (Float.compare(size, textSize) == 0) {
                        paint.setTextSize(textSize);
                    } else {
                        dateText1.setTextSize(0, size);
                        init();
                    }
                }
            }
        }
    }
}
