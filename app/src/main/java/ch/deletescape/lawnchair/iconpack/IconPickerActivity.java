package ch.deletescape.lawnchair.iconpack;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class IconPickerActivity extends Activity implements IconGridAdapter.Listener {

    private EditIconActivity.IconPackInfo mIconPackInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.INSTANCE.applyDarkTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_picker);

        if (!loadIconPack()) {
            finish();
            return;
        }

        setTitle(mIconPackInfo.label);

        RecyclerView recyclerView = findViewById(R.id.categoryRecyclerView);
        IconCategoryAdapter adapter = new IconCategoryAdapter();
        adapter.setCategoryList(mIconPackInfo.iconPack.getIconList());
        adapter.setListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(recyclerView);

        BlurWallpaperProvider.Companion.applyBlurBackground(this);
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
    public void onSelect(IconPack.IconEntry iconEntry) {
        Intent data = new Intent();
        data.putExtra("packageName", iconEntry.getPackageName());
        data.putExtra("resourceId", iconEntry.resId);
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
