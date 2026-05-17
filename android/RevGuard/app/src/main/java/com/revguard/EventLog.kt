package com.revguard

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe, timestamped event logger.
 * Events are held in memory for UI display and persisted to a text file for export.
 */
class EventLog(private val context: Context) {

    data class Entry(
        val timestamp: Long,
        val tag: String,
        val message: String
    ) {
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        override fun toString(): String = "[${fmt.format(Date(timestamp))}] $tag: $message"
    }

    private val entries = CopyOnWriteArrayList<Entry>()
    private val listeners = CopyOnWriteArrayList<(Entry) -> Unit>()

    private val logFile: File by lazy {
        File(context.filesDir, "rev_guard_log.txt").also { it.parentFile?.mkdirs() }
    }

    val allEntries: List<Entry> get() = entries.toList()

    /** Record a timestamped event. Persists to disk and notifies UI listeners. */
    fun log(tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), tag, message)
        entries.add(entry)

        // Persist to file
        logFile.appendText("$entry\n")

        // Notify UI listeners
        listeners.forEach { it(entry) }
    }

    /** Register a listener to receive new log entries in real time (UI updates). */
    fun addListener(listener: (Entry) -> Unit) {
        listeners.add(listener)
    }

    /** Unregister a previously added listener. */
    fun removeListener(listener: (Entry) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Returns the full log file for sharing via intent.
     */
    fun exportLogFile(): File = logFile

    /**
     * Clears in-memory entries (file is preserved for archival).
     */
    fun clearMemory() {
        entries.clear()
    }
}
