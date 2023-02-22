package com.android.launcher3

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

private val TEST_BOOLEAN_ITEM = LauncherPrefs.nonRestorableItem("1", false)
private val TEST_STRING_ITEM = LauncherPrefs.nonRestorableItem("2", "( ͡❛ ͜ʖ ͡❛)")
private val TEST_INT_ITEM = LauncherPrefs.nonRestorableItem("3", -1)
private val TEST_CONTEXTUAL_ITEM = ContextualItem("4", true, { true }, Boolean::class.java)

private const val TEST_DEFAULT_VALUE = "default"
private const val TEST_PREF_KEY = "test_pref_key"

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherPrefsTest {

    private val launcherPrefs by lazy {
        LauncherPrefs.get(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            remove(TEST_BOOLEAN_ITEM, TEST_STRING_ITEM, TEST_INT_ITEM)
        }
    }

    @Test
    fun has_keyMissingFromLauncherPrefs_returnsFalse() {
        assertThat(launcherPrefs.has(TEST_BOOLEAN_ITEM)).isFalse()
    }

    @Test
    fun has_keyPresentInLauncherPrefs_returnsTrue() {
        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue))
            assertThat(has(TEST_BOOLEAN_ITEM)).isTrue()
            remove(TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun addListener_listeningForStringItemUpdates_isCorrectlyNotifiedOfUpdates() {
        val latch = CountDownLatch(1)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue))
            addListener(listener, TEST_STRING_ITEM)
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "abc"))

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
            remove(TEST_STRING_ITEM)
        }
    }

    @Test
    fun removeListener_previouslyListeningForStringItemUpdates_isNoLongerNotifiedOfUpdates() {
        val latch = CountDownLatch(1)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            addListener(listener, TEST_STRING_ITEM)
            removeListener(listener, TEST_STRING_ITEM)
            putSync(TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "hello."))

            // latch will be still be 1 (and await will return false) if the listener was not called
            assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse()
            remove(TEST_STRING_ITEM)
        }
    }

    @Test
    fun addListenerAndRemoveListener_forMultipleItems_bothWorkProperly() {
        var latch = CountDownLatch(3)
        val listener = OnSharedPreferenceChangeListener { _, _ -> latch.countDown() }

        with(launcherPrefs) {
            addListener(listener, TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)
            putSync(
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue + 123),
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue + "abc"),
                TEST_BOOLEAN_ITEM.to(!TEST_BOOLEAN_ITEM.defaultValue)
            )
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()

            removeListener(listener, TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)
            latch = CountDownLatch(1)
            putSync(
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue),
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue),
                TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue)
            )
            remove(TEST_INT_ITEM, TEST_STRING_ITEM, TEST_BOOLEAN_ITEM)

            assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse()
        }
    }

    @Test
    fun get_booleanItemNotInLauncherprefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_BOOLEAN_ITEM)).isEqualTo(TEST_BOOLEAN_ITEM.defaultValue)
    }

    @Test
    fun get_stringItemNotInLauncherPrefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_STRING_ITEM)).isEqualTo(TEST_STRING_ITEM.defaultValue)
    }

    @Test
    fun get_intItemNotInLauncherprefs_returnsDefaultValue() {
        assertThat(launcherPrefs.get(TEST_INT_ITEM)).isEqualTo(TEST_INT_ITEM.defaultValue)
    }

    @Test
    fun put_storesItemInLauncherPrefs_successfully() {
        val notDefaultValue = !TEST_BOOLEAN_ITEM.defaultValue

        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(notDefaultValue))
            assertThat(get(TEST_BOOLEAN_ITEM)).isEqualTo(notDefaultValue)
            remove(TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun put_storesListOfItemsInLauncherPrefs_successfully() {
        with(launcherPrefs) {
            putSync(
                TEST_STRING_ITEM.to(TEST_STRING_ITEM.defaultValue),
                TEST_INT_ITEM.to(TEST_INT_ITEM.defaultValue),
                TEST_BOOLEAN_ITEM.to(TEST_BOOLEAN_ITEM.defaultValue)
            )
            assertThat(has(TEST_BOOLEAN_ITEM, TEST_INT_ITEM, TEST_STRING_ITEM)).isTrue()
            remove(TEST_STRING_ITEM, TEST_INT_ITEM, TEST_BOOLEAN_ITEM)
        }
    }

    @Test
    fun remove_deletesItemFromLauncherPrefs_successfully() {
        val notDefaultValue = !TEST_BOOLEAN_ITEM.defaultValue

        with(launcherPrefs) {
            putSync(TEST_BOOLEAN_ITEM.to(notDefaultValue))
            remove(TEST_BOOLEAN_ITEM)
            assertThat(get(TEST_BOOLEAN_ITEM)).isEqualTo(TEST_BOOLEAN_ITEM.defaultValue)
        }
    }

    @Test
    fun get_contextualItem_returnsCorrectDefault() {
        assertThat(launcherPrefs.get(TEST_CONTEXTUAL_ITEM)).isTrue()
    }

    @Test
    fun getItemSharedPrefFile_forNonRestorableItem_isCorrect() {
        val nonRestorableItem = LauncherPrefs.nonRestorableItem(TEST_PREF_KEY, TEST_DEFAULT_VALUE)
        assertThat(nonRestorableItem.sharedPrefFile).isEqualTo(LauncherFiles.DEVICE_PREFERENCES_KEY)
    }

    @Test
    fun getItemSharedPrefFile_forBackedUpItem_isCorrect() {
        val backedUpItem = LauncherPrefs.backedUpItem(TEST_PREF_KEY, TEST_DEFAULT_VALUE)
        assertThat(backedUpItem.sharedPrefFile).isEqualTo(LauncherFiles.SHARED_PREFERENCES_KEY)
    }
}
