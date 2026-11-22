package com.carlosrpg.cherry.quicksettings.phone

import kotlin.math.abs

/**
 * A QR code encoder for the pairing screen: byte mode, error correction level M, versions 1 to
 * 10 (up to 213 bytes, far more than a pairing link). No library on the car or in the build
 * offers one. Follows ISO/IEC 18004; the structure is the well-known reference algorithm.
 */
object QrCode {
    // Level M, indexed by version (0 unused).
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)
    private const val MAX_VERSION = 10
    private const val FORMAT_LEVEL_M = 0

    /** The modules, `[y][x]`, true = dark, without the quiet zone (leave 4 modules light around it). */
    fun encode(text: String): Array<BooleanArray> {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = (1..MAX_VERSION).firstOrNull { v ->
            4 + (if (v < 10) 8 else 16) + data.size * 8 <= dataCodewords(v) * 8
        } ?: throw IllegalArgumentException("text too long for a version $MAX_VERSION QR code")
        val bits = ArrayList<Boolean>()
        fun append(value: Int, length: Int) = (length - 1 downTo 0).forEach { bits += (value ushr it) and 1 != 0 }
        append(0b0100, 4)
        append(data.size, if (version < 10) 8 else 16)
        data.forEach { append(it.toInt() and 0xFF, 8) }
        val capacity = dataCodewords(version) * 8
        append(0, minOf(4, capacity - bits.size))
        append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacity) {
            append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        val codewords = IntArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, b -> (acc shl 1) or (if (bits[i * 8 + b]) 1 else 0) } }
        return Matrix(version).build(interleave(codewords, version))
    }

    private fun rawDataModules(v: Int): Int {
        var result = (16 * v + 128) * v + 64
        if (v >= 2) {
            val align = v / 7 + 2
            result -= (25 * align - 10) * align - 55
            if (v >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewords(v: Int) = rawDataModules(v) / 8 - ECC_PER_BLOCK[v] * BLOCKS[v]

    /** Splits the data into blocks, adds each block's Reed-Solomon codewords, interleaves them. */
    private fun interleave(data: IntArray, v: Int): IntArray {
        val blocks = BLOCKS[v]
        val eccLen = ECC_PER_BLOCK[v]
        val raw = rawDataModules(v) / 8
        val shortBlocks = blocks - raw % blocks
        val shortLen = raw / blocks
        val divisor = rsDivisor(eccLen)
        var k = 0
        val all = (0 until blocks).map { i ->
            val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + len)
            k += len
            val ecc = rsRemainder(dat, divisor)
            // Short blocks get a placeholder so every block has the same length; it is skipped below.
            (if (i < shortBlocks) dat + 0 else dat) + ecc
        }
        val out = ArrayList<Int>(raw)
        for (i in all[0].indices) for (j in all.indices) {
            if (i != shortLen - eccLen || j >= shortBlocks) out += all[j][i]
        }
        return out.toIntArray()
    }

    internal fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree).also { it[degree - 1] = 1 }
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        return result
    }

    internal fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return result
    }

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    /** The 15 format bits (level M, [mask]) with their BCH code, before placement. */
    internal fun formatBits(mask: Int): Int {
        val data = (FORMAT_LEVEL_M shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        return ((data shl 10) or rem) xor 0x5412
    }

    /** The 18 version bits for versions 7 and up. */
    internal fun versionBits(version: Int): Int {
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        return (version shl 12) or rem
    }

    private class Matrix(val version: Int) {
        val size = version * 4 + 17
        val dark = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, on: Boolean) {
            dark[y][x] = on
            function[y][x] = true
        }

        fun build(codewords: IntArray): Array<BooleanArray> {
            drawFunctionPatterns()
            drawCodewords(codewords)
            val mask = (0 until 8).minBy { m ->
                applyMask(m)
                drawFormat(m)
                penalty().also { applyMask(m) }
            }
            applyMask(mask)
            drawFormat(mask)
            return dark
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val align = alignmentPositions()
            val n = align.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(align[i] + dx, align[j] + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
            drawFormat(0) // reserves the area; the real bits go in once the mask is chosen
            if (version >= 7) {
                val bits = versionBits(version)
                for (i in 0 until 18) {
                    val bit = (bits ushr i) and 1 != 0
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit)
                    set(b, a, bit)
                }
            }
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                val dist = maxOf(abs(dx), abs(dy))
                if (x in 0 until size && y in 0 until size) set(x, y, dist != 2 && dist != 4)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val n = version / 7 + 2
            val step = (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val result = IntArray(n)
            result[0] = 6
            var pos = size - 7
            for (i in n - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun drawFormat(mask: Int) {
            val bits = formatBits(mask)
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawCodewords(data: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = (right + 1) and 2 == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!function[y][x] && i < data.size * 8) {
                        dark[y][x] = (data[i ushr 3] ushr (7 - (i and 7))) and 1 != 0
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !function[y][x]) dark[y][x] = !dark[y][x]
            }
        }

        /** The standard's four penalty rules; the mask with the lowest score scans best. */
        private fun penalty(): Int {
            var score = 0
            fun at(x: Int, y: Int, row: Boolean) = if (row) dark[y][x] else dark[x][y]
            for (row in listOf(true, false)) for (a in 0 until size) {
                var run = 1
                for (b in 1 until size) {
                    if (at(b, a, row) == at(b - 1, a, row)) {
                        run++
                        if (run == 5) score += 3 else if (run > 5) score++
                    } else run = 1
                }
                // Finder-like 1:1:3:1:1 with four light modules on either side (outside counts as light).
                for (start in -4 until size) {
                    fun m(i: Int) = (start + i).let { it in 0 until size && at(it, a, row) }
                    val core = m(4) && !m(5) && m(6) && m(7) && m(8) && !m(9) && m(10)
                    if (core && (0..3).none { m(it) }) score += 40
                    val core2 = m(0) && !m(1) && m(2) && m(3) && m(4) && !m(5) && m(6)
                    if (core2 && (7..10).none { m(it) }) score += 40
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = dark[y][x]
                if (c == dark[y][x + 1] && c == dark[y + 1][x] && c == dark[y + 1][x + 1]) score += 3
            }
            val total = size * size
            val darkCount = dark.sumOf { r -> r.count { it } }
            val k = (abs(darkCount * 20 - total * 10) + total - 1) / total - 1
            return score + maxOf(k, 0) * 10
        }
    }
}
