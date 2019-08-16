/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.allapps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.ColorEngine.ResolveInfo;
import ch.deletescape.lawnchair.colors.ColorEngine.Resolvers;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.google.android.apps.nexuslauncher.qsb.AllAppsQsbLayout;
import org.jetbrains.annotations.NotNull;

public class AllAppsSearchContainerView extends AllAppsContainerView
        implements ColorEngine.OnColorChangeListener {

    private boolean mClearQsb;

    public AllAppsSearchContainerView(Context context) {
        this(context, null);
    }

    public AllAppsSearchContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsSearchContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void dispatchDraw(Canvas canvas) {
        View searchView = getSearchView();
        if (mClearQsb && searchView instanceof AllAppsQsbLayout) {
            AllAppsQsbLayout qsb = (AllAppsQsbLayout) searchView;
            int left = (int) (qsb.getLeft() + qsb.getTranslationX());
            int top = (int) (qsb.getTop() + qsb.getTranslationY());
            int right = left + qsb.getWidth() + 1;
            int bottom = top + qsb.getHeight() + 1;
            if (Utilities.ATLEAST_P && Utilities.HIDDEN_APIS_ALLOWED) {
                canvas.saveUnclippedLayer(left, 0, right, bottom);
            } else {
                int flags = Utilities.ATLEAST_P ? Canvas.ALL_SAVE_FLAG : 0x04 /* HAS_ALPHA_LAYER_SAVE_FLAG */;
                canvas.saveLayer(left, 0, right, bottom, null, flags);
            }
        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ColorEngine.getInstance(getContext()).addColorChangeListeners(this, Resolvers.ALLAPPS_QSB_BG);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ColorEngine.getInstance(getContext()).removeColorChangeListeners(this, Resolvers.ALLAPPS_QSB_BG);
    }

    @Override
    public void onColorChange(@NotNull ResolveInfo resolveInfo) {
        super.onColorChange(resolveInfo);
        if (Resolvers.ALLAPPS_QSB_BG.equals(resolveInfo.getKey())) {
            LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
            mClearQsb = Color.alpha(resolveInfo.getColor()) != 255
                    && prefs.getAllAppsSearch() && !prefs.getLowPerformanceMode();
            invalidate();
        }
    }
}
