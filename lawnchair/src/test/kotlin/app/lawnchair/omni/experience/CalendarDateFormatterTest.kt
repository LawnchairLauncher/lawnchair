package app.lawnchair.omni.experience

import app.lawnchair.smartspace.CalendarDateFormatter
import app.lawnchair.smartspace.CalendarDateOptions
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDateFormatterTest {
    private val ramadan = Instant.parse("2024-03-11T12:00:00Z").toEpochMilli()
    private val options = CalendarDateOptions(language = "en", digits = "latn", year = true, weekday = false)
    private fun format(mode: String, at: Long = ramadan, style: CalendarDateOptions = options, zone: String = "UTC", date: Boolean = true, time: Boolean = false, twentyFour: Boolean = true) = CalendarDateFormatter(mode, style, date, time, twentyFour, Locale.US, zone).format(at)

    @Test fun knownUmmAlQuraRamadanDate() {
        val text = format("hijri")
        assertTrue(text, text.contains("1445"))
        assertTrue(text, text.contains("Ramadan"))
        assertTrue(text, text.contains("1"))
        assertFalse(text.contains("2024"))
    }

    @Test fun dualOrderAndLayoutAreRespected() {
        val hijri = format("hijri")
        val gregorian = format("gregorian")
        assertEquals("$hijri\n$gregorian", format("dual"))
        assertEquals("$gregorian · $hijri", format("dual", style = options.copy(hijriFirst = false, stacked = false)))
    }

    @Test fun hijriAdjustmentNeverChangesGregorianDate() {
        val gregorian = format("gregorian")
        assertEquals(gregorian, format("gregorian", style = options.copy(offset = 2)))
        val tomorrow = format("hijri", at = ramadan + 86_400_000L)
        assertEquals(tomorrow, format("hijri", style = options.copy(offset = 1)))
        val previous = format("hijri", at = ramadan - 86_400_000L)
        assertEquals(previous, format("hijri", style = options.copy(offset = -1)))
    }

    @Test fun adjustmentUsesCalendarDaysAcrossDaylightSaving() {
        val before = Instant.parse("2024-03-09T17:00:00Z").toEpochMilli()
        val after = Instant.parse("2024-03-10T16:00:00Z").toEpochMilli()
        assertEquals(format("hijri", at = after, zone = "America/New_York"), format("hijri", at = before, style = options.copy(offset = 1), zone = "America/New_York"))
    }

    @Test fun zoneChangesCalendarAtLocalMidnight() {
        val midnight = Instant.parse("2024-03-11T00:30:00Z").toEpochMilli()
        assertFalse(format("gregorian", at = midnight, zone = "UTC") == format("gregorian", at = midnight, zone = "America/Los_Angeles"))
    }

    @Test fun digitsCanBeChosenIndependentlyOfLanguage() {
        assertTrue(format("hijri", style = options.copy(language = "ar", digits = "latn")).contains("1445"))
        val arabic = format("hijri", style = options.copy(language = "ar", digits = "arab"))
        assertTrue(arabic, arabic.contains("١٤٤٥"))
        assertFalse(arabic.contains("1445"))
    }

    @Test fun timeOnlyHasNoDateOrDuplicateClock() {
        val text = format("dual", date = false, time = true)
        assertFalse(text.contains("1445") || text.contains("2024") || text.contains("\n"))
        assertTrue(text, text.contains("12:00"))
        assertEquals("", format("dual", date = false, time = false))
        assertTrue(format("hijri", date = false, time = true, twentyFour = false).contains("PM"))
    }

    @Test fun calculationMethodsAndInvalidStoredOptionsAreSafe() {
        listOf("islamic-umalqura", "islamic-civil", "islamic", "invalid").forEach { method ->
            assertTrue(format("hijri", style = options.copy(method = method)).contains("1445"))
        }
        assertEquals(format("hijri", style = options.copy(offset = 2)), format("hijri", style = options.copy(offset = Int.MAX_VALUE)))
    }

    @Test fun automaticSceneExpiryRestoresOnlyAnAutomaticBaseline() {
        val scene = HomeScene("night", "Night", automatic = true, startMinute = 1320, endMinute = 540)
        val state = ExperienceState(scenes = listOf(scene), automaticScenes = true)
        assertEquals(AutomaticSceneChange.Apply("night"), state.automaticSceneChange(1380, false, false))
        assertEquals(AutomaticSceneChange.None, state.automaticSceneChange(600, false, false))
        val applied = state.copy(activeScene = "night", automaticSceneId = "night", automaticBaseline = HomeScene("base", "Base"))
        assertEquals(AutomaticSceneChange.None, applied.automaticSceneChange(1380, false, false))
        assertEquals(AutomaticSceneChange.Restore, applied.automaticSceneChange(600, false, false))
        assertEquals(AutomaticSceneChange.Restore, applied.copy(automaticScenes = false).automaticSceneChange(1380, false, false))
        assertEquals(AutomaticSceneChange.Restore, applied.copy(scenes = emptyList()).automaticSceneChange(1380, false, false))
    }

    @Test fun stackSwipesMirrorWithLayoutDirection() {
        assertEquals(-1, widgetSwipeDelta(true, false))
        assertEquals(1, widgetSwipeDelta(true, true))
        assertEquals(1, widgetSwipeDelta(false, false))
        assertEquals(-1, widgetSwipeDelta(false, true))
    }

    @Test fun scenePriorityChangesWithoutDroppingOtherState() {
        val state = ExperienceState(scenes = listOf(HomeScene("a", "A"), HomeScene("b", "B")), activeScene = "a")
        val moved = state.moveScene("b", -1)
        assertEquals(listOf("b", "a"), moved.scenes.map { it.id })
        assertEquals("a", moved.activeScene)
        assertEquals(state, state.moveScene("a", -1))
        assertEquals(state, state.moveScene("missing", 1))
    }

    @Test fun adjustedHijriKeepsTodaysWeekdayAndGregorian() {
        val at = Instant.parse("2024-03-11T12:00:00Z").toEpochMilli()
        val shifted = format("dual", at, options.copy(offset = 1, weekday = true))
        assertTrue(shifted, shifted.contains("Mon"))
        assertFalse(shifted, shifted.contains("Tue"))
        assertTrue(shifted, shifted.contains("2024"))
    }

    @Test fun movingAndRemovingStackMembersPreservesSelectedIdentity() {
        val stack = WidgetStack(10, listOf(1, 2, 3), 1)
        val moved = stack.moveMember(1, 2)
        assertEquals(listOf(1, 3, 2), moved.widgetIds)
        assertEquals(2, moved.widgetIds[moved.selected])
        val removed = stack.removeMember(1)
        assertEquals(2, removed.widgetIds[removed.selected])
        assertEquals(0, WidgetStack(1, listOf(5), 0).removeMember(5).selected)
        assertEquals(stack, stack.moveMember(-1, 5))
    }
}
