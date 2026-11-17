package com.chrono.phantom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF080B0D)
private val SurfaceDark = Color(0xFF12191B)
private val Lime = Color(0xFFB8F477)
private val Teal = Color(0xFF65DCE2)
private val Dim = Color(0xFF839092)
private val Amber = Color(0xFFFFC66D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhantomApp(this) }
    }
    override fun onResume() {
        super.onResume()
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
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { observer.sync() }
            events = withContext(Dispatchers.IO) { store.recent(500) }
            insights = withContext(Dispatchers.Default) { InsightEngine.analyse(events) }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, secondary = Teal, background = Background, surface = SurfaceDark, onSurface = Color.White)) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    val labels = listOf("Overview", "Timeline", "Insights", "Access")
                    val icons = listOf(Icons.Default.Dashboard, Icons.Default.Timeline, Icons.Default.QueryStats, Icons.Default.Security)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = page == index, onClick = { page = index },
                            icon = { Icon(icons[index], null) }, label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime, indicatorColor = Color(0xFF293723), unselectedIconColor = Dim, unselectedTextColor = Dim)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CHRONO /", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("PHANTOM", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("EVENT INTELLIGENCE", color = Teal, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("${events.size} LOCAL EVENTS", color = Dim, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (page) {
                    0 -> OverviewPage(events, insights, observer, busy, ::refresh, context)
                    1 -> TimelinePage(events)
                    2 -> InsightsPage(insights)
                    else -> AccessPage(observer, context, events, insights)
                }
            }
        }
    }
}

@Composable
private fun OverviewPage(events: List<PhantomEvent>, insights: List<PhantomInsight>, observer: DeviceObserver, busy: Boolean, refresh: () -> Unit, context: Context) {
    val latest = events.firstOrNull()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DEVICE SITUATION", color = Dim, fontSize = 10.sp, letterSpacing = 2.sp)
            TextButton(onClick = refresh) { Icon(Icons.Default.Refresh, null, tint = Lime); Text("Sync events", color = Lime) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("EVENT JOURNAL", events.size.toString(), "stored locally", Modifier.weight(1f))
            MetricCard("INSIGHTS", insights.count { it.severity == "WARNING" }.toString(), "items to review", Modifier.weight(1f))
        }
        Panel {
            Text("CURRENT COLLECTION", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            StatusLine("Usage Access", if (observer.hasUsageAccess()) "ENABLED" else "NOT ENABLED", observer.hasUsageAccess())
            StatusLine("Notification Access", if (observer.hasNotificationAccess()) "ENABLED" else "OPTIONAL", observer.hasNotificationAccess())
            Text("The journal is local to this device. Notification message contents are never recorded.", color = Dim, fontSize = 11.sp)
        }
        Panel {
            Text("LATEST OBSERVATION", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (latest == null) Text("No events yet. Enable the access you want and sync the journal.", color = Dim)
            else {
                Text(latest.type.replace("_", " "), color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(latest.detail, color = Color.White, fontSize = 14.sp)
                Text("${latest.source} · ${latest.displayTime()}", color = Dim, fontSize = 10.sp)
            }
        }
        Panel {
            Text("INTELLIGENCE PREVIEW", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            insights.take(2).forEach { insight ->
                Text(insight.title, color = if (insight.severity == "WARNING") Amber else Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(insight.explanation, color = Dim, fontSize = 11.sp)
            }
        }
        Button(onClick = { refresh() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Background)) {
            if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Background, strokeWidth = 2.dp) else Icon(Icons.Default.Sync, null)
            Spacer(Modifier.width(8.dp)); Text(if (busy) "UPDATING JOURNAL…" else "SYNC DEVICE EVENTS")
        }
        Text("Collection is permission-based and constrained by Android background limits.", color = Dim, fontSize = 10.sp)
    }
}

@Composable
private fun TimelinePage(events: List<PhantomEvent>) {
    var filter by remember { mutableStateOf("ALL") }
    val types = listOf("ALL", "APP_FOREGROUND", "NOTIFICATION_POSTED", "CONNECTIVITY", "BATTERY_DROP")
    val visible = if (filter == "ALL") events else events.filter { it.type == filter }
    Column(Modifier.fillMaxSize()) {
        Text("SEARCHABLE EVENT HISTORY", color = Dim, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.take(3).forEach { type ->
                FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(if (type == "ALL") "All" else type.substringBefore("_")) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.drop(3).forEach { type ->
                FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type.replace("_", " ")) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching events in the journal.", color = Dim) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(visible, key = { it.id }) { event ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(event.type.replace("_", " "), color = if (event.severity == "WARNING") Amber else Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(event.displayTime(), color = Dim, fontSize = 10.sp)
                    }
                    Text(event.detail, color = Color.White, fontSize = 13.sp)
                    Text(event.source, color = Dim, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightsPage(insights: List<PhantomInsight>) {
    Column(Modifier.fillMaxSize()) {
        Text("LOCAL CORRELATION & ANOMALY DETECTION", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(insights) { insight ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(insight.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(insight.severity, color = if (insight.severity == "WARNING") Amber else Teal, fontSize = 9.sp)
                    }
                    Text(insight.explanation, color = Dim, fontSize = 12.sp)
                    Text("EVIDENCE  ·  ${insight.evidence}", color = Teal, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AccessPage(observer: DeviceObserver, context: Context, events: List<PhantomEvent>, insights: List<PhantomInsight>) {
    val usage = observer.hasUsageAccess()
    val notifications = observer.hasNotificationAccess()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PERMISSION & REPORT CONTROLS", color = Dim, fontSize = 10.sp, letterSpacing = 2.sp)
        Panel {
            Text("USAGE ACCESS", color = Lime, fontWeight = FontWeight.Bold)
            Text("Allows PHANTOM to reconstruct app foreground activity from Android's usage event history.", color = Dim, fontSize = 12.sp)
            StatusLine("Status", if (usage) "ENABLED" else "DISABLED", usage)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293723), contentColor = Lime)) { Text("OPEN USAGE ACCESS") }
        }
        Panel {
            Text("NOTIFICATION ACCESS", color = Lime, fontWeight = FontWeight.Bold)
            Text("Records which app posted or removed a notification. Notification text, titles, and message bodies are not read or stored.", color = Dim, fontSize = 12.sp)
            StatusLine("Status", if (notifications) "ENABLED" else "OPTIONAL", notifications)
            Button(onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293723), contentColor = Lime)) { Text("OPEN NOTIFICATION ACCESS") }
        }
        Panel {
            Text("DIAGNOSTIC REPORT", color = Lime, fontWeight = FontWeight.Bold)
            Text("Create a text report from the local event journal and current findings. Nothing is shared until you choose an app in Android's share sheet.", color = Dim, fontSize = 12.sp)
            Button(onClick = {
                val report = DiagnosticReport.build(events, insights, usage, notifications)
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "ChronoPhantom diagnostic report"); putExtra(Intent.EXTRA_TEXT, report) }
                context.startActivity(Intent.createChooser(send, "Export PHANTOM report"))
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Background)) {
                Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(8.dp)); Text("EXPORT DIAGNOSTIC REPORT")
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(title, color = Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = Dim, fontSize = 10.sp)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(15.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun StatusLine(label: String, status: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(status, color = if (enabled) Lime else Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
