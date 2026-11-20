package com.carlosrpg.cherry.quicksettings.settings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AppUpdaterTest {
    @Test
    fun `newer only when MAJOR MINOR PATCH is higher`() {
        assertThat(AppUpdater.isNewer("0.1.9", "0.1.8")).isTrue()
        assertThat(AppUpdater.isNewer("0.2.0", "0.1.99")).isTrue()
        assertThat(AppUpdater.isNewer("0.1.8", "0.1.8")).isFalse()
        assertThat(AppUpdater.isNewer("0.1.7", "0.1.8")).isFalse()
    }

    @Test
    fun `suffixes are ignored and garbage is never newer`() {
        assertThat(AppUpdater.isNewer("0.1.9", "0.1.9-dev")).isFalse()
        assertThat(AppUpdater.isNewer("0.1.10", "0.1.9-pr.3.12")).isTrue()
        assertThat(AppUpdater.isNewer("nonsense", "0.1.8")).isFalse()
        assertThat(AppUpdater.isNewer("0.1.9", "unknown")).isTrue()
    }
}
