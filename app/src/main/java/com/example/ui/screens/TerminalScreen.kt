package com.example.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun TerminalScreen(
    terminalLines: List<TerminalOutputLine>,
    isTerminalRunning: Boolean,
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

    // Auto scroll to bottom when new line arrives
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
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
                            .background(if (isTerminalRunning) TerminalGreen else Color(0xFFE53935))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTerminalRunning) "ubuntu@arm64: active" else "ubuntu@arm64: stopped",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTerminalRunning) TerminalGreen else Color.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTerminalRunning) {
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
                color = TerminalGreen,
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
                        text = "Ubuntu komutu yazın (örn: ls -la, uname -m)",
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
