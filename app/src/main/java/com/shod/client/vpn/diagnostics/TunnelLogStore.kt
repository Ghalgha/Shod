package com.shod.client.vpn.diagnostics

import com.shod.client.LogEntry
import com.shod.client.LogLevel

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the in-memory diagnostic journal, coalescing, ordering and UI publication.
 * Process lifecycle validation is supplied by the caller so this component does not
 * depend on VPN process orchestration.
 */
internal class TunnelLogStore(
    private val scope: CoroutineScope,
    private val evictionIndex: (List<LogEntry>, Int) -> Int,
) {
    private data class PendingLog(
        val isCurrent: (() -> Boolean)?,
        val key: String,
        var message: String,
        val priority: Int,
        val isError: Boolean,
        val level: LogLevel?,
        var count: Int,
    )

    companion object {
        private const val UI_FLUSH_MS = 120L
        private const val MAX_ENTRIES = 100
        private const val MAX_PENDING = 512
        private const val MAX_MESSAGE_LENGTH = 4_096
    }

    private val pendingLock = Any()
    private val pending = LinkedHashMap<String, PendingLog>()
    private var flushScheduled = false

    val logs = mutableStateListOf<LogEntry>()
    val unreadErrorCount = MutableStateFlow(0)

    fun append(
        generation: Long,
        isCurrent: (() -> Boolean)?,
        key: String,
        message: String,
        priority: Int,
        isError: Boolean,
        level: LogLevel?,
    ) {
        if (isCurrent != null && !isCurrent()) return
        val cleanMessage = message.take(MAX_MESSAGE_LENGTH)
        if (cleanMessage.isEmpty()) return
        val pendingKey = "$generation:$key:$priority:$isError:" + (level?.name ?: "")
        synchronized(pendingLock) {
            val existing = pending[pendingKey]
            if (existing == null) {
                if (pending.size >= MAX_PENDING) pending.remove(pending.entries.first().key)
                pending[pendingKey] = PendingLog(isCurrent, key, cleanMessage, priority, isError, level, 1)
            } else {
                existing.message = cleanMessage
                existing.count += 1
            }
            scheduleFlushLocked()
        }
    }

    fun clearUnreadErrors() {
        unreadErrorCount.value = 0
    }

    fun clear() {
        synchronized(pendingLock) { pending.clear() }
        unreadErrorCount.value = 0
        scope.launch(Dispatchers.Main) { logs.clear() }
    }

    private fun scheduleFlushLocked() {
        if (flushScheduled) return
        flushScheduled = true
        scope.launch(Dispatchers.Main) {
            delay(UI_FLUSH_MS)
            flushPending()
        }
    }

    private fun flushPending() {
        val batch = synchronized(pendingLock) {
            val values = pending.values.toList()
            pending.clear()
            flushScheduled = false
            values
        }
        batch.forEach(::apply)
    }

    private fun apply(item: PendingLog) {
        if (item.isCurrent != null && !item.isCurrent()) return
        val index = logs.indexOfFirst { it.key == item.key }
        if (item.isError && index == -1) unreadErrorCount.value++

        if (index >= 0) {
            val previous = logs[index]
            val updated = LogEntry(
                key = item.key,
                message = item.message,
                count = previous.count + item.count,
                priority = item.priority,
                isError = item.isError,
                level = item.level,
            )
            if (previous.priority == item.priority) {
                logs[index] = updated
            } else {
                logs.removeAt(index)
                insertSorted(updated)
            }
        } else {
            insertSorted(LogEntry(item.key, item.message, item.count, item.priority, item.isError, item.level))
        }

        while (true) {
            val indexToEvict = evictionIndex(logs, MAX_ENTRIES)
            if (indexToEvict < 0) break
            logs.removeAt(indexToEvict)
        }
    }

    private fun insertSorted(entry: LogEntry) {
        val comparator = compareBy<LogEntry>({ it.priority }, { if (it.isError) 1 else 0 }, { it.key })
        val index = logs.indexOfFirst { comparator.compare(entry, it) < 0 }
        if (index < 0) logs.add(entry) else logs.add(index, entry)
    }
}
