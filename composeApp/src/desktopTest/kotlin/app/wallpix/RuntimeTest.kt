package app.wallpix

import app.wallpix.core.WallPixRuntime
import app.wallpix.model.FeedItem
import app.wallpix.model.Source
import kotlinx.coroutines.isActive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun feedItem(key: String) = FeedItem(
    Source.BING, "us", "Title $key", "© test", "https://img.peapix.com/${key}_1920.jpg",
    "https://img.peapix.com/${key}_640.jpg", "https://img.peapix.com/$key.jpg", "https://peapix.com/bing/1", "2026-10-18",
)

class RuntimeTest {

    @Test fun startInitialisesThenRunsTheFirstCheckAndIgnoresASecondStart() {
        val r = Rig()
        try {
            r.api.items = listOf(feedItem("runtime0001"))
            val runtime = WallPixRuntime(r.controller, r.scope, r.paths, r.favDir)
            runtime.start()
            runtime.start() // must not spawn a second loop

            val deadline = System.currentTimeMillis() + 10_000
            while (r.setter.applied.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(25)

            assertEquals(1, r.state.entries.size)
            assertEquals(1, r.setter.applied.size)          // new wallpaper applied once, not twice
            assertEquals("runtime0001", r.state.currentKey)
            assertTrue(Files.isDirectory(r.paths.recents))  // initialize() ran before the check
            assertTrue(Files.isDirectory(r.favDir))
        } finally {
            r.close()
        }
    }

    @Test fun stopCancelsTheBackgroundLoop() {
        val r = Rig()
        try {
            val runtime = WallPixRuntime(r.controller, r.scope, r.paths, r.favDir)
            runtime.start()
            assertTrue(r.scope.isActive)
            runtime.stop()
            assertFalse(r.scope.isActive)
        } finally {
            r.close()
        }
    }
}
