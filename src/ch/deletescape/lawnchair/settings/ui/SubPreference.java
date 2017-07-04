package ch.deletescape.lawnchair.settings.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;

import ch.deletescape.lawnchair.R;

public class SubPreference extends Preference {

    private int mContent;

    public SubPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SubPreference);
        for (int i = a.getIndexCount() - 1; i >= 0; i--) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.SubPreference_content:
                    mContent = a.getResourceId(attr, 0);
                    break;
            }
        }
        a.recycle();
        setFragment("");
    }

    public int getContent() {
        return mContent;
    }
}