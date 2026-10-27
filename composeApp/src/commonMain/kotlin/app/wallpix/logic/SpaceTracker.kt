package app.wallpix.logic

/**
 * macOS can only change the wallpaper of the Space that is on screen: there is no public way to reach the others.
 * So WallPix re-applies the wallpaper whenever the user arrives on a Space that has not received it yet. This class
 * remembers which Spaces already show the current picture. It knows nothing about macOS itself, so it is testable.
 *
 * A null Space id means "unknown" (the lookup failed); the tracker then stays disarmed and never asks for a re-apply.
 */
class SpaceTracker {
    private val done = HashSet<Long>()
    private var armed = false

    /** A new wallpaper was just applied while [activeSpace] was on screen. Every other Space is now out of date. */
    @Synchronized
    fun reset(activeSpace: Long?) {
        done.clear()
        armed = activeSpace != null
        if (activeSpace != null) done.add(activeSpace)
    }

    /** True when the user is on a Space that does not show the current wallpaper yet. */
    @Synchronized
    fun needsApply(activeSpace: Long?): Boolean = armed && activeSpace != null && activeSpace !in done

    /** The wallpaper was re-applied while [activeSpace] was on screen. */
    @Synchronized
    fun markApplied(activeSpace: Long?) {
        if (armed && activeSpace != null) done.add(activeSpace)
    }
}
