package ch.deletescape.wallpaperpicker.tileinfo;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import ch.deletescape.lawnchair.R;

/**
 * WallpaperTileInfo which uses drawable as the thumbnail.
 */
public abstract class DrawableThumbWallpaperInfo extends WallpaperTileInfo {

    private final Drawable mThumb;

    DrawableThumbWallpaperInfo(Drawable thumb) {
        mThumb = thumb;
    }

    @Override
    public View createView(Context context, LayoutInflater inflator, ViewGroup parent) {
        mView = inflator.inflate(R.layout.wallpaper_picker_item, parent, false);
        setThumb(mThumb);
        return mView;
    }

    public void setThumb(Drawable thumb) {
        if (mView != null && thumb != null) {
            thumb.setDither(true);
            ImageView image = mView.findViewById(R.id.wallpaper_image);
            image.setImageDrawable(thumb);
        }
    }
}
