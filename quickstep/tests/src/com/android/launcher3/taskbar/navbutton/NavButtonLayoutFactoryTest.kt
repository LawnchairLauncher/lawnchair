package com.android.launcher3.taskbar.navbutton

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.runner.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.taskbar.TaskbarManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import com.android.launcher3.R
import org.junit.Assume.assumeTrue
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import java.lang.IllegalStateException

@RunWith(AndroidJUnit4::class)
class NavButtonLayoutFactoryTest {

    @Mock
    lateinit var mockDeviceProfile: DeviceProfile
    @Mock
    lateinit var mockParentButtonContainer: FrameLayout
    @Mock
    lateinit var mockNavLayout: LinearLayout
    @Mock
    lateinit var mockStartContextualLayout: ViewGroup
    @Mock
    lateinit var mockEndContextualLayout: ViewGroup
    @Mock
    lateinit var mockResources: Resources
    @Mock
    lateinit var mockBackButton: ImageView
    @Mock
    lateinit var mockRecentsButton: ImageView
    @Mock
    lateinit var mockHomeButton: ImageView

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        // Init end nav buttons
        whenever(mockNavLayout.childCount).thenReturn(3)
        whenever(mockNavLayout.findViewById<View>(R.id.back)).thenReturn(mockBackButton)
        whenever(mockNavLayout.findViewById<View>(R.id.home)).thenReturn(mockHomeButton)
        whenever(mockNavLayout.findViewById<View>(R.id.recent_apps)).thenReturn(mockRecentsButton)

        // Init top level layout
        whenever(mockParentButtonContainer.findViewById<LinearLayout>(R.id.end_nav_buttons))
                .thenReturn(mockNavLayout)
        whenever(mockParentButtonContainer.findViewById<ViewGroup>(R.id.end_contextual_buttons))
                .thenReturn(mockEndContextualLayout)
        whenever(mockParentButtonContainer.findViewById<ViewGroup>(R.id.start_contextual_buttons))
                .thenReturn(mockStartContextualLayout)
    }

    @Test
    fun getKidsLayoutter() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
                getLayoutter(isKidsMode = true, isInSetup = false, isThreeButtonNav = false,
                        phoneMode = false)
        assert(layoutter is KidsNavLayoutter)
    }

    @Test
    fun getSetupLayoutter() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
                getLayoutter(isKidsMode = false, isInSetup = true, isThreeButtonNav = false,
                        phoneMode = false)
        assert(layoutter is SetupNavLayoutter)
    }

    @Test
    fun getTaskbarNavLayoutter() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
                getLayoutter(isKidsMode = false, isInSetup = false, isThreeButtonNav = false,
                        phoneMode = false)
        assert(layoutter is TaskbarNavLayoutter)
    }

    @Test(expected = IllegalStateException::class)
    fun noValidLayoutForLargeScreenTaskbarNotPresent() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = false
        getLayoutter(isKidsMode = false, isInSetup = false, isThreeButtonNav = false,
                        phoneMode = false)
    }

    @Test
    fun getTaskbarPortraitLayoutter() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = false
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
                getLayoutter(isKidsMode = false, isInSetup = false, isThreeButtonNav = true,
                        phoneMode = true)
        assert(layoutter is PhonePortraitNavLayoutter)
    }

    @Test
    fun getTaskbarLandscapeLayoutter() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = false
        setDeviceProfileLandscape()
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
                getLayoutter(isKidsMode = false, isInSetup = false, isThreeButtonNav = true,
                        phoneMode = true)
        assert(layoutter is PhoneLandscapeNavLayoutter)
    }

    @Test(expected = IllegalStateException::class)
    fun noValidLayoutForPhoneGestureNav() {
        assumeTrue(TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW)
        mockDeviceProfile.isTaskbarPresent = false
        getLayoutter(isKidsMode = false, isInSetup = false, isThreeButtonNav = false,
                phoneMode = true)
    }

    private fun setDeviceProfileLandscape() {
        // Use reflection to modify landscape field
        val landscapeField = mockDeviceProfile.javaClass.getDeclaredField("isLandscape")
        landscapeField.isAccessible = true
        landscapeField.set(mockDeviceProfile, true)
    }

    private fun getLayoutter(isKidsMode: Boolean, isInSetup: Boolean,
                             isThreeButtonNav: Boolean, phoneMode: Boolean):
            NavButtonLayoutFactory.NavButtonLayoutter {
        return NavButtonLayoutFactory.getUiLayoutter(
                deviceProfile = mockDeviceProfile,
                navButtonsView = mockParentButtonContainer,
                resources = mockResources,
                isKidsMode = isKidsMode, isInSetup = isInSetup,
                isThreeButtonNav = isThreeButtonNav, phoneMode = phoneMode
        )
    }
}