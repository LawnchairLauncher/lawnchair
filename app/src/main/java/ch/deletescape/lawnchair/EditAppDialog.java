package ch.deletescape.lawnchair;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import ch.deletescape.lawnchair.compat.LauncherAppsCompat;
import ch.deletescape.lawnchair.iconpack.EditIconActivity;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;

public class EditAppDialog extends Launcher.LauncherDialog {
    private static IPreferenceProvider sharedPrefs;
    private AppInfo info;
    private EditText title;
    private Switch visibility;
    private boolean visibleState;
    private Launcher launcher;

    public EditAppDialog(@NonNull Context context, AppInfo info, Launcher launcher) {
        super(context);
        this.info = info;
        this.launcher = launcher;
        sharedPrefs = Utilities.getPrefs(context.getApplicationContext());
        setCanceledOnTouchOutside(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_edit_dialog);

        final ComponentName component = info.componentName;
        setTitle(info.originalTitle);

        title = findViewById(R.id.title);
        TextView packageName = findViewById(R.id.package_name);
        ImageView icon = findViewById(R.id.icon);
        visibility = findViewById(R.id.visibility);
        ImageButton reset = findViewById(R.id.reset_title);

        title.setText(info.title);
        packageName.setText(component.getPackageName());
        visibleState = !Utilities.isAppHidden(getContext(), component.flattenToString());
        visibility.setChecked(visibleState);

        View.OnClickListener editIcon = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getContext(), EditIconActivity.class);
                intent.putExtra("componentName", info.componentName);
                intent.putExtra("userHandle", info.user);
                getContext().startActivity(intent);
            }
        };
        icon.setOnClickListener(editIcon);

        View.OnLongClickListener olcl = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    LauncherAppsCompat.getInstance(launcher).showAppDetailsForProfile(component, info.user);
                    return true;
                } catch (SecurityException | ActivityNotFoundException e) {
                    Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                    Log.e("EditAppDialog", "Unable to launch settings", e);
                }
                return false;
            }
        };
        icon.setOnLongClickListener(olcl);

        View.OnClickListener resetTitle = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                title.setText(info.originalTitle);
            }
        };
        reset.setOnClickListener(resetTitle);

        onResume();
    }

    @Override
    public void onResume() {
        ImageView icon = findViewById(R.id.icon);
        launcher.getIconCache().getTitleAndIcon(info, null, false);
        icon.setImageBitmap(info.iconBitmap);
    }

    @Override
    public void dismiss() {
        String key = info.componentName.flattenToString();
        if (visibility.isChecked() != visibleState) {
            Utilities.setAppVisibility(getContext(), key, visibility.isChecked());
        }
        String titleS = title.getText().toString();
        if (!titleS.trim().equals(info.title.toString().trim())) {
            info.title = titleS.trim();
            sharedPrefs.itemAlias(key, titleS, false);
        }
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.reloadAll(false);
        }
        super.dismiss();
    }
}
