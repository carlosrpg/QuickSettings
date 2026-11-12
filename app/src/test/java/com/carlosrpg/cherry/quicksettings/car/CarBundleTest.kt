package com.carlosrpg.cherry.quicksettings.car

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The head unit has no copy of the vendor client an app can load (it is not on the boot
 * classpath and not a shared library), so the APK must carry it. The first on-car build did
 * not, and every car value read as unavailable.
 */
class CarBundleTest {
    @Test
    fun `the vendor client the car facade reflects over is bundled`() {
        val proxy = Class.forName("com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy")
        assertThat(proxy.getMethod("getInstance")).isNotNull()
        assertThat(Class.forName("com.desaysv.ivi.vdb.client.VDBus")).isNotNull()
    }
}
