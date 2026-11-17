package com.chrono.phantom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Void = Color(0xFF080B0D)
private val Panel = Color(0xFF11191B)
private val Green = Color(0xFFB6F36B)
private val Cyan = Color(0xFF67DCE5)
private val Muted = Color(0xFF829092)
private val Red = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    private lateinit var collector: DeviceCollector
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collector = DeviceCollector(this)
        setContent { PhantomApp(this, collector) }
    }
}

@Composable
private fun PhantomApp(context: Context, collector: DeviceCollector) {
    var tab by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(DeviceSnapshot()) }
    LaunchedEffect(Unit) { snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { collector.snapshot() } }
    var question by remember { mutableStateOf("Why is my data draining?") }
    var summary by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(SecretStore.get(context)) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(background = Void, surface = Panel, primary = Green, secondary = Cyan, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Void, bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("Overview", "AI Summary", "Settings").forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(listOf(Icons.Default.Dashboard, Icons.Default.AutoAwesome, Icons.Default.Settings)[i], null) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF263522), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CHRONO", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("PHANTOM", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                    Text("DEVICE INTELLIGENCE", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                when (tab) {
                    0 -> {
                        Text("LIVE DEVICE PICTURE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Metric("MOBILE DATA · 24H", snapshot.mobile24.formatBytes(), Modifier.weight(1f))
                            Metric("WI-FI · 24H", snapshot.wifi24.formatBytes(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Metric("BATTERY", "${snapshot.batteryPercent}%", Modifier.weight(1f))
                            Metric("CONNECTION", snapshot.connection, Modifier.weight(1f))
                        }
                        PanelCard {
                            Text("DATA TREND", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(snapshot.trend, color = Color.White, fontSize = 14.sp)
                            Text("Mobile: ${snapshot.mobile24.formatBytes()} in the last 24 hours · Wi-Fi: ${snapshot.wifi24.formatBytes()}", color = Muted, fontSize = 11.sp)
                        }
                        PanelCard {
                            Text("TOP APP USAGE", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (snapshot.topApps.isEmpty()) Text("Grant Usage Access to see app activity.", color = Muted)
                            snapshot.topApps.take(5).forEach { (name, time) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = Color.White, fontSize = 13.sp)
                                    Text(time, color = Cyan, fontSize = 12.sp)
                                }
                            }
                        }
                        Button(onClick = {
                            if (!collector.hasUsageAccess()) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            else scope.launch { snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { collector.snapshot() } }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Void)) {
                            Text(if (collector.hasUsageAccess()) "REFRESH DEVICE PICTURE" else "GRANT USAGE ACCESS")
                        }
                        Text("Data figures are Android network-accounting totals, not live packet capture. Android may delay or aggregate these statistics.", color = Muted, fontSize = 10.sp)
                    }
                    1 -> {
                        Text("INTELLIGENCE BRIEFING", color = Green, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text("Describe the problem. PHANTOM analyses the device data it has collected and explains likely causes.", color = Muted, fontSize = 13.sp)
                        OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("What are you trying to understand?") }, minLines = 2, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Muted, focusedLabelColor = Green))
                        Button(onClick = {
                            if (apiKey.isBlank()) { error = "Add your OpenRouter API key in Settings first."; return@Button }
                            busy = true; error = ""; summary = ""
                            scope.launch {
                                try {
                                    snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { collector.snapshot() }
                                    summary = OpenRouterClient.analyse(apiKey, question, snapshot)
                                } catch (e: Exception) { error = e.message ?: "Analysis failed." }
                                busy = false
                            }
                        }, enabled = !busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Void)) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Void, strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp)); Text(if (busy) "ANALYSING DEVICE DATA…" else "GENERATE INTELLIGENCE SUMMARY")
                        }
                        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
                        if (summary.isNotBlank()) PanelCard { Text("PHANTOM'S FINDINGS", color = Green, fontWeight = FontWeight.Bold); Text(summary, color = Color.White, fontSize = 14.sp, lineHeight = 21.sp) }
                        Text("One-shot report, not a chatbot. Your question and displayed device metrics are sent to OpenRouter only when you press the button.", color = Muted, fontSize = 10.sp)
                    }
                    else -> {
                        Text("CONFIGURATION", color = Green, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        PanelCard {
                            Text("OPENROUTER CONNECTION", color = Green, fontWeight = FontWeight.Bold)
                            Text("Endpoint: https://openrouter.ai/api/v1", color = Muted, fontSize = 11.sp)
                            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("OpenRouter API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Muted))
                            Button(onClick = { SecretStore.put(context, apiKey); error = "API key saved securely on this device." }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Void)) { Text("SAVE API KEY") }
                            if (error.isNotBlank()) Text(error, color = Cyan, fontSize = 11.sp)
                        }
                        PanelCard {
                            Text("PERMISSIONS & DATA", color = Green, fontWeight = FontWeight.Bold)
                            Text("Usage Access enables Android's network-usage and app-activity summaries. PHANTOM does not read message contents or capture private traffic.", color = Muted, fontSize = 12.sp)
                            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263522), contentColor = Green)) { Text("OPEN USAGE ACCESS SETTINGS") }
                        }
                        Text("AI model: openrouter/free · API key never ships inside the APK or source code.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    PanelCard(modifier) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
