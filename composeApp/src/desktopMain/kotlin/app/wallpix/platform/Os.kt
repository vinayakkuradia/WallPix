package app.wallpix.platform

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class Os(val key: String) {
    WINDOWS("windows"), MAC("mac"), LINUX("linux");

    companion object {
        fun current(): Os {
            val n = System.getProperty("os.name").lowercase()
            // "darwin" contains "win", so macOS must be checked first.
            return when {
                "mac" in n || "darwin" in n -> MAC
                "win" in n -> WINDOWS
                else -> LINUX
            }
        }
    }
}

/** Runs an external command without a shell and with a timeout. */
object Proc {
    data class Result(val exit: Int, val output: String) {
        val ok: Boolean get() = exit == 0
    }

    fun run(argv: List<String>, timeoutSec: Long = 20): Result {
        val p = try {
            ProcessBuilder(argv).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return Result(127, e.message ?: "command not found: ${argv.firstOrNull()}")
        }
        val out = StringBuffer()
        val reader = thread(isDaemon = true, name = "proc-reader") {
            runCatching { p.inputStream.bufferedReader().use { out.append(it.readText()) } }
        }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return Result(124, "timed out after ${timeoutSec}s")
        }
        reader.join(1_000)
        return Result(p.exitValue(), out.toString().trim())
    }
}
