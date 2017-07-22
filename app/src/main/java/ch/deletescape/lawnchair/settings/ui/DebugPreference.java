package ch.deletescape.lawnchair.settings.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class DebugPreference extends SubPreference implements View.OnLongClickListener {

    public DebugPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onLongClick(View view) {
        return false;
    }
}
