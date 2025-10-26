package com.android.launcher3

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Utilities.*
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeleteDropTargetTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()

    @Mock private val msdlPlayerWrapper = mock<MSDLPlayerWrapper>()

    private var mContext: Context = ActivityContextWrapper(getApplicationContext())

    // Use a non-abstract class implementation
    private var buttonDropTarget: DeleteDropTarget = DeleteDropTarget(mContext)

    @Before
    fun setup() {
        enableRunningInTestHarnessForTests()
    }

    @Test
    fun isTextClippedVerticallyTest() {
        buttonDropTarget.updateText("My Test")
        buttonDropTarget.setPadding(0, 0, 0, 0)
        buttonDropTarget.setTextMultiLine(false)

        // No space for text
        assertThat(buttonDropTarget.isTextClippedVertically(1)).isTrue()

        // A lot of space for text so the text should not be clipped
        assertThat(buttonDropTarget.isTextClippedVertically(1000)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onDragEnter_performsMSDLSwipeThresholdFeedback() {
        buttonDropTarget.setMSDLPlayerWrapper(msdlPlayerWrapper)
        val target = DropTarget.DragObject(mContext)
        target.dragView = mock<DragView<*>>()
        buttonDropTarget.onDragEnter(target)

        verify(msdlPlayerWrapper, times(1)).playToken(eq(MSDLToken.SWIPE_THRESHOLD_INDICATOR))
        verifyNoMoreInteractions(msdlPlayerWrapper)
    }
}
