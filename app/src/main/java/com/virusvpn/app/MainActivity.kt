package com.virusvpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF00A651)
private val DarkGreen = Color(0xFF007A3D)
private val LightGreen = Color(0xFFBDE8CD)
private val Pink = Color(0xFFEAABAD)
private val Red = Color(0xFFE53935)
private val PremiumGold = Color(0xFFFFC107)

// Built-in admin-panel update URL, hidden inside the app — never shown in the UI
// and never typed by the user. The admin sets this at build time once the panel
// exists. Empty until then, so UPDATE reports "not configured yet" honestly.
private const val PANEL_UPDATE_URL = ""

// Built-in default server slot: one server shipped inside the app, like other
// VPN apps. The admin fills this VLESS link at build time once a real VPS
// server exists. Empty until then — no fake server is invented here.
private const val BUILTIN_SERVER_LINK = ""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VirusHomeScreen(onExit = { finish() }) }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
        else -> "%.2f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
fun VirusHomeScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf(ServerStore.getServers(context)) }
    var selectedId by remember { mutableStateOf(ServerStore.getSelectedId(context)) }
    var isRunning by remember { mutableStateOf(VirusVpnService.isRunning) }
    var downBps by remember { mutableStateOf(0L) }
    var upBps by remember { mutableStateOf(0L) }
    var startTime by remember { mutableStateOf(0L) }
    var durationSec by remember { mutableStateOf(0L) }
    val speedHistory = remember { mutableStateListOf<Float>() }
    var configVersion by remember { mutableStateOf(ServerStore.getString(context, "panel_config_version", "1.0")) }
    var networks by remember { mutableStateOf(ServerStore.getNetworks(context)) }
    var telegramUrl by remember { mutableStateOf(ServerStore.getString(context, "panel_telegram_url")) }
    var username by rememberSaveable { mutableStateOf(ServerStore.getString(context, "username")) }
    var password by rememberSaveable { mutableStateOf(ServerStore.getString(context, "password")) }
    var passwordVisible by remember { mutableStateOf(false) }
    var networkChoice by rememberSaveable { mutableStateOf(ServerStore.getString(context, "network_choice", "")) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showTweaksDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var announcement by remember { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf("") }

    val selectedServer = servers.firstOrNull { it.id == selectedId }
    val selectedNetwork = networks.firstOrNull { it.name == networkChoice } ?: networks.firstOrNull()
    val topMode = selectedNetwork?.mode?.ifBlank { null }
        ?: when {
            networkChoice.contains("UDP", ignoreCase = true) -> "UDP"
            networkChoice.contains("SSL", ignoreCase = true) || networkChoice.contains("TLS", ignoreCase = true) -> "SSL/TLS"
            networkChoice.contains("INJECT", ignoreCase = true) -> "INJECT"
            else -> "—"
        }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == VirusVpnService.BROADCAST_STATUS) {
                    isRunning = intent.getBooleanExtra(VirusVpnService.EXTRA_RUNNING, false)
                    if (isRunning) {
                        startTime = intent.getLongExtra(VirusVpnService.EXTRA_START_TIME, startTime)
                        if (intent.hasExtra(VirusVpnService.EXTRA_DOWN)) {
                            downBps = intent.getLongExtra(VirusVpnService.EXTRA_DOWN, 0L)
                            upBps = intent.getLongExtra(VirusVpnService.EXTRA_UP, 0L)
                            speedHistory.add(downBps / 1024f)
                            if (speedHistory.size > 40) speedHistory.removeAt(0)
                        }
                    } else {
                        downBps = 0L
                        upBps = 0L
                        durationSec = 0L
                        val err = intent.getStringExtra(VirusVpnService.EXTRA_ERROR)
                        if (!err.isNullOrBlank()) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val filter = IntentFilter(VirusVpnService.BROADCAST_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(isRunning, startTime) {
        while (isRunning && startTime > 0) {
            durationSec = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) startVpnService(context)
    }

    fun toggleService() {
        if (isRunning) {
            val intent = Intent(context, VirusVpnService::class.java)
            intent.action = VirusVpnService.ACTION_STOP
            context.startService(intent)
            isRunning = false
        } else {
            if (selectedServer == null && servers.isEmpty()) {
                Toast.makeText(context, "No server yet — press UPDATES to fetch servers from your admin panel", Toast.LENGTH_LONG).show()
                showUpdateDialog = true
                return
            }
            val prepare = VpnService.prepare(context)
            if (prepare == null) startVpnService(context) else vpnLauncher.launch(prepare)
        }
    }

    fun refreshServers() {
        servers = ServerStore.getServers(context)
        selectedId = ServerStore.getSelectedId(context)
        configVersion = ServerStore.getString(context, "panel_config_version", "1.0")
        networks = ServerStore.getNetworks(context)
        telegramUrl = ServerStore.getString(context, "panel_telegram_url")
        if (networkChoice.isBlank()) {
            networkChoice = networks.firstOrNull()?.name ?: ""
        }
    }

    // Built-in default server: if the admin filled BUILTIN_SERVER_LINK at build
    // time and no panel servers exist yet, ship that one server inside the app.
    LaunchedEffect(Unit) {
        if (servers.isEmpty() && BUILTIN_SERVER_LINK.isNotBlank()) {
            ServerStore.parseVless(BUILTIN_SERVER_LINK)?.let { builtin ->
                ServerStore.saveServers(context, listOf(builtin))
                ServerStore.setSelectedId(context, builtin.id)
                refreshServers()
            }
        }
    }

    fun fetchPanel() {
        if (PANEL_UPDATE_URL.isBlank()) {
            updateStatus = "Panel update is not configured in this build yet — the admin sets the built-in panel URL once the panel exists."
            return
        }
        updateStatus = "Updating..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(PANEL_UPDATE_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.requestMethod = "GET"
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    ServerStore.parsePanel(text)
                } catch (e: Exception) {
                    null
                }
            }
            if (result == null || (result.servers.isEmpty() && result.networks.isEmpty())) {
                updateStatus = "Update failed. Check internet and the panel, then try again."
            } else {
                if (result.servers.isNotEmpty()) {
                    ServerStore.saveServers(context, result.servers)
                    ServerStore.setSelectedId(context, result.servers.firstOrNull()?.id)
                }
                if (result.networks.isNotEmpty()) {
                    ServerStore.saveNetworks(context, result.networks)
                    ServerStore.setString(context, "network_choice", result.networks.first().name)
                }
                if (result.telegramUrl.isNotBlank()) {
                    ServerStore.setString(context, "panel_telegram_url", result.telegramUrl)
                }
                ServerStore.setString(context, "panel_config_version", result.configVersion)
                refreshServers()
                updateStatus = "Updated from panel: ${result.servers.size} server(s), ${result.networks.size} network(s)."
                if (result.announcement.isNotBlank()) announcement = result.announcement
            }
        }
    }

    // Glow pulse while connected.
    val glowTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by glowTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale",
    )
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.55f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color.White, Color.White))), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            // Premium header: gradient band with glowing logo when connected.
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Green, DarkGreen)))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isRunning) {
                            Box(modifier = Modifier.size((110 * glowScale).dp).clip(CircleShape).background(Green.copy(alpha = glowAlpha)))
                            Box(modifier = Modifier.size((100 * glowScale).dp).clip(CircleShape).border(2.dp, PremiumGold.copy(alpha = glowAlpha), CircleShape))
                        }
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.White).border(3.dp, LightGreen, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("V", color = Green, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Virus VPN", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (isRunning) "● Connected" else "● Disconnected", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("↓ " + if (isRunning) formatSpeed(downBps) else "0 B/s", fontSize = 13.sp)
                Text(topMode, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("↑ " + if (isRunning) formatSpeed(upBps) else "0 B/s", fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isRunning) "Connected" else "Disconnected",
                color = if (isRunning) Green else Red,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "Config: $configVersion  •  IP: ${selectedServer?.address ?: "--"}  •  " + if (isRunning) formatDuration(durationSec) else "00:00:00",
                fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Real-time graph, visible while connected.
            if (isRunning) {
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().border(1.dp, LightGreen, RoundedCornerShape(16.dp))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("LIVE SPEED", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                            val max = (speedHistory.maxOrNull() ?: 1f).coerceAtLeast(1f)
                            val w = size.width
                            val h = size.height
                            val path = Path()
                            val fill = Path()
                            speedHistory.forEachIndexed { i, v ->
                                val x = if (speedHistory.size > 1) w * i / (speedHistory.size - 1) else 0f
                                val y = h - (v / max) * h
                                if (i == 0) { path.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y) }
                                else { path.lineTo(x, y); fill.lineTo(x, y) }
                            }
                            if (speedHistory.isNotEmpty()) {
                                fill.lineTo(w, h)
                                fill.close()
                                drawPath(fill, Green.copy(alpha = 0.18f))
                                drawPath(path, Green, style = Stroke(width = 3f))
                            }
                            drawLine(Color.LightGray, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
                        }
                        Text("↓ ${formatSpeed(downBps)}   ↑ ${formatSpeed(upBps)}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            VirusCard(label = "LOCATION", onClick = { showServerDialog = true }) {
                Text("⌖", color = Green, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedServer?.name ?: "No server yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (selectedServer == null) "Admin adds servers from panel" else "${countryFlag(selectedServer.countryCode)} ${selectedServer.countryName} • ${selectedServer.protocol} • ${if (selectedServer.isFree) "FREE" else "PREMIUM"}",
                        color = Green, fontSize = 12.sp,
                    )
                }
                Text("▾", color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))

            VirusCard(label = "NETWORK", onClick = { showNetworkDialog = true }) {
                Text("◉", color = Green, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(networkChoice, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(selectedNetwork?.subtitle?.ifBlank { "Good Working" } ?: "Good Working", color = Green, fontSize = 12.sp)
                }
                Text("▾", color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))

            // Telegram channel box — link comes from the admin panel, not typed by the user.
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(2.dp, Green, RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable {
                        if (telegramUrl.isBlank()) {
                            Toast.makeText(context, "Telegram channel not set by admin yet.", Toast.LENGTH_SHORT).show()
                        } else {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))) }
                            catch (_: Exception) { Toast.makeText(context, "Could not open Telegram link.", Toast.LENGTH_SHORT).show() }
                        }
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✈", color = Green, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Telegram Channel", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Join Now", color = Green, fontSize = 12.sp)
                    }
                    Text("›", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, Green, RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(56.dp).height(48.dp).background(Green), contentAlignment = Alignment.Center) {
                    Text("ID", color = Color.White, fontWeight = FontWeight.Bold)
                }
                BasicTextField(
                    value = username,
                    onValueChange = { username = it; ServerStore.setString(context, "username", it) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    decorationBox = { inner -> if (username.isEmpty()) Text("Username", color = Color.Gray); inner() },
                )
                Text("Expiry: --/--/--", color = Green, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, Green, RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(56.dp).height(48.dp).background(Green), contentAlignment = Alignment.Center) {
                    Text("🔑", fontSize = 18.sp)
                }
                BasicTextField(
                    value = password,
                    onValueChange = { password = it; ServerStore.setString(context, "password", it) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    decorationBox = { inner -> if (password.isEmpty()) Text("Password", color = Color.Gray); inner() },
                )
                Text(
                    if (passwordVisible) "Hide" else "Show", color = Green, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 12.dp).clickable { passwordVisible = !passwordVisible },
                )
            }
            Spacer(Modifier.height(24.dp))

            // START / STOP with glow ring while connected.
            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    Box(modifier = Modifier.size((200 * glowScale).dp).clip(CircleShape).background(Green.copy(alpha = glowAlpha)))
                    Box(modifier = Modifier.size((190 * glowScale).dp).clip(CircleShape).border(3.dp, PremiumGold.copy(alpha = glowAlpha), CircleShape))
                }
                Box(
                    modifier = Modifier.size(190.dp).clip(CircleShape).background(if (isRunning) Green else Pink).clickable { toggleService() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(168.dp).clip(CircleShape).background(if (isRunning) Green else Color.White)
                            .border(2.dp, if (isRunning) Color.White else Pink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (isRunning) "STOP" else "START", color = if (isRunning) Color.White else Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                BottomButton(label = "UPDATES", modifier = Modifier.weight(1f)) { updateStatus = ""; showUpdateDialog = true }
                BottomButton(label = "TWEAKS", modifier = Modifier.weight(1f)) { showTweaksDialog = true }
                BottomButton(label = "EXIT", modifier = Modifier.weight(1f)) { onExit() }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showServerDialog) {
        ServerPickDialog(
            servers = servers,
            onClose = { showServerDialog = false },
            onPick = { id -> ServerStore.setSelectedId(context, id); refreshServers(); showServerDialog = false },
            onUpdate = { showServerDialog = false; updateStatus = ""; showUpdateDialog = true },
        )
    }
    if (showNetworkDialog) {
        NetworkPickDialog(
            networks = networks,
            current = networkChoice,
            onClose = { showNetworkDialog = false },
            onPick = { networkChoice = it; ServerStore.setString(context, "network_choice", it); showNetworkDialog = false },
            onUpdate = { showNetworkDialog = false; updateStatus = ""; showUpdateDialog = true },
        )
    }
    if (showTweaksDialog) {
        TweaksDialog(onClose = { showTweaksDialog = false })
    }
    if (showUpdateDialog) {
        Dialog(onDismissRequest = { showUpdateDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦ UPDATES", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Press UPDATE to fetch the latest servers and networks from your admin panel. Servers and networks come only from the panel — nothing is added manually in the app.",
                        fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center,
                    )
                    if (updateStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(updateStatus, fontSize = 13.sp, color = Green, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("CLOSE", color = Green, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { fetchPanel() }) { Text("UPDATE", color = Green, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    if (announcement != null) {
        Dialog(onDismissRequest = { announcement = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📢 Update", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(announcement ?: "", fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { announcement = null }) { Text("OK", color = Green, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun startVpnService(context: Context) {
    val intent = Intent(context, VirusVpnService::class.java)
    intent.action = VirusVpnService.ACTION_START
    context.startService(intent)
}

@Composable
private fun VirusCard(label: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().border(2.dp, Green, RoundedCornerShape(12.dp)).padding(4.dp).clickable { onClick() },
    ) {
        Column {
            Text(label, color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp).border(1.dp, LightGreen, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, content = content,
            )
        }
    }
}

@Composable
private fun RowScope.BottomButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.border(1.dp, Green, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun ServerPickDialog(servers: List<VpnServer>, onClose: () -> Unit, onPick: (String) -> Unit, onUpdate: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search Here") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.height(12.dp))
                val filtered = servers.filter { it.name.contains(query, ignoreCase = true) || query.isBlank() }
                if (filtered.isEmpty()) {
                    Text("No servers yet.", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Nothing is pre-loaded. When the admin adds a server in the panel, press UPDATE and it will appear here automatically.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onUpdate) { Text("UPDATE FROM PANEL", color = Green, fontWeight = FontWeight.Bold) }
                }
                filtered.forEach { s ->
                    ServerRow(server = s) { onPick(s.id) }
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Green).clickable { onClose() }.padding(horizontal = 32.dp, vertical = 12.dp)) {
                        Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(server: VpnServer, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Green, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(LightGreen), contentAlignment = Alignment.Center) {
            Text(countryFlag(server.countryCode), fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${server.countryName.ifBlank { server.address }} • ${server.protocol}", color = Green, fontSize = 12.sp)
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (server.isFree) Green else PremiumGold).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(if (server.isFree) "FREE" else "PREMIUM", color = if (server.isFree) Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NetworkPickDialog(networks: List<PanelNetwork>, current: String, onClose: () -> Unit, onPick: (String) -> Unit, onUpdate: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("NETWORK", color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                if (networks.isEmpty()) {
                    Text("No networks yet.", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Networks come only from the admin panel. Press UPDATE and the admin's networks will appear here.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onUpdate) { Text("UPDATE FROM PANEL", color = Green, fontWeight = FontWeight.Bold) }
                }
                networks.forEach { net ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onPick(net.name) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = net.name == current, onClick = { onPick(net.name) }, colors = RadioButtonDefaults.colors(selectedColor = Green))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(net.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (net.subtitle.isNotBlank()) Text(net.subtitle, fontSize = 12.sp, color = Green)
                        }
                        if (net.mode.isNotBlank()) Text(net.mode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onClose) { Text("CLOSE", color = Green, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun TweaksDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { ServerStore.getTweaks(context) }
    var mode by rememberSaveable { mutableStateOf(saved.mode) }
    var connection by rememberSaveable { mutableStateOf(saved.connection) }
    var type by rememberSaveable { mutableStateOf(saved.type) }
    var tweakName by rememberSaveable { mutableStateOf(saved.tweakName) }
    var tweakInfo by rememberSaveable { mutableStateOf(saved.tweakInfo) }
    var frontQuery by rememberSaveable { mutableStateOf(saved.frontQuery) }
    var backQuery by rememberSaveable { mutableStateOf(saved.backQuery) }
    var payload by rememberSaveable { mutableStateOf(saved.payload) }
    var useDefaultProxy by rememberSaveable { mutableStateOf(saved.useDefaultProxy) }
    var proxyHost by rememberSaveable { mutableStateOf(saved.proxyHost) }
    var proxyPort by rememberSaveable { mutableStateOf(saved.proxyPort) }
    var showMenu by remember { mutableStateOf(false) }
    val connectionOptions = listOf("Direct Connection", "Direct SSL/TLS", "HTTP Proxy", "SSL/TLS Payload", "SSL/TLS Proxy + Payload")
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Custom Tweaks", color = Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Saved tweaks are applied to the VLESS config on the next START: Front Query → SNI, Back Query → Host, Payload (/) → Path, WS/CF/HTTP → network, SSL/TLS options → security.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row {
                    listOf("OVPN", "UDP", "V2ray").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { mode = option }) {
                            RadioButton(selected = mode == option, onClick = { mode = option }, colors = RadioButtonDefaults.colors(selectedColor = Green))
                            Text(option, fontSize = 13.sp)
                        }
                    }
                }
                Box {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showMenu = true }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(connection, fontSize = 15.sp); Text("▾", color = Color.Gray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        connectionOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { connection = option; showMenu = false }) }
                    }
                }
                Row {
                    listOf("CF", "WS", "HTTP").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { type = option }) {
                            RadioButton(selected = type == option, onClick = { type = option }, colors = RadioButtonDefaults.colors(selectedColor = Green))
                            Text(option, fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(value = tweakName, onValueChange = { tweakName = it }, label = { Text("Tweak Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tweakInfo, onValueChange = { tweakInfo = it }, label = { Text("Tweak Info") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = frontQuery, onValueChange = { frontQuery = it }, label = { Text("Front Query (SNI)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = backQuery, onValueChange = { backQuery = it }, label = { Text("Back Query (Host)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = payload, onValueChange = { payload = it }, label = { Text("Payload (Path, starts with /)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use default server and squid port", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = useDefaultProxy, onCheckedChange = { useDefaultProxy = it }, colors = SwitchDefaults.colors(checkedThumbColor = Green, checkedTrackColor = LightGreen))
                }
                Row {
                    OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("Proxy") }, placeholder = { Text("[Default]") }, singleLine = true, modifier = Modifier.weight(2f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("CANCEL", color = Green, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = {
                        ServerStore.saveTweaks(context, TweakSettings(mode, connection, type, tweakName, tweakInfo, frontQuery, backQuery, payload, useDefaultProxy, proxyHost, proxyPort))
                        Toast.makeText(context, "Tweaks saved — applied on next START", Toast.LENGTH_SHORT).show()
                        onClose()
                    }) { Text("SAVE", color = Green, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
