package com.carlosrpg.cherry.companion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CarFinderTest {

    @Test
    fun `given a ping, when the car id is unknown or matches, then it is accepted`() {
        val body = """{"carId":"abc12345","online":true,"path":"lan"}"""
        assertThat(CarFinder.parsePing(body, null)).isEqualTo("abc12345")
        assertThat(CarFinder.parsePing(body, "abc12345")).isEqualTo("abc12345")
        assertThat(CarFinder.parsePing(body, "other")).isNull()
        assertThat(CarFinder.parsePing("<html>", null)).isNull()
        assertThat(CarFinder.parsePing("{}", null)).isNull()
    }

    @Test
    fun `given the phone's address, then the scan covers the rest of its slash 24`() {
        val hosts = CarFinder.neighbours(byteArrayOf(192.toByte(), 168.toByte(), 43, 7))
        assertThat(hosts).hasSize(253)
        assertThat(hosts).contains("192.168.43.1")
        assertThat(hosts).contains("192.168.43.254")
        assertThat(hosts).doesNotContain("192.168.43.7")
    }
}
