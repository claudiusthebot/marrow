package rocks.talon.marrow.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBufferTest {

    @Test
    fun `snapshot is empty before any push`() {
        val buf = HistoryBuffer(capacity = 5)
        assertEquals(emptyList<Float>(), buf.snapshot())
    }

    @Test
    fun `single push results in size 1`() {
        val buf = HistoryBuffer(capacity = 5)
        buf.push(42f)
        assertEquals(1, buf.size)
        assertEquals(listOf(42f), buf.snapshot())
    }

    @Test
    fun `values within capacity are retained in insertion order`() {
        val buf = HistoryBuffer(capacity = 5)
        listOf(1f, 2f, 3f).forEach { buf.push(it) }
        assertEquals(listOf(1f, 2f, 3f), buf.snapshot())
    }

    @Test
    fun `oldest value is evicted when capacity is reached`() {
        val buf = HistoryBuffer(capacity = 3)
        listOf(10f, 20f, 30f, 40f).forEach { buf.push(it) }
        // 10f should have been evicted; remaining: 20, 30, 40
        assertEquals(listOf(20f, 30f, 40f), buf.snapshot())
        assertEquals(3, buf.size)
    }

    @Test
    fun `clear empties the buffer`() {
        val buf = HistoryBuffer(capacity = 5)
        listOf(1f, 2f, 3f).forEach { buf.push(it) }
        buf.clear()
        assertEquals(0, buf.size)
        assertTrue(buf.snapshot().isEmpty())
    }

    @Test
    fun `snapshot returns a defensive copy - mutation does not affect buffer`() {
        val buf = HistoryBuffer(capacity = 5)
        buf.push(7f)
        val snap = buf.snapshot().toMutableList()
        snap.add(99f)                // mutate the snapshot copy
        assertEquals(listOf(7f), buf.snapshot())  // original buffer unchanged
    }

    @Test
    fun `capacity 1 always retains only the last push`() {
        val buf = HistoryBuffer(capacity = 1)
        buf.push(1f)
        buf.push(2f)
        buf.push(3f)
        assertEquals(listOf(3f), buf.snapshot())
    }
}
