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
    private static SharedPreferences sharedPrefs;
    private static Set<String> hiddenApps;
    private static Context mContext;

    public EditDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        sharedPrefs = Utilities.getPrefs(context.getApplicationContext());
        hiddenApps = sharedPrefs.getStringSet("pref_hiddenApps", null);
        if (hiddenApps == null) {
            hiddenApps = new HashSet<>();
        }
        mContext = context;
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
        Dialog dialog = new Dialog(mContext);
        AppInfo info = (AppInfo) d.dragInfo;
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(R.layout.app_edit_dialog);
        EditText title = ((EditText) dialog.findViewById(R.id.title));
        TextView packageName = ((TextView) dialog.findViewById(R.id.package_name));
        ImageView icon = ((ImageView) dialog.findViewById(R.id.icon));
        final Switch visibility = ((Switch) dialog.findViewById(R.id.visibility));
        icon.setImageBitmap(info.iconBitmap);
        final String componentName = info.componentName.flattenToString();
        title.setText(info.title);
        packageName.setText(info.componentName.getPackageName());
        final boolean visible = !hiddenApps.contains(componentName);
        visibility.setChecked(visible);
        DialogInterface.OnDismissListener odl = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if(visibility.isChecked() != visible){
                    if(visibility.isChecked()){
                        hiddenApps.remove(componentName);
                    } else {
                        hiddenApps.add(componentName);
                    }
                    sharedPrefs.edit().putStringSet("pref_hiddenApps",hiddenApps).apply();
                }
            }
        };
        dialog.setOnDismissListener(odl);
        dialog.show();
    }

}
