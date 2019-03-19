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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.support.v4.provider.FontRequest
import android.support.v4.provider.FontsContractCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.uiWorkerHandler
import com.android.launcher3.R
import com.android.launcher3.Utilities

@Keep
class CustomFontFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_custom_font, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = view.context.lawnchairPrefs

        val fontName = view.findViewById<TextView>(R.id.font_name)
        val submitButton = view.findViewById<View>(R.id.button)

        fontName.text = prefs.customFontName
        submitButton.setOnClickListener {
            setFont(view.context, fontName.text.toString())
        }
    }

    private fun setFont(context: Context, fontName: String) {
        val request = FontRequest(
                "com.google.android.gms.fonts", // ProviderAuthority
                "com.google.android.gms",  // ProviderPackage
                "name=$fontName",  // Query
                R.array.com_google_android_gms_fonts_certs)

        // retrieve font in the background
        FontsContractCompat.requestFont(context, request, object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                super.onTypefaceRetrieved(typeface)

                val prefs = context.lawnchairPrefs
                prefs.blockingEdit { customFontName = fontName }
                Utilities.restartLauncher(context)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                super.onTypefaceRequestFailed(reason)

                Toast.makeText(context, "Failed to load $fontName", Toast.LENGTH_LONG).show()
            }
        }, uiWorkerHandler)
    }
}
