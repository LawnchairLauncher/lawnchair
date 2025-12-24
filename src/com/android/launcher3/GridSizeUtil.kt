/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3

import android.content.Context
import android.util.Log
import android.util.Xml
import com.android.launcher3.AutoInstallsLayout.getAttributeValueAsInt
import java.io.StringReader
import kotlin.math.abs
import org.xmlpull.v1.XmlPullParserException

/**
 * This parser checks for two attributes - "rows" & "columns" - at the base level of the given xml.
 * If those exist, it updates the launcher's grid using those values. If no grid values exist with
 * those sizes, it attempts a next-best-valid-option approximation based on an arbitrary assessment.
 */
class GridSizeUtil(private val context: Context) {
    companion object {
        private const val TAG = "GridSizeParser"

        const val COLUMN_ATTR = "columns"
        const val ROW_ATTR = "rows"
    }

    /**
     * Given an XML string in a format compatible with Launcher's AutoInstall feature, this method
     * will retrieve the row and column count attributes from the base XML, and then use those
     * values to update the grid being used by launcher.
     *
     * <p>
     *
     * @param xml string that needs to conform to Launcher's AutoInstalls format.
     */
    fun parseAndSetGridSize(xml: String) {
        try {
            val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }

            // Only accept Launcher3 XML format, not NexusLauncher XML format.
            AutoInstallsLayout.beginDocument(parser, AutoInstallsLayout.TAG_WORKSPACE)

            val (rows, columns) =
                getAttributeValueAsInt(parser, ROW_ATTR) to
                    getAttributeValueAsInt(parser, COLUMN_ATTR)

            val idp = InvariantDeviceProfile.INSTANCE.get(context)
            val gridOptions = idp.parseAllGridOptions(context)

            if (gridOptions.isEmpty()) {
                Log.w(TAG, "no grid options, can't set grid size to [columns=$columns,rows=$rows]")
                return
            }

            // First find the closest grid option where both the rows and columns are equal to or
            // larger than the parsed grid dimensions. If none exist, then find use the closest
            // match. Prefer larger grids so no data is lost during import.
            val closestMatch =
                gridOptions
                    .filter { it.numRows >= rows && it.numColumns >= columns }
                    .minByOrNull { it.numRows + it.numColumns }
                    ?: gridOptions.minBy { abs(it.numRows - rows) + abs(it.numColumns - columns) }

            idp.setCurrentGrid(closestMatch.name)
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "failed to parse or set xml for grid row and column counts", e)
        }
    }
}
