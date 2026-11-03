package com.virusvpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val Green = Color(0xFF00A651)
private val LightGreen = Color(0xFFBDE8CD)
private val Pink = Color(0xFFEAABAD)
private val Red = Color(0xFFE53935)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VirusHomeScreen(onExit = { finish() }) }
    }
}

@Composable
fun VirusHomeScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf(ServerStore.getServers(context)) }
    var selectedId by remember { mutableStateOf(ServerStore.getSelectedId(context)) }
    var isRunning by remember { mutableStateOf(VirusVpnService.isRunning) }
    var username by rememberSaveable { mutableStateOf(ServerStore.getString(context, "username")) }
    var password by rememberSaveable { mutableStateOf(ServerStore.getString(context, "password")) }
    var passwordVisible by remember { mutableStateOf(false) }
    var networkChoice by rememberSaveable { mutableStateOf(ServerStore.getString(context, "network_choice", "WiFi BDIX Bypass")) }
    var customMode by rememberSaveable { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showTweaksDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var editLink by remember { mutableStateOf("") }

    val selectedServer = servers.firstOrNull { it.id == selectedId }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == VirusVpnService.BROADCAST_STATUS) {
                    isRunning = intent.getBooleanExtra(VirusVpnService.EXTRA_RUNNING, false)
                    val err = intent.getStringExtra(VirusVpnService.EXTRA_ERROR)
                    if (!err.isNullOrBlank() && !isRunning) {
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
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

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpnService(context)
        }
    }

    fun toggleService() {
        if (isRunning) {
            val intent = Intent(context, VirusVpnService::class.java)
            intent.action = VirusVpnService.ACTION_STOP
            context.startService(intent)
            isRunning = false
        } else {
            if (selectedServer == null && servers.isEmpty()) {
                Toast.makeText(context, "Add a server first", Toast.LENGTH_SHORT).show()
                showAddDialog = true
                return
            }
            val prepare = VpnService.prepare(context)
            if (prepare == null) {
                startVpnService(context)
            } else {
                vpnLauncher.launch(prepare)
            }
        }
    }

    fun refreshServers() {
        servers = ServerStore.getServers(context)
        selectedId = ServerStore.getSelectedId(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Green)
                .border(3.dp, LightGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("V", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("Virus VPN", color = Green, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(LightGreen)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isRunning) "↓ —" else "↓ 0 KB", fontSize = 13.sp)
            Text("V2RAY", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (isRunning) "↑ —" else "↑ 0 KB", fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (isRunning) "Connected" else "Disconnected",
            color = if (isRunning) Green else Red,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("Config Version : 1.0", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        VirusCard(label = "LOCATION", onClick = { showServerDialog = true }) {
            Text("⌖", color = Green, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(selectedServer?.name ?: "Auto Select Servers", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Virus VPN", color = Green, fontSize = 12.sp)
            }
            Text("▾", color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))

        VirusCard(label = "NETWORK", onClick = { showNetworkDialog = true }) {
            Text("◉", color = Green, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(networkChoice, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (customMode) "Custom 1" else "Good Working", color = Green, fontSize = 12.sp)
            }
            Text("▾", color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Green, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(48.dp)
                    .background(Green),
                contentAlignment = Alignment.Center,
            ) {
                Text("ID", color = Color.White, fontWeight = FontWeight.Bold)
            }
            BasicTextField(
                value = username,
                onValueChange = {
                    username = it
                    ServerStore.setString(context, "username", it)
                },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    if (username.isEmpty()) Text("Username", color = Color.Gray)
                    inner()
                },
            )
            Text("Expiry: --/--/--", color = Green, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Green, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(48.dp)
                    .background(Green),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔑", fontSize = 18.sp)
            }
            BasicTextField(
                value = password,
                onValueChange = {
                    password = it
                    ServerStore.setString(context, "password", it)
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    if (password.isEmpty()) Text("Password", color = Color.Gray)
                    inner()
                },
            )
            Text(
                if (passwordVisible) "Hide" else "Show",
                color = Green,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp).clickable { passwordVisible = !passwordVisible },
            )
        }
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(if (isRunning) Green else Pink)
                .clickable { toggleService() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) Green else Color.White)
                    .border(2.dp, if (isRunning) Color.White else Pink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isRunning) "STOP" else "START",
                    color = if (isRunning) Color.White else Green,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+", color = Green, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    editLink = ""
                    showAddDialog = true
                })
            Text("  |  ", color = Color.Gray)
            Text("✎", color = Green, fontSize = 22.sp,
                modifier = Modifier.clickable {
                    selectedServer?.let {
                        editLink = it.rawLink
                        showAddDialog = true
                    }
                })
            Text("  |  ", color = Color.Gray)
            Text("🗑", color = Green, fontSize = 22.sp,
                modifier = Modifier.clickable {
                    selectedServer?.let { s ->
                        val rest = servers.filter { it.id != s.id }
                        ServerStore.saveServers(context, rest)
                        ServerStore.setSelectedId(context, rest.firstOrNull()?.id)
                        refreshServers()
                    }
                })
            Text("  |  ", color = Color.Gray)
            Text("Custom", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = customMode,
                onCheckedChange = {
                    customMode = it
                    networkChoice = if (it) "Custom Payload" else "WiFi BDIX Bypass"
                    ServerStore.setString(context, "network_choice", networkChoice)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Green, checkedTrackColor = LightGreen),
            )
        }
        Spacer(Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            BottomButton(label = "UPDATES", modifier = Modifier.weight(1f)) { showUpdateDialog = true }
            BottomButton(label = "TWEAKS", modifier = Modifier.weight(1f)) { showTweaksDialog = true }
            BottomButton(label = "EXIT", modifier = Modifier.weight(1f)) { onExit() }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showServerDialog) {
        ServerPickDialog(
            servers = servers,
            onClose = { showServerDialog = false },
            onPick = { id ->
                ServerStore.setSelectedId(context, id)
                refreshServers()
                showServerDialog = false
            },
            onAdd = {
                showServerDialog = false
                editLink = ""
                showAddDialog = true
            },
        )
    }
    if (showNetworkDialog) {
        NetworkPickDialog(
            current = networkChoice,
            onClose = { showNetworkDialog = false },
            onPick = {
                networkChoice = it
                ServerStore.setString(context, "network_choice", it)
                showNetworkDialog = false
            },
        )
    }
    if (showAddDialog) {
        AddServerDialog(
            initial = editLink,
            onClose = { showAddDialog = false },
            onSave = { link ->
                val parsed = ServerStore.parseVless(link)
                if (parsed == null) {
                    Toast.makeText(context, "Invalid VLESS link", Toast.LENGTH_SHORT).show()
                } else {
                    val rest = servers.filter { it.id != selectedId || editLink.isBlank() }
                    val updated = rest + parsed
                    ServerStore.saveServers(context, updated)
                    ServerStore.setSelectedId(context, parsed.id)
                    refreshServers()
                    showAddDialog = false
                }
            },
        )
    }
    if (showTweaksDialog) {
        TweaksDialog(onClose = { showTweaksDialog = false })
    }
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Updates") },
            text = { Text("No updates available.") },
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("OK", color = Green)
                }
            },
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Green, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .clickable { onClick() },
    ) {
        Column {
            Text(label, color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, LightGreen, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun RowScope.BottomButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .border(1.dp, Green, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ServerPickDialog(servers: List<VpnServer>, onClose: () -> Unit, onPick: (String) -> Unit, onAdd: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search Here") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.height(12.dp))
                ServerRow(title = "Auto Select Servers", subtitle = "Virus VPN", badge = "Auto") {
                    val first = servers.firstOrNull()
                    if (first != null) onPick(first.id) else onClose()
                }
                val filtered = servers.filter { it.name.contains(query, ignoreCase = true) || query.isBlank() }
                if (filtered.isEmpty()) {
                    Text("No servers yet. Add a VLESS link first.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onAdd) { Text("Add server", color = Green) }
                }
                filtered.forEach { s ->
                    ServerRow(title = s.name, subtitle = s.address, badge = null) { onPick(s.id) }
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Green).clickable { onClose() }.padding(horizontal = 32.dp, vertical = 12.dp),
                    ) {
                        Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(title: String, subtitle: String, badge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Green, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(LightGreen), contentAlignment = Alignment.Center) {
            Text("V", color = Green, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Green, fontSize = 12.sp)
        }
        if (badge != null) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Green).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NetworkPickDialog(current: String, onClose: () -> Unit, onPick: (String) -> Unit) {
    val options = listOf("WiFi BDIX Bypass", "Custom Payload", "Direct Connection", "Auto")
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("NETWORK", color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                options.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == current, onClick = { onPick(option) }, colors = RadioButtonDefaults.colors(selectedColor = Green))
                        Spacer(Modifier.width(8.dp))
                        Text(option, fontSize = 15.sp)
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
private fun AddServerDialog(initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var link by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Server", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Paste a VLESS link. No server is included in the app.", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("VLESS link") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("CANCEL", color = Green) }
                    TextButton(onClick = { onSave(link) }) { Text("SAVE", color = Green, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun TweaksDialog(onClose: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("OVPN") }
    var connection by rememberSaveable { mutableStateOf("Direct Connection") }
    var type by rememberSaveable { mutableStateOf("CF") }
    var tweakName by rememberSaveable { mutableStateOf("") }
    var tweakInfo by rememberSaveable { mutableStateOf("") }
    var frontQuery by rememberSaveable { mutableStateOf("") }
    var backQuery by rememberSaveable { mutableStateOf("") }
    var payload by rememberSaveable { mutableStateOf("") }
    var useDefaultProxy by rememberSaveable { mutableStateOf(true) }
    var proxyHost by rememberSaveable { mutableStateOf("") }
    var proxyPort by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val connectionOptions = listOf("Direct Connection", "Direct SSL/TLS", "HTTP Proxy", "SSL/TLS Payload", "SSL/TLS Proxy + Payload")
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Custom Tweaks", color = Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                        Text(connection, fontSize = 15.sp)
                        Text("▾", color = Color.Gray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        connectionOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { connection = option; showMenu = false })
                        }
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
                OutlinedTextField(value = frontQuery, onValueChange = { frontQuery = it }, label = { Text("Front Query") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = backQuery, onValueChange = { backQuery = it }, label = { Text("Back Query") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = payload, onValueChange = { payload = it }, label = { Text("Payload") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                    TextButton(onClick = onClose) { Text("SAVE", color = Green, fontWeight = FontWeight.Bold) }
                }
                Text("Tweak values are stored locally only and do not change the connection yet.", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
