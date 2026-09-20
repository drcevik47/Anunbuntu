package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SystemInfo
import com.example.ui.theme.UbuntuOrange
import com.example.ui.theme.UbuntuWarmOrange

@Composable
fun SystemCheckScreen(
    systemInfo: SystemInfo,
    onRefresh: () -> Unit,
    onProceedToInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Aşama 1: Sistem & Mimari Analizi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cihazın Ubuntu ARM64 uyumluluğu kontrol ediliyor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_system_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sistemi Yeniden Tara",
                    tint = UbuntuOrange
                )
            }
        }

        // 1. Architecture Check Card (Crucial for ARM64)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (systemInfo.isArm64) Color(0xFF1B3B2B) else Color(0xFF3E2723)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (systemInfo.isArm64) Color(0xFF2E7D32) else Color(0xFFD32F2F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (systemInfo.isArm64) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Mimari Durumu",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (systemInfo.isArm64) "ARM64 Mimarisi Uyumlu" else "ARM64 Mimarisi Bulunamadı",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (systemInfo.isArm64) {
                            "Cihazınız 64-bit ARM (${systemInfo.primaryAbi}) mimarisini destekliyor. Ubuntu ARM64 doğrudan çalıştırılabilir."
                        } else {
                            "Birincil ABI: ${systemInfo.primaryAbi}. Ubuntu ARM64 için 64-bit aarch64 gereklidir."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFE0E0E0)
                    )
                }
            }
        }

        // 2. Storage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Depolama",
                        tint = UbuntuOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dahili Depolama Alanı",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                val storagePercent = if (systemInfo.totalStorageMb > 0) {
                    (systemInfo.totalStorageMb - systemInfo.freeStorageMb).toFloat() / systemInfo.totalStorageMb.toFloat()
                } else 0f

                LinearProgressIndicator(
                    progress = { storagePercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (systemInfo.hasEnoughSpace) UbuntuOrange else Color(0xFFEF5350),
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Boş: ${systemInfo.freeStorageMb} MB (~${systemInfo.freeStorageMb / 1024} GB)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (systemInfo.hasEnoughSpace) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                    Text(
                        text = "Gereken: En az 500 MB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Hardware & Environment Specifications
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Donanım ve Çekirdek Ayrıntıları",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DetailRow(
                    icon = Icons.Default.DeveloperBoard,
                    label = "Desteklenen ABI Listesi",
                    value = systemInfo.allSupportedAbis.joinToString(", ")
                )

                DetailRow(
                    icon = Icons.Default.Memory,
                    label = "RAM Bellek",
                    value = "Kullanılabilir ${systemInfo.availableRamMb} MB / Toplam ${systemInfo.totalRamMb} MB"
                )

                DetailRow(
                    icon = Icons.Default.DeveloperBoard,
                    label = "Android Sürümü",
                    value = "Android ${systemInfo.androidVersion} (API ${systemInfo.apiLevel})"
                )

                Column {
                    Text(
                        text = "Linux Çekirdeği (Kernel):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = systemInfo.kernelVersion,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 4. Feature Summary / Without Termux note
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF241432),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Neden Harici Uygulama (Termux vb.) Gerekmez?",
                    fontWeight = FontWeight.Bold,
                    color = UbuntuWarmOrange,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bu uygulama doğrudan ARM64 Ubuntu minimal rootfs arşivini indirir, uygulamanın kendi korumalı dosya dizinine çıkartır ve tüm DNS, bash ve terminal akışlarını dahili olarak yönetir.",
                    fontSize = 12.sp,
                    color = Color(0xFFD1C4E9),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Proceed Button
        Button(
            onClick = onProceedToInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("proceed_to_install_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = UbuntuOrange
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Aşama 2: Ubuntu Kurulumuna Geç",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "İlerle",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = UbuntuOrange
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
