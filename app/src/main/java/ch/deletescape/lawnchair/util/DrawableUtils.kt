package ch.deletescape.lawnchair.util

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Xml
import ch.deletescape.lawnchair.pixelify.AdaptiveIconDrawableCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException



class DrawableUtils {

    companion object {

        private val classLoader = DrawableUtils::class.java.classLoader
        private val classDrawableInflater by lazy { classLoader.loadClass("android.graphics.drawable.DrawableInflater") }
        private val methodInflateFromXml by lazy {
            classDrawableInflater.getDeclaredMethod("inflateFromXml",
                    String::class.java, XmlPullParser::class.java, AttributeSet::class.java, Resources.Theme::class.java)
        }
        private val fieldClassLoader by lazy { classDrawableInflater.getDeclaredField("mClassLoader") }
        private val methodGetDrawableInflater by lazy { Resources::class.java.getDeclaredMethod("getDrawableInflater") }

        val wrappedClassLoader = object : ClassLoader() {

            override fun loadClass(name: String?): Class<*> {
                return classLoader.loadClass(if (name == "adaptive-icon") AdaptiveIconDrawableCompat::class.java.name else name)
            }
        }

        fun getDrawableInflater(res: Resources): Any {
            val inflater = DrawableUtils.methodGetDrawableInflater.invoke(res)
            DrawableUtils.fieldClassLoader.isAccessible = true
            DrawableUtils.fieldClassLoader.set(inflater, wrappedClassLoader)
            return inflater
        }

        fun inflateFromXml(drawableInflater: Any, parser: XmlPullParser)
                = inflateFromXml(drawableInflater, parser, null)

        fun inflateFromXml(drawableInflater: Any, parser: XmlPullParser, theme: Resources.Theme?)
                = inflateFromXml(drawableInflater, parser, Xml.asAttributeSet(parser), theme)

        fun inflateFromXml(drawableInflater: Any, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?): Drawable {
            while (parser.next() != XmlPullParser.START_TAG) {
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            }

            if (parser.eventType != XmlPullParser.START_TAG) {
                throw XmlPullParserException("No start tag found")
            }

            return methodInflateFromXml
                    .invoke(drawableInflater, parser.name, parser, attrs, theme) as Drawable
        }

    }
}

val Resources.drawableInflater get() = DrawableUtils.getDrawableInflater(this)