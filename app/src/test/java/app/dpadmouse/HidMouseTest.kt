package app.dpadmouse

import app.dpadmouse.hid.HidMouse
import app.dpadmouse.shell.ShellV2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HidMouseTest {
    @Test
    fun negativeDeltasBecomeUnsignedBytes() {
        assertEquals(
            "{\"id\":1,\"command\":\"report\",\"report\":[1,255,2,0]}",
            HidMouse.reportJson(1, -1, 2, 0)
        )
    }

    @Test
    fun registerCarriesDescriptor() {
        val j = HidMouse.registerJson()
        assertTrue(j.contains("\"command\":\"register\""))
        assertTrue(j.contains("\"bus\":\"usb\""))
        assertTrue(j.contains("\"descriptor\":[5,1,9,2,161,1"))
    }

    @Test
    fun shellV2ParserHandlesSplitPackets() {
        fun frame(id: Int, payload: ByteArray) =
            ByteBuffer.allocate(5 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(id.toByte()).putInt(payload.size).put(payload).array()

        val all = frame(1, "abc".toByteArray()) + frame(3, byteArrayOf(0))
        val got = mutableListOf<Pair<Int, String>>()
        val p = ShellV2Parser()
        // Cắt giữa gói để thử ghép
        p.feed(all.copyOfRange(0, 6)) { id, d -> got += id to String(d) }
        assertTrue(got.isEmpty())
        p.feed(all.copyOfRange(6, all.size)) { id, d -> got += id to String(d) }
        assertEquals(2, got.size)
        assertEquals(1 to "abc", got[0])
        assertEquals(3, got[1].first)
    }
}
