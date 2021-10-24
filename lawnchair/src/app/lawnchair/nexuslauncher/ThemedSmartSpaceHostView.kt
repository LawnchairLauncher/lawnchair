package app.lawnchair.nexuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import app.lawnchair.font.FontManager
import app.lawnchair.util.recursiveChildren
import com.android.launcher3.R
import com.android.launcher3.ResourceUtils
import com.android.launcher3.icons.ShadowGenerator
import com.android.launcher3.util.Themes
import com.android.launcher3.views.DoubleShadowBubbleTextView

class ThemedSmartSpaceHostView(context: Context) : SmartSpaceHostView(context) {

    private val isWorkspaceDarkText = Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
    private val workspaceTextColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private val shadowGenerator = ShadowGenerator(ResourceUtils.pxFromDp(48f, resources.displayMetrics))

    private val templateTextView = LayoutInflater.from(context)
        .inflate(R.layout.smartspace_text_template, this, false) as DoubleShadowBubbleTextView

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        val images = mutableListOf<ImageView>()
        for (child in recursiveChildren) {
            when (child) {
                is TextView -> overrideTextView(child)
                is ImageView -> images.add(child)
            }
        }

        if (images.isNotEmpty()) {
            overrideWeatherIcon(images.last())
        }
        if (images.size > 1) {
            overrideCardIcon(images.first())
        }
    }

    private fun overrideTextView(tv: TextView) {
        if (isWorkspaceDarkText) {
            tv.paint.clearShadowLayer()
        } else {
            val shadowInfo = templateTextView.shadowInfo
            tv.paint.setShadowLayer(
                shadowInfo.ambientShadowBlur,
                shadowInfo.keyShadowOffsetX,
                shadowInfo.keyShadowOffsetY,
                shadowInfo.ambientShadowColor
            )
        }
        tv.setTextColor(workspaceTextColor)
        tv.letterSpacing = -0.02F
        FontManager.INSTANCE.get(context).setCustomFont(tv, R.id.font_heading)
    }

    private fun overrideWeatherIcon(iv: ImageView) {
        if (!isWorkspaceDarkText) {
            val drawable = iv.drawable
            if (drawable is BitmapDrawable) {
                iv.setImageBitmap(addShadowToBitmap(drawable.bitmap))
            }
        }
    }

    private fun overrideCardIcon(iv: ImageView) {
        if (isWorkspaceDarkText) {
            iv.setColorFilter(workspaceTextColor)
        }
    }

    private fun addShadowToBitmap(bitmap: Bitmap): Bitmap {
        return if (!bitmap.isRecycled) {
            val newBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(newBitmap)
            shadowGenerator.recreateIcon(bitmap, canvas)
            newBitmap
        } else {
            bitmap
        }
    }
}
