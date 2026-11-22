package com.carlosrpg.cherry.quicksettings.phone

import app.jconfig.carbus.carinfo.AssistIds
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.VDEventCarInfo
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.car.seededMockBus
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.media.VolumeChannel
import com.carlosrpg.cherry.quicksettings.settings.QsConfig
import com.carlosrpg.cherry.quicksettings.weather.WeatherState
import com.google.common.truth.Truth.assertThat
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** The phone server end to end on a free local port, over the seeded mock car. */
class PhoneApiTest {
    private val bus = seededMockBus()
    private val car = CarRepository(bus, CoroutineScope(Dispatchers.Unconfined))
    private var stored: String? = null
    private val registry = PhoneRegistry(load = { stored }, save = { stored = it })
    private val calls = mutableListOf<String>()
    private val actions = object : PhoneActions {
        override fun playPause() { calls += "playPause" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun recentPackages() = listOf("com.example.music")
        override fun playRecent(pkg: String) = true.also { calls += "playRecent $pkg" }
        override fun mediaSearch(query: String) = true.also { calls += "search $query" }
        override fun setVolume(channel: VolumeChannel, value: Int) { calls += "volume $channel $value" }
        override fun toggleMute() { calls += "mute" }
        override fun navSearch(query: String) = true.also { calls += "nav $query" }
        override fun toggleNavOnCluster() { calls += "cluster" }
        override fun bringToFront(nav: Boolean) = true
    }
    private val commands = PhoneCommands(car, actions)
    private val backend = object : PhoneBackend {
        override fun state() = phoneStateJson(
            PhoneSnapshot(
                connected = true, car = car.state.value, applyingDriver = null, applyingPassenger = null,
                applyingFan = car.fanTarget.value, config = QsConfig(), media = MediaState(title = "Song"), positionAtMs = 0,
                volumes = emptyMap(), nav = null, navAppLabel = null, navOnCluster = false, weather = WeatherState(),
            ),
            ImageCache(),
        )
        override suspend fun command(action: String, args: JSONObject, remote: Boolean) = commands.run(action, args, remote)
        override fun image(key: String): ByteArray? = null
    }
    private var remoteAssist = false
    private val api = PhoneApi(registry, backend, asset = { if (it == "web/index.html") "<html>".toByteArray() else null }, Dispatchers.Unconfined, remoteAssist = { remoteAssist })
    private val server = PhoneServer(0, api::handle).also { check(it.start()) }

    @AfterEach
    fun stop() = server.stop()

    private class Reply(val status: Int, val body: String) {
        val json get() = JSONObject(body)
    }

    private fun call(path: String, body: JSONObject? = null, token: String? = null): Reply {
        val c = URL("http://127.0.0.1:${server.boundPort}$path").openConnection() as HttpURLConnection
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val status = c.responseCode
        val text = (if (status < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes()) }.orEmpty()
        return Reply(status, text)
    }

    /** Pairs a phone through the API, tapping Allow as the head unit would. */
    private fun pairedToken(): String = runBlocking {
        val code = registry.newCode().code
        val answer = Thread {
            while (registry.request.value == null) Thread.sleep(5)
            registry.answer(true)
        }.apply { start() }
        val reply = call("/api/pair", JSONObject().put("code", code).put("name", "Test phone"))
        answer.join()
        assertThat(reply.status).isEqualTo(200)
        reply.json.getString("token")
    }

    private fun cmd(token: String, action: String, args: JSONObject = JSONObject()) =
        call("/api/cmd", JSONObject().put("action", action).put("args", args), token)

    @Test
    fun `ping and the page need no token, everything else does`() {
        assertThat(call("/api/ping").json.getString("carId")).isEqualTo(registry.carId)
        assertThat(call("/").body).isEqualTo("<html>")
        assertThat(call("/../secret").status).isEqualTo(404)
        assertThat(call("/api/state").status).isEqualTo(401)
        assertThat(call("/api/state", token = "made-up").status).isEqualTo(401)
        assertThat(cmd("made-up", "media.next").status).isEqualTo(401)
        assertThat(call("/api/pair", JSONObject().put("code", "guess").put("name", "x")).status).isEqualTo(403)
    }

    @Test
    fun `a paired phone reads the car's state and loses access once removed`() = runBlocking {
        car.refresh()
        val token = pairedToken()
        val state = call("/api/state", token = token).json
        assertThat(state.getString("path")).isEqualTo("lan")
        assertThat(state.getJSONObject("permissions").getBoolean("assist")).isTrue()
        val energy = state.getJSONObject("car").getJSONObject("energy")
        assertThat(energy.getInt("batterySoc")).isEqualTo(72)
        assertThat(energy.getString("regen")).isEqualTo("MEDIUM")
        assertThat(state.getJSONObject("media").getString("title")).isEqualTo("Song")
        assertThat(api.watching()).isTrue()
        registry.remove(registry.phones.value.single().id)
        assertThat(call("/api/state", token = token).status).isEqualTo(401)
    }

    @Test
    fun `commands reach the car through the drawer's own calls`() = runBlocking {
        car.refresh()
        val token = pairedToken()
        val start = car.state.value.fanLevel!!
        assertThat(cmd(token, "climate.setFan", JSONObject().put("level", start + 1)).json.getBoolean("ok")).isTrue()
        while (car.fanTarget.value != null) delay(20)
        assertThat(bus.getItemValue(VDEventCarInfo.MODULE_HVAC, FanSpec.ITEM_READ)).isEqualTo(start + 1)

        cmd(token, "media.search", JSONObject().put("query", "jazz"))
        cmd(token, "media.playRecent", JSONObject().put("packageName", "com.example.music"))
        assertThat(calls).containsExactly("search jazz", "playRecent com.example.music").inOrder()

        val bad = cmd(token, "media.playRecent", JSONObject().put("packageName", "com.not.recent")).json
        assertThat(bad.getString("blocked")).isEqualTo("BAD_ARGS")
        assertThat(cmd(token, "climate.setFan", JSONObject().put("level", 99)).json.getString("blocked")).isEqualTo("BAD_ARGS")
        assertThat(cmd(token, "drawer.open").json.getString("blocked")).isEqualTo("UNKNOWN_ACTION")
        // The seeded car has the zones split; a synced car refuses the passenger zone.
        assertThat(cmd(token, "climate.setPassengerTemp", JSONObject().put("c", 21)).json.getBoolean("ok")).isTrue()
    }

    @Test
    fun `switching a safety function off takes confirm, and the relay path never reaches assist`() = runBlocking {
        car.refresh()
        val token = pairedToken()
        bus.sendItemValue(AssistIds.MODULE, AssistIds.ITEM_LANE_DEPARTURE_PREVENTION, AssistIds.command(true))
        car.refresh()
        assertThat(car.state.value.laneKeep).isTrue()
        val unconfirmed = cmd(token, "assist.toggle", JSONObject().put("assist", "LANE")).json
        assertThat(unconfirmed.getString("blocked")).isEqualTo("NEEDS_CONFIRM")
        assertThat(car.state.value.laneKeep).isTrue()

        val remote = commands.run("assist.toggle", JSONObject().put("assist", "AUTO_HOLD"), remote = true)
        assertThat(remote.getString("blocked")).isEqualTo("REMOTE_DISABLED")
        val remoteFcw = commands.run("assist.cycleCollisionWarning", JSONObject(), remote = true)
        assertThat(remoteFcw.getString("blocked")).isEqualTo("REMOTE_DISABLED")
    }

    @Test
    fun `more than ten commands a second from one phone are refused`() {
        val token = pairedToken()
        val statuses = (1..12).map { cmd(token, "media.next").status }
        assertThat(statuses).contains(429)
        assertThat(statuses.count { it == 200 }).isAtMost(PhoneApi.MAX_COMMANDS_PER_SECOND)
    }

    @Test
    fun `through the relay, pairing is refused and assist follows the car's setting`() {
        val token = pairedToken()
        fun relay(path: String, body: JSONObject? = null) = api.handle(
            HttpRequest(if (body == null) "GET" else "POST", path, emptyMap(), mapOf("authorization" to "Bearer $token"), body?.toString().orEmpty()),
            remote = true,
        ).let { Reply(it.status, String(it.body)) }
        val aeb = JSONObject().put("action", "assist.toggle").put("args", JSONObject().put("assist", "AUTO_HOLD"))

        assertThat(relay("/api/pair", JSONObject().put("code", registry.newCode().pin)).json.getString("error")).isEqualTo("LAN_ONLY")
        val state = relay("/api/state").json
        assertThat(state.getString("path")).isEqualTo("relay")
        assertThat(state.getJSONObject("permissions").getBoolean("assist")).isFalse()
        assertThat(relay("/api/cmd", aeb).json.getString("blocked")).isEqualTo("REMOTE_DISABLED")

        remoteAssist = true
        assertThat(relay("/api/state").json.getJSONObject("permissions").getBoolean("assist")).isTrue()
        assertThat(relay("/api/cmd", aeb).json.optString("blocked")).isNotEqualTo("REMOTE_DISABLED")
    }
}
