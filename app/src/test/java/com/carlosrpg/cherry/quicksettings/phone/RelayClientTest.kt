package com.carlosrpg.cherry.quicksettings.phone

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RelayClientTest {
    @Test
    fun `the websocket accept matches RFC 6455's example`() {
        assertThat(RelayClient.acceptFor("dGhlIHNhbXBsZSBub25jZQ==")).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
    }
}
