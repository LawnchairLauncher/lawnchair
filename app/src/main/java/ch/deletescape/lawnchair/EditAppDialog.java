package ch.deletescape.lawnchair;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
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
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;

public class EditAppDialog extends Launcher.LauncherDialog {
    private static IPreferenceProvider sharedPrefs;
    private EditableItemInfo info;
    private EditText title;
    private Switch visibility;
    private boolean visibleState;
    private Launcher launcher;

    public EditAppDialog(@NonNull Context context, EditableItemInfo info, Launcher launcher) {
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

        final ComponentName component = info.getComponentName();
        setTitle(info.getTitle());

        title = findViewById(R.id.title);
        TextView packageName = findViewById(R.id.package_name);
        ImageView icon = findViewById(R.id.icon);
        visibility = findViewById(R.id.visibility);
        ImageButton reset = findViewById(R.id.reset_title);

        title.setText(info.getTitle());
        if (component != null) {
            packageName.setText(component.getPackageName());
            if (info instanceof AppInfo)
                visibleState = !Utilities.isAppHidden(getContext(), component.flattenToString());
        } else {
            packageName.setVisibility(View.GONE);
        }
        if (info instanceof AppInfo)
            visibility.setChecked(visibleState);
        else
            findViewById(R.id.visibility_container).setVisibility(View.GONE);

        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launcher.startEditIcon(info);
            }
        });

        if (component != null) {
            icon.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    try {
                        LauncherAppsCompat.getInstance(launcher).showAppDetailsForProfile(component, Utilities.myUserHandle());
                        return true;
                    } catch (SecurityException | ActivityNotFoundException e) {
                        Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                        Log.e("EditAppDialog", "Unable to launch settings", e);
                    }
                    return false;
                }
            });
        }

        View.OnClickListener resetTitle = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                info.setTitle(getContext(), null);
                EditAppDialog.this.title.setText(info.getTitle(getContext()));
            }
        };
        reset.setOnClickListener(resetTitle);

        onResume();
    }

    @Override
    public void onResume() {
        ImageView icon = findViewById(R.id.icon);
        info.reloadIcon(launcher);
        icon.setImageBitmap(info.getIconBitmap(launcher.getIconCache()));
    }

    @Override
    public void dismiss() {
        if (info instanceof AppInfo && visibility.isChecked() != visibleState) {
            String key = ((AppInfo) info).componentName.flattenToString();
            Utilities.setAppVisibility(getContext(), key, visibility.isChecked());
        }
        String titleS = title.getText().toString();
        if (!titleS.trim().equals(info.getTitle().trim())) {
            info.setTitle(getContext(), titleS);
        }
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.reloadAll(false);
        }
        super.dismiss();
    }
}
