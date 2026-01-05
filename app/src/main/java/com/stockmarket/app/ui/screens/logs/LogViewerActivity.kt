package com.stockmarket.app.ui.screens.logs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockmarket.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Activity to view app logs in real-time
 * Accessed by long-pressing the search icon
 */
class LogViewerActivity : ComponentActivity() {
    
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            StockMarketTheme {
                LogViewerScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBackClick: () -> Unit) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Load logs on launch
    LaunchedEffect(Unit) {
        logs = loadLogs()
        isLoading = false
        // Scroll to bottom (latest logs)
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    // Filter logs
    val filteredLogs = remember(logs, filter) {
        if (filter.isEmpty()) logs
        else logs.filter { it.message.contains(filter, ignoreCase = true) || it.tag.contains(filter, ignoreCase = true) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Logs", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            logs = loadLogs()
                            isLoading = false
                            if (logs.isNotEmpty()) {
                                listState.animateScrollToItem(logs.size - 1)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextPrimary)
                    }
                    IconButton(onClick = { 
                        logs = emptyList()
                        filter = ""
                    }) {
                        Icon(Icons.Default.Delete, "Clear", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter TextField
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter logs...", color = TextTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = Border,
                    cursorColor = AccentBlue
                )
            )
            
            // Log count
            Text(
                text = "Showing ${filteredLogs.size} logs",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logs found", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(filteredLogs) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    val levelColor = when (log.level) {
        "E" -> Color(0xFFEF5350) // Red for error
        "W" -> Color(0xFFFFB74D) // Orange for warning
        "I" -> Color(0xFF81C784) // Green for info
        "D" -> Color(0xFF64B5F6) // Blue for debug
        else -> TextSecondary
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // Level indicator
        Text(
            text = log.level,
            color = levelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )
        
        // Tag
        Text(
            text = log.tag,
            color = AccentBlue,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(120.dp)
        )
        
        // Message
        Text(
            text = log.message,
            color = TextPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String
)

suspend fun loadLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
    val logs = mutableListOf<LogEntry>()
    try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "brief"))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        reader.useLines { lines ->
            lines.forEach { line ->
                val parsed = parseLogLine(line)
                if (parsed != null && parsed.tag.contains("stockmarket", ignoreCase = true)) {
                    logs.add(parsed)
                }
            }
        }
        
        // Keep last 500 logs
        if (logs.size > 500) {
            return@withContext logs.takeLast(500)
        }
    } catch (e: Exception) {
        logs.add(LogEntry("E", "LogViewer", "Failed to load logs: ${e.message}"))
    }
    logs
}

fun parseLogLine(line: String): LogEntry? {
    // Format: "I/Tag: message" or "I/Tag(pid): message"
    val regex = Regex("^([VDIWEFS])/([^:()]+)(?:\\(\\d+\\))?:\\s*(.*)$")
    val match = regex.find(line) ?: return null
    
    return LogEntry(
        level = match.groupValues[1],
        tag = match.groupValues[2].trim(),
        message = match.groupValues[3]
    )
}
