package com.virusvpn.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a standard Xray/V2Ray core JSON config from one saved VLESS server.
 * No geoip rules are used (no geoip.dat is bundled in v1).
 */
object V2RayConfig {
    fun build(server: VpnServer): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))

        val tunInbound = JSONObject()
        tunInbound.put("tag", "tun")
        tunInbound.put("protocol", "tun")
        val tunSettings = JSONObject()
        tunSettings.put("name", "xray0")
        tunSettings.put("MTU", 1500)
        tunSettings.put("userLevel", 8)
        tunInbound.put("settings", tunSettings)
        val sniffing = JSONObject()
        sniffing.put("enabled", true)
        sniffing.put("destOverride", JSONArray(listOf("http", "tls", "quic")))
        tunInbound.put("sniffing", sniffing)
        config.put("inbounds", JSONArray(listOf(tunInbound)))

        val proxy = JSONObject()
        proxy.put("tag", "proxy")
        proxy.put("protocol", "vless")
        val vnext = JSONObject()
        vnext.put("address", server.address)
        vnext.put("port", server.port)
        val user = JSONObject()
        user.put("id", server.uuid)
        user.put("encryption", "none")
        if (server.flow.isNotBlank()) user.put("flow", server.flow)
        user.put("level", 8)
        vnext.put("users", JSONArray(listOf(user)))
        val settings = JSONObject()
        settings.put("vnext", JSONArray(listOf(vnext)))
        proxy.put("settings", settings)

        val stream = JSONObject()
        stream.put("network", server.network.ifBlank { "tcp" })
        stream.put("security", server.security.ifBlank { "tls" })
        if (server.security == "tls") {
            val tls = JSONObject()
            if (server.sni.isNotBlank()) tls.put("serverName", server.sni)
            if (server.fingerprint.isNotBlank()) tls.put("fingerprint", server.fingerprint)
            tls.put("allowInsecure", false)
            stream.put("tlsSettings", tls)
        }
        if (server.security == "reality") {
            val reality = JSONObject()
            if (server.sni.isNotBlank()) reality.put("serverName", server.sni)
            if (server.fingerprint.isNotBlank()) reality.put("fingerprint", server.fingerprint)
            if (server.publicKey.isNotBlank()) reality.put("publicKey", server.publicKey)
            if (server.shortId.isNotBlank()) reality.put("shortId", server.shortId)
            stream.put("realitySettings", reality)
        }
        if (server.network == "ws") {
            val ws = JSONObject()
            ws.put("path", server.path.ifBlank { "/" })
            if (server.host.isNotBlank()) {
                ws.put("headers", JSONObject().put("Host", server.host))
            }
            stream.put("wsSettings", ws)
        }
        if (server.network == "grpc") {
            val grpc = JSONObject()
            grpc.put("serviceName", server.path.ifBlank { "" })
            stream.put("grpcSettings", grpc)
        }
        proxy.put("streamSettings", stream)
        proxy.put("mux", JSONObject().put("enabled", false))

        val direct = JSONObject()
        direct.put("tag", "direct")
        direct.put("protocol", "freedom")
        val block = JSONObject()
        block.put("tag", "block")
        block.put("protocol", "blackhole")
        block.put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
        config.put("outbounds", JSONArray(listOf(proxy, direct, block)))

        val rule = JSONObject()
        rule.put("type", "field")
        rule.put("outboundTag", "proxy")
        rule.put("network", "tcp,udp")
        val routing = JSONObject()
        routing.put("domainStrategy", "AsIs")
        routing.put("rules", JSONArray(listOf(rule)))
        config.put("routing", routing)

        val dns = JSONObject()
        dns.put("servers", JSONArray(listOf("1.1.1.1")))
        config.put("dns", dns)

        return config.toString()
    }
}
