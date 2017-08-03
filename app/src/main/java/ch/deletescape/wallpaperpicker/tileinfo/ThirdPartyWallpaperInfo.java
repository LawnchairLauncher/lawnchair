package ch.deletescape.wallpaperpicker.tileinfo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.wallpaperpicker.WallpaperPickerActivity;

public class ThirdPartyWallpaperInfo extends WallpaperTileInfo {

    private final ResolveInfo mResolveInfo;
    private final int mIconSize;

    public ThirdPartyWallpaperInfo(ResolveInfo resolveInfo, int iconSize) {
        mResolveInfo = resolveInfo;
        mIconSize = iconSize;
    }

    @Override
    public void onClick(WallpaperPickerActivity a) {
        final ComponentName itemComponentName = new ComponentName(
                mResolveInfo.activityInfo.packageName, mResolveInfo.activityInfo.name);
        Intent launchIntent = new Intent(Intent.ACTION_SET_WALLPAPER)
                .setComponent(itemComponentName)
                .putExtra(Utilities.EXTRA_WALLPAPER_OFFSET,
                        a.getWallpaperParallaxOffset());
        a.startActivityForResultSafely(
                launchIntent, WallpaperPickerActivity.PICK_WALLPAPER_THIRD_PARTY_ACTIVITY);
    }

    @Override
    public View createView(Context context, LayoutInflater inflator, ViewGroup parent) {
        mView = inflator.inflate(R.layout.wallpaper_picker_third_party_item, parent, false);

        TextView label = mView.findViewById(R.id.wallpaper_item_label);
        label.setText(mResolveInfo.loadLabel(context.getPackageManager()));
        Drawable icon = mResolveInfo.loadIcon(context.getPackageManager());
        icon.setBounds(new Rect(0, 0, mIconSize, mIconSize));
        label.setCompoundDrawables(null, icon, null, null);
        return mView;
    }

    public static List<ThirdPartyWallpaperInfo> getAll(Context context) {
        ArrayList<ThirdPartyWallpaperInfo> result = new ArrayList<>();
        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.wallpaperItemIconSize);

        final PackageManager pm = context.getPackageManager();
        Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
        HashSet<String> excludePackages = new HashSet<>();
        // Exclude packages which contain an image picker
        for (ResolveInfo info : pm.queryIntentActivities(pickImageIntent, 0)) {
            excludePackages.add(info.activityInfo.packageName);
        }
        excludePackages.add(context.getPackageName());
        excludePackages.add("com.android.wallpaper.livepicker");

        final Intent pickWallpaperIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        for (ResolveInfo info : pm.queryIntentActivities(pickWallpaperIntent, 0)) {
            if (!excludePackages.contains(info.activityInfo.packageName)) {
                result.add(new ThirdPartyWallpaperInfo(info, iconSize));
            }
        }
        return result;
    }
}