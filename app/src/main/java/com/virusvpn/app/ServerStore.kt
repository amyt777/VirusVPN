package com.virusvpn.app

import android.content.Context
import android.content.SharedPreferences
import java.net.URLDecoder
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Original, small storage for Virus VPN.
 * No server, panel, username or password is bundled in the app.
 * Servers are only added by the user (a VLESS link they paste),
 * and the username/password fields are stored locally only —
 * they are not sent anywhere until a panel exists (VPS later).
 */
data class VpnServer(
    val id: String,
    val name: String,
    val uuid: String,
    val address: String,
    val port: Int,
    val security: String,
    val network: String,
    val sni: String,
    val flow: String,
    val path: String,
    val host: String,
    val publicKey: String,
    val shortId: String,
    val fingerprint: String,
    val rawLink: String,
    val countryCode: String = "",
    val countryName: String = "",
    val isFree: Boolean = true,
    val protocol: String = "VLESS",
)

/** Custom Tweaks as saved by the user. These are applied to the VLESS config on START. */
data class TweakSettings(
    val mode: String = "V2ray",
    val connection: String = "Direct Connection",
    val type: String = "CF",
    val tweakName: String = "",
    val tweakInfo: String = "",
    val frontQuery: String = "",
    val backQuery: String = "",
    val payload: String = "",
    val useDefaultProxy: Boolean = true,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val protocol: String = "VLESS",
)

/** One panel update: a config version, an optional centred announcement, and servers. */
data class PanelUpdate(
    val configVersion: String,
    val announcement: String,
    val servers: List<VpnServer>,
    val networks: List<PanelNetwork> = emptyList(),
    val telegramUrl: String = "",
)

/** A network from the admin panel: name, subtitle and the top-centre mode
 * (UDP / SSL/TLS / INJECT) it makes the app show. No preset networks in the app. */
data class PanelNetwork(
    val name: String,
    val subtitle: String = "",
    val mode: String = "",
    val icon: String = "",
)

fun countryFlag(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (code.length != 2) return "🌐"
    val base = 0x1F1E6
    val first = base + (code[0].code - 'A'.code)
    val second = base + (code[1].code - 'A'.code)
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

object ServerStore {
    private const val PREFS = "virus_vpn"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getServers(context: Context): List<VpnServer> {
        val raw = prefs(context).getString("servers", "[]") ?: "[]"
        val out = mutableListOf<VpnServer>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    VpnServer(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        uuid = o.optString("uuid"),
                        address = o.optString("address"),
                        port = o.optInt("port", 443),
                        security = o.optString("security"),
                        network = o.optString("network"),
                        sni = o.optString("sni"),
                        flow = o.optString("flow"),
                        path = o.optString("path"),
                        host = o.optString("host"),
                        publicKey = o.optString("publicKey"),
                        shortId = o.optString("shortId"),
                        fingerprint = o.optString("fingerprint"),
                        rawLink = o.optString("rawLink"),
                        countryCode = o.optString("countryCode"),
                        countryName = o.optString("countryName"),
                        isFree = o.optBoolean("isFree", true),
                        protocol = o.optString("protocol", "VLESS").ifBlank { "VLESS" },
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveServers(context: Context, servers: List<VpnServer>) {
        val arr = JSONArray()
        for (s in servers) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("uuid", s.uuid)
            o.put("address", s.address)
            o.put("port", s.port)
            o.put("security", s.security)
            o.put("network", s.network)
            o.put("sni", s.sni)
            o.put("flow", s.flow)
            o.put("path", s.path)
            o.put("host", s.host)
            o.put("publicKey", s.publicKey)
            o.put("shortId", s.shortId)
            o.put("fingerprint", s.fingerprint)
            o.put("rawLink", s.rawLink)
            o.put("countryCode", s.countryCode)
            o.put("countryName", s.countryName)
            o.put("isFree", s.isFree)
            o.put("protocol", s.protocol)
            arr.put(o)
        }
        prefs(context).edit().putString("servers", arr.toString()).apply()
    }

    fun getNetworks(context: Context): List<PanelNetwork> {
        val raw = prefs(context).getString("panel_networks", "[]") ?: "[]"
        val out = mutableListOf<PanelNetwork>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    PanelNetwork(
                        name = o.optString("name"),
                        subtitle = o.optString("subtitle"),
                        mode = o.optString("mode"),
                        icon = o.optString("icon"),
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveNetworks(context: Context, networks: List<PanelNetwork>) {
        val arr = JSONArray()
        for (n in networks) {
            val o = JSONObject()
            o.put("name", n.name)
            o.put("subtitle", n.subtitle)
            o.put("mode", n.mode)
            o.put("icon", n.icon)
            arr.put(o)
        }
        prefs(context).edit().putString("panel_networks", arr.toString()).apply()
    }

    fun getSelectedId(context: Context): String? =
        prefs(context).getString("selected_id", null)

    fun setSelectedId(context: Context, id: String?) {
        prefs(context).edit().putString("selected_id", id).apply()
    }

    fun getString(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun getDeviceId(context: Context): String {
        val p = prefs(context)
        var id = p.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            p.edit().putString("device_id", id).apply()
        }
        return id
    }

    /** Parse a VLESS link. Returns null when the link is not a usable VLESS link. */
    fun parseVless(link: String): VpnServer? {
        try {
            val text = link.trim()
            if (!text.startsWith("vless://")) return null
            val withoutScheme = text.removePrefix("vless://")
            val namePart = withoutScheme.substringAfter("#", "")
            val mainPart = withoutScheme.substringBefore("#")
            val queryPart = mainPart.substringAfter("?", "")
            val beforeQuery = mainPart.substringBefore("?")
            val uuid = beforeQuery.substringBefore("@")
            val hostPort = beforeQuery.substringAfter("@")
            if (uuid.isBlank() || hostPort.isBlank()) return null
            val address = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
            if (address.isBlank()) return null
            val params = mutableMapOf<String, String>()
            if (queryPart.isNotBlank()) {
                for (pair in queryPart.split("&")) {
                    val k = pair.substringBefore("=")
                    val v = pair.substringAfter("=", "")
                    if (k.isNotBlank()) {
                        params[k] = URLDecoder.decode(v, "UTF-8")
                    }
                }
            }
            val name = if (namePart.isNotBlank()) {
                URLDecoder.decode(namePart, "UTF-8")
            } else {
                address
            }
            return VpnServer(
                id = UUID.randomUUID().toString(),
                name = name,
                uuid = uuid,
                address = address,
                port = port,
                security = params["security"] ?: "tls",
                network = params["type"] ?: "tcp",
                sni = params["sni"] ?: "",
                flow = params["flow"] ?: "",
                path = params["path"] ?: "/",
                host = params["host"] ?: "",
                publicKey = params["pbk"] ?: "",
                shortId = params["sid"] ?: "",
                fingerprint = params["fp"] ?: "",
                rawLink = text,
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun getTweaks(context: Context): TweakSettings {
        val p = prefs(context)
        return TweakSettings(
            mode = p.getString("tweak_mode", "V2ray") ?: "V2ray",
            connection = p.getString("tweak_connection", "Direct Connection") ?: "Direct Connection",
            type = p.getString("tweak_type", "CF") ?: "CF",
            tweakName = p.getString("tweak_name", "") ?: "",
            tweakInfo = p.getString("tweak_info", "") ?: "",
            frontQuery = p.getString("tweak_front", "") ?: "",
            backQuery = p.getString("tweak_back", "") ?: "",
            payload = p.getString("tweak_payload", "") ?: "",
            useDefaultProxy = p.getBoolean("tweak_default_proxy", true),
            proxyHost = p.getString("tweak_proxy_host", "") ?: "",
            proxyPort = p.getString("tweak_proxy_port", "") ?: "",
            protocol = p.getString("tweak_protocol", "VLESS") ?: "VLESS",
        )
    }

    fun saveTweaks(context: Context, t: TweakSettings) {
        prefs(context).edit()
            .putString("tweak_mode", t.mode)
            .putString("tweak_connection", t.connection)
            .putString("tweak_type", t.type)
            .putString("tweak_name", t.tweakName)
            .putString("tweak_info", t.tweakInfo)
            .putString("tweak_front", t.frontQuery)
            .putString("tweak_back", t.backQuery)
            .putString("tweak_payload", t.payload)
            .putBoolean("tweak_default_proxy", t.useDefaultProxy)
            .putString("tweak_proxy_host", t.proxyHost)
            .putString("tweak_proxy_port", t.proxyPort)
            .putString("tweak_protocol", t.protocol)
            .apply()
    }

    /**
     * Parse an admin-panel update. Accepted shapes:
     * {"configVersion":"2.0","announcement":"...","servers":[{...}]}
     * Each server: {"name","country":"BD","countryName":"Bangladesh","free":true,
     * "protocol":"VLESS","link":"vless://..."} — or raw address/uuid/port fields.
     * Also accepts a plain list of vless:// links, one per line.
     */
    fun parsePanel(jsonText: String): PanelUpdate? {
        try {
            val text = jsonText.trim()
            if (text.startsWith("vless://")) {
                val servers = text.lines().mapNotNull { parseVless(it) }
                if (servers.isEmpty()) return null
                return PanelUpdate("1.0", "", servers)
            }
            val root = JSONObject(text)
            val version = root.optString("configVersion", root.optString("version", "1.0"))
            val announcement = root.optString("announcement", root.optString("notice", ""))
            val telegramUrl = root.optString("telegramUrl", root.optString("telegram", ""))
            val networks = mutableListOf<PanelNetwork>()
            val netArr = root.optJSONArray("networks")
            if (netArr != null) {
                for (i in 0 until netArr.length()) {
                    val o = netArr.getJSONObject(i)
                    networks.add(
                        PanelNetwork(
                            name = o.optString("name"),
                            subtitle = o.optString("subtitle", o.optString("info", "")),
                            mode = o.optString("mode", o.optString("protocol", "")),
                            icon = o.optString("icon", o.optString("logo", "")),
                        )
                    )
                }
            }
            val arr = root.optJSONArray("servers") ?: return null
            val servers = mutableListOf<VpnServer>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val link = o.optString("link", o.optString("vless", ""))
                val base = if (link.isNotBlank()) parseVless(link) else null
                if (base != null) {
                    servers.add(
                        base.copy(
                            name = o.optString("name", base.name),
                            countryCode = o.optString("country", o.optString("countryCode", base.countryCode)),
                            countryName = o.optString("countryName", base.countryName),
                            isFree = o.optBoolean("free", o.optBoolean("isFree", base.isFree)),
                            protocol = o.optString("protocol", base.protocol).ifBlank { base.protocol },
                        )
                    )
                } else if (o.optString("address").isNotBlank() && o.optString("uuid").isNotBlank()) {
                    servers.add(
                        VpnServer(
                            id = UUID.randomUUID().toString(),
                            name = o.optString("name", o.optString("address")),
                            uuid = o.optString("uuid"),
                            address = o.optString("address"),
                            port = o.optInt("port", 443),
                            security = o.optString("security", "reality"),
                            network = o.optString("type", o.optString("network", "tcp")),
                            sni = o.optString("sni", ""),
                            flow = o.optString("flow", ""),
                            path = o.optString("path", "/"),
                            host = o.optString("host", ""),
                            publicKey = o.optString("pbk", o.optString("publicKey", "")),
                            shortId = o.optString("sid", o.optString("shortId", "")),
                            fingerprint = o.optString("fp", o.optString("fingerprint", "")),
                            rawLink = "",
                            countryCode = o.optString("country", o.optString("countryCode", "")),
                            countryName = o.optString("countryName", ""),
                            isFree = o.optBoolean("free", o.optBoolean("isFree", true)),
                            protocol = o.optString("protocol", "VLESS").ifBlank { "VLESS" },
                        )
                    )
                }
            }
            return PanelUpdate(version.ifBlank { "1.0" }, announcement, servers, networks, telegramUrl)
        } catch (_: Exception) {
            return null
        }
    }
}
