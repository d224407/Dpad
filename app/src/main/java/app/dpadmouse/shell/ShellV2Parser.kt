package app.dpadmouse.shell

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tách khung "shell protocol v2" của adbd: [id:1][độ dài:4 LE][dữ liệu].
 * id: 0=stdin, 1=stdout, 2=stderr, 3=mã thoát.
 * Không phụ thuộc Android -> test được bằng JUnit thuần.
 */
class ShellV2Parser {
    private var buf = ByteArray(0)

    @Throws(IOException::class)
    fun feed(data: ByteArray, sink: (id: Int, payload: ByteArray) -> Unit) {
        buf += data
        var off = 0
        while (buf.size - off >= 5) {
            val id = buf[off].toInt() and 0xFF
            val len = ByteBuffer.wrap(buf, off + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (len < 0 || len > (1 shl 20)) throw IOException("Khung shell v2 hỏng (len=$len)")
            if (buf.size - off - 5 < len) break
            sink(id, buf.copyOfRange(off + 5, off + 5 + len))
            off += 5 + len
        }
        buf = if (off == 0) buf else buf.copyOfRange(off, buf.size)
    }
}
