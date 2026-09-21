package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.UbuntuDarkAubergine
import com.example.ui.theme.UbuntuMidAubergine
import com.example.ui.theme.UbuntuOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogViewerScreen(
    logs: List<LogEntry>,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> String,
    onTestBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var onlyErrors by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, selectedCategory, onlyErrors, searchQuery) {
        logs.filter { entry ->
            val matchCategory = selectedCategory == LogCategory.ALL || entry.category == selectedCategory
            val matchLevel = !onlyErrors || (entry.level == LogLevel.ERROR || entry.level == LogLevel.WARN)
            val matchSearch = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    (entry.details?.contains(searchQuery, ignoreCase = true) == true)
            matchCategory && matchLevel && matchSearch
        }
    }

    // Auto-scroll to latest log if list grows
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem((filteredLogs.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Top Action Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E1E2E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF312544)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = UbuntuOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sistem & Hata Günlüğü",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${filteredLogs.size} / ${logs.size} Kayıt",
                                color = Color(0xFFAAA0B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val text = onExportLogs()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Ubuntu ARM64 Logs", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Loglar panoya kopyalandı (${logs.size} satır)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp).testTag("copy_logs_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onRefreshLogs,
                            modifier = Modifier.size(34.dp).testTag("refresh_logs_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = UbuntuOrange, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.size(34.dp).testTag("clear_logs_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Temizle", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Diagnostic Test Button (Browser tester)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTestBrowser,
                        colors = ButtonDefaults.buttonColors(containerColor = UbuntuMidAubergine),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tarayıcıyı Test Et", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onRefreshLogs,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sistem Loglarını Çek", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Loglarda ara (hata, exit, browser, vnc...)", color = Color(0xFF757589), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UbuntuOrange,
                unfocusedBorderColor = Color(0xFF312544),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF161622),
                unfocusedContainerColor = Color(0xFF161622)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips row
        val filterScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(filterScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = onlyErrors,
                onClick = { onlyErrors = !onlyErrors },
                label = { Text("⚠️ Yalnızca Hatalar", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF7F1D1D),
                    selectedLabelColor = Color(0xFFFCA5A5),
                    containerColor = Color(0xFF241C2E),
                    labelColor = Color(0xFFB39DDB)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            LogCategory.values().forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.displayName, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UbuntuOrange,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF241C2E),
                        labelColor = Color(0xFFB39DDB)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log Items List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TerminalGreen,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (logs.isEmpty()) "Henüz log kaydı yok." else "Filtreye uygun log bulunamadı.",
                        color = Color(0xFFAAA0B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onRefreshLogs) {
                        Text("Ubuntu Dosya Sisteminden Logları Çek")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogItemCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LogItemCard(entry: LogEntry) {
    var isExpanded by remember { mutableStateOf(false) }
    val timeStr = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
    }

    val (badgeBg, badgeText, contentColor) = when (entry.level) {
        LogLevel.ERROR -> Triple(Color(0xFF5C1D1D), Color(0xFFFF8A80), Color(0xFFFFCDD2))
        LogLevel.WARN -> Triple(Color(0xFF5A3A10), Color(0xFFFFD180), Color(0xFFFFE0B2))
        LogLevel.SUCCESS -> Triple(Color(0xFF1B4D2E), Color(0xFFA7F3D0), Color(0xFFC8E6C9))
        LogLevel.SYSTEM -> Triple(Color(0xFF312544), Color(0xFFD1C4E9), Color(0xFFE1BEE7))
        LogLevel.INFO -> Triple(Color(0xFF1E293B), Color(0xFF93C5FD), Color(0xFFE2E8F0))
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !entry.details.isNullOrBlank()) {
                isExpanded = !isExpanded
            }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Time
                Text(
                    text = timeStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF888899)
                )

                // Level Badge
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = entry.level.name,
                        color = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Category Badge
                Surface(
                    color = Color(0xFF261D2E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "[${entry.category.name}]",
                        color = Color(0xFFCE93D8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Tag
                Text(
                    text = entry.tag,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.White
                )

                if (!entry.details.isNullOrBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isExpanded) "▲ Kapat" else "▼ Detay",
                        fontSize = 10.sp,
                        color = UbuntuOrange,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Message
            Text(
                text = entry.message,
                color = contentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Expanded Details Block
            if (isExpanded && !entry.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0D0D14),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.details,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
