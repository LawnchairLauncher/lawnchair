package com.google.android.apps.nexuslauncher.superg;

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

import ch.deletescape.lawnchair.LawnchairUtilsKt;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

import java.util.Locale;

public class DateWidgetView extends LinearLayout implements TextWatcher {
    private String mText = "";
    private float mDateText1TextSize;
    private DoubleShadowTextClock mDateText1;
    private DoubleShadowTextClock mDateText2;
    private int mWidth = 0;

    public DateWidgetView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDateText1 = findViewById(R.id.date_text1);
        mDateText1TextSize = mDateText1.getTextSize();
        mDateText1.addTextChangedListener(this);
        mDateText1.setFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd"));
        mDateText2 = findViewById(R.id.date_text2);
        mDateText2.setFormat(getContext().getString(R.string.week_day_format, "EEEE", "yyyy"));
        init();
    }

    private void init() {
        Locale locale = Locale.getDefault();
        if (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            Paint paint = mDateText1.getPaint();
            Rect rect = new Rect();
            paint.getTextBounds("x", 0, 1, rect);
            int height = rect.height();
            mDateText2.setPadding(0, 0, 0, ((int) (Math.abs(paint.getFontMetrics().ascent) - ((float) height))) / 2);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Launcher launcher = LawnchairUtilsKt.getLauncherOrNull(getContext());
        int marginEnd;
        if (launcher != null) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            int size = MeasureSpec.getSize(widthMeasureSpec) / deviceProfile.inv.numColumns;
            marginEnd = (size - deviceProfile.iconSizePx) / 2;
            mWidth = (deviceProfile.inv.numColumns - Math.max(1, (int) Math.ceil((double) (getResources().getDimension(R.dimen.qsb_min_width_with_mic) / ((float) size))))) * size;
            mText = "";
            update();
        } else {
            marginEnd = getResources().getDimensionPixelSize(R.dimen.smartspace_preview_widget_margin);
        }
        setMarginEnd(mDateText1, marginEnd);
        setMarginEnd(mDateText2, marginEnd);
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
        if (mWidth > 0) {
            String dateText1Text = mDateText1.getText().toString();
            if (!mText.equals(dateText1Text)) {
                mText = dateText1Text;
                if (!dateText1Text.isEmpty()) {
                    TextPaint paint = mDateText1.getPaint();
                    float textSize = paint.getTextSize();
                    float size = mDateText1TextSize;
                    for (int i = 0; i < 10; i++) {
                        paint.setTextSize(size);
                        float measureText = paint.measureText(dateText1Text);
                        if (measureText <= ((float) mWidth)) {
                            break;
                        }
                        size = (size * ((float) mWidth)) / measureText;
                    }
                    if (Float.compare(size, textSize) == 0) {
                        paint.setTextSize(textSize);
                    } else {
                        mDateText1.setTextSize(0, size);
                        init();
                    }
                }
            }
        }
    }
}