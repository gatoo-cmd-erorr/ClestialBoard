package com.clestial.keyboard.math

import com.clestial.keyboard.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRouterClientTest {

    @Test
    fun defaultModel_isGemini38FlashHigh() {
        assertEquals("ag/gemini-3.8-flash-high", Prefs.DEFAULT_CHATGPT_MODEL)
    }

    @Test
    fun resolveChatCompletionsEndpoint_handlesDynamicBaseUrls() {
        assertEquals(
            "https://passive-candidate-knife-computers.trycloudflare.com/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("https://passive-candidate-knife-computers.trycloudflare.com")
        )
        assertEquals(
            "https://demo-tunnel.trycloudflare.com/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("https://demo-tunnel.trycloudflare.com")
        )
        assertEquals(
            "https://demo-tunnel.trycloudflare.com/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("https://demo-tunnel.trycloudflare.com/")
        )
        assertEquals(
            "http://52.141.18.29:20128/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("http://52.141.18.29:20128/v1")
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("https://api.groq.com/openai/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiRouterClient.resolveChatCompletionsEndpoint("")
        )
    }

    @Test
    fun resolveModelsEndpoint_handlesDynamicBaseUrls() {
        assertEquals(
            "https://demo-tunnel.trycloudflare.com/v1/models",
            AiRouterClient.resolveModelsEndpoint("https://demo-tunnel.trycloudflare.com")
        )
        assertEquals(
            "http://52.141.18.29:20128/v1/models",
            AiRouterClient.resolveModelsEndpoint("http://52.141.18.29:20128/v1/")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            AiRouterClient.resolveModelsEndpoint("https://api.openai.com/v1/chat/completions")
        )
    }

    @Test
    fun normalizeBaseUrl_andCustomEndpointCheck() {
        assertNull(AiRouterClient.normalizeBaseUrl("ftp://invalid.example.com"))
        assertNull(AiRouterClient.normalizeBaseUrl("   "))
        assertEquals(
            "https://my-router.example.com",
            AiRouterClient.normalizeBaseUrl("  https://my-router.example.com/  ")
        )
        assertTrue(AiRouterClient.isCustomOpenAiEndpoint("https://my-router.example.com/v1"))
        assertFalse(AiRouterClient.isCustomOpenAiEndpoint("https://api.openai.com/v1/chat/completions"))
    }

    @Test
    fun maskKey_andParseModelIds_workCorrectly() {
        assertEquals("Kosong", AiRouterClient.maskKey("  "))
        assertEquals("sk-test...abcd", AiRouterClient.maskKey("sk-test1234567890abcd"))

        val models = AiRouterClient.parseModelIds(
            """{"object":"list","data":[{"id":"agy-codex-combo"},{"id":"gpt-4o-mini"}]}"""
        )
        assertEquals(listOf("agy-codex-combo", "gpt-4o-mini"), models)
    }
}
