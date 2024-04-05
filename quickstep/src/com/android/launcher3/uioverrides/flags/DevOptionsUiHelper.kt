/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.uioverrides.flags

import android.os.Handler
import android.provider.DeviceConfig
import android.text.Html
import android.view.inputmethod.EditorInfo
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.R
import com.android.quickstep.util.DeviceConfigHelper
import com.android.quickstep.util.DeviceConfigHelper.Companion.NAMESPACE_LAUNCHER
import com.android.quickstep.util.DeviceConfigHelper.DebugInfo

/** Helper class to generate UI for Device Config */
class DevOptionsUiHelper {

    /** Inflates preferences for all server flags in the provider PreferenceGroup */
    fun inflateServerFlags(parent: PreferenceGroup) {
        val prefs = DeviceConfigHelper.prefs
        // Sort the keys in the order of modified first followed by natural order
        val allProps =
            DeviceConfigHelper.allProps.values
                .toList()
                .sortedWith(
                    Comparator.comparingInt { prop: DebugInfo<*> ->
                            if (prefs.contains(prop.key)) 0 else 1
                        }
                        .thenComparing { prop: DebugInfo<*> -> prop.key }
                )

        // First add boolean flags
        allProps.forEach {
            if (it.isInt) return@forEach
            val info = it as DebugInfo<Boolean>

            val preference =
                object : SwitchPreference(parent.context) {
                    override fun onBindViewHolder(holder: PreferenceViewHolder) {
                        super.onBindViewHolder(holder)
                        holder.itemView.setOnLongClickListener {
                            prefs.edit().remove(key).apply()
                            setChecked(info.getBoolValue())
                            summary = info.getSummary()
                            true
                        }
                    }
                }
            preference.key = info.key
            preference.isPersistent = false
            preference.title = info.key
            preference.summary = info.getSummary()
            preference.setChecked(prefs.getBoolean(info.key, info.getBoolValue()))
            preference.setOnPreferenceChangeListener { _, newVal ->
                DeviceConfigHelper.prefs.edit().putBoolean(info.key, newVal as Boolean).apply()
                preference.summary = info.getSummary()
                true
            }
            parent.addPreference(preference)
        }

        // Apply Int flags
        allProps.forEach {
            if (!it.isInt) return@forEach
            val info = it as DebugInfo<Int>

            val preference =
                object : Preference(parent.context) {
                    override fun onBindViewHolder(holder: PreferenceViewHolder) {
                        super.onBindViewHolder(holder)
                        val textView = holder.findViewById(R.id.pref_edit_text) as ExtendedEditText
                        textView.setText(info.getIntValueAsString())
                        textView.setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_DONE) {
                                DeviceConfigHelper.prefs
                                    .edit()
                                    .putInt(key, textView.text.toString().toInt())
                                    .apply()
                                Handler().post { summary = info.getSummary() }
                                true
                            }
                            false
                        }
                        textView.setOnBackKeyListener {
                            textView.setText(info.getIntValueAsString())
                            true
                        }

                        holder.itemView.setOnLongClickListener {
                            prefs.edit().remove(key).apply()
                            textView.setText(info.getIntValueAsString())
                            summary = info.getSummary()
                            true
                        }
                    }
                }
            preference.key = info.key
            preference.isPersistent = false
            preference.title = info.key
            preference.summary = info.getSummary()
            preference.widgetLayoutResource = R.layout.develop_options_edit_text
            parent.addPreference(preference)
        }
    }

    /**
     * Returns the summary to show the description and whether the flag overrides the default value.
     */
    private fun DebugInfo<*>.getSummary() =
        Html.fromHtml(
            (if (DeviceConfigHelper.prefs.contains(this.key))
                "<font color='red'><b>[OVERRIDDEN]</b></font><br>"
            else "") + this.desc
        )

    private fun DebugInfo<Boolean>.getBoolValue() =
        DeviceConfigHelper.prefs.getBoolean(
            this.key,
            DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, this.key, this.valueInCode)
        )

    private fun DebugInfo<Int>.getIntValueAsString() =
        DeviceConfigHelper.prefs
            .getInt(this.key, DeviceConfig.getInt(NAMESPACE_LAUNCHER, this.key, this.valueInCode))
            .toString()

    companion object {
        const val TAG = "DeviceConfigUIHelper"
    }
}
