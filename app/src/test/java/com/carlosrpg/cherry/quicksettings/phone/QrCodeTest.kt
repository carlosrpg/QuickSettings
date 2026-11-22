package com.carlosrpg.cherry.quicksettings.phone

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

class QrCodeTest {
    @Test
    fun `error correction, format and version bits match the standard's published examples`() {
        // "HELLO WORLD" at 1-M: its 16 data codewords and their 10 error correction codewords.
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        assertThat(QrCode.rsRemainder(data, QrCode.rsDivisor(10)).toList())
            .containsExactly(196, 35, 39, 119, 235, 215, 231, 226, 93, 23).inOrder()
        val formatM = listOf(
            "101010000010010", "101000100100101", "101111001111100", "101101101001011",
            "100010111111001", "100000011001110", "100111110010111", "100101010100000",
        )
        assertThat((0 until 8).map { QrCode.formatBits(it).toString(2).padStart(15, '0') }).isEqualTo(formatM)
        assertThat(QrCode.versionBits(7).toString(2).padStart(18, '0')).isEqualTo("000111110010010100")
    }

    @Test
    fun `the version grows with the text and the fixed patterns are in place`() {
        assertThat(QrCode.encode("hi").size).isEqualTo(21) // version 1
        val link = "http://192.168.100.200:8080/#pair=AbCdEfGhIjKlMnOpQrStUv"
        val m = QrCode.encode(link)
        assertThat(m.size).isEqualTo(33) // version 4: 62 bytes at level M
        // Finder patterns: dark ring, light ring, dark 3x3 core, top-left / top-right / bottom-left.
        for ((x0, y0) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) {
            assertThat(m[y0][x0]).isTrue()
            assertThat(m[y0 + 1][x0 + 1]).isFalse()
            assertThat(m[y0 + 3][x0 + 3]).isTrue()
        }
        // Timing pattern and the always-dark module.
        assertThat((8 until m.size - 8).map { m[6][it] }).isEqualTo((8 until m.size - 8).map { it % 2 == 0 })
        assertThat(m[m.size - 8][8]).isTrue()
        // A larger version carries the version information blocks.
        assertThat(QrCode.encode("x".repeat(150)).size).isEqualTo(4 * 8 + 17)
        // For decoding with an independent reader when the encoder changes.
        run {
            File("build/qr-dump.txt").writeText(listOf("hi", link, "x".repeat(150)).joinToString("\n") { text ->
                QrCode.encode(text).joinToString("|") { row -> row.joinToString("") { if (it) "1" else "0" } }
            })
        }
    }
}
