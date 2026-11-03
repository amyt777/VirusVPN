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
)

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
            arr.put(o)
        }
        prefs(context).edit().putString("servers", arr.toString()).apply()
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
}
