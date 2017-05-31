/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.dragndrop;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.HolographicOutlineHelper;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.graphics.DragPreviewProvider;

/**
 * Extension of {@link DragPreviewProvider} which provides a dummy outline when drag starts from
 * a different window.
 * It just draws an empty circle to a placeholder outline.
 */
public class ExternalDragPreviewProvider extends DragPreviewProvider {

    private final Launcher mLauncher;

    private final int[] mOutlineSize;

    public ExternalDragPreviewProvider(Launcher launcher, ItemInfo addInfo) {
        super(null);
        mLauncher = launcher;

        mOutlineSize = mLauncher.getWorkspace().estimateItemSize(addInfo, false);
    }

    public Rect getPreviewBounds() {
        Rect rect = new Rect();
        DeviceProfile dp = mLauncher.getDeviceProfile();
        rect.left = DRAG_BITMAP_PADDING / 2;
        rect.top = (mOutlineSize[1] - dp.cellHeightPx) / 2;
        rect.right = rect.left + dp.iconSizePx;
        rect.bottom = rect.top + dp.iconSizePx;
        return rect;
    }

    @Override
    public Bitmap createDragOutline(Canvas canvas) {
        final Bitmap b = Bitmap.createBitmap(mOutlineSize[0], mOutlineSize[1], Bitmap.Config.ALPHA_8);
        canvas.setBitmap(b);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        // Use 0.9f times the radius for the actual circle to account for icon normalization.
        float radius = getPreviewBounds().width() * 0.5f;
        canvas.drawCircle(DRAG_BITMAP_PADDING / 2 + radius,
                DRAG_BITMAP_PADDING / 2 + radius, radius * 0.9f, paint);

        HolographicOutlineHelper.obtain(mLauncher).applyExpensiveOutlineWithBlur(b, canvas);
        canvas.setBitmap(null);
        return b;
    }
}
