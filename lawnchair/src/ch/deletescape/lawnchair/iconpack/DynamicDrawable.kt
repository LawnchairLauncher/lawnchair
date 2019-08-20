/*
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

package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.util.Log
import android.util.Xml
import ch.deletescape.lawnchair.get
import com.android.launcher3.FastBitmapDrawable
import com.google.android.apps.nexuslauncher.clock.CustomClock
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

class DynamicDrawable {
    companion object {
        const val TAG = "DynamicDrawable"

        fun getIcon(context: Context, drawable: Drawable, metadata: Metadata, iconDpi: Int): Drawable {
            metadata.load(context, iconDpi)
            return when (metadata.type) {
                Type.CLOCK -> CustomClock.getClock(context, metadata.clockMetadata!!.drawable,
                        metadata.clockMetadata!!.metadata, iconDpi)
                else -> drawable
            }
        }

        fun drawIcon(context: Context, icon: Bitmap, metadata: Metadata, drawableFactory: LawnchairDrawableFactory, iconDpi: Int) : FastBitmapDrawable? {
            metadata.load(context, iconDpi)
            return when (metadata.type) {
                Type.CLOCK -> drawableFactory.customClockDrawer.drawIcon(icon, metadata.clockMetadata!!.drawable, metadata.clockMetadata!!.metadata)
                else -> null
            }
        }

        private fun getXml(context: Context, name: String, packageName: String): XmlPullParser? {
            val res: Resources
            try {
                res = context.packageManager.getResourcesForApplication(packageName)
                val resourceId = res.getIdentifier(name, "raw", packageName)
                if (0 != resourceId) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(res.openRawResource(resourceId), Xml.Encoding.UTF_8.toString())
                    return parser
                }
            } catch (e: PackageManager.NameNotFoundException) {
            } catch (e: IOException) {
            } catch (e: XmlPullParserException) {
            }
            return null
        }

        fun getDrawable(context: Context, packageName: String, name: String, density: Int): Drawable? {
            return try {
                val res = context.packageManager.getResourcesForApplication(packageName)
                val id = res.getIdentifier(name, "drawable", packageName)
                if (id != 0) res.getDrawableForDensity(id, density) else null
            } catch (e: Resources.NotFoundException) {
                Log.e(TAG, "Can't get drawable ($name) from $packageName", e)
                null
            }
        }
    }

    class Metadata(val xml: String, val packageName: String) {
        internal var type: Type? = null
        internal var items: List<Item>? = null
        internal var clockMetadata: ClockMetadata? = null
        internal var loaded: Boolean = false

        internal fun load(context: Context, iconDpi: Int) {
            if (!loaded) {
                val mutableItems: MutableList<Item> = ArrayList()
                val parser = getXml(context, xml, packageName)
                while (parser != null &&parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.name == "item") {
                            val item = Item(
                                    parser["type"]!!,
                                    parser["key"]!!)
                            item.parseItem(parser)
                            mutableItems.add(item)
                        }
                    }
                }
                items = mutableItems.toList()
                type = when {
                    items!!.count { it.key == "day" } > 0 -> Type.CALENDAR
                    items!!.count { it.key == "temp" } > 0 -> Type.WEATHER
                    items!!.count { it.key == "hour_hand" } > 0 -> Type.CLOCK
                    else -> null
                }
                when (type) {
                    Type.CLOCK -> {
                        loadClock(context, iconDpi)
                    }
                    else -> {}
                }
                loaded = true
            }
        }

        private fun loadClock(context: Context, iconDpi: Int) {
            Log.d(TAG, "loadClock")
            val drawables: MutableList<Drawable> = ArrayList()
            val relevantItems = listOf("background", "hour_hand", "minute_hand", "second_hand")
            val hourTo = 5000f
            val minTo = 60000f
            val secTo = 6000f
            for (relevantItem in relevantItems) {
                val item = items!!.findLast { it.key == relevantItem }
                if (item != null) {
                    if (item.drawables.size > 1) {
                        Log.e(TAG, "Unexpected number of drawables ${item.drawables.size}! " +
                                "Assuming first and continuing.")
                    } else if (item.drawables.isEmpty()) {
                        throw RuntimeException("Item '${item.key}' has no drawables!")
                    }
                    var d = getDrawable(context, packageName, item.drawables.first().res, iconDpi)
                    if (d != null) {
                        if (relevantItem != "background"){
                            val d2 = RotateDrawable()
                            with (d2) {
                                drawable = d
                                fromDegrees = 0f
                                toDegrees = if (relevantItem == "hour_hand") hourTo else if(relevantItem == "minute_hand") minTo else secTo
                            }
                            d = d2
                        }
                        drawables.add(d.mutate())
                    }
                }
            }
            val layerDrawable = LayerDrawable(drawables.toTypedArray())
            this.clockMetadata = ClockMetadata(
                        layerDrawable,
                    CustomClock.Metadata(
                            1,
                            2,
                            3,
                            0,
                            0,
                            0
                    )
            )
        }
    }

    internal class Item(val type: String, val key: String) {
        val drawables: MutableList<DrawableItem> = ArrayList()
        var textAttributes: TextAttributes? = null

        fun parseItem(parser: XmlPullParser) {
            when (type) {
                "drawable" -> {
                    while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "item")) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "drawable") {
                            drawables.add(DrawableItem(
                                    parser["value"]!!,
                                    parser["res"]!!
                            ))
                        }
                    }
                }
                "text" -> {
                    textAttributes = TextAttributes(
                            parser["alignX"],
                            parser["alignY"],
                            parser["offsetX"],
                            parser["offsetY"],
                            parser["textSize"],
                            parser["font"],
                            parser["color"],
                            parser["shadowLayerX"],
                            parser["shadowLayerY"],
                            parser["shadowLayerRadius"]?.toInt(),
                            parser["shadowLayerColor"],
                            parser["shadowLayerAlpha"],
                            parser["enabled"]?.toBoolean() ?: true
                    )
                }
            }
        }
    }

    internal class DrawableItem(val value: String, val res: String)
    internal class TextAttributes(val alignX: String?, val alignY: String?, val offsetX: String?,
                                  val offsetY: String?, val textSize: String?, val font: String?,
                                  val color: String?, val shadowLayerX: String?, val shadowLayerY: String?,
                                  val shadowLayerRadius: Int?, val shadowLayerColor: String?,
                                  val shadowLayerAlpha: String?, val enabled: Boolean = true)
    internal class ClockMetadata(val drawable: Drawable, val metadata: CustomClock.Metadata)

    internal enum class Type {
        CALENDAR, CLOCK, WEATHER
    }
}