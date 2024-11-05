package com.android.launcher3

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Utilities.*
import com.android.launcher3.util.ActivityContextWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeleteDropTargetTest {

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
}
