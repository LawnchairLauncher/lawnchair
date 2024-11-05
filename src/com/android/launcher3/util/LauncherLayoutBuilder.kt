/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.util

import android.util.Xml
import com.android.launcher3.AutoInstallsLayout.ATTR_CLASS_NAME
import com.android.launcher3.AutoInstallsLayout.ATTR_CONTAINER
import com.android.launcher3.AutoInstallsLayout.ATTR_PACKAGE_NAME
import com.android.launcher3.AutoInstallsLayout.ATTR_RANK
import com.android.launcher3.AutoInstallsLayout.ATTR_SCREEN
import com.android.launcher3.AutoInstallsLayout.ATTR_SHORTCUT_ID
import com.android.launcher3.AutoInstallsLayout.ATTR_SPAN_X
import com.android.launcher3.AutoInstallsLayout.ATTR_SPAN_Y
import com.android.launcher3.AutoInstallsLayout.ATTR_TITLE
import com.android.launcher3.AutoInstallsLayout.ATTR_TITLE_TEXT
import com.android.launcher3.AutoInstallsLayout.ATTR_USER_TYPE
import com.android.launcher3.AutoInstallsLayout.ATTR_X
import com.android.launcher3.AutoInstallsLayout.ATTR_Y
import com.android.launcher3.AutoInstallsLayout.TAG_APPWIDGET
import com.android.launcher3.AutoInstallsLayout.TAG_AUTO_INSTALL
import com.android.launcher3.AutoInstallsLayout.TAG_FOLDER
import com.android.launcher3.AutoInstallsLayout.TAG_SHORTCUT
import com.android.launcher3.AutoInstallsLayout.TAG_WORKSPACE
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.containerToString
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import org.xmlpull.v1.XmlSerializer

/** Helper class to build xml for Launcher Layout */
class LauncherLayoutBuilder {
    private val nodes = ArrayList<Node>()

    fun atHotseat(rank: Int) =
        ItemTarget(
            mapOf(
                ATTR_CONTAINER to containerToString(CONTAINER_HOTSEAT),
                ATTR_RANK to rank.toString()
            )
        )

    fun atWorkspace(x: Int, y: Int, screen: Int) =
        ItemTarget(
            mapOf(
                ATTR_CONTAINER to containerToString(CONTAINER_DESKTOP),
                ATTR_X to x.toString(),
                ATTR_Y to y.toString(),
                ATTR_SCREEN to screen.toString()
            )
        )

    @Throws(IOException::class) fun build() = StringWriter().apply { build(this) }.toString()

    @Throws(IOException::class)
    fun build(writer: Writer) {
        Xml.newSerializer().apply {
            setOutput(writer)
            setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
            startDocument("UTF-8", true)
            startTag(null, TAG_WORKSPACE)
            writeNodes(nodes)
            endTag(null, TAG_WORKSPACE)
            endDocument()
            flush()
        }
    }

    open inner class ItemTarget(private val baseValues: Map<String, String>) {
        @JvmOverloads
        fun putApp(packageName: String, className: String?, userType: String? = null) =
            addItem(
                TAG_AUTO_INSTALL,
                userType,
                mapOf(
                    ATTR_PACKAGE_NAME to packageName,
                    ATTR_CLASS_NAME to (className ?: packageName)
                )
            )

        @JvmOverloads
        fun putShortcut(packageName: String, shortcutId: String, userType: String? = null) =
            addItem(
                TAG_SHORTCUT,
                userType,
                mapOf(ATTR_PACKAGE_NAME to packageName, ATTR_SHORTCUT_ID to shortcutId)
            )

        @JvmOverloads
        fun putWidget(
            packageName: String,
            className: String,
            spanX: Int,
            spanY: Int,
            userType: String? = null
        ) =
            addItem(
                TAG_APPWIDGET,
                userType,
                mapOf(
                    ATTR_PACKAGE_NAME to packageName,
                    ATTR_CLASS_NAME to className,
                    ATTR_SPAN_X to spanX.toString(),
                    ATTR_SPAN_Y to spanY.toString()
                )
            )

        fun putFolder(titleResId: Int) = putFolder(ATTR_TITLE, titleResId.toString())

        fun putFolder(title: String?) = putFolder(ATTR_TITLE_TEXT, title)

        protected open fun addItem(
            tag: String,
            userType: String?,
            props: Map<String, String>,
            children: List<Node>? = null
        ): LauncherLayoutBuilder {
            nodes.add(
                Node(
                    tag,
                    HashMap(baseValues).apply {
                        putAll(props)
                        userType?.let { put(ATTR_USER_TYPE, it) }
                    },
                    children
                )
            )
            return this@LauncherLayoutBuilder
        }

        protected open fun putFolder(titleKey: String, titleValue: String?): FolderBuilder {
            val folderBuilder = FolderBuilder()
            addItem(TAG_FOLDER, null, mapOf(titleKey to (titleValue ?: "")), folderBuilder.children)
            return folderBuilder
        }
    }

    inner class FolderBuilder : ItemTarget(mapOf()) {

        val children = ArrayList<Node>()

        fun addApp(packageName: String, className: String?): FolderBuilder {
            putApp(packageName, className)
            return this
        }

        fun addShortcut(packageName: String, shortcutId: String): FolderBuilder {
            putShortcut(packageName, shortcutId)
            return this
        }

        override fun addItem(
            tag: String,
            userType: String?,
            props: Map<String, String>,
            childrenIgnored: List<Node>?
        ): LauncherLayoutBuilder {
            children.add(
                Node(tag, HashMap(props).apply { userType?.let { put(ATTR_USER_TYPE, it) } })
            )
            return this@LauncherLayoutBuilder
        }

        override fun putFolder(titleKey: String, titleValue: String?): FolderBuilder {
            throw IllegalArgumentException("Can't have folder inside a folder")
        }

        fun build() = this@LauncherLayoutBuilder
    }

    @Throws(IOException::class)
    private fun XmlSerializer.writeNodes(nodes: List<Node>) {
        nodes.forEach { node ->
            startTag(null, node.name)
            node.attrs.forEach { (key, value) -> attribute(null, key, value) }
            node.children?.let { writeNodes(it) }
            endTag(null, node.name)
        }
    }

    data class Node(
        val name: String,
        val attrs: Map<String, String>,
        val children: List<Node>? = null
    )
}
