package com.virusvpn.app

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firebase (Firestore) sync for Virus VPN.
 * Collections (see admin-panel/firebase-panel.html):
 *   servers  — name, country, countryName, tunnelType, connectFrom, customPayload, link, free, protocol
 *   networks — name, subtitle, mode, icon, bugHost, frontQuery, backQuery, payload
 *   users    — username, expiry (yyyy-MM-dd), premium, active, gbUsed
 *   settings/app — configVersion, announcement, telegramUrl
 * Passwords are never stored in Firestore — they live in Firebase Authentication.
 */
object FirebaseSync {

    data class PanelData(
        val servers: List<VpnServer>,
        val networks: List<PanelNetwork>,
        val configVersion: String,
        val announcement: String,
        val telegramUrl: String,
    )

    sealed interface VerifyResult {
        data class Ok(val expiry: String, val email: String) : VerifyResult
        object NotFound : VerifyResult
        object Expired : VerifyResult
        /** Firebase unreachable / not configured — caller decides (do not block offline). */
        object Offline : VerifyResult
    }

    suspend fun fetchPanel(): PanelData? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val serversSnap = db.collection("servers").get().await()
            val networksSnap = db.collection("networks").get().await()
            val settingsSnap = db.collection("settings").document("app").get().await()

            val servers = serversSnap.documents.mapNotNull { d ->
                val link = d.getString("link").orEmpty()
                val base = if (link.isNotBlank()) ServerStore.parseVless(link) else null
                if (base != null) {
                    base.copy(
                        name = d.getString("name") ?: base.name,
                        countryCode = d.getString("country") ?: base.countryCode,
                        countryName = d.getString("countryName") ?: base.countryName,
                        isFree = d.getBoolean("free") ?: base.isFree,
                        protocol = d.getString("protocol")?.ifBlank { base.protocol } ?: base.protocol,
                    )
                } else null
            }
            val networks = networksSnap.documents.mapNotNull { d ->
                val name = d.getString("name").orEmpty()
                if (name.isBlank()) null else PanelNetwork(
                    name = name,
                    subtitle = d.getString("subtitle") ?: "",
                    mode = d.getString("mode") ?: "",
                    icon = d.getString("icon") ?: "",
                )
            }
            val settings = settingsSnap.data ?: emptyMap<String, Any>()
            PanelData(
                servers = servers,
                networks = networks,
                configVersion = (settings["configVersion"] as? String) ?: "1.0",
                announcement = (settings["announcement"] as? String) ?: "",
                telegramUrl = (settings["telegramUrl"] as? String) ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Check a username against Firestore users: exists? expired? Passwords are NOT checked here — they are in Firebase Auth. */
    suspend fun verifyUser(username: String): VerifyResult {
        return try {
            val db = FirebaseFirestore.getInstance()
            val snap = db.collection("users").whereEqualTo("username", username).limit(1).get().await()
            val doc = snap.documents.firstOrNull() ?: return VerifyResult.NotFound
            val expiry = doc.getString("expiry") ?: ""
            if (expiry.isNotBlank()) {
                try {
                    if (LocalDate.parse(expiry).isBefore(LocalDate.now())) return VerifyResult.Expired
                } catch (_: Exception) {
                    // Unparseable expiry — do not block on a bad date format.
                }
            }
            VerifyResult.Ok(expiry, doc.getString("email") ?: "")
        } catch (_: Exception) {
            VerifyResult.Offline
        }
    }
}
