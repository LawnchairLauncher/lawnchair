package app.lawnchair.omni.experience

import app.lawnchair.omni.OmniLauncherAccessPolicy
import app.lawnchair.search.algorithms.engine.provider.apps.AppMatcher
import app.lawnchair.search.algorithms.engine.provider.apps.MatchType
import app.lawnchair.search.algorithms.engine.provider.apps.SearchTextNormalizer
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceRulesTest {
    @Test fun inventoryLabelsSurviveMissingProviderText() {
        assertEquals("com.example.app", resolveAppLabel(null, "com.example.app"))
        assertEquals("com.example.app", resolveAppLabel("  ", "com.example.app"))
        assertEquals("Omni Note", resolveAppLabel(StringBuilder(" Omni Note "), "com.example.app"))
        assertEquals("أُومني نوت", resolveAppLabel("أُومني نوت", "com.example.app"))
    }

    @Test fun localizedInventoryLabelsDriveNameRules() {
        val label = resolveAppLabel("أُومني نوت", "com.example.notes")
        assertTrue(FolderRule(1, nameContains = "اومني").matches("profile/key", label, "com.example.notes", -1))
        assertFalse(FolderRule(1, nameContains = "null").matches("profile/key", resolveAppLabel(null, "com.example.notes"), "com.example.notes", -1))
    }

    @Test fun slowProviderDoesNotDelayFirstResults() = kotlinx.coroutines.runBlocking {
        val slow = kotlinx.coroutines.flow.flow<List<String>> {
            kotlinx.coroutines.delay(10_000)
            emit(listOf("web"))
        }
        val flow = app.lawnchair.search.algorithms.progressiveResults(listOf(slow))
        val first = kotlinx.coroutines.withTimeout(1_000) { flow.first() }
        assertTrue(first.isEmpty())
    }

    @Test fun providerFailurePreservesOtherResults() = kotlinx.coroutines.runBlocking {
        val failed = kotlinx.coroutines.flow.flow<List<String>> { throw java.io.IOException("offline") }
        val values = app.lawnchair.search.algorithms.progressiveResults(listOf(failed, kotlinx.coroutines.flow.flowOf(listOf("local")))).toList()
        assertTrue(values.last().contains("local"))
    }

    @Test fun cancellationIsNeverConvertedToAnEmptySuccess() = kotlinx.coroutines.runBlocking {
        val cancelled = kotlinx.coroutines.flow.flow<List<String>> { throw kotlinx.coroutines.CancellationException("replaced") }
        var propagated = false
        try {
            app.lawnchair.search.algorithms.progressiveResults(listOf(cancelled)).toList()
        } catch (_: kotlinx.coroutines.CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test fun unicodeMatchingHandlesArabicMarksAccentsAndDigits() {
        assertEquals("اومني 123", SearchTextNormalizer.normalize("أُومْنِي ١٢٣"))
        assertEquals("cafe", SearchTextNormalizer.normalize("CAFÉ"))
        assertEquals("東京", SearchTextNormalizer.normalize("東京"))
        assertEquals("привет", SearchTextNormalizer.normalize("ПРИВЕТ"))
        assertEquals("whatsapp", SearchTextNormalizer.normalize("WhatsApp"))
    }

    @Test fun matchingDoesNotDependOnDeviceLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("instagram", SearchTextNormalizer.normalize("INSTAGRAM"))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test fun exactPrefixAndInitialsBeatFuzzyMatches() {
        assertEquals(MatchType.EXACT_MATCH, AppMatcher.match("Omni Note", "Omni Note").type)
        assertEquals(MatchType.DIRECT_PREFIX, AppMatcher.match("Omni Note", "omni").type)
        assertEquals(MatchType.INITIALS, AppMatcher.match("Google Maps", "gm").type)
        assertEquals(MatchType.EXACT_MATCH, AppMatcher.match("إعدادات", "اعدادات").type)
        assertEquals(MatchType.NO_MATCH, AppMatcher.match("Camera", "zzzzzzzz").type)
    }

    @Test fun overnightScenesHaveCorrectBoundaries() {
        val scene = HomeScene("night", "Night", automatic = true, startMinute = 22 * 60, endMinute = 9 * 60)
        assertTrue(scene.isActive(23 * 60, false, false))
        assertTrue(scene.isActive(0, false, false))
        assertTrue(scene.isActive(8 * 60 + 59, false, false))
        assertFalse(scene.isActive(9 * 60, false, false))
        assertFalse(scene.isActive(21 * 60, false, false))
    }

    @Test fun sceneConditionsAndOptInAreRequired() {
        val scene = HomeScene("gym", "Gym", automatic = true, charging = true, headset = true)
        assertFalse(scene.isActive(800, false, true))
        assertFalse(scene.isActive(800, true, false))
        assertTrue(scene.isActive(800, true, true))
        assertFalse(scene.copy(automatic = false).isActive(800, true, true))
    }

    @Test fun scheduleRejectsMalformedHours() {
        assertEquals(0, parseSceneTime("00:00"))
        assertEquals(1439, parseSceneTime("23:59"))
        listOf("24:00", "12:60", "-1:30", "", "12:00:00", "nine").forEach { assertNull(parseSceneTime(it)) }
    }

    @Test fun ruleConditionsCombineAndExclusionsWin() {
        val rule = FolderRule(1, "اومني", "com.omni", include = setOf("manual"), exclude = setOf("blocked", "both"))
        assertTrue(rule.matches("key", "أُومني نوت", "com.omni.note", -1))
        assertFalse(rule.matches("key", "أومني", "other.app", -1))
        assertTrue(rule.matches("manual", "Other", "other.app", -1))
        assertFalse(rule.copy(include = setOf("both")).matches("both", "أومني", "com.omni", -1))
        assertFalse(FolderRule(1).matches("key", "Any", "any", -1))
        assertFalse(FolderRule(1, category = 0).matches("key", "Any", "any", 1))
    }

    @Test fun unknownAndPrivilegedActionsAreNotGrantedByFlavorNameAlone() {
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace", "launcher.shelf.list"))
        assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.norm", "launcher.list_actions"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.norm", "launcher.apply_scene"))
        assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.pro", "launcher.apply_scene"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.pro", "launcher.oem.suspend_app"))
        assertTrue(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.oem", "launcher.oem.suspend_app"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.oem.evil", "launcher.oem.suspend_app"))
        assertFalse(OmniLauncherAccessPolicy.allows("com.omnidev.workspace.admin", "launcher.future_unknown"))
    }
}
