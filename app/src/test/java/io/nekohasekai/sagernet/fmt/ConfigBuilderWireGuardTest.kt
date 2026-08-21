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
import org.junit.Assert.fail
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

    @Test
    fun selectorReferencesWireGuardEndpointMemberWithoutLegacyOutbound() {
        val options = topologyOptions()
        options.outbounds.add(0, buildSelectorOutbound(MAIN_TAG, listOf(MAIN_TAG, NEXT_TAG)))
        options.route.final_ = TAG_PROXY

        val config = finalizedTopology(options)
        val selector = outbound(config, TAG_PROXY)

        assertEquals(MAIN_TAG, selector.get("default").asString)
        assertEquals(listOf(MAIN_TAG, NEXT_TAG), selector
            .getAsJsonArray("outbounds").map { it.asString })
        assertTopologyReferencesResolve(config)
    }

    @Test
    fun urlTestTopologyUsesWireGuardEndpointAsMainTarget() {
        val config = finalizedTopology(topologyOptions())

        assertEquals(MAIN_TAG, config.getAsJsonObject("route").get("final").asString)
        assertTopologyReferencesResolve(config)
    }

    @Test
    fun applicationFacingWireGuardDetoursToNextOutboundInExistingChainOrder() {
        val options = topologyOptions()
        options.outbounds.single { it.tag == MAIN_TAG }.detourTo(NEXT_TAG)

        val config = finalizedTopology(options)
        assertEquals(NEXT_TAG, endpoint(config, MAIN_TAG).get("detour").asString)
        assertEquals(listOf(MAIN_TAG, NEXT_TAG), chainPath(config))
        assertTopologyReferencesResolve(config)
    }

    @Test
    fun egressFacingWireGuardIsReferencedByPreviousOutbound() {
        val options = topologyOptions()
        options.route.final_ = NEXT_TAG
        options.outbounds.single { it.tag == NEXT_TAG }.detourTo(MAIN_TAG)

        val config = finalizedTopology(options)
        assertEquals(MAIN_TAG, outbound(config, NEXT_TAG).get("detour").asString)
        assertFalse(endpoint(config, MAIN_TAG).has("detour"))
        assertEquals(listOf(NEXT_TAG, MAIN_TAG), chainPath(config))
        assertTopologyReferencesResolve(config)
    }

    @Test
    fun wireGuardWithListenPortRejectsPositionRequiringDetour() {
        val endpoint = wireGuardEndpoint(listenPort = 51820)

        try {
            endpoint.detourTo(NEXT_TAG)
            fail("Expected WireGuard listen_port and detour conflict")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(MAIN_TAG))
            assertTrue(error.message.orEmpty().contains(NEXT_TAG))
            assertTrue(error.message.orEmpty().contains("listen_port"))
            assertFalse(endpoint.asMap().containsKey("detour"))
        }
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

    private fun topologyOptions() = MyOptions().apply {
        endpoints = mutableListOf()
        route = RouteOptions().apply { final_ = MAIN_TAG }
        outbounds = mutableListOf(
            wireGuardEndpoint(),
            Outbound().apply {
                type = "socks"
                tag = NEXT_TAG
                _hack_config_map["server"] = "192.0.2.20"
                _hack_config_map["server_port"] = 1080
            },
            Outbound().apply {
                type = "direct"
                tag = TAG_DIRECT
            },
        )
    }

    private fun wireGuardEndpoint(listenPort: Int = 0) =
        buildSingBoxEndpointWireGuardBean(WireGuardBean().apply {
            initializeDefaultValues()
            serverAddress = "198.51.100.10"
            serverPort = 51820
            localAddress = "10.0.0.2/32, fd00::2/128"
            privateKey = TEST_PRIVATE_KEY
            peerPublicKey = TEST_PUBLIC_KEY
            this.listenPort = listenPort
        }).apply { tag = MAIN_TAG }

    private fun finalizedTopology(options: MyOptions) = gson.toJsonTree(
        finalizeRootConfig(options)
    ).asJsonObject.also { config ->
        val endpoints = config.getAsJsonArray("endpoints").map { it.asJsonObject }
        val outbounds = config.getAsJsonArray("outbounds").map { it.asJsonObject }
        assertEquals(1, endpoints.count { it.get("tag").asString == MAIN_TAG })
        assertFalse(outbounds.any { it.get("type").asString == "wireguard" })
    }

    private fun endpoint(config: com.google.gson.JsonObject, tag: String) =
        config.getAsJsonArray("endpoints")
            .map { it.asJsonObject }
            .single { it.get("tag").asString == tag }

    private fun outbound(config: com.google.gson.JsonObject, tag: String) =
        config.getAsJsonArray("outbounds")
            .map { it.asJsonObject }
            .single { it.get("tag").asString == tag }

    private fun assertTopologyReferencesResolve(config: com.google.gson.JsonObject) {
        val endpoints = config.getAsJsonArray("endpoints").map { it.asJsonObject }
        val outbounds = config.getAsJsonArray("outbounds").map { it.asJsonObject }
        val availableTags = (endpoints + outbounds).map { it.get("tag").asString }.toSet()
        val referencedTags = buildList {
            add(config.getAsJsonObject("route").get("final").asString)
            endpoints.mapNotNullTo(this) { it.get("detour")?.asString }
            outbounds.mapNotNullTo(this) { it.get("detour")?.asString }
            outbounds.filter { it.get("type").asString == "selector" }.forEach { selector ->
                add(selector.get("default").asString)
                selector.getAsJsonArray("outbounds").mapTo(this) { it.asString }
            }
        }

        assertTrue("Unresolved topology tags: ${referencedTags - availableTags}",
            availableTags.containsAll(referencedTags))
    }

    private fun chainPath(config: com.google.gson.JsonObject): List<String> {
        val nodes = (
            config.getAsJsonArray("endpoints").map { it.asJsonObject } +
                config.getAsJsonArray("outbounds").map { it.asJsonObject }
            ).associateBy { it.get("tag").asString }
        val path = mutableListOf<String>()
        var currentTag: String? = config.getAsJsonObject("route").get("final").asString
        while (currentTag != null) {
            check(path.size <= nodes.size) { "Cycle in topology path: $path" }
            path += currentTag
            currentTag = nodes.getValue(currentTag).get("detour")?.asString
        }
        return path
    }

    private fun endpointOverrideJson(name: String): String {
        return """{"endpoints":[{"type":"wireguard","tag":"$MAIN_TAG","name":"$name"}]}"""
    }

    private companion object {
        const val MAIN_TAG = "wireguard-main"
        const val NEXT_TAG = "next-hop"
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
