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
import android.content.res.XmlResourceParser
import android.util.Xml
import com.android.launcher3.R
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType
import com.android.launcher3.util.ResourceHelper
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class ResponsiveSpecsParser(private val resourceHelper: ResourceHelper) {

    fun <T : IResponsiveSpec> parseXML(
        responsiveSpecType: ResponsiveSpecType,
        map:
            (
                responsiveSpecType: ResponsiveSpecType,
                attributes: TypedArray,
                sizeSpecs: Map<String, SizeSpec>
            ) -> T
    ): List<ResponsiveSpecGroup<T>> {
        val parser: XmlResourceParser = resourceHelper.getXml()

        try {
            val groups = mutableListOf<ResponsiveSpecGroup<T>>()
            val specs = mutableListOf<T>()
            var groupAttrs: TypedArray? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                // Parsing Group
                when {
                    parser starts ResponsiveSpecGroup.XML_GROUP_NAME -> {
                        groupAttrs =
                            resourceHelper.obtainStyledAttributes(
                                Xml.asAttributeSet(parser),
                                R.styleable.ResponsiveSpecGroup
                            )
                    }
                    parser ends ResponsiveSpecGroup.XML_GROUP_NAME -> {
                        checkNotNull(groupAttrs)
                        groups += ResponsiveSpecGroup.create(groupAttrs, specs)
                        specs.clear()
                        groupAttrs.recycle()
                        groupAttrs = null
                    }
                    // Mapping Spec to WorkspaceSpec, AllAppsSpec, FolderSpecs, HotseatSpec
                    parser starts responsiveSpecType.xmlTag -> {
                        val attrs =
                            resourceHelper.obtainStyledAttributes(
                                Xml.asAttributeSet(parser),
                                R.styleable.ResponsiveSpec
                            )

                        val sizeSpecs = parseSizeSpecs(parser)
                        specs += map(responsiveSpecType, attrs, sizeSpecs)
                        attrs.recycle()
                    }
                }

                eventType = parser.next()
            }

            parser.close()

            // All the specs should have been linked to a group, otherwise the XML is invalid
            check(specs.isEmpty()) {
                throw InvalidResponsiveGridSpec(
                    "Invalid XML. ${specs.size} specs not linked to a group."
                )
            }

            return groups
        } catch (e: Exception) {
            when (e) {
                is NoSuchFieldException,
                is IOException,
                is XmlPullParserException ->
                    throw RuntimeException("Failure parsing specs file.", e)
                else -> throw e
            }
        } finally {
            parser.close()
        }
    }

    private fun parseSizeSpecs(parser: XmlResourceParser): Map<String, SizeSpec> {
        val parentName = parser.name
        parser.next()

        val result = mutableMapOf<String, SizeSpec>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT && parser.name != parentName) {
            if (parser.eventType == XmlResourceParser.START_TAG) {
                result[parser.name] = SizeSpec.create(resourceHelper, Xml.asAttributeSet(parser))
            }
            parser.next()
        }

        return result
    }

    private infix fun XmlResourceParser.starts(tag: String): Boolean =
        name == tag && eventType == XmlPullParser.START_TAG

    private infix fun XmlResourceParser.ends(tag: String): Boolean =
        name == tag && eventType == XmlPullParser.END_TAG
}

fun Map<String, SizeSpec>.getOrError(key: String): SizeSpec {
    return this.getOrElse(key) { error("Attr '$key' must be defined.") }
}

class InvalidResponsiveGridSpec(message: String) : Exception(message)
