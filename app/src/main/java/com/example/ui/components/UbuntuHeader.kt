package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InstallState
import com.example.ui.theme.UbuntuDarkAubergine
import com.example.ui.theme.UbuntuOrange
import com.example.ui.theme.UbuntuWarmOrange

@Composable
fun UbuntuHeader(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    installState: InstallState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = UbuntuDarkAubergine,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(UbuntuOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ">_",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ubuntu",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ARM64",
                                color = UbuntuWarmOrange,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            )
                        }
                        Text(
                            text = "Termux gerektirmeyen bağımsız Linux",
                            color = Color(0xFFD1C4E9),
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Pill
                val (statusText, statusBg, statusColor) = when (installState) {
                    is InstallState.Installed -> Triple("Hazır", Color(0xFF1B5E20), Color(0xFF81C784))
                    is InstallState.Downloading -> Triple("İndiriliyor", Color(0xFFE65100), Color(0xFFFFCC80))
                    is InstallState.Extracting -> Triple("Kuruluyor", Color(0xFF4A148C), Color(0xFFCE93D8))
                    is InstallState.Configuring -> Triple("Ayar", Color(0xFF4A148C), Color(0xFFCE93D8))
                    is InstallState.Error -> Triple("Hata", Color(0xFFB71C1C), Color(0xFFEF9A9A))
                    is InstallState.Idle -> Triple("Kurulu Değil", Color(0xFF37474F), Color(0xFFCFD8DC))
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusBg,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Step Navigation Tabs
            val tabs = listOf(
                Triple("1. Sistem", Icons.Default.Info, "tab_system"),
                Triple("2. Kurulum", Icons.Default.CheckCircle, "tab_install"),
                Triple("3. Terminal", Icons.Default.Terminal, "tab_terminal"),
                Triple("4. Paketler", Icons.Default.Widgets, "tab_packages"),
                Triple("5. Masaüstü", Icons.Default.DesktopWindows, "tab_desktop")
            )

            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = UbuntuWarmOrange,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = UbuntuOrange,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                tabs.forEachIndexed { index, (title, icon, testTag) ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { onTabSelected(index) },
                        modifier = Modifier.testTag(testTag),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (activeTab == index) UbuntuOrange else Color(0xFFAAA0B8)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == index) Color.White else Color(0xFFAAA0B8)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
