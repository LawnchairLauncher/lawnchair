package ch.deletescape.lawnchair;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class EditDropTarget extends ButtonDropTarget {

    public EditDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.uninstall_target_hover_tint);

        setDrawable(R.drawable.ic_info_launcher);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return info instanceof AppInfo;
    }

    @Override
    void completeDrop(final DragObject d) {
        new EditAppDialog(getContext(), ((AppInfo) d.dragInfo), mLauncher).show();
    }

}
