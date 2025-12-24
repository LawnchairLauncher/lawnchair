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

package com.android.launcher3.taskbar.bubbles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.taskbar.bubbles.BubbleBarController.BubbleBarLocationListener
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DropTargetView
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/** Unit tests for [DragToBubbleControllerTest]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DragToBubbleControllerTest {

    @get:Rule val animatorTestRule = AnimatorTestRule()

    private val context = getApplicationContext<Context>()
    private val container = FrameLayout(context)
    private val bubbleBarViewController: BubbleBarViewController = mock()
    private val bubbleActivityStarter: BubbleActivityStarter = mock()
    private val bubbleBarLocationListener: BubbleBarLocationListener = mock()
    private val bubbleBarPropertiesProvider = FakeBubbleBarPropertiesProvider()
    private val testDragZonesFactory = createTestDragZoneFactory()
    private val dragObject = DragObject(context)
    private val packageName = "test.package"
    private val itemIntent =
        Intent().apply {
            component = ComponentName(packageName, "TestClass")
            `package` = packageName
        }
    private val appInfo = AppInfo().apply { intent = itemIntent }
    private lateinit var dragToBubbleController: DragToBubbleController

    private val dropTargetView: DropTargetView
        get() = dragToBubbleController.launcherDropTargetManager.dropTargetView

    private val secondDropTargetView: DropTargetView?
        get() = dragToBubbleController.launcherDropTargetManager.secondDropTargetView

    private val shellDropTargetView: DropTargetView?
        get() = dragToBubbleController.shellDropTargetManager.secondDropTargetView

    private val bubbleBarLeftDropTarget: DropTarget
        get() = dragToBubbleController.bubbleBarLeftDropTarget

    private val bubbleBarRightDropTarget: DropTarget
        get() = dragToBubbleController.bubbleBarRightDropTarget

    private val leftDropTargetRect: Rect
        get() = testDragZonesFactory.getBubbleBarDropRect(isLeftSide = true)

    private val rightDropTargetRect: Rect
        get() = testDragZonesFactory.getBubbleBarDropRect(isLeftSide = false)

    @Before
    fun setUp() {
        prepareBubbleBarViewController()
        setAppInfo()
        dragToBubbleController = DragToBubbleController(context, container)
        dragToBubbleController.init(
            bubbleBarViewController,
            bubbleBarPropertiesProvider,
            bubbleBarLocationListener,
            bubbleActivityStarter,
        )
        dragToBubbleController.dragZoneFactory = testDragZonesFactory
    }

    @Test
    fun dragStarted_noAppInfo_noDropZonesAdded() {
        dragObject.dragInfo = null
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        assertThat(bubbleBarLeftDropTarget.isDropEnabled).isFalse()
        assertThat(bubbleBarRightDropTarget.isDropEnabled).isFalse()
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun dragStarted_noBubbleBar_dropZonesAdded() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        assertThat(bubbleBarLeftDropTarget.isDropEnabled).isTrue()
        assertThat(bubbleBarRightDropTarget.isDropEnabled).isTrue()
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_NO_BUBBLE_BAR)
        assertThat(dropTargetView.parent).isEqualTo(container)
        assertThat(secondDropTargetView!!.parent).isEqualTo(container)
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(0f)
        assertThat(dragToBubbleController.isItemDropHandled).isFalse()
    }

    @Test
    fun dragStarted_hasBubbleBar_dropZonesAdded() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)
        assertThat(dropTargetView.parent).isEqualTo(container)
        assertThat(dropTargetView.alpha).isEqualTo(0f)
        assertThat(secondDropTargetView).isNull()
    }

    @Test
    fun dragEnded_allViewsRemoved() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onDragEnd()
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }

        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun draggedToTheRightDropZone_noBubbles_dropTargetViewsShown() {
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(secondDropTargetView!!.alpha).isEqualTo(1f)
        verify(bubbleBarViewController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun draggedToTheRightDropZone_hasBubblesOnTheRight_dropTargetViewShown() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }

        assertThat(dropTargetView.alpha).isEqualTo(1f)
        assertThat(secondDropTargetView).isNull()
    }

    @Test
    fun draggedToTheRightDropZone_hasNoAppData_Noop() {
        dragObject.dragInfo = null
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }

        assertThat(container.childCount).isEqualTo(0)
        assertThat(bubbleBarLeftDropTarget.isDropEnabled).isFalse()
        assertThat(bubbleBarRightDropTarget.isDropEnabled).isFalse()
    }

    @Test
    fun draggedToTheRightDropZone_hasBubblesOnTheRight_bubbleBarLocationChangeNotRequested() {
        prepareBubbleBarViewController(hasBubbles = true)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        verify(bubbleBarViewController, never()).animateBubbleBarLocation(any())
    }

    @Test
    fun draggedToTheLeftDropZone_hasBubblesOnTheRight_bubbleBarLocationChangeRequested() {
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.RIGHT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }
        verify(bubbleBarViewController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
    }

    @Test
    fun draggedToTheLeftDropZone_dragEnded_hasBubblesOnTheRight_locationRestored() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = bubbleBarOriginalLocation,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
            dragObject.updateXY(x = 0, y = 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        verify(bubbleBarViewController).animateBubbleBarLocation(BubbleBarLocation.LEFT)
        verify(bubbleBarViewController).animateBubbleBarLocation(bubbleBarOriginalLocation)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun acceptDrop_nullItemInfo_returnsFalse() {
        // Prepare DragObject with null dragInfo
        val dragObjectWithNullInfo = DragObject(context)
        dragObjectWithNullInfo.dragInfo = null // Explicitly set to null

        dragToBubbleController.onDragStart(dragObjectWithNullInfo, DragOptions())

        // Attempt to drop on the left target
        val acceptedLeft = bubbleBarLeftDropTarget.acceptDrop(dragObjectWithNullInfo)
        assertThat(acceptedLeft).isFalse()

        // Attempt to drop on the right target
        val acceptedRight = bubbleBarRightDropTarget.acceptDrop(dragObjectWithNullInfo)
        assertThat(acceptedRight).isFalse()
    }

    @Test
    fun onDrop_nullItemInfo_onLeftTarget_noSysUiProxyInteractionAndDropNotHandled() {
        // Prepare DragObject with null dragInfo
        val dragObjectWithNullInfo = DragObject(context)
        dragObjectWithNullInfo.dragInfo = null
        dragToBubbleController.onDragStart(dragObjectWithNullInfo, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObjectWithNullInfo.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(
                dragObjectWithNullInfo
            ) // Needed for drop target to be active
            bubbleBarLeftDropTarget.onDrop(dragObjectWithNullInfo, DragOptions())
        }

        assertThat(dragToBubbleController.isItemDropHandled).isFalse()
        verify(bubbleActivityStarter, never()).showAppBubble(any(), any(), any(), any())
        verify(bubbleActivityStarter, never()).showShortcutBubble(any(), any(), any())
    }

    @Test
    fun droppedAtTheLeftDropZone_noBubblesOnTheRight_appBubbleCreationRequested() {
        val bubbleBarOriginalLocation = BubbleBarLocation.RIGHT
        prepareBubbleBarViewController(
            hasBubbles = false,
            bubbleBarLocation = bubbleBarOriginalLocation,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
            bubbleBarLeftDropTarget.onDrop(dragObject, DragOptions())
            assertThat(dragToBubbleController.isItemDropHandled).isTrue()
            bubbleBarLeftDropTarget.onDragExit(dragObject)
        }

        // Intent does not implement equals() so we need to capture and compare the intents manually
        val intentCaptor = argumentCaptor<Intent>()
        verify(bubbleActivityStarter)
            .showAppBubble(
                intentCaptor.capture(),
                eq(appInfo.user),
                eq(EntryPoint.TASKBAR_ICON_DRAG),
                eq(BubbleBarLocation.LEFT),
            )
        assertThat(intentCaptor.firstValue.filterEquals(itemIntent)).isTrue()
    }

    @Test
    fun dragExitRightZone_noBubbles_listenerNotNotified() {
        // Scenario: No bubbles. Drag enters RIGHT, then exits to no particular zone.
        // This is distinct as it starts on the default side.
        prepareBubbleBarViewController(
            hasBubbles = false,
            bubbleBarLocation = BubbleBarLocation.RIGHT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject) // Location is the same
        }
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0) // Move out of all zones
            bubbleBarRightDropTarget.onDragExit(dragObject)
        }

        // Exiting the RIGHT zone (which is the default) should not re-notify of RIGHT
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    @Test
    fun onDragEnd_noBubbles_wasDraggingLeft_listenerNotifiedWithDefaultRightLocationAnimated() {
        val startingLocation = BubbleBarLocation.RIGHT
        // Scenario: No bubbles. Drag was over LEFT zone. Drag ends.
        prepareBubbleBarViewController(hasBubbles = false, bubbleBarLocation = startingLocation)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }
        // Notifies onBubbleBarLocationAnimated(LEFT)
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
        clearInvocations(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onDragEnd()
        }
        assertThat(dragToBubbleController.isItemDropHandled).isFalse()

        // After drag ends (and no bubbles), the listener should be notified of the default location
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(startingLocation)
    }

    @Test
    fun onDragEnd_noBubbles_wasDraggingRight_listenerNotifiedWithDefaultRightLocationAnimated() {
        // Scenario: No bubbles. Drag was over RIGHT zone (default side). Drag ends.
        prepareBubbleBarViewController(hasBubbles = false)
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
        }
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
        clearInvocations(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        // After drag ends (and no bubbles), listener  should not be notified of the default
        // location.
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    @Test
    fun dragEnterLeftZone_bubblesOnLeft_listenerNotNotified() {
        // Scenario: Bubbles on LEFT. Drag enters LEFT zone.
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.LEFT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)
        }

        // Bubbles are already on the LEFT, and drag enters LEFT.
        // No new animation to LEFT should be triggered by the zone entry itself.
        verify(bubbleBarLocationListener, never())
            .onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
    }

    @Test
    fun onDragEnd_bubblesOnLeft_defaultIsLeft_wasDraggingRight_listenerNotifiedLeftAnimated() {
        // Scenario: Bubbles on LEFT. Drag was over RIGHT zone. Drag ends.
        prepareBubbleBarViewController(
            hasBubbles = true,
            bubbleBarLocation = BubbleBarLocation.LEFT,
        )
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject) // Notifies Animated(RIGHT)
        }
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
        clearInvocations(bubbleBarLocationListener) // Clear the Animated(RIGHT)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)
            dragToBubbleController.onDragEnd()
        }

        // Bubble bar's final animated location should be LEFT.
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
    }

    @Test
    fun dragEnterLeftThenExitToNoZoneThenEnterRight_noBubbles_listenerSequenceCorrectAnimated() {
        // Scenario: No bubbles. Complex drag path: Left -> None -> Right
        prepareBubbleBarViewController(hasBubbles = false)
        dragToBubbleController.onDragStart(dragObject, DragOptions())
        clearInvocations(bubbleBarLocationListener)

        val inOrder = inOrder(bubbleBarLocationListener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // 1. Enter Left
            dragObject.updateXYToCenterOf(leftDropTargetRect)
            bubbleBarLeftDropTarget.onDragEnter(dragObject)

            // 2. Exit Left to no zone
            dragObject.updateXY(0, 0)
            bubbleBarLeftDropTarget.onDragExit(dragObject)

            // 3. Enter Right
            dragObject.updateXYToCenterOf(rightDropTargetRect)
            bubbleBarRightDropTarget.onDragEnter(dragObject)
        }

        inOrder
            .verify(bubbleBarLocationListener)
            .onBubbleBarLocationAnimated(BubbleBarLocation.LEFT)
        // Revert to default, following enter of the same zone should not trigger updated
        inOrder
            .verify(bubbleBarLocationListener)
            .onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
    }

    @Test
    fun onShellDragStateChanged_true_preparesShellDragManager() {
        // When
        dragToBubbleController.onShellDragStateChanged(true)

        // Then
        assertThat(shellDropTargetView!!.alpha).isEqualTo(0f)
        assertThat(shellDropTargetView!!.parent).isEqualTo(container)
    }

    @Test
    fun onShellDragStateChanged_false_endsShellDragManager() {
        // Given
        dragToBubbleController.onShellDragStateChanged(true)

        // When, end the drag state
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onShellDragStateChanged(false)
            animatorTestRule.advanceTimeBy(
                ANIMATION_DELAY_MS
            ) // Advance time for fade out animation
        }

        // Then drop target view should be removed from container
        assertThat(container.childCount).isEqualTo(0)
        assertThat(shellDropTargetView).isNull()
    }

    @Test
    fun showShellBubbleBarDropTargetAt_leftLocation_showsLeftDropTarget() {
        // Given
        dragToBubbleController.onShellDragStateChanged(true)
        // When
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.LEFT)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        // Then view should be visible
        assertThat(shellDropTargetView!!.alpha).isEqualTo(1f)
        assertThat(shellDropTargetView!!.parent).isEqualTo(container)
    }

    @Test
    fun showShellBubbleBarDropTargetAt_rightLocation_showsRightDropTarget() {
        // Given
        dragToBubbleController.onShellDragStateChanged(true)
        // When
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.RIGHT)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        // Then view should be visible
        assertThat(shellDropTargetView!!.alpha).isEqualTo(1f)
        assertThat(shellDropTargetView!!.parent).isEqualTo(container)
    }

    @Test
    fun showShellBubbleBarDropTargetAtLeft_nullLocation_hidesDropTarget() {
        // Given
        dragToBubbleController.onShellDragStateChanged(true)
        // When show LEFT drop target
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.LEFT)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        // Then view should be visible
        assertThat(shellDropTargetView!!.alpha).isEqualTo(1f)

        // When show null location
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(null)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        // Then view should be hidden
        assertThat(shellDropTargetView!!.alpha).isEqualTo(0f)
    }

    @Test
    fun showShellBubbleBarDropTargetToNotNullLocation_setsBubbleBarIsShowingDropTargetToTrue() {
        // When show null location
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.RIGHT)
        }
        // Then bubbleBarViewController isShowingDropTarget should change to true
        verify(bubbleBarViewController).isShowingDropTarget = true
    }

    @Test
    fun showShellBubbleBarDropTargetNullLocation_setsBubbleBarIsShowingDropTargetToFalse() {
        // When show null location
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(null)
        }
        // Then bubbleBarViewController isShowingDropTarget should change to false
        verify(bubbleBarViewController).isShowingDropTarget = false
    }

    @Test
    fun showShellBubbleBarDropTargetAt_consecutiveCallsSameLocation_noCallsToListener() {
        // Given
        prepareBubbleBarViewController(bubbleBarLocation = BubbleBarLocation.LEFT)
        dragToBubbleController.onShellDragStateChanged(true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.RIGHT)
            animatorTestRule.advanceTimeBy(ANIMATION_DELAY_MS)
        }
        verify(bubbleBarLocationListener).onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT)
        clearInvocations(bubbleBarLocationListener)

        // When calling again with the same location
        repeat(10) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                dragToBubbleController.showShellBubbleBarDropTargetAt(BubbleBarLocation.RIGHT)
            }
        }
        // Then no new calls to onDragUpdated
        verify(bubbleBarLocationListener, never()).onBubbleBarLocationAnimated(any())
    }

    @Test
    fun setOverlayContainerView_nullPassed_sameContainerIsUsed() {
        // Given
        dragToBubbleController.setOverlayContainerView(null)
        // When
        dragToBubbleController.onDragStart(dragObject, DragOptions())
        // Then
        val dropContainer = dragToBubbleController.launcherDropTargetManager.dropTargetView.parent
        assertThat(dropContainer).isEqualTo(container)
        assertThat(container.childCount).isEqualTo(DROP_VIEWS_COUNT_NO_BUBBLE_BAR)
    }

    @Test
    fun setOverlayContainerView_containerPassed_newContainerIsUsed() {
        // Given
        val newContainer = FrameLayout(context)
        dragToBubbleController.setOverlayContainerView(newContainer)
        // When
        dragToBubbleController.onDragStart(dragObject, DragOptions())
        // Then
        val dropContainer = dragToBubbleController.launcherDropTargetManager.dropTargetView.parent
        assertThat(dropContainer).isEqualTo(newContainer)
        assertThat(container.childCount).isEqualTo(0)
        assertThat(newContainer.childCount).isEqualTo(DROP_VIEWS_COUNT_NO_BUBBLE_BAR)
    }

    @Test
    fun isDragInProgress_initially_returnsFalse() {
        assertThat(dragToBubbleController.isDragInProgress).isFalse()
    }

    @Test
    fun isDragInProgress_afterLauncherDragStart_returnsTrue() {
        // When
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        // Then
        assertThat(dragToBubbleController.isDragInProgress).isTrue()
    }

    @Test
    fun isDragInProgress_afterShellDragStart_returnsTrue() {
        // When
        dragToBubbleController.onShellDragStateChanged(true)

        // Then
        assertThat(dragToBubbleController.isDragInProgress).isTrue()
    }

    @Test
    fun isDragInProgress_afterLauncherDragStartAndDragEnd_returnsFalse() {
        // Given
        dragToBubbleController.onDragStart(dragObject, DragOptions())

        // When
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onDragEnd()
        }

        // Then
        assertThat(dragToBubbleController.isDragInProgress).isFalse()
    }

    @Test
    fun isDragInProgress_afterLauncherAndShellDragStartAndDragForShellEnd_returnsTrue() {
        // Given
        dragToBubbleController.onDragStart(dragObject, DragOptions())
        dragToBubbleController.onShellDragStateChanged(true)

        // When
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dragToBubbleController.onShellDragStateChanged(false)
        }

        // Then
        assertThat(dragToBubbleController.isDragInProgress).isTrue()
    }

    private fun prepareBubbleBarViewController(
        hasBubbles: Boolean = false,
        bubbleBarLocation: BubbleBarLocation = BubbleBarLocation.RIGHT,
    ) {
        bubbleBarViewController.stub {
            on { hasBubbles() } doReturn hasBubbles
            on { getBubbleBarLocation() } doReturn bubbleBarLocation
        }
    }

    private fun setAppInfo() {
        dragObject.dragInfo = appInfo
    }

    private fun DragObject.updateXYToCenterOf(rect: Rect) {
        updateXY(rect.centerX(), rect.centerY())
    }

    private fun DragObject.updateXY(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    private fun createTestDragZoneFactory(): DragZoneFactory {
        val deviceConfig =
            DeviceConfig(
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                insets = Insets.NONE,
            )
        return DragZoneFactory(
            context,
            deviceConfig,
            { SplitScreenMode.NONE },
            { false },
            bubbleBarPropertiesProvider,
        )
    }

    private class FakeBubbleBarPropertiesProvider : BubbleBarPropertiesProvider {

        override fun getHeight(): Int = BUBBLE_BAR_HEIGHT

        override fun getWidth(): Int = BUBBLE_BAR_WIDTH

        override fun getBottomPadding(): Int = BUBBLE_BAR_BOTTOM_PADDING
    }

    companion object {
        const val BUBBLE_BAR_WIDTH = 100
        const val BUBBLE_BAR_HEIGHT = 110
        const val BUBBLE_BAR_BOTTOM_PADDING = 70
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
        const val DROP_VIEWS_COUNT = 1
        const val DROP_VIEWS_COUNT_NO_BUBBLE_BAR = 2
        const val ANIMATION_DELAY_MS = 250L
    }
}
