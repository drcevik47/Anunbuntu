package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InstallState
import com.example.model.LineType
import com.example.model.TerminalOutputLine
import com.example.ui.theme.TerminalBarBg
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalText
import com.example.ui.theme.TerminalYellow
import com.example.ui.theme.UbuntuOrange
import com.example.ui.theme.UbuntuWarmOrange

@Composable
fun BlinkingCursor(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    Box(
        modifier = modifier
            .width(8.dp)
            .height(15.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(TerminalGreen)
    )
}

@Composable
fun TerminalScreen(
    terminalLines: List<TerminalOutputLine>,
    isTerminalRunning: Boolean,
    isCommandExecuting: Boolean = false,
    installState: InstallState,
    onSendCommand: (String) -> Unit,
    onSendSpecialKey: (String) -> Unit,
    onStartTerminal: () -> Unit,
    onStopTerminal: () -> Unit,
    onClearTerminal: () -> Unit,
    onGoToInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new line arrives or execution state changes
    LaunchedEffect(terminalLines.size, isCommandExecuting) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        // 1. Top Terminal Toolbar
        Surface(
            color = TerminalBarBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !isTerminalRunning -> Color(0xFFE53935)
                                    isCommandExecuting -> UbuntuWarmOrange
                                    else -> TerminalGreen
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when {
                                !isTerminalRunning -> "ubuntu@arm64: kapalı"
                                isCommandExecuting -> "ubuntu@arm64: ÇALIŞIYOR"
                                else -> "ubuntu@arm64: HAZIR"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                !isTerminalRunning -> Color.Gray
                                isCommandExecuting -> UbuntuWarmOrange
                                else -> TerminalGreen
                            }
                        )
                        if (isTerminalRunning) {
                            Text(
                                text = if (isCommandExecuting) "⏳ Komut yürütülüyor..." else "✓ Komut bekleniyor",
                                fontSize = 10.sp,
                                color = if (isCommandExecuting) UbuntuWarmOrange.copy(alpha = 0.9f) else Color.Gray
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCommandExecuting) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFD32F2F),
                            modifier = Modifier
                                .clickable { onSendSpecialKey("CTRL_C") }
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Ctrl+C Durdur",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isTerminalRunning) {
                        IconButton(
                            onClick = {
                                onStopTerminal()
                                onStartTerminal()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("restart_terminal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yeniden Başlat",
                                tint = UbuntuWarmOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onStopTerminal,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("stop_terminal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Durdur",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onStartTerminal,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("start_terminal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Başlat",
                                tint = TerminalGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearTerminal,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clear_terminal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Temizle",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Quick Command Chips
        val quickCommands = listOf(
            "rm -f /etc/apt/apt.conf.d/00_debconf 2>/dev/null; chmod -R 755 /usr/share/debconf /var/lib/dpkg/info 2>/dev/null; dpkg --configure -a" to "🛠️ DPKG Onar",
            "rm -f /etc/apt/apt.conf.d/00_debconf 2>/dev/null; apt-get update && apt --fix-broken install -y" to "📦 Paket Düzelt",
            "apt-get update && apt-get install -y --no-install-recommends xfce4 xfce4-terminal tigervnc-standalone-server tigervnc-common novnc websockify dbus-x11 adwaita-icon-theme" to "🖥️ Masaüstü Kur",
            "cat /etc/os-release" to "OS Bilgisi",
            "uname -m" to "Mimari",
            "df -h" to "Disk",
            "free -m" to "RAM",
            "ls -la /" to "RootFS",
            "pwd && whoami" to "Kullanıcı",
            "cat /etc/resolv.conf" to "DNS"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161020))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { (cmd, label) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF281E38),
                    modifier = Modifier.clickable { onSendCommand(cmd) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = UbuntuWarmOrange
                        )
                    }
                }
            }
        }

        // 2. Terminal Log Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (terminalLines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ubuntu ARM64 Terminal Konsolu",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (installState is InstallState.Installed) {
                            "Başlatmak için yukarıdaki 'Başlat' butonuna basın veya aşağıdaki komut alanından bir komut yazın."
                        } else {
                            "Ubuntu henüz kurulmamış. Lütfen '2. Kurulum' sekmesinden Ubuntu'yu yükleyin."
                        },
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (installState !is InstallState.Installed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onGoToInstall,
                            colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Kurulum Sekmesine Git", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(terminalLines, key = { it.id }) { line ->
                        val textColor = when (line.type) {
                            LineType.STDOUT -> TerminalText
                            LineType.STDERR -> TerminalRed
                            LineType.STDIN -> TerminalGreen
                            LineType.SYSTEM -> TerminalCyan
                            LineType.SUCCESS -> TerminalGreen
                            LineType.WARNING -> TerminalYellow
                        }

                        Text(
                            text = line.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dynamic Terminal Status / Cursor at Bottom
                    if (isTerminalRunning) {
                        if (isCommandExecuting) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF26180B), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = UbuntuWarmOrange,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Komut yürütülüyor... Çıktı bekleniyor",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UbuntuWarmOrange
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD32F2F),
                                        modifier = Modifier.clickable { onSendSpecialKey("CTRL_C") }
                                    ) {
                                        Text(
                                            text = "CTRL+C",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "root@ubuntu-arm64:~# ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TerminalGreen
                                    )
                                    BlinkingCursor()
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Mobile Keyboard Accessory Bar (Unix keys missing from on-screen keyboards)
        val specialKeys = listOf(
            "CTRL_C" to "Ctrl+C",
            "TAB" to "Tab",
            "CTRL_D" to "Ctrl+D",
            "ESC" to "Esc",
            "UP" to "↑",
            "DOWN" to "↓",
            "|" to "|",
            "/" to "/",
            "-" to "-",
            "~" to "~"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1424))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            specialKeys.forEach { (action, label) ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2C223C),
                    modifier = Modifier.clickable {
                        if (action.startsWith("CTRL_") || action == "TAB" || action == "ESC" || action == "UP" || action == "DOWN") {
                            onSendSpecialKey(action)
                        } else {
                            commandInput += action
                        }
                    }
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFE1BEE7),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 4. Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBarBg)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#",
                color = if (isCommandExecuting) UbuntuWarmOrange else TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 8.dp)
            )

            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                placeholder = {
                    Text(
                        text = if (isCommandExecuting) "Komut çalışıyor... (Durdur: Ctrl+C)" else "Ubuntu komutu yazın (örn: ls -la, uname -m)",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.DarkGray
                    )
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UbuntuOrange,
                    unfocusedBorderColor = Color(0xFF382A4A),
                    cursorColor = TerminalGreen
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commandInput.isNotBlank()) {
                        onSendCommand(commandInput)
                        commandInput = ""
                    }
                })
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onSendCommand(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(UbuntuOrange, RoundedCornerShape(8.dp))
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Komut Gönder",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
