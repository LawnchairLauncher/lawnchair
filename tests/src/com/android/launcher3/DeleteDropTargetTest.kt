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

    // Needs mText, mTempRect, getPaddingTop, getPaddingBottom
    // availableHeight as a parameter
    @Test
    fun isTextClippedVerticallyTest() {
        buttonDropTarget.mText = "My Test"
        // No space for text
        assertThat(buttonDropTarget.isTextClippedVertically(30)).isTrue()

        // Some space for text, and just enough that the text should not be clipped
        assertThat(buttonDropTarget.isTextClippedVertically(50)).isFalse()

        // A lot of space for text so the text should not be clipped
        assertThat(buttonDropTarget.isTextClippedVertically(100)).isFalse()
    }
}
