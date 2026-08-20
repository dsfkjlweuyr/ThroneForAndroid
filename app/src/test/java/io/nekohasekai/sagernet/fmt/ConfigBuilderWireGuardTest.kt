package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.fmt.wireguard.buildSingBoxEndpointWireGuardBean
import moe.matsuri.nb4a.SingBoxOptions.MyOptions
import moe.matsuri.nb4a.SingBoxOptions.Outbound
import moe.matsuri.nb4a.SingBoxOptions.RouteOptions
import moe.matsuri.nb4a.utils.JavaUtil.gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderWireGuardTest {

    @Test
    fun singleWireGuardMainUsesEndpointTagAndCoexistsWithCustomEndpoint() {
        val options = baseOptions()
        val config = gson.toJsonTree(
            finalizeRootConfig(options, globalCustomConfig = CUSTOM_ENDPOINT_JSON)
        ).asJsonObject

        val endpoints = config.getAsJsonArray("endpoints")
        assertEquals(2, endpoints.size())

        val generated = endpoints
            .map { it.asJsonObject }
            .single { it.get("tag").asString == MAIN_TAG }
        assertEquals("wireguard", generated.get("type").asString)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), generated
            .getAsJsonArray("address").map { it.asString })
        assertTrue(TEST_PRIVATE_KEY == generated.get("private_key").asString)
        assertEquals(TEST_PUBLIC_KEY, generated.getAsJsonArray("peers")
            .single().asJsonObject.get("public_key").asString)

        val custom = endpoints
            .map { it.asJsonObject }
            .single { it.get("tag").asString == CUSTOM_TAG }
        assertEquals("wireguard", custom.get("type").asString)

        val outbounds = config.getAsJsonArray("outbounds")
        assertTrue(outbounds.any { it.asJsonObject.get("tag").asString == TAG_DIRECT })
        assertFalse(outbounds.any { it.asJsonObject.get("type").asString == "wireguard" })
        assertEquals(MAIN_TAG, config.getAsJsonObject("route").get("final").asString)
    }

    @Test
    fun profileCustomEndpointWinsSameTagAfterGlobalConfig() {
        val config = gson.toJsonTree(
            finalizeRootConfig(
                baseOptions(),
                globalCustomConfig = endpointOverrideJson("global"),
                profileCustomConfig = endpointOverrideJson("profile"),
            )
        ).asJsonObject

        val matching = config.getAsJsonArray("endpoints")
            .map { it.asJsonObject }
            .filter { it.get("tag").asString == MAIN_TAG }
        assertEquals(1, matching.size)
        assertEquals("profile", matching.single().get("name").asString)
        assertNotNull(config.getAsJsonObject("route"))
        assertEquals(MAIN_TAG, config.getAsJsonObject("route").get("final").asString)
    }

    private fun baseOptions() = MyOptions().apply {
        endpoints = mutableListOf()
        route = RouteOptions().apply { final_ = MAIN_TAG }
        outbounds = mutableListOf(
            buildSingBoxEndpointWireGuardBean(WireGuardBean().apply {
                initializeDefaultValues()
                serverAddress = "198.51.100.10"
                serverPort = 51820
                localAddress = "10.0.0.2/32, fd00::2/128"
                privateKey = TEST_PRIVATE_KEY
                peerPublicKey = TEST_PUBLIC_KEY
                mtu = 1380
            }).apply { tag = MAIN_TAG },
            Outbound().apply {
                type = "direct"
                tag = TAG_DIRECT
            },
        )
    }

    private fun endpointOverrideJson(name: String): String {
        return """{"endpoints":[{"type":"wireguard","tag":"$MAIN_TAG","name":"$name"}]}"""
    }

    private companion object {
        const val MAIN_TAG = "wireguard-main"
        const val CUSTOM_TAG = "custom-wireguard"
        const val TEST_PRIVATE_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val TEST_PUBLIC_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        const val CUSTOM_ENDPOINT_JSON = """
            {
              "endpoints": [
                {
                  "type": "wireguard",
                  "tag": "$CUSTOM_TAG",
                  "address": ["10.0.1.2/32"],
                  "private_key": "$TEST_PRIVATE_KEY",
                  "peers": [
                    {
                      "address": "203.0.113.10",
                      "port": 51820,
                      "public_key": "$TEST_PUBLIC_KEY",
                      "allowed_ips": ["0.0.0.0/0", "::/0"]
                    }
                  ]
                }
              ]
            }
        """
    }
}
