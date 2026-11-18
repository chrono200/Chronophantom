package com.chrono.phantom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF070B10)
private val PanelColor = Color(0xFF111B24)
private val PanelRaised = Color(0xFF172630)
private val Acid = Color(0xFFB8F477)
private val Cyan = Color(0xFF64E4E8)
private val Muted = Color(0xFF82949D)
private val Warning = Color(0xFFFFBD69)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 11, 16)
        window.navigationBarColor = android.graphics.Color.rgb(7, 11, 16)
        setContent { PhantomApp(this) }
    }
}

@Composable
private fun PhantomApp(context: Context) {
    val store = remember { EventStore(context) }
    val observer = remember { DeviceObserver(context, store) }
    var page by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf(emptyList<PhantomEvent>()) }
    var insights by remember { mutableStateOf(emptyList<PhantomInsight>()) }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val usage = remember(permissionTick) { observer.hasUsageAccess() }
    val notifications = remember(permissionTick) { observer.hasNotificationAccess() }
    val scope = rememberCoroutineScope()

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionTick++
        scope.launch { loadJournal(observer, store, { events = it.first }, { insights = it.second }, { issue = it }) }
    }

    fun refresh() {
        scope.launch {
            busy = true
            issue = null
            try {
                withContext(Dispatchers.IO) { observer.sync() }
                val loaded = withContext(Dispatchers.IO) { store.recent(500) }
                events = loaded
                insights = withContext(Dispatchers.Default) { InsightEngine.analyse(loaded) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                issue = e.localizedMessage ?: "The local scan could not finish."
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Acid, secondary = Cyan, background = Ink, surface = PanelColor, onSurface = Color.White)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = PanelColor, tonalElevation = 0.dp) {
                    val labels = listOf("Command", "Timeline", "Signals", "Access")
                    val icons = listOf(Icons.Default.Radar, Icons.Default.Timeline, Icons.Default.Bolt, Icons.Default.Shield)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = page == i, onClick = { page = i },
                            icon = { Icon(icons[i], null) }, label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Acid, selectedTextColor = Acid, indicatorColor = Color(0xFF2A3B2B), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CHRONO / FIELD SYSTEM", color = Cyan, fontSize = 9.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
                        Text("PHANTOM", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        LiveDot(active = usage || notifications)
                        Text(if (usage || notifications) "MONITOR READY" else "SETUP REQUIRED", color = if (usage || notifications) Acid else Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${events.size} LOCAL RECORDS", color = Muted, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedVisibility(issue != null) {
                    Panel {
                        Text("SCAN INTERRUPTED", color = Warning, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(issue ?: "", color = Color.White, fontSize = 11.sp)
                        Text("Your saved journal remains on this device.", color = Muted, fontSize = 10.sp)
                    }
                }
                when (page) {
                    0 -> CommandPage(events, insights, usage, notifications, busy, ::refresh, { page = it })
                    1 -> TimelinePage(events)
                    2 -> InsightsPage(insights)
                    else -> AccessPage(observer, events, insights, usage, notifications, { intent -> settingsLauncher.launch(intent) })
                }
            }
        }
    }
}

private suspend fun loadJournal(observer: DeviceObserver, store: EventStore, onEvents: (List<PhantomEvent>) -> Unit, onInsights: (List<PhantomInsight>) -> Unit, onError: (String) -> Unit) {
    try {
        withContext(Dispatchers.IO) { observer.sync() }
        val loaded = withContext(Dispatchers.IO) { store.recent(500) }
        onEvents(loaded)
        onInsights(withContext(Dispatchers.Default) { InsightEngine.analyse(loaded) })
    } catch (e: Exception) { onError(e.localizedMessage ?: "Could not refresh the event journal.") }
}

@Composable
private fun LiveDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val pulse by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).alpha(if (active) pulse else 0.35f).background(if (active) Acid else Warning, CircleShape))
        Text(if (active) "● LIVE" else "○ IDLE", color = if (active) Acid else Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CommandPage(events: List<PhantomEvent>, insights: List<PhantomInsight>, usage: Boolean, notifications: Boolean, busy: Boolean, refresh: () -> Unit, navigate: (Int) -> Unit) {
    val warningCount = insights.count { it.severity == "WARNING" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DEVICE COMMAND", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("Your device. Your evidence.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("A local intelligence snapshot — no cloud upload.", color = Muted, fontSize = 10.sp)
            }
            ScanCore(scanning = busy, active = usage || notifications)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricTile("EVENTS", events.size.toString(), "journal entries", Modifier.weight(1f), Acid)
            MetricTile("SIGNALS", insights.size.toString(), "patterns found", Modifier.weight(1f), Cyan)
            MetricTile("ALERTS", warningCount.toString(), "need a look", Modifier.weight(1f), Warning)
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MONITORING MATRIX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("LIVE STATUS", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            MonitorBar("App activity", usage, "Usage Access", 0.82f)
            MonitorBar("Notification events", notifications, "Notification Access", 0.68f)
            MonitorBar("Battery sampling", true, "On manual sync", 0.92f)
            MonitorBar("Network state", true, "On manual sync", 0.74f)
            Text("Battery and network are sampled when you sync. They are not continuous background trackers.", color = Muted, fontSize = 10.sp)
        }
        Button(onClick = refresh, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink)) {
            if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = Ink, strokeWidth = 2.dp)
            else Icon(Icons.Default.Bolt, null)
            Spacer(Modifier.width(9.dp))
            Text(if (busy) "SCANNING DEVICE…" else "RUN DEVICE SCAN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionTile("EVENT STREAM", "Inspect captured activity", Icons.Default.Timeline, Modifier.weight(1f)) { navigate(1) }
            ActionTile("INTELLIGENCE", "Review detected patterns", Icons.Default.Insights, Modifier.weight(1f)) { navigate(2) }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LATEST SIGNAL", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { navigate(1) }) { Text("OPEN STREAM", color = Acid, fontSize = 9.sp) }
            }
            val latest = events.firstOrNull()
            if (latest == null) Text("No observations yet. Enable access, then run a scan.", color = Muted, fontSize = 12.sp)
            else {
                Text(latest.type.replace("_", " "), color = if (latest.severity == "WARNING") Warning else Acid, fontWeight = FontWeight.Bold)
                Text(latest.detail, color = Color.White, fontSize = 13.sp)
                Text("${latest.source}  •  ${latest.displayTime()}", color = Muted, fontSize = 10.sp)
            }
        }
        if (!usage) Panel {
            Text("UNLOCK APP ACTIVITY", color = Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Android requires you to grant Usage Access in system settings before app activity can be recorded.", color = Muted, fontSize = 11.sp)
            Text("Open the ACCESS tab to enable it.", color = Acid, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ScanCore(scanning: Boolean, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "core")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (scanning) 900 else 4200, easing = LinearEasing)), label = "rotation")
    val pulse by transition.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "core-pulse")
    Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            drawCircle(Cyan.copy(alpha = .13f), radius = size.minDimension * .48f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawCircle(Acid.copy(alpha = .35f), radius = size.minDimension * .36f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 6f), rotation)))
            drawCircle(if (active) Acid.copy(alpha = .12f) else Warning.copy(alpha = .10f), radius = size.minDimension * .25f * pulse)
        }
        Icon(if (scanning) Icons.Default.Radar else Icons.Default.Memory, null, tint = if (active) Acid else Muted, modifier = Modifier.size(31.dp))
    }
}

@Composable
private fun MonitorBar(title: String, enabled: Boolean, note: String, amount: Float) {
    val progress by animateFloatAsState(if (enabled) amount else 0.06f, tween(850), label = title)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (enabled) "READY" else "LOCKED", color = if (enabled) Acid else Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = if (enabled) Cyan else Warning, trackColor = Color(0xFF26343D))
        Text(note, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun MetricTile(label: String, value: String, caption: String, modifier: Modifier, accent: Color) {
    Panel(modifier) {
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(caption, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(PanelRaised).clickable(onClick = action).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
        Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun TimelinePage(events: List<PhantomEvent>) {
    var filter by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    val types = listOf("ALL", "APP_FOREGROUND", "NOTIFICATION_POSTED", "NOTIFICATION_REMOVED", "CONNECTIVITY", "BATTERY_DROP")
    val visible = events.filter { (filter == "ALL" || it.type == filter) && (query.isBlank() || it.detail.contains(query, true) || it.source.contains(query, true) || it.type.contains(query, true)) }
    Column(Modifier.fillMaxSize()) {
        Text("BLACK BOX / EVENT STREAM", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelColor).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp), decorationBox = { inner -> if (query.isEmpty()) Text("Search events or app names…", color = Muted, fontSize = 12.sp); inner() })
            if (query.isNotEmpty()) Icon(Icons.Default.Close, "Clear search", tint = Muted, modifier = Modifier.clickable { query = "" }.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "APP_FOREGROUND", "NOTIFICATION_POSTED").forEach { t -> FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(when (t) { "ALL" -> "All"; "APP_FOREGROUND" -> "Apps"; else -> "Notifs" }, fontSize = 10.sp) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("CONNECTIVITY", "BATTERY_DROP", "NOTIFICATION_REMOVED").forEach { t -> FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(when (t) { "CONNECTIVITY" -> "Network"; "BATTERY_DROP" -> "Battery"; else -> "Dismissed" }, fontSize = 9.sp) }) }
        }
        Text("${visible.size} MATCHING RECORDS", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching events. Run a device scan or change your filter.", color = Muted, fontSize = 12.sp) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visible, key = { it.id }) { event ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (event.severity == "WARNING") Warning else Cyan, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(event.type.replace("_", " "), color = if (event.severity == "WARNING") Warning else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(event.displayTime(), color = Muted, fontSize = 9.sp)
                    }
                    Text(event.detail, color = Color.White, fontSize = 12.sp)
                    Text(event.source, color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightsPage(insights: List<PhantomInsight>) {
    Column(Modifier.fillMaxSize()) {
        Text("SIGNAL ANALYSIS / LOCAL RULES", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text("Patterns worth your attention", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (insights.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No patterns detected yet. Build a little event history with a scan.", color = Muted, fontSize = 12.sp) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(insights) { insight ->
                var expanded by remember { mutableStateOf(false) }
                Panel(Modifier.animateContentSize().clickable { expanded = !expanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(insight.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(if (insight.severity == "WARNING") "ATTENTION" else "OBSERVATION", color = if (insight.severity == "WARNING") Warning else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Muted)
                    }
                    Text(insight.explanation, color = Muted, fontSize = 11.sp)
                    AnimatedVisibility(expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("EVIDENCE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(insight.evidence, color = Color.White, fontSize = 11.sp)
                            Text("Tap the card to collapse this finding.", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessPage(observer: DeviceObserver, events: List<PhantomEvent>, insights: List<PhantomInsight>, usage: Boolean, notifications: Boolean, openSettings: (Intent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("ACCESS CONTROL / DEVICE SETTINGS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text("You stay in control.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        AccessCard("USAGE ACCESS", "Required for app foreground history. Android handles this special permission separately from normal app permissions.", usage, "OPEN USAGE SETTINGS") {
            openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        AccessCard("NOTIFICATION ACCESS", "Optional. Logs the app that posted or dismissed a notification. It never stores notification text or message contents.", notifications, "OPEN NOTIFICATION SETTINGS") {
            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Panel {
            Text("WHY ANDROID ASKS", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("These are restricted system-level access controls. ChronoPhantom cannot silently switch them on. In Settings, find ChronoPhantom and enable its access, then return here; the status refreshes automatically.", color = Muted, fontSize = 11.sp)
        }
        Panel {
            Text("EXPORT YOUR EVIDENCE", color = Acid, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Build a local diagnostic report. It is shared only after you choose a destination in Android's share sheet.", color = Muted, fontSize = 11.sp)
            Button(onClick = {
                val report = DiagnosticReport.build(events, insights, usage, notifications)
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "ChronoPhantom diagnostic report"); putExtra(Intent.EXTRA_TEXT, report) }
                openSettings(Intent.createChooser(send, "Export PHANTOM report"))
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink)) {
                Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(8.dp)); Text("GENERATE & EXPORT REPORT", fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
private fun AccessCard(title: String, description: String, enabled: Boolean, actionLabel: String, onClick: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(if (enabled) "ENABLED" else "NOT ENABLED", color = if (enabled) Acid else Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text(description, color = Muted, fontSize = 11.sp)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Cyan)) {
            Icon(if (enabled) Icons.Default.Settings else Icons.Default.LockOpen, null)
            Spacer(Modifier.width(7.dp))
            Text(if (enabled) "MANAGE ACCESS" else actionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(PanelColor, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}
