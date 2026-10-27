package app.wallpix

import app.wallpix.logic.Labels
import app.wallpix.model.UNGROUPED
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelsTest {
    @Test fun minutesAreWorded() {
        assertEquals("1 minute", Labels.minutes(1))
        assertEquals("5 minutes", Labels.minutes(5))
        assertEquals("30 minutes", Labels.minutes(30))
        assertEquals("1 hour", Labels.minutes(60))
        assertEquals("2 hours", Labels.minutes(120))
        assertEquals("24 hours", Labels.minutes(1440))
        assertEquals("90 minutes", Labels.minutes(90))
    }

    @Test fun rootGroupHasAFriendlyName() {
        assertEquals("No group", Labels.group(UNGROUPED))
        assertEquals("Alps", Labels.group("Alps"))
    }

    @Test fun currentValueIsAlwaysOffered() {
        assertEquals(listOf(30, 60, 120), Labels.withCurrent(listOf(30, 60, 120), 60))
        assertEquals(listOf(30, 45, 60, 120), Labels.withCurrent(listOf(30, 60, 120), 45))
    }
}
