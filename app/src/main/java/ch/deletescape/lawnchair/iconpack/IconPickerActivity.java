package ch.deletescape.lawnchair.iconpack;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.MenuItem;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class IconPickerActivity extends Activity implements IconGridAdapter.Listener {

    private EditIconActivity.IconPackInfo mIconPackInfo;
    private GridLayoutManager mLayoutManager;
    private int mColumnWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.applyDarkTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_picker);

        if (!loadIconPack()) {
            finish();
            return;
        }

        setTitle(mIconPackInfo.label);

        mColumnWidth = getResources().getDimensionPixelSize(R.dimen.icon_grid_column_width);
        RecyclerView recyclerView = findViewById(R.id.iconRecyclerView);
        IconGridAdapter adapter = new IconGridAdapter();
        adapter.setIconList(mIconPackInfo.iconPack.getIconList());
        adapter.setListener(this);
        mLayoutManager = new GridLayoutManager(this, 1);
        updateColumnCount();
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(adapter);

        BlurWallpaperProvider.applyBlurBackground(this);
    }

    private boolean loadIconPack() {
        if (getIntent() == null) return false;

        ResolveInfo resolveInfo = getIntent().getParcelableExtra("resolveInfo");
        String packageName = getIntent().getStringExtra("packageName");

        mIconPackInfo = new EditIconActivity.IconPackInfo(
                IconPackProvider.loadAndGetIconPack(this, packageName),
                resolveInfo,
                getPackageManager()
        );

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateColumnCount();
    }

    private void updateColumnCount() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int columnCount = displayMetrics.widthPixels / mColumnWidth;
        mLayoutManager.setSpanCount(columnCount);
    }

    @Override
    public void onSelect(IconPack.IconEntry iconEntry) {
        Intent data = new Intent();
        data.putExtra("packageName", iconEntry.getPackageName());
        data.putExtra("resourceName", iconEntry.resourceName);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
