package app.dayflow.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One writer per data directory. Invalid input is never silently reset. */
class JsonFileStore(private val file: File) : SnapshotStore, AutoCloseable {
    private val lockFile: RandomAccessFile
    private val lock: FileLock
    init {
        file.parentFile?.mkdirs()
        lockFile = RandomAccessFile(File(file.path + ".lock"), "rw")
        lock = try { lockFile.channel.tryLock() ?: error("Dayflow가 이미 실행 중입니다.") }
        catch (error: Exception) { lockFile.close(); throw error }
    }

    override fun load(): Snapshot = if (file.exists()) AppCodec.decode(file.readText(Charsets.UTF_8)) else Snapshot()

    override fun save(snapshot: Snapshot) {
        snapshot.validate()
        val pending = File(file.path + ".pending")
        val bytes = AppCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
        pending.outputStream().use { it.write(bytes); it.fd.sync() }
        // Keep the previous committed state, including when replacing fails.
        if (file.exists()) Files.copy(file.toPath(), File(file.path + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun close() { lock.release(); lockFile.close() }
}
