package app.wallpix

import app.wallpix.logic.NativeCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeCommandsTest {

    @Test
    fun psQuoteDoublesSingleQuotes() {
        assertEquals("'C:\\Users\\O''Brien\\a.jpg'", NativeCommands.psQuote("C:\\Users\\O'Brien\\a.jpg"))
    }

    @Test
    fun asQuoteEscapesBackslashAndQuote() {
        assertEquals("\"a\\\"b\\\\c\"", NativeCommands.asQuote("a\"b\\c"))
    }

    @Test
    fun trashCommandsPerOs() {
        val mac = NativeCommands.trash("mac", "/Users/me/pic \"1\".jpg")
        assertEquals(1, mac.size)
        assertTrue(mac[0][2].contains("Finder") && mac[0][2].contains("POSIX file"))
        assertTrue(mac[0][2].contains("\\\"1\\\""))

        val win = NativeCommands.trash("windows", "C:\\a'b.jpg")
        assertEquals("powershell", win[0][0])
        assertTrue(win[0].last().contains("SendToRecycleBin"))
        assertTrue(win[0].last().contains("'C:\\a''b.jpg'"))

        val linux = NativeCommands.trash("linux", "/home/me/a.jpg")
        assertEquals(listOf("gio", "trash", "/home/me/a.jpg"), linux[0])
        assertEquals(3, linux.size)
    }

    @Test
    fun openUrlOnlyAcceptsPlainHttps() {
        assertNull(NativeCommands.openUrl("linux", "http://peapix.com"))
        assertNull(NativeCommands.openUrl("linux", "file:///etc/passwd"))
        assertNull(NativeCommands.openUrl("windows", "https://a.com/x y"))
        assertNull(NativeCommands.openUrl("mac", "https://a.com/\nrm"))
        assertEquals(listOf("open", "https://peapix.com"), NativeCommands.openUrl("mac", "https://peapix.com"))
        assertEquals(
            listOf("rundll32", "url.dll,FileProtocolHandler", "https://peapix.com"),
            NativeCommands.openUrl("windows", "https://peapix.com"),
        )
        assertNotNull(NativeCommands.openUrl("linux", "https://peapix.com"))
    }

    @Test
    fun pickerCommandsCarryTitleAndStart() {
        val linux = NativeCommands.pickFolder("linux", "/home/me/Downloads/", "Pick")
        assertTrue(linux[0].contains("--filename=/home/me/Downloads/"))
        assertTrue(linux[0].contains("--title=Pick"))
        assertEquals("kdialog", linux[1][0])
        val mac = NativeCommands.pickFolder("mac", "/Users/me", "Pick")
        assertEquals(2, mac.size)
        assertTrue(mac[0][2].startsWith("POSIX path of (choose folder"))
        val win = NativeCommands.pickFolder("windows", "C:\\Users\\me", "Pick")
        assertTrue(win[0].last().contains("OpenFileDialog"))
    }

    @Test
    fun parsePickedFolder() {
        assertEquals("/Users/me/Pics", NativeCommands.parsePickedFolder("/Users/me/Pics/\n"))
        assertEquals("C:\\Users\\me\\Pics", NativeCommands.parsePickedFolder("C:\\Users\\me\\Pics\r\n"))
        assertEquals("/", NativeCommands.parsePickedFolder("/"))
        assertNull(NativeCommands.parsePickedFolder("  \n"))
    }

    @Test
    fun parseScreenSizes() {
        val mac = """
            Graphics/Displays:
              Apple M3:
                Displays:
                  Color LCD:
                    Resolution: 3456 x 2234 Retina
                    UI Looks like: 1728 x 1117 @ 120.00Hz
                  LG HDR:
                    Resolution: 3840 x 2160 (2160p/4K UHD 1 - Ultra High Definition)
        """.trimIndent()
        assertEquals(3840, NativeCommands.parseLongestEdge("mac", mac))

        assertEquals(2560, NativeCommands.parseLongestEdge("windows", "2560 x 1440\r\n1920 x 1080\r\n"))
        assertNull(NativeCommands.parseLongestEdge("windows", " x \r\n"))

        val xrandr = """
            Screen 0: minimum 320 x 200, current 5760 x 2160, maximum 16384 x 16384
            DP-1 connected primary 3840x2160+0+0 (normal left inverted right x axis y axis) 600mm x 340mm
               3840x2160     60.00*+  30.00
               2560x1440     59.95
            HDMI-1 connected 1920x1080+3840+0 (normal left inverted right x axis y axis) 510mm x 290mm
               1920x1080     60.00*+  50.00
               1280x720      60.00
        """.trimIndent()
        assertEquals(3840, NativeCommands.parseLongestEdge("linux", xrandr))
        assertNull(NativeCommands.parseLongestEdge("linux", "Can't open display"))
    }
}
