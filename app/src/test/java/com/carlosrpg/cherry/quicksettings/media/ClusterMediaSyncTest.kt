package com.carlosrpg.cherry.quicksettings.media

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClusterMediaSyncTest {
    private class FakeSink(var up: Boolean = true) : ClusterMediaSink {
        override val connected: Boolean get() = up
        val calls = mutableListOf<String>()
        override fun showTrack(title: String, artist: String, cover: Bitmap?): Boolean {
            if (up) calls += "track($title,$artist)"
            return up
        }
        override fun showProgress(playing: Boolean, positionMs: Long, durationMs: Long): Boolean {
            if (up) calls += "progress($playing,$positionMs,$durationMs)"
            return up
        }
    }

    private var clock = 10_000L
    private fun app(title: String = "Song", playing: Boolean = true, position: Long = 1_000, at: Long = 10_000) = MediaState(
        packageName = "com.carlosrpg.aabrowser", appLabel = "Motion", title = title, artist = "Band",
        playing = playing, positionMs = position, positionAt = at, durationMs = 200_000, hasAccess = true,
    )

    @Test
    fun `a playing app sends its track once, then progress on every tick`() {
        val sink = FakeSink()
        val sync = ClusterMediaSync(sink) { clock }
        sync.tick(app())
        clock += 2_000
        sync.tick(app())
        assertThat(sink.calls).containsExactly(
            "track(Song,Band)", "progress(true,1000,200000)", "progress(true,3000,200000)",
        ).inOrder()
    }

    @Test
    fun `a new track is sent again, and a pause is sent once`() {
        val sink = FakeSink()
        val sync = ClusterMediaSync(sink) { clock }
        sync.tick(app())
        sync.tick(app(title = "Next"))
        assertThat(sync.tick(app(title = "Next", playing = false, position = 5_000))).isEqualTo("paused")
        assertThat(sync.tick(app(title = "Next", playing = false, position = 5_000))).isNull()
        assertThat(sink.calls).containsExactly(
            "track(Song,Band)", "progress(true,1000,200000)",
            "track(Next,Band)", "progress(true,1000,200000)",
            "progress(false,5000,200000)",
        ).inOrder()
    }

    @Test
    fun `a paused app never takes the card over`() {
        val sink = FakeSink()
        ClusterMediaSync(sink) { clock }.tick(app(playing = false))
        assertThat(sink.calls).isEmpty()
    }

    @Test
    fun `a car source hands the card back, and the app's track is sent again when it returns`() {
        val sink = FakeSink()
        val sync = ClusterMediaSync(sink) { clock }
        sync.tick(app())
        assertThat(sync.tick(MediaState(appLabel = "Bluetooth", title = "BT song", playing = true, hasAccess = true)))
            .isEqualTo("car source or nothing playing: the cluster is the car's again")
        sync.tick(app())
        assertThat(sink.calls.filter { it.startsWith("track") }).containsExactly("track(Song,Band)", "track(Song,Band)")
    }

    @Test
    fun `nothing is remembered as sent while the cluster is not connected`() {
        val sink = FakeSink(up = false)
        val sync = ClusterMediaSync(sink) { clock }
        assertThat(sync.tick(app())).isEqualTo("cluster not connected")
        sink.up = true
        sync.tick(app())
        assertThat(sink.calls.first()).isEqualTo("track(Song,Band)")
    }

    @Test
    fun `the live position stops at the track's end and stays put while paused`() {
        val sync = ClusterMediaSync(FakeSink()) { clock }
        clock = 10_000 + 500_000
        assertThat(sync.livePosition(app())).isEqualTo(200_000)
        assertThat(sync.livePosition(app(playing = false))).isEqualTo(1_000)
    }
}
