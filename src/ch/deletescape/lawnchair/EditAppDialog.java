package ch.deletescape.lawnchair;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;


public class EditAppDialog extends Dialog {
    private static final String KEY_PREF_HIDDEN_APPS = "pref_hiddenApps";
    private static SharedPreferences sharedPrefs;
    private static Set<String> hiddenApps;
    private AppInfo info;
    private Switch visibility;
    private boolean visibleState;

    public EditAppDialog(@NonNull Context context, AppInfo info) {
        super(context);
        this.info = info;
        sharedPrefs = Utilities.getPrefs(context.getApplicationContext());
        hiddenApps = sharedPrefs.getStringSet(KEY_PREF_HIDDEN_APPS, null);
        if (hiddenApps == null) {
            hiddenApps = new HashSet<>();
        }
        setCanceledOnTouchOutside(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_edit_dialog);

        ComponentName component = info.componentName;

        EditText title = ((EditText) findViewById(R.id.title));
        TextView packageName = ((TextView) findViewById(R.id.package_name));
        ImageView icon = ((ImageView) findViewById(R.id.icon));
        visibility = ((Switch) findViewById(R.id.visibility));

        icon.setImageBitmap(info.iconBitmap);
        title.setText(info.title);
        packageName.setText(component.getPackageName());
        visibleState = !hiddenApps.contains(component.flattenToString());
        visibility.setChecked(visibleState);
    }

    @Override
    public void dismiss() {
        String key = info.componentName.flattenToString();
        if(visibility.isChecked() != visibleState){
            if(visibility.isChecked()){
                hiddenApps.remove(key);
            } else {
                hiddenApps.add(key);
            }
            sharedPrefs.edit().putStringSet(KEY_PREF_HIDDEN_APPS,hiddenApps).apply();
        }
        super.dismiss();
    }
}
