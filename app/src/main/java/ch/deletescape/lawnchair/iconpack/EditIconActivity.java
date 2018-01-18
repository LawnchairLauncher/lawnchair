package ch.deletescape.lawnchair.iconpack;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.EditableItemInfo;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class EditIconActivity extends AppCompatActivity implements CustomIconAdapter.Listener, IconPackAdapter.Listener {

    private static final int REQUEST_PICK_ICON = 0;
    private EditableItemInfo mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.INSTANCE.applyDarkTheme(this);
        Utilities.setupPirateLocale(this);

        Utilities.getPrefs(this).getEnableScreenRotation();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_icon);

        mInfo = getIntent().getExtras().getParcelable("itemInfo");
        if (mInfo == null) {
            finish();
            return;
        }
        ComponentName component = mInfo.getComponentName();
        List<IconPackInfo> iconPacks = new ArrayList<>(loadAvailableIconPacks().values());
        Collections.sort(iconPacks, new Comparator<IconPackInfo>() {
            @Override
            public int compare(IconPackInfo lhs, IconPackInfo rhs) {
                return lhs.label.toString().compareToIgnoreCase(rhs.label.toString());
            }
        });

        setTitle(mInfo.getTitle());

        BlurWallpaperProvider.Companion.applyBlurBackground(this);

        if (mInfo.getType() == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION && mInfo.getComponentName() != null) {
            RecyclerView iconRecyclerView = findViewById(R.id.iconRecyclerView);
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(component);
            LauncherActivityInfoCompat laic = LauncherActivityInfoCompat.create(this, mInfo.getUser(), i);
            CustomIconAdapter iconAdapter = new CustomIconAdapter(this, laic, iconPacks);
            iconAdapter.setListener(this);
            iconRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            iconRecyclerView.setAdapter(iconAdapter);
        } else {
            findViewById(R.id.iconRecyclerView).setVisibility(View.GONE);
            findViewById(R.id.divider).setVisibility(View.GONE);
        }

        RecyclerView iconPackRecyclerView = findViewById(R.id.iconPackRecyclerView);
        IconPackAdapter iconPackAdapter = new IconPackAdapter(this, iconPacks);
        iconPackAdapter.setListener(this);
        iconPackRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        iconPackRecyclerView.setAdapter(iconPackAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_icon, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset) {
            resetIcon();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Map<String, IconPackInfo> loadAvailableIconPacks() {
        PackageManager pm = getPackageManager();
        Map<String, IconPackInfo> iconPacks = new HashMap<>();
        List<ResolveInfo> list;
        list = pm.queryIntentActivities(new Intent("com.novalauncher.THEME"), 0);
        list.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("com.dlto.atom.launcher.THEME"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0));
        for (ResolveInfo info : list) {
            IconPack iconPack = IconPackProvider.loadAndGetIconPack(this, info.activityInfo.packageName);
            iconPacks.put(
                    info.activityInfo.packageName,
                    new IconPackInfo(iconPack, info, pm));
        }
        return iconPacks;
    }

    @Override
    public void onSelect(CustomIconAdapter.IconInfo iconInfo) {
        setAlternateIcon(iconInfo.toString());
    }

    private void setAlternateIcon(String alternateIcon) {
        Intent data = new Intent();
        data.putExtra("alternateIcon", alternateIcon);
        setResult(RESULT_OK, data);
        finish();
    }

    private void resetIcon() {
        setResult(RESULT_OK, new Intent());
        finish();
    }

    /*private void updateCache() {
        Utilities.updatePackage(this, mInfo.getUser(), mInfo.getComponentName().getPackageName());
    }*/

    @Override
    public void startPicker(IconPackInfo iconPackInfo) {
        Intent intent = new Intent(this, IconPickerActivity.class);
        intent.putExtra("resolveInfo", iconPackInfo.resolveInfo);
        intent.putExtra("packageName", iconPackInfo.packageName);
        startActivityForResult(intent, REQUEST_PICK_ICON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_ICON) {
            if (resultCode == RESULT_OK) {
                String packageName = data.getStringExtra("packageName");
                String resourceName = data.getStringExtra("resource");
                setAlternateIcon("resource/" + packageName + "/" + resourceName);
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    static class IconPackInfo {
        ResolveInfo resolveInfo;
        IconPack iconPack;
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(IconPack ip, ResolveInfo r, PackageManager packageManager) {
            this(ip, r.loadIcon(packageManager), r.loadLabel(packageManager));
            resolveInfo = r;
        }

        IconPackInfo(IconPack ip, Drawable ic, CharSequence lb) {
            iconPack = ip;
            packageName = iconPack.getPackageName();
            icon = ic;
            label = lb;
        }

        IconPack getIconPack() {
            return iconPack;
        }
    }
}
