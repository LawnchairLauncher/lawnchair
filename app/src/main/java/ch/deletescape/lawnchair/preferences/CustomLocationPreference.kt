/*
 * Copyright (C) 2017 MoKee Android Open Source Project
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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet

import ch.deletescape.lawnchair.R

class CustomLocationPreference : EditTextPreference {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
    }

    override fun persistString(value: String?): Boolean {
        return super.persistString(value).apply { updateSummary() }
    }

    private fun updateSummary() {
        val city = sharedPreferences.getString(PreferenceFlags.KEY_WEATHER_CITY, PreferenceFlags.PREF_WEATHER_DEFAULT_CITY)
        summary = if (!TextUtils.isEmpty(city)) city else context.getString(R.string.pref_weather_city_summary)
    }

}
