/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.responsive

import android.content.res.TypedArray
import android.util.Log
import com.android.launcher3.R
import com.android.launcher3.util.ResourceHelper

class HotseatSpecs(val specs: List<HotseatSpec>) {

    fun getCalculatedHeightSpec(availableHeight: Int): CalculatedHotseatSpec {
        val spec = specs.firstOrNull { availableHeight <= it.maxAvailableSize }
        check(spec != null) { "No available height spec found within $availableHeight." }
        return CalculatedHotseatSpec(availableHeight, spec)
    }

    companion object {
        private const val XML_HOTSEAT_SPEC = "hotseatSpec"

        @JvmStatic
        fun create(resourceHelper: ResourceHelper): HotseatSpecs {
            val parser = ResponsiveSpecsParser(resourceHelper)
            val specs = parser.parseXML(XML_HOTSEAT_SPEC, ::HotseatSpec)
            return HotseatSpecs(specs.filter { it.specType == ResponsiveSpec.SpecType.HEIGHT })
        }
    }
}

data class HotseatSpec(
    val maxAvailableSize: Int,
    val specType: ResponsiveSpec.SpecType,
    val hotseatQsbSpace: SizeSpec
) {

    init {
        check(isValid()) { "Invalid HotseatSpec found." }
    }

    constructor(
        attrs: TypedArray,
        specs: Map<String, SizeSpec>
    ) : this(
        maxAvailableSize =
            attrs.getDimensionPixelSize(R.styleable.ResponsiveSpec_maxAvailableSize, 0),
        specType =
            ResponsiveSpec.SpecType.values()[
                    attrs.getInt(
                        R.styleable.ResponsiveSpec_specType,
                        ResponsiveSpec.SpecType.HEIGHT.ordinal
                    )],
        hotseatQsbSpace = specs.getOrError(SizeSpec.XmlTags.HOTSEAT_QSB_SPACE)
    )

    fun isValid(): Boolean {
        if (maxAvailableSize <= 0) {
            Log.e(LOG_TAG, "${this::class.simpleName}#isValid - maxAvailableSize <= 0")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            Log.e(LOG_TAG, "${this::class.simpleName}#isValid - !allSpecsAreValid()")
            return false
        }

        return true
    }

    private fun allSpecsAreValid(): Boolean {
        return hotseatQsbSpace.isValid() && hotseatQsbSpace.onlyFixedSize()
    }

    companion object {
        private const val LOG_TAG = "HotseatSpec"
    }
}

class CalculatedHotseatSpec(val availableSpace: Int, val spec: HotseatSpec) {

    var hotseatQsbSpace: Int = 0
        private set

    init {
        hotseatQsbSpace = spec.hotseatQsbSpace.getCalculatedValue(availableSpace)
    }

    override fun hashCode(): Int {
        var result = availableSpace.hashCode()
        result = 31 * result + hotseatQsbSpace.hashCode()
        result = 31 * result + spec.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is CalculatedHotseatSpec &&
            availableSpace == other.availableSpace &&
            hotseatQsbSpace == other.hotseatQsbSpace &&
            spec == other.spec
    }

    override fun toString(): String {
        return "${this::class.simpleName}(" +
            "availableSpace=$availableSpace, hotseatQsbSpace=$hotseatQsbSpace, " +
            "${spec::class.simpleName}.maxAvailableSize=${spec.maxAvailableSize}" +
            ")"
    }
}
