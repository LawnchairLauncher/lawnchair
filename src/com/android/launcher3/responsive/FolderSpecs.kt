package com.android.launcher3.responsive

import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import com.android.launcher3.R
import com.android.launcher3.responsive.FolderSpec.*
import com.android.launcher3.util.ResourceHelper
import com.android.launcher3.workspace.CalculatedWorkspaceSpec
import com.android.launcher3.workspace.WorkspaceSpec
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val LOG_TAG = "FolderSpecs"

class FolderSpecs(resourceHelper: ResourceHelper) {

    object XmlTags {
        const val FOLDER_SPECS = "folderSpecs"

        const val FOLDER_SPEC = "folderSpec"
        const val START_PADDING = "startPadding"
        const val END_PADDING = "endPadding"
        const val GUTTER = "gutter"
        const val CELL_SIZE = "cellSize"
    }

    private val _heightSpecs = mutableListOf<FolderSpec>()
    val heightSpecs: List<FolderSpec>
        get() = _heightSpecs

    private val _widthSpecs = mutableListOf<FolderSpec>()
    val widthSpecs: List<FolderSpec>
        get() = _widthSpecs

    // TODO(b/286538013) Remove this init after a more generic or reusable parser is created
    init {
        var parser: XmlResourceParser? = null
        try {
            parser = resourceHelper.getXml()
            val depth = parser.depth
            var type: Int
            while (
                (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                    parser.depth > depth) && type != XmlPullParser.END_DOCUMENT
            ) {
                if (type == XmlPullParser.START_TAG && XmlTags.FOLDER_SPECS == parser.name) {
                    val displayDepth = parser.depth
                    while (
                        (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                            parser.depth > displayDepth) && type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (type == XmlPullParser.START_TAG && XmlTags.FOLDER_SPEC == parser.name) {
                            val attrs =
                                resourceHelper.obtainStyledAttributes(
                                    Xml.asAttributeSet(parser),
                                    R.styleable.FolderSpec
                                )
                            val maxAvailableSize =
                                attrs.getDimensionPixelSize(
                                    R.styleable.FolderSpec_maxAvailableSize,
                                    0
                                )
                            val specType =
                                SpecType.values()[
                                        attrs.getInt(
                                            R.styleable.FolderSpec_specType,
                                            SpecType.HEIGHT.ordinal
                                        )]
                            attrs.recycle()

                            var startPadding: SizeSpec? = null
                            var endPadding: SizeSpec? = null
                            var gutter: SizeSpec? = null
                            var cellSize: SizeSpec? = null

                            val limitDepth = parser.depth
                            while (
                                (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                                    parser.depth > limitDepth) && type != XmlPullParser.END_DOCUMENT
                            ) {
                                val attr: AttributeSet = Xml.asAttributeSet(parser)
                                if (type == XmlPullParser.START_TAG) {
                                    val sizeSpec = SizeSpec.create(resourceHelper, attr)
                                    when (parser.name) {
                                        XmlTags.START_PADDING -> startPadding = sizeSpec
                                        XmlTags.END_PADDING -> endPadding = sizeSpec
                                        XmlTags.GUTTER -> gutter = sizeSpec
                                        XmlTags.CELL_SIZE -> cellSize = sizeSpec
                                    }
                                }
                            }

                            checkNotNull(startPadding) {
                                "Attr 'startPadding' in FolderSpec must be defined."
                            }
                            checkNotNull(endPadding) {
                                "Attr 'endPadding' in FolderSpec must be defined."
                            }
                            checkNotNull(gutter) { "Attr 'gutter' in FolderSpec must be defined." }
                            checkNotNull(cellSize) {
                                "Attr 'cellSize' in FolderSpec must be defined."
                            }

                            val folderSpec =
                                FolderSpec(
                                    maxAvailableSize,
                                    specType,
                                    startPadding,
                                    endPadding,
                                    gutter,
                                    cellSize
                                )

                            check(folderSpec.isValid()) { "Invalid FolderSpec found." }

                            if (folderSpec.specType == SpecType.HEIGHT) {
                                _heightSpecs += folderSpec
                            } else {
                                _widthSpecs += folderSpec
                            }
                        }
                    }

                    check(_widthSpecs.isNotEmpty() && _heightSpecs.isNotEmpty()) {
                        "FolderSpecs is incomplete - " +
                            "height list size = ${_heightSpecs.size}; " +
                            "width list size = ${_widthSpecs.size}."
                    }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is IOException,
                is XmlPullParserException -> {
                    throw RuntimeException("Failure parsing folder specs file.", e)
                }
                else -> throw e
            }
        } finally {
            parser?.close()
        }
    }

    /**
     * Returns the [CalculatedFolderSpec] for width, based on the available width, FolderSpecs and
     * WorkspaceSpecs.
     */
    fun getWidthSpec(
        columns: Int,
        availableWidth: Int,
        workspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedFolderSpec {
        check(workspaceSpec.workspaceSpec.specType == WorkspaceSpec.SpecType.WIDTH) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: ${WorkspaceSpec.SpecType.WIDTH} - " +
                "Found: ${workspaceSpec.workspaceSpec.specType}}"
        }

        val widthSpec = _widthSpecs.firstOrNull { availableWidth <= it.maxAvailableSize }
        check(widthSpec != null) { "No FolderSpec for width spec found with $availableWidth." }

        return convertToCalculatedFolderSpec(widthSpec, availableWidth, columns, workspaceSpec)
    }

    /**
     * Returns the [CalculatedFolderSpec] for height, based on the available height, FolderSpecs and
     * WorkspaceSpecs.
     */
    fun getHeightSpec(
        rows: Int,
        availableHeight: Int,
        workspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedFolderSpec {
        check(workspaceSpec.workspaceSpec.specType == WorkspaceSpec.SpecType.HEIGHT) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: ${WorkspaceSpec.SpecType.HEIGHT} - " +
                "Found: ${workspaceSpec.workspaceSpec.specType}}"
        }

        val heightSpec = _heightSpecs.firstOrNull { availableHeight <= it.maxAvailableSize }
        check(heightSpec != null) { "No FolderSpec for height spec found with $availableHeight." }

        return convertToCalculatedFolderSpec(heightSpec, availableHeight, rows, workspaceSpec)
    }
}

data class CalculatedFolderSpec(
    val availableSpace: Int,
    val cells: Int,
    val startPaddingPx: Int,
    val endPaddingPx: Int,
    val gutterPx: Int,
    val cellSizePx: Int
)

/**
 * Responsive folder specs to be used to calculate the paddings, gutter and cell size for folders in
 * the workspace.
 *
 * @param maxAvailableSize indicates the breakpoint to use this specification.
 * @param specType indicates whether the paddings and gutters will be applied vertically or
 *   horizontally.
 * @param startPadding padding used at the top or left (right in RTL) in the workspace folder.
 * @param endPadding padding used at the bottom or right (left in RTL) in the workspace folder.
 * @param gutter the space between the cells vertically or horizontally depending on the [specType].
 * @param cellSize height or width of the cell depending on the [specType].
 */
data class FolderSpec(
    val maxAvailableSize: Int,
    val specType: SpecType,
    val startPadding: SizeSpec,
    val endPadding: SizeSpec,
    val gutter: SizeSpec,
    val cellSize: SizeSpec
) {

    enum class SpecType {
        HEIGHT,
        WIDTH
    }

    fun isValid(): Boolean {
        if (maxAvailableSize <= 0) {
            Log.e(LOG_TAG, "FolderSpec#isValid - maxAvailableSize <= 0")
            return false
        }

        // All specs are valid
        if (
            !(startPadding.isValid() &&
                endPadding.isValid() &&
                gutter.isValid() &&
                cellSize.isValid())
        ) {
            Log.e(LOG_TAG, "FolderSpec#isValid - !allSpecsAreValid()")
            return false
        }

        return true
    }
}

/** Helper function to convert [FolderSpec] to [CalculatedFolderSpec] */
private fun convertToCalculatedFolderSpec(
    folderSpec: FolderSpec,
    availableSpace: Int,
    cells: Int,
    workspaceSpec: CalculatedWorkspaceSpec
): CalculatedFolderSpec {
    // Map if is fixedSize, ofAvailableSpace or matchWorkspace
    var startPaddingPx =
        folderSpec.startPadding.getCalculatedValue(availableSpace, workspaceSpec.startPaddingPx)
    var endPaddingPx =
        folderSpec.endPadding.getCalculatedValue(availableSpace, workspaceSpec.endPaddingPx)
    var gutterPx = folderSpec.gutter.getCalculatedValue(availableSpace, workspaceSpec.gutterPx)
    var cellSizePx =
        folderSpec.cellSize.getCalculatedValue(availableSpace, workspaceSpec.cellSizePx)

    // Remainder space
    val gutters = cells - 1
    val usedSpace = startPaddingPx + endPaddingPx + (gutterPx * gutters) + (cellSizePx * cells)
    val remainderSpace = availableSpace - usedSpace

    startPaddingPx = folderSpec.startPadding.getRemainderSpaceValue(remainderSpace, startPaddingPx)
    endPaddingPx = folderSpec.endPadding.getRemainderSpaceValue(remainderSpace, endPaddingPx)
    gutterPx = folderSpec.gutter.getRemainderSpaceValue(remainderSpace, gutterPx)
    cellSizePx = folderSpec.cellSize.getRemainderSpaceValue(remainderSpace, cellSizePx)

    return CalculatedFolderSpec(
        availableSpace = availableSpace,
        cells = cells,
        startPaddingPx = startPaddingPx,
        endPaddingPx = endPaddingPx,
        gutterPx = gutterPx,
        cellSizePx = cellSizePx
    )
}
