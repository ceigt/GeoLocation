package io.github.ceigt.geolocation.manager.control

import java.io.File
import java.util.concurrent.TimeUnit

internal object RootCommands {
    fun validPackage(packageName: String): Boolean =
        Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(packageName)

    fun forceStop(packageName: String): Boolean =
        validPackage(packageName) && run("am force-stop '$packageName'")

    fun hasRoot(): Boolean = run("test \"\$(id -u)\" = 0")

    fun enableMockLocation(packageName: String): Boolean =
        validPackage(packageName) && run("appops set '$packageName' android:mock_location allow")

    private fun run(command: String): Boolean {
        var process: Process? = null
        return try {
            // Discard output at the OS boundary: no readText() can block before the timeout.
            process = ProcessBuilder("su", "-c", command)
                .redirectOutput(File("/dev/null")).redirectError(File("/dev/null")).start()
            process.outputStream.close()
            process.waitFor(8, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        } finally {
            if (process?.isAlive == true) process.destroyForcibly()
        }
    }
}
