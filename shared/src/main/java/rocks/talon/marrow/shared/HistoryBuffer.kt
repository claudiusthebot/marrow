package rocks.talon.marrow.shared

/**
 * Fixed-capacity circular buffer for [Float] values.
 *
 * Thread-safe — all mutations and reads are [synchronized].
 * Used to maintain rolling history for sparkline charts without
 * allocating a new list on every live-loop tick.
 *
 * When [capacity] is reached, the oldest entry is silently dropped
 * to make room for the incoming value (circular / ring semantics).
 *
 * @param capacity Maximum number of entries retained. Must be ≥ 1.
 */
class HistoryBuffer(val capacity: Int = 60) {

    init {
        require(capacity >= 1) { "capacity must be >= 1, got $capacity" }
    }

    private val buffer = ArrayDeque<Float>(capacity)

    /**
     * Append [value] to the buffer. If the buffer is at [capacity],
     * the oldest entry is removed first.
     */
    @Synchronized
    fun push(value: Float) {
        if (buffer.size == capacity) buffer.removeFirst()
        buffer.addLast(value)
    }

    /**
     * Return an immutable snapshot of current contents in insertion order
     * (oldest first, newest last). Allocates a new list on each call.
     */
    @Synchronized
    fun snapshot(): List<Float> = buffer.toList()

    /** Remove all entries. */
    @Synchronized
    fun clear() = buffer.clear()

    /** Current number of entries (0 ≤ size ≤ [capacity]). */
    val size: Int get() = synchronized(this) { buffer.size }
}
