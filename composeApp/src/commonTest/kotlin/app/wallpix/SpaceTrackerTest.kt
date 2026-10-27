package app.wallpix

import app.wallpix.logic.SpaceTracker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceTrackerTest {
    @Test
    fun staysQuietOnTheSpaceTheWallpaperWasAppliedOn() {
        val t = SpaceTracker()
        t.reset(5)
        assertFalse(t.needsApply(5))
    }

    @Test
    fun asksForReapplyOnAnUnseenSpaceOnlyOnce() {
        val t = SpaceTracker()
        t.reset(5)
        assertTrue(t.needsApply(7))
        t.markApplied(7)
        assertFalse(t.needsApply(7))
        assertFalse(t.needsApply(5))
        assertTrue(t.needsApply(9))
    }

    @Test
    fun aNewWallpaperMakesEverySpaceStaleAgain() {
        val t = SpaceTracker()
        t.reset(5)
        t.markApplied(7)
        t.reset(7)
        assertTrue(t.needsApply(5))
        assertFalse(t.needsApply(7))
    }

    @Test
    fun neverActsWhenTheSpaceIsUnknown() {
        val t = SpaceTracker()
        assertFalse(t.needsApply(3))
        t.reset(null)
        assertFalse(t.needsApply(3))
        assertFalse(t.needsApply(null))
        t.reset(1)
        assertFalse(t.needsApply(null))
    }
}
