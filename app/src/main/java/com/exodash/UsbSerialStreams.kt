package com.exodash

import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.InputStream
import java.io.OutputStream

class UsbSerialInputStream(private val port: UsbSerialPort) : InputStream() {
    private val buffer = ByteArray(4096)
    private var pos = 0
    private var limite = 0

    override fun read(): Int {
        if (pos >= limite) {
            val lido = try { port.read(buffer, 500) } catch (e: Exception) { -1 }
            if (lido <= 0) return -1
            pos = 0
            limite = lido
        }
        return buffer[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            if (pos >= limite) {
                val lido = try { port.read(buffer, 500) } catch (e: Exception) { return if (total > 0) total else -1 }
                if (lido <= 0) return if (total > 0) total else -1
                pos = 0
                limite = lido
            }
            val copia = minOf(len - total, limite - pos)
            System.arraycopy(buffer, pos, b, off + total, copia)
            pos += copia
            total += copia
        }
        return total
    }

    override fun available(): Int = limite - pos
}

class UsbSerialOutputStream(private val port: UsbSerialPort) : OutputStream() {
    override fun write(b: Int) {
        try { port.write(byteArrayOf(b.toByte()), 1000) } catch (e: Exception) {}
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        try { port.write(b.copyOfRange(off, off + len), 1000) } catch (e: Exception) {}
    }

    override fun write(b: ByteArray) {
        try { port.write(b, 1000) } catch (e: Exception) {}
    }
}
