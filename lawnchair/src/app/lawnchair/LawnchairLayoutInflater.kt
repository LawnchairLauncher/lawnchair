/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import app.lawnchair.font.FontManager

class LawnchairLayoutInflater(original: LayoutInflater, newContext: Context) : LayoutInflater(original, newContext) {

    private val fontManager by lazy { FontManager.INSTANCE.get(context) }

    override fun cloneInContext(newContext: Context): LayoutInflater {
        return LawnchairLayoutInflater(this, newContext)
    }

    private fun onViewCreated(view: View, attrs: AttributeSet?) {
        if (view is TextView) {
            fontManager.overrideFont(view, attrs)
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

    companion object {

        @JvmStatic
        private val sClassPrefixList = arrayOf("android.widget.", "android.webkit.", "android.app.")
    }
}