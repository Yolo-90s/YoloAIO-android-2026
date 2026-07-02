package com.example.yoloaio.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigTest {
    @Test
    fun effectiveGoogleWebClientId_usesConfiguredValueWhenPresent() {
        val config = AppConfig(googleWebClientId = "configured-id")

        assertEquals("configured-id", config.effectiveGoogleWebClientId("fallback-id"))
    }

    @Test
    fun effectiveGoogleWebClientId_fallsBackToProvidedValueWhenConfigIsBlank() {
        val config = AppConfig()

        assertEquals("fallback-id", config.effectiveGoogleWebClientId("fallback-id"))
    }

    @Test
    fun effectiveGoogleWebClientId_returnsEmptyWhenNoConfiguredOrFallbackValue() {
        val config = AppConfig()

        assertEquals("", config.effectiveGoogleWebClientId(null))
    }
}
