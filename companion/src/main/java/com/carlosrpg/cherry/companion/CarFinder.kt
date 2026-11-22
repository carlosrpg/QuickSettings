package com.carlosrpg.cherry.companion

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Finds the car: its last address on the Wi-Fi, then every address of the phone's local /24
 * networks (the car's Wi-Fi as a client, or the phone's own hotspot), then the relay.
 * Only plain `java.net`, so no dependency.
 */
class CarFinder(private val relayUrl: String) {

    /** Where the page is: the car on the LAN, or the relay; [carId] from the car's ping. */
    data class Found(val url: String, val carId: String, val lanHost: String?)

    /**
     * [lastHost] and [carId] are what the app remembers (null before the first find). A car with a
     * different id is ignored once one is known.
     */
    fun find(lastHost: String?, carId: String?): Found? {
        lastHost?.let { host -> ping(lanUrl(host), carId)?.let { return Found(lanUrl(host), it, host) } }
        scan(carId)?.let { (host, id) -> return Found(lanUrl(host), id, host) }
        if (relayUrl.isNotEmpty() && carId != null) {
            val base = "${relayUrl.trimEnd('/')}/c/$carId/"
            // The relay answers for the car even when the car is offline; the page then says so.
            if (ping(base, carId, RELAY_TIMEOUT_MS) != null) return Found(base, carId, null)
        }
        return null
    }

    /** Whether the page at [url] (as [find] returned it) still answers. */
    fun stillThere(url: String, carId: String?) = ping(url, carId, if (url.startsWith("https")) RELAY_TIMEOUT_MS else LAN_TIMEOUT_MS) != null

    private fun scan(carId: String?): Pair<String, String>? {
        val hosts = localHosts()
        if (hosts.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(SCAN_THREADS)
        try {
            val done = ExecutorCompletionService<Pair<String, String>?>(pool)
            hosts.forEach { host -> done.submit { ping(lanUrl(host), carId)?.let { host to it } } }
            repeat(hosts.size) {
                val hit = done.poll(SCAN_BUDGET_MS, TimeUnit.MILLISECONDS)?.get() ?: return@repeat
                return hit
            }
            return null
        } finally {
            pool.shutdownNow()
        }
    }

    /** The carId the car at [base] reports from `api/ping`, if it is a QuickSettings car (and the one we know). */
    private fun ping(base: String, carId: String?, timeoutMs: Int = LAN_TIMEOUT_MS): String? = try {
        val c = URL(base + "api/ping").openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.useCaches = false
        try {
            if (c.responseCode != 200) null
            else parsePing(c.inputStream.bufferedReader().readText(), carId)
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val PORT = 8080
        private const val LAN_TIMEOUT_MS = 1_500
        private const val RELAY_TIMEOUT_MS = 5_000
        private const val SCAN_THREADS = 64
        private const val SCAN_BUDGET_MS = 6_000L

        fun lanUrl(host: String) = "http://$host:$PORT/"

        internal fun parsePing(body: String, carId: String?): String? {
            val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val id = o.optString("carId").takeIf { it.isNotEmpty() } ?: return null
            return id.takeIf { carId == null || it == carId }
        }

        /** The other 253 addresses of each /24 around [address]. */
        internal fun neighbours(address: ByteArray): List<String> {
            val prefix = "${address[0].toInt() and 0xFF}.${address[1].toInt() and 0xFF}.${address[2].toInt() and 0xFF}"
            val own = address[3].toInt() and 0xFF
            return (1..254).filter { it != own }.map { "$prefix.$it" }
        }

        /** Every neighbour on the phone's Wi-Fi and hotspot interfaces (not mobile data, not VPNs). */
        private fun localHosts(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && WIFI_NAMES.any { n -> it.name.contains(n) } }
                .flatMap { nic -> nic.interfaceAddresses.mapNotNull { a -> (a.address as? Inet4Address)?.takeIf { it.isSiteLocalAddress } } }
                .flatMap { neighbours(it.address) }
                .distinct()
        }.getOrDefault(emptyList())

        private val WIFI_NAMES = listOf("wlan", "ap", "swlan", "eth", "rndis")
    }
}
