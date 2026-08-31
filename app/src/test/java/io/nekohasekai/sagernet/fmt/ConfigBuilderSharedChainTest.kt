package io.nekohasekai.sagernet.fmt

import com.google.gson.JsonObject
import moe.matsuri.nb4a.SingBoxOptions.MyOptions
import moe.matsuri.nb4a.SingBoxOptions.Outbound
import moe.matsuri.nb4a.SingBoxOptions.RouteOptions
import moe.matsuri.nb4a.SingBoxOptions.Rule_DefaultOptions
import moe.matsuri.nb4a.utils.JavaUtil.gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderSharedChainTest {

    @Test
    fun sharedRuleOutboundBuiltBeforeContainingChainUsesItsFinalReadableTag() {
        val topology = TestTopology()
        val mainTag = topology.buildSingle(MAIN_ID, MAIN_TAG)
        val sharedTag = topology.buildSingle(SHARED_ID, SHARED_TAG)
        val chainTag = topology.buildChain(CHAIN_ID, CHAIN_TAG, SHARED_ID)

        val config = topology.finish(
            mainTag = mainTag,
            selectorMembers = listOf(mainTag, sharedTag, chainTag),
            ruleTargets = listOf(sharedTag, chainTag),
        )

        assertEquals(SHARED_TAG, node(config, chainTag).get("detour").asString)
        assertFalse(availableTags(config).contains("g-$SHARED_ID"))
        assertEquals(SHARED_TAG, topology.profileTagMap.getValue(SHARED_ID))
        assertEquals(CHAIN_TAG, topology.profileTagMap.getValue(CHAIN_ID))
        assertEquals(listOf(SHARED_ID), topology.trafficMap.getValue(SHARED_TAG))
        assertEquals(listOf(CHAIN_ID, SHARED_ID), topology.trafficMap.getValue(CHAIN_TAG))
        assertTopologyReferencesResolve(config)
    }

    @Test
    fun mainChainBuiltBeforeSharedRuleOutboundKeepsResolvableGlobalHop() {
        val topology = TestTopology()
        val chainTag = topology.buildChain(CHAIN_ID, CHAIN_TAG, SHARED_ID)
        val sharedTag = topology.buildSingle(SHARED_ID, SHARED_TAG)

        val config = topology.finish(
            mainTag = chainTag,
            selectorMembers = listOf(chainTag, sharedTag),
            ruleTargets = listOf(sharedTag),
        )

        assertEquals("g-$SHARED_ID", node(config, chainTag).get("detour").asString)
        assertEquals("g-$SHARED_ID", sharedTag)
        assertEquals(chainTag, config.getAsJsonObject("route").get("final").asString)
        assertEquals(listOf(CHAIN_ID, SHARED_ID), topology.trafficMap.getValue(chainTag))
        assertEquals(listOf(SHARED_ID), topology.trafficMap.getValue(sharedTag))
        assertTopologyReferencesResolve(config)
    }

    private class TestTopology {
        private val globalOutbounds = linkedMapOf<Long, String>()
        private val options = MyOptions().apply {
            endpoints = mutableListOf()
            outbounds = mutableListOf()
            route = RouteOptions().apply { rules = mutableListOf() }
        }
        val profileTagMap = linkedMapOf<Long, String>()
        val trafficMap = linkedMapOf<String, List<Long>>()

        fun buildSingle(profileId: Long, readableTag: String): String {
            val resolved = addHop(profileId, readableTag, needGlobal = true)
            profileTagMap[profileId] = resolved.tag
            trafficMap[resolved.tag] = listOf(profileId)
            return resolved.tag
        }

        fun buildChain(chainId: Long, readableTag: String, sharedId: Long): String {
            val first = addHop(chainId, readableTag, needGlobal = false)
            val shared = addHop(sharedId, "g-$sharedId", needGlobal = true)
            nodeOption(first.tag).detourTo(shared.tag)
            profileTagMap[chainId] = first.tag
            trafficMap[first.tag] = listOf(chainId, sharedId)
            return first.tag
        }

        fun finish(
            mainTag: String,
            selectorMembers: List<String>,
            ruleTargets: List<String>,
        ): JsonObject {
            options.outbounds.add(buildSelectorOutbound(mainTag, selectorMembers))
            ruleTargets.forEach { target ->
                options.route.rules.add(Rule_DefaultOptions().apply {
                    domain = listOf("full:$target.example")
                    outbound = target
                })
            }
            options.route.final_ = mainTag
            return gson.toJsonTree(finalizeRootConfig(options)).asJsonObject
        }

        private fun addHop(
            profileId: Long,
            proposedTag: String,
            needGlobal: Boolean,
        ): ChainHopTag {
            val resolved = resolveChainHopTag(
                profileId,
                proposedTag,
                needGlobal,
                globalOutbounds,
            )
            if (!resolved.reused) {
                options.outbounds.add(Outbound().apply {
                    type = "socks"
                    tag = resolved.tag
                    _hack_config_map["server"] = "192.0.2.$profileId"
                    _hack_config_map["server_port"] = 1080
                })
            }
            return resolved
        }

        private fun nodeOption(tag: String) = options.outbounds.single {
            it.asMap()["tag"] == tag
        }
    }

    private fun assertTopologyReferencesResolve(config: JsonObject) {
        val available = availableTags(config)
        val references = buildList {
            add(config.getAsJsonObject("route").get("final").asString)
            config.getAsJsonArray("endpoints").forEach { endpoint ->
                endpoint.asJsonObject.get("detour")?.asString?.let(::add)
            }
            config.getAsJsonArray("outbounds").forEach { outboundElement ->
                val outbound = outboundElement.asJsonObject
                outbound.get("detour")?.asString?.let(::add)
                if (outbound.get("type").asString == "selector") {
                    add(outbound.get("default").asString)
                    outbound.getAsJsonArray("outbounds").forEach { add(it.asString) }
                }
            }
            config.getAsJsonObject("route").getAsJsonArray("rules").forEach { rule ->
                rule.asJsonObject.get("outbound")?.asString?.let(::add)
            }
        }

        assertTrue("Unresolved topology tags: ${references - available}",
            available.containsAll(references))
        assertEquals(available.size, availableTagsInOrder(config).size)
    }

    private fun node(config: JsonObject, tag: String) =
        (config.getAsJsonArray("endpoints") + config.getAsJsonArray("outbounds"))
            .map { it.asJsonObject }
            .single { it.get("tag").asString == tag }

    private fun availableTags(config: JsonObject) = availableTagsInOrder(config).toSet()

    private fun availableTagsInOrder(config: JsonObject) =
        (config.getAsJsonArray("endpoints") + config.getAsJsonArray("outbounds"))
            .map { it.asJsonObject.get("tag").asString }

    private companion object {
        const val MAIN_ID = 2913L
        const val SHARED_ID = 3045L
        const val CHAIN_ID = 3046L
        const val MAIN_TAG = "Main node"
        const val SHARED_TAG = "Shared node"
        const val CHAIN_TAG = "Shared chain"
    }
}
