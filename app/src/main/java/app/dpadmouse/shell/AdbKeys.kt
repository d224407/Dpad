package app.dpadmouse.shell

import android.util.Base64
import app.dpadmouse.DLog
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/** Khoá RSA-2048 của "máy chủ ADB" (chính app này), lưu trong filesDir. */
class AdbKeys(private val dir: File) {
    private val keyPair: KeyPair by lazy { loadOrCreate() }

    private fun loadOrCreate(): KeyPair {
        val priv = File(dir, "adbkey.pk8")
        val pub = File(dir, "adbkey.x509")
        try {
            if (priv.exists() && pub.exists()) {
                val kf = KeyFactory.getInstance("RSA")
                return KeyPair(
                    kf.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes()))
                )
            }
        } catch (e: Exception) {
            DLog.w("Không đọc được khoá ADB, tạo khoá mới", e)
        }
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val kp = gen.generateKeyPair()
        dir.mkdirs()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        DLog.i("Đã tạo khoá ADB mới")
        return kp
    }

    /** Ký "token" 20 byte của adbd: PKCS#1 v1.5 với DigestInfo SHA-1 (token coi như đã băm). */
    fun sign(token: ByteArray): ByteArray {
        val prefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val digestInfo = prefix + token
        val padded = ByteArray(256)
        padded[1] = 1
        val psLen = 256 - 3 - digestInfo.size
        for (i in 0 until psLen) padded[2 + i] = 0xFF.toByte()
        System.arraycopy(digestInfo, 0, padded, 3 + psLen, digestInfo.size)
        val c = Cipher.getInstance("RSA/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keyPair.private)
        return c.doFinal(padded)
    }

    /** Khoá công khai theo định dạng Android (struct RSAPublicKey) + base64 + tên, kết thúc bằng NUL. */
    fun publicKeyMessage(): ByteArray {
        val pub = keyPair.public as RSAPublicKey
        val n = pub.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = n.mod(r32).modInverse(r32).negate().mod(r32)
        val rr = BigInteger.ONE.shiftLeft(2048 * 2).mod(n)

        val bb = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(64)
        bb.putInt(n0inv.toInt())
        bb.put(toLittleEndian(n, 256))
        bb.put(toLittleEndian(rr, 256))
        bb.putInt(pub.publicExponent.toInt())
        val b64 = Base64.encodeToString(bb.array(), Base64.NO_WRAP)
        return "$b64 dpadmouse@android\u0000".toByteArray()
    }

    private fun toLittleEndian(x: BigInteger, size: Int): ByteArray {
        val be = x.toByteArray()
        val out = ByteArray(size)
        for (i in 0 until size) {
            val src = be.size - 1 - i
            if (src >= 0) out[i] = be[src]
        }
        return out
    }
}
