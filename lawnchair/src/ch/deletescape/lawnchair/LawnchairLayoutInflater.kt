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

package ch.deletescape.lawnchair

import android.content.Context
import android.support.v7.app.AppCompatDelegate
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ch.deletescape.lawnchair.font.CustomFontManager
import org.xmlpull.v1.XmlPullParser

class LawnchairLayoutInflater(original: LayoutInflater, newContext: Context) : LayoutInflater(original, newContext) {

    private val fontManager by lazy { CustomFontManager.getInstance(context) }

    fun installFactory(delegate: AppCompatDelegate) {
        factory2 = object : LayoutInflater.Factory2 {
            override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                val view = onCreateViewImpl(parent, name, context, attrs)
                if (view != null) {
                    onViewCreated(view, attrs)
                }
                return view
            }

            private fun onCreateViewImpl(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                if (name == "android.support.v7.widget.DialogTitle") {
                    return (Class.forName(name).getConstructor(Context::class.java, AttributeSet::class.java)
                            .newInstance(context, attrs) as TextView).apply { setCustomFont(CustomFontManager.FONT_DIALOG_TITLE) }
                }
                return delegate.createView(parent, name, context, attrs)
            }

            override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
                return onCreateView(null, name, context, attrs)
            }

        }
    }

    override fun cloneInContext(newContext: Context): LayoutInflater {
        return LawnchairLayoutInflater(this, newContext)
    }

    private fun onViewCreated(view: View, attrs: AttributeSet?) {
        if (view is TextView) {
            fontManager.loadCustomFont(view, attrs)
        }
    }

    override fun onCreateView(parent: View?, name: String?, attrs: AttributeSet?): View {
        val view = super.onCreateView(parent, name, attrs)
        if (view != null) {
            onViewCreated(view, attrs)
        }
        return view
    }

    @Throws(ClassNotFoundException::class)
    override fun onCreateView(name: String, attrs: AttributeSet): View {
        for (prefix in sClassPrefixList) {
            try {
                val view = createView(name, prefix, attrs)
                if (view != null) {
                    return view
                }
            } catch (e: ClassNotFoundException) {
                // In this case we want to let the base class take a crack
                // at it.
            }

        }

        return super.onCreateView(name, attrs)
    }

    override fun inflate(parser: XmlPullParser?, root: ViewGroup?, attachToRoot: Boolean): View {
        val view = super.inflate(parser, root, attachToRoot)
        hookInflate(view)
        return view
    }

    private fun hookInflate(view: View) {
        if (view is TextView) {

        }
    }

    companion object {

        @JvmStatic
        private val sClassPrefixList = arrayOf("android.widget.", "android.webkit.", "android.app.")
    }
}
