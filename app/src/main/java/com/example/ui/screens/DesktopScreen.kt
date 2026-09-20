package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.DesktopResolution
import com.example.model.DesktopState
import com.example.model.InstallState
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.UbuntuOrange
import com.example.ui.theme.UbuntuWarmOrange

private const val TOUCH_TO_MOUSE_JS = """
(function() {
    if (window.__ubuntuBridgeInstalled) return;
    window.__ubuntuBridgeInstalled = true;
    console.log("[UbuntuARM64] Initializing touch-to-mouse tracker...");

    window.__ubuntuMouseMode = 'direct';
    window.__ubuntuDragMode = false;
    var activeTouchId = null;
    var startX = 0, startY = 0;
    var lastX = 200, lastY = 200;
    var touchStartTime = 0;
    var moved = false;
    var longPressTimer = null;

    function getCanvas() {
        return document.getElementById('noVNC_canvas') || document.querySelector('canvas');
    }

    function getTarget() {
        return getCanvas() || document.getElementById('noVNC_container') || document.body;
    }

    function getRemoteCoords(clientX, clientY) {
        var canvas = getCanvas();
        if (!canvas) return { rx: Math.round(clientX), ry: Math.round(clientY) };
        var rect = canvas.getBoundingClientRect();
        var rfb = window.UI && window.UI.rfb;
        var fw = (rfb && rfb._fbWidth) || canvas.width || (rect.width > 0 ? rect.width : 1280);
        var fh = (rfb && rfb._fbHeight) || canvas.height || (rect.height > 0 ? rect.height : 720);
        var scaleX = fw / (rect.width || 1);
        var scaleY = fh / (rect.height || 1);
        var rx = Math.max(0, Math.min(fw - 1, Math.round((clientX - rect.left) * scaleX)));
        var ry = Math.max(0, Math.min(fh - 1, Math.round((clientY - rect.top) * scaleY)));
        return { rx: rx, ry: ry };
    }

    function sendPointerMove(rx, ry, clientX, clientY) {
        try {
            if (window.UI && window.UI.rfb) {
                var rfb = window.UI.rfb;
                if (typeof rfb._handleMouseMove === 'function') {
                    rfb._handleMouseMove(rx, ry);
                }
            }
        } catch (e) {
            console.error(e);
        }

        var target = getTarget();
        if (target) {
            var ev = new MouseEvent('mousemove', {
                clientX: clientX,
                clientY: clientY,
                screenX: clientX,
                screenY: clientY,
                button: 0,
                buttons: window.__ubuntuDragMode ? 1 : 0,
                bubbles: true,
                cancelable: true,
                view: window
            });
            target.dispatchEvent(ev);
        }
    }

    function sendPointerButton(rx, ry, clientX, clientY, button, isDown) {
        try {
            if (window.UI && window.UI.rfb) {
                var rfb = window.UI.rfb;
                if (typeof rfb._handleMouseButton === 'function') {
                    var mask = button === 2 ? 4 : (button === 1 ? 2 : 1);
                    rfb._handleMouseButton(rx, ry, isDown ? 1 : 0, mask);
                }
            }
        } catch (e) {
            console.error(e);
        }

        var target = getTarget();
        if (target) {
            var evType = isDown ? 'mousedown' : 'mouseup';
            var ev = new MouseEvent(evType, {
                clientX: clientX,
                clientY: clientY,
                button: button,
                buttons: isDown ? (button === 2 ? 2 : 1) : 0,
                bubbles: true,
                cancelable: true,
                view: window
            });
            target.dispatchEvent(ev);
            if (!isDown && button === 0) {
                target.dispatchEvent(new MouseEvent('click', {
                    clientX: clientX,
                    clientY: clientY,
                    button: 0,
                    bubbles: true,
                    cancelable: true,
                    view: window
                }));
            } else if (!isDown && button === 2) {
                target.dispatchEvent(new MouseEvent('contextmenu', {
                    clientX: clientX,
                    clientY: clientY,
                    button: 2,
                    bubbles: true,
                    cancelable: true,
                    view: window
                }));
            }
        }
    }

    window.__ubuntuClick = function(button) {
        var coords = getRemoteCoords(lastX, lastY);
        sendPointerButton(coords.rx, coords.ry, lastX, lastY, button, true);
        setTimeout(function() {
            sendPointerButton(coords.rx, coords.ry, lastX, lastY, button, false);
        }, 60);
    };

    window.__ubuntuToggleDrag = function(enable) {
        window.__ubuntuDragMode = enable;
        var coords = getRemoteCoords(lastX, lastY);
        if (enable) {
            sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, true);
        } else {
            sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, false);
        }
    };

    window.__ubuntuSetMode = function(mode) {
        window.__ubuntuMouseMode = mode;
    };

    window.addEventListener('touchstart', function(e) {
        if (e.touches.length === 1) {
            var t = e.touches[0];
            activeTouchId = t.identifier;
            startX = t.clientX;
            startY = t.clientY;
            touchStartTime = Date.now();
            moved = false;

            if (window.__ubuntuMouseMode === 'direct') {
                lastX = t.clientX;
                lastY = t.clientY;
                var coords = getRemoteCoords(lastX, lastY);
                sendPointerMove(coords.rx, coords.ry, lastX, lastY);

                if (window.__ubuntuDragMode) {
                    sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, true);
                }
            }

            clearTimeout(longPressTimer);
            longPressTimer = setTimeout(function() {
                if (!moved && activeTouchId !== null && !window.__ubuntuDragMode) {
                    var c = getRemoteCoords(lastX, lastY);
                    sendPointerButton(c.rx, c.ry, lastX, lastY, 2, true);
                    setTimeout(function() {
                        sendPointerButton(c.rx, c.ry, lastX, lastY, 2, false);
                    }, 60);
                }
            }, 500);
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchmove', function(e) {
        for (var i = 0; i < e.changedTouches.length; i++) {
            var t = e.changedTouches[i];
            if (t.identifier === activeTouchId) {
                var dist = Math.hypot(t.clientX - startX, t.clientY - startY);
                if (dist > 4) {
                    moved = true;
                    clearTimeout(longPressTimer);
                }

                if (window.__ubuntuMouseMode === 'direct') {
                    lastX = t.clientX;
                    lastY = t.clientY;
                } else {
                    var dx = t.clientX - startX;
                    var dy = t.clientY - startY;
                    lastX += dx * 1.3;
                    lastY += dy * 1.3;
                    startX = t.clientX;
                    startY = t.clientY;
                }

                var coords = getRemoteCoords(lastX, lastY);
                sendPointerMove(coords.rx, coords.ry, lastX, lastY);

                e.preventDefault();
                e.stopPropagation();
            }
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchend', function(e) {
        for (var i = 0; i < e.changedTouches.length; i++) {
            var t = e.changedTouches[i];
            if (t.identifier === activeTouchId) {
                clearTimeout(longPressTimer);
                var elapsed = Date.now() - touchStartTime;
                var dist = Math.hypot(t.clientX - startX, t.clientY - startY);
                var coords = getRemoteCoords(lastX, lastY);

                if (window.__ubuntuDragMode) {
                    sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, false);
                } else if (!moved || (elapsed < 320 && dist < 12)) {
                    sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, true);
                    setTimeout(function() {
                        sendPointerButton(coords.rx, coords.ry, lastX, lastY, 0, false);
                    }, 50);
                }

                activeTouchId = null;
            }
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchcancel', function() {
        clearTimeout(longPressTimer);
        activeTouchId = null;
    }, { capture: true, passive: false });

    setInterval(function() {
        try {
            if (window.UI && window.UI.rfb) {
                window.UI.rfb.showDotCursor = true;
            }
        } catch(e) {}
    }, 2000);

    console.log("[UbuntuARM64] Touch tracking bridge ready.");
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DesktopScreen(
    installState: InstallState,
    desktopState: DesktopState,
    selectedResolution: DesktopResolution,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (Boolean) -> Unit = {},
    onSelectResolution: (DesktopResolution) -> Unit,
    onInstallDesktop: () -> Unit,
    onStartDesktop: () -> Unit,
    onStopDesktop: () -> Unit,
    onGoToInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showToolbar by remember { mutableStateOf(true) }
    var mouseMode by remember { mutableStateOf("direct") } // "direct" or "trackpad"
    var isDragMode by remember { mutableStateOf(false) }

    if (installState !is InstallState.Installed) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.DesktopWindows,
                contentDescription = null,
                tint = UbuntuOrange,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ubuntu Henüz Kurulmamış",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "XFCE4 grafik masaüstünü çalıştırabilmek için lütfen önce 2. sekmeden Ubuntu ARM64'ü kurun.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoToInstall,
                colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kuruluma Git", color = Color.White)
            }
        }
        return
    }

    when (desktopState) {
        is DesktopState.Running -> {
            // Live Interactive In-App Desktop Viewer
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // Disable built-in zoom controls so swipe gestures are immediately passed to touch tracking
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(TOUCH_TO_MOUSE_JS, null)
                                }
                            }
                            webChromeClient = WebChromeClient()

                            loadUrl(desktopState.url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Toolbar (Collapsible)
                AnimatedVisibility(
                    visible = showToolbar,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEE1E1E2E),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TerminalGreen)
                            )
                            Text(
                                text = "XFCE4",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            // Mouse Tracking Mode (Direct Finger Follow vs Trackpad)
                            IconButton(
                                onClick = {
                                    mouseMode = if (mouseMode == "direct") "trackpad" else "direct"
                                    webViewRef?.evaluateJavascript("window.__ubuntuSetMode('$mouseMode');", null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (mouseMode == "direct") Icons.Default.TouchApp else Icons.Default.Mouse,
                                    contentDescription = if (mouseMode == "direct") "Parmak Takip Modu" else "Touchpad Modu",
                                    tint = if (mouseMode == "direct") TerminalGreen else UbuntuOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Window / Selection Drag Toggle
                            IconButton(
                                onClick = {
                                    isDragMode = !isDragMode
                                    webViewRef?.evaluateJavascript("window.__ubuntuToggleDrag($isDragMode);", null)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isDragMode) UbuntuOrange.copy(alpha = 0.35f) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PanTool,
                                    contentDescription = "Pencere Sürükleme",
                                    tint = if (isDragMode) UbuntuOrange else Color.LightGray,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Quick Right Click
                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ubuntuClick(2);", null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdsClick,
                                    contentDescription = "Sağ Tık",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Virtual Keyboard button
                            IconButton(
                                onClick = {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    webViewRef?.requestFocus()
                                    imm?.showSoftInput(webViewRef, InputMethodManager.SHOW_IMPLICIT)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Klavye", tint = UbuntuWarmOrange, modifier = Modifier.size(18.dp))
                            }

                            // Refresh button
                            IconButton(
                                onClick = {
                                    webViewRef?.reload()
                                    webViewRef?.evaluateJavascript(TOUCH_TO_MOUSE_JS, null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }

                            // Fullscreen toggle
                            IconButton(
                                onClick = { onToggleFullscreen(!isFullscreen) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isFullscreen) Color(0xFF4A148C) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Tam Ekrandan Çık" else "Tam Ekran",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Minimize toolbar toggle
                            IconButton(
                                onClick = { showToolbar = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Araç Çubuğunu Gizle", tint = Color.Gray, modifier = Modifier.size(15.dp))
                            }

                            // Stop server
                            IconButton(
                                onClick = onStopDesktop,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Durdur", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Small pill to toggle toolbar visibility if hidden
                if (!showToolbar) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xCC1E1E2E),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clickable { showToolbar = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Menüyü Göster", tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Araçlar", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        is DesktopState.Starting -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = UbuntuOrange,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "XFCE4 Masaüstü ve VNC Başlatılıyor...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TigerVNC sanal ekranı ve noVNC WebSocket köprüsü açılıyor (Port: 6080)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            // NotInstalled or Stopped Config/Launch Screen
            val scrollState = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Aşama 5: XFCE4 Grafik Arayüzü (GUI)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dahili HTML5 noVNC köprüsü ile telefon içinde tam masaüstü",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // XFCE4 Info Hero Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF261833))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(UbuntuOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DesktopWindows,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Ubuntu XFCE4 Masaüstü",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Xubuntu hafif masaüstü ve pencere yöneticisi",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD1C4E9)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "• Harici hiçbir VNC uygulaması (Termux, VNC Viewer vb.) gerekmez.\n" +
                                   "• Başlat menüsü, Thunar dosya yöneticisi ve görev çubuğu sunar.\n" +
                                   "• HTML5 Canvas ile doğrudan bu sekme içinde tam ekran çalışır.",
                            fontSize = 12.sp,
                            color = Color(0xFFE1BEE7),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Resolution Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Masaüstü Ekran Çözünürlüğü:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        DesktopResolution.values().forEach { res ->
                            val isSelected = res == selectedResolution
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF382A4A) else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, UbuntuOrange) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectResolution(res) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = res.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) UbuntuWarmOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = UbuntuOrange, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Card (Install or Start)
                if (desktopState is DesktopState.NotInstalled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2838))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "XFCE4 & TigerVNC Kurulumu Gerekli",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grafik arayüz için 'xfce4', 'tigervnc-standalone-server', 'novnc' ve 'websockify' paketleri kurulacaktır (~180 MB).",
                                fontSize = 12.sp,
                                color = Color(0xFFBBDEFB),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onInstallDesktop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("install_desktop_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("XFCE4 Masaüstünü Kur (~180 MB)", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    // Stopped -> Ready to Start
                    Button(
                        onClick = onStartDesktop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_desktop_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "XFCE4 Masaüstünü Başlat (${selectedResolution.geometry})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Architecture explanation card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Masaüstü Nasıl Çalışır?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. PRoot konteynerinde TigerVNC sunucusu sanal ekran (:1) oluşturur.\n" +
                                   "2. Websockify köprüsü VNC soketini (5901) yerel WebSocket'e (6080) çevirir.\n" +
                                   "3. noVNC HTML5 istemcisi doğrudan uygulamamızın içine gömülü WebView'de açılır.\n" +
                                   "4. Dokunmatik ekran fare ve klavye girdilerini anlık olarak masaüstüne iletir.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
