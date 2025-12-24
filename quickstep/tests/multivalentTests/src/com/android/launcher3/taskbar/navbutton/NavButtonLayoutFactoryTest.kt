package com.android.launcher3.taskbar.navbutton

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Surface
import android.view.Surface.ROTATION_270
import android.view.Surface.Rotation
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.test.runner.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.taskbar.navbutton.LayoutResourceHelper.ID_END_CONTEXTUAL_BUTTONS
import com.android.launcher3.taskbar.navbutton.LayoutResourceHelper.ID_END_NAV_BUTTONS
import com.android.launcher3.taskbar.navbutton.LayoutResourceHelper.ID_START_CONTEXTUAL_BUTTONS
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NavButtonLayoutFactoryTest {

    private val mockDeviceProfile: DeviceProfile = mock()
    private val mockParentButtonContainer: NearestTouchFrame = mock()
    private val mockNavLayout: LinearLayout = mock()
    private val mockStartContextualLayout: ViewGroup = mock()
    private val mockEndContextualLayout: ViewGroup = mock()
    private val mockResources: Resources = mock()
    private val mockBackButton: ImageView = mock()
    private val mockRecentsButton: ImageView = mock()
    private val mockHomeButton: ImageView = mock()
    private val mockImeSwitcher: ImageView = mock()
    private val mockA11yButton: ImageView = mock()
    private val mockSpace: Space = mock()
    private val mockConfiguration: Configuration = mock()

    private var surfaceRotation = Surface.ROTATION_0

    @Before
    fun setup() {
        // Init end nav buttons
        whenever(mockNavLayout.childCount).thenReturn(3)
        whenever(mockNavLayout.findViewById<View>(R.id.back)).thenReturn(mockBackButton)
        whenever(mockNavLayout.findViewById<View>(R.id.home)).thenReturn(mockHomeButton)
        whenever(mockNavLayout.findViewById<View>(R.id.recent_apps)).thenReturn(mockRecentsButton)
        val devicePropertiesMock: DeviceProperties = mock()
        whenever(mockDeviceProfile.deviceProperties).thenReturn(devicePropertiesMock)

        // Init top level layout
        whenever(mockParentButtonContainer.requireViewById<LinearLayout>(ID_END_NAV_BUTTONS))
            .thenReturn(mockNavLayout)
        whenever(mockParentButtonContainer.requireViewById<ViewGroup>(ID_END_CONTEXTUAL_BUTTONS))
            .thenReturn(mockEndContextualLayout)
        whenever(mockParentButtonContainer.requireViewById<ViewGroup>(ID_START_CONTEXTUAL_BUTTONS))
            .thenReturn(mockStartContextualLayout)
        whenever(mockBackButton.resources).thenReturn(mockResources)
        whenever(mockResources.configuration).thenReturn(mockConfiguration)
        whenever(mockConfiguration.layoutDirection).thenReturn(0)
    }

    @Test
    fun getKidsLayoutter() {
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = true,
                isInSetup = false,
                isThreeButtonNav = false,
                phoneMode = false,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is KidsNavLayoutter)
    }

    @Test
    fun getSetupLayoutter() {
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = true,
                isThreeButtonNav = false,
                phoneMode = false,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is SetupNavLayoutter)
    }

    @Test
    fun getTaskbarNavLayoutter() {
        mockDeviceProfile.isTaskbarPresent = true
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = false,
                phoneMode = false,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is TaskbarNavLayoutter)
    }

    @Test
    fun noValidLayoutForLargeScreenTaskbarNotPresent() {
        mockDeviceProfile.isTaskbarPresent = false
        assertThrows(IllegalStateException::class.java) {
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = false,
                phoneMode = false,
                surfaceRotation = surfaceRotation,
            )
        }
    }

    @Test
    fun getTaskbarPortraitLayoutter() {
        mockDeviceProfile.isTaskbarPresent = false
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = true,
                phoneMode = true,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is PhonePortraitNavLayoutter)
    }

    @Test
    fun getTaskbarLandscapeLayoutter() {
        mockDeviceProfile.isTaskbarPresent = false
        setDeviceProfileLandscape()
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = true,
                phoneMode = true,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is PhoneLandscapeNavLayoutter)
    }

    @Test
    fun getTaskbarSeascapeLayoutter() {
        mockDeviceProfile.isTaskbarPresent = false
        setDeviceProfileLandscape()
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = true,
                phoneMode = true,
                surfaceRotation = ROTATION_270,
            )
        assert(layoutter is PhoneSeascapeNavLayoutter)
    }

    @Test
    fun getTaskbarPhoneGestureNavLayoutter() {
        mockDeviceProfile.isTaskbarPresent = false
        val layoutter: NavButtonLayoutFactory.NavButtonLayoutter =
            getLayoutter(
                isKidsMode = false,
                isInSetup = false,
                isThreeButtonNav = false,
                phoneMode = true,
                surfaceRotation = surfaceRotation,
            )
        assert(layoutter is PhoneGestureLayoutter)
    }

    private fun setDeviceProfileLandscape() {
        whenever(mockDeviceProfile.deviceProperties.isLandscape).thenReturn(true)
    }

    private fun getLayoutter(
        isKidsMode: Boolean,
        isInSetup: Boolean,
        isThreeButtonNav: Boolean,
        phoneMode: Boolean,
        @Rotation surfaceRotation: Int,
    ): NavButtonLayoutFactory.NavButtonLayoutter {
        return NavButtonLayoutFactory.getUiLayoutter(
            deviceProfile = mockDeviceProfile,
            navButtonsView = mockParentButtonContainer,
            resources = mockResources,
            isKidsMode = isKidsMode,
            isInSetup = isInSetup,
            isThreeButtonNav = isThreeButtonNav,
            phoneMode = phoneMode,
            surfaceRotation = surfaceRotation,
            imeSwitcher = mockImeSwitcher,
            a11yButton = mockA11yButton,
            space = mockSpace,
        )
    }
}
