package app.lawnchair.omni

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.views.SearchResultIconRow
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.search.adapter.SearchTargetFactory
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs only on a clean emulator: its test APK intentionally supplies two Workspace choices. */
class OmniSearchHandoffTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun resetPreferences() {
        runBlocking(Dispatchers.IO) {
            ExperienceStore.get(context).update { it.copy(preferredWorkspace = null) }
            PreferenceManager2.omniPreferencesDataStore(context).edit { values ->
                values[androidx.datastore.preferences.core.stringPreferencesKey("dock_search_bar_provider")] = "omni"
            }
        }
    }

    @Test
    fun tappingActualSearchRowDeliversArabicDraftAcrossProcesses() {
        val query = "اهلا من بحث اللانشر"
        home { scenario ->
            scenario.onActivity { launcher ->
                val row = LayoutInflater.from(launcher).inflate(R.layout.search_result_tall_icon_row, null) as SearchResultIconRow
                row.bind(SearchTargetFactory(launcher).createOmniAskTarget(query), emptyList())
                showTapTarget(launcher, row)
            }
            tapAndChoose()
            assertTrue(device.wait(Until.hasObject(By.text("ASK_OMNI")), 5_000))
            assertTrue(device.wait(Until.hasObject(By.text(query)), 5_000))
        }
    }

    @Test
    fun tappingActualDockOpensWorkspaceWithoutQuery() {
        home { scenario ->
            scenario.onActivity { launcher ->
                val dock = LayoutInflater.from(launcher).inflate(R.layout.search_container_hotseat, null) as LawnQsbLayout
                showTapTarget(launcher, dock)
            }
            tapAndChoose()
            assertTrue(device.wait(Until.hasObject(By.text("OPEN_OMNI")), 5_000))
        }
    }

    @Test
    fun cancellingChooserKeepsHomeTappableAndNextRequestWorks() {
        home { scenario ->
            scenario.onActivity { launcher ->
                val row = LayoutInflater.from(launcher).inflate(R.layout.search_result_tall_icon_row, null) as SearchResultIconRow
                row.bind(SearchTargetFactory(launcher).createOmniAskTarget("retry draft"), emptyList())
                showTapTarget(launcher, row)
            }
            tapTarget()
            assertTrue(device.wait(Until.hasObject(By.text("Handoff fixture A")), 5_000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("omni-real-tap-target")), 3_000))
            tapAndChoose()
            assertTrue(device.wait(Until.hasObject(By.text("retry draft")), 5_000))
        }
    }

    @Test
    fun compatibilityActivityAlsoDeliversTypedQuery() {
        home { scenario ->
            scenario.onActivity { it.startActivity(OmniAskActivity.createIntent(it, "legacy draft")) }
            chooseWorkspace()
            assertTrue(device.wait(Until.hasObject(By.text("legacy draft")), 5_000))
        }
    }

    private fun home(block: (ActivityScenario<LawnchairLauncher>) -> Unit) {
        ActivityScenario.launch<LawnchairLauncher>(Intent(context, LawnchairLauncher::class.java)).use(block)
    }

    private fun showTapTarget(launcher: LawnchairLauncher, child: View) {
        // Keep the real Launcher/model/lifecycle. Only mount the real production view above its
        // Home content so the test does not depend on a particular saved grid or drawer order.
        val host = FrameLayout(launcher).apply {
            setBackgroundColor(Color.DKGRAY)
            addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180, Gravity.CENTER))
        }
        child.contentDescription = "omni-real-tap-target"
        (launcher.window.decorView as ViewGroup).addView(host, ViewGroup.LayoutParams(-1, -1))
    }

    private fun tapTarget() {
        val target = device.wait(Until.findObject(By.desc("omni-real-tap-target")), 5_000)
        assertTrue("Real search/dock view did not render", target != null)
        target.click()
    }
    private fun tapAndChoose() {
        tapTarget()
        chooseWorkspace()
    }
    private fun chooseWorkspace() {
        val choice = device.wait(Until.findObject(By.text("Handoff fixture A")), 5_000)
        assertTrue("Workspace chooser never appeared", choice != null)
        choice.click()
        assertTrue("External receiver never appeared", device.wait(Until.hasObject(By.text("omni-capture-active")), 5_000))
        // Settle the optional write before the next test resets it; launching itself never waits.
        runBlocking(Dispatchers.IO) {
            withTimeout(2_000) { ExperienceStore.get(context).state.first { it?.preferredWorkspace != null } }
        }
    }
}
