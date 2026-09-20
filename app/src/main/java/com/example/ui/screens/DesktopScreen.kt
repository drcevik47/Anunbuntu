package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    if (window.__ubuntuBridgeInstalled) {
        console.log("[UbuntuARM64] Bridge already loaded, refreshing hooks.");
        return;
    }
    window.__ubuntuBridgeInstalled = true;
    console.log("[UbuntuARM64] Initializing refined touch-to-mouse bridge...");

    window.__ubuntuMouseMode = 'direct'; // 'direct' or 'trackpad'
    window.__ubuntuDragMode = false;
    var activeTouchId = null;
    var startClientX = 0, startClientY = 0;
    var lastClientX = 300, lastClientY = 300;
    var touchStartTime = 0;
    var hasMoved = false;
    var longPressTimer = null;
    var lastSentRx = -1, lastSentRy = -1;

    function getCanvas() {
        return document.getElementById('noVNC_canvas') || document.querySelector('canvas');
    }

    function getRfb() {
        if (window.UI && window.UI.rfb) return window.UI.rfb;
        if (window.rfb) return window.rfb;
        return null;
    }

    function getRemoteCoords(clientX, clientY) {
        var canvas = getCanvas();
        if (!canvas) return { rx: Math.round(clientX), ry: Math.round(clientY) };
        var rect = canvas.getBoundingClientRect();
        var rfb = getRfb();
        var fw = (rfb && rfb._fbWidth) || canvas.width || (rect.width > 0 ? rect.width : 1280);
        var fh = (rfb && rfb._fbHeight) || canvas.height || (rect.height > 0 ? rect.height : 720);
        var w = rect.width > 0 ? rect.width : fw;
        var h = rect.height > 0 ? rect.height : fh;
        var rx = Math.max(0, Math.min(fw - 1, Math.round((clientX - rect.left) * (fw / w))));
        var ry = Math.max(0, Math.min(fh - 1, Math.round((clientY - rect.top) * (fh / h))));
        return { rx: rx, ry: ry, fw: fw, fh: fh };
    }

    function sendMouseMove(rx, ry) {
        if (rx === lastSentRx && ry === lastSentRy) return;
        lastSentRx = rx;
        lastSentRy = ry;

        var rfb = getRfb();
        if (rfb) {
            try {
                if (typeof rfb._handleMouseMove === 'function') {
                    rfb._handleMouseMove(rx, ry);
                    return;
                } else if (typeof rfb.sendPointerEvent === 'function') {
                    rfb.sendPointerEvent(rx, ry, window.__ubuntuDragMode ? 1 : 0);
                    return;
                }
            } catch (e) {
                console.warn("[UbuntuARM64] RFB mouse move error", e);
            }
        }

        var canvas = getCanvas() || document.body;
        if (canvas) {
            var ev = new MouseEvent('mousemove', {
                clientX: lastClientX,
                clientY: lastClientY,
                bubbles: true,
                cancelable: true,
                view: window
            });
            canvas.dispatchEvent(ev);
        }
    }

    function sendMouseButton(rx, ry, button, isDown) {
        var mask = button === 2 ? 4 : (button === 1 ? 2 : 1);
        var rfb = getRfb();
        if (rfb) {
            try {
                if (typeof rfb._handleMouseButton === 'function') {
                    rfb._handleMouseButton(rx, ry, isDown ? 1 : 0, mask);
                    return;
                } else if (typeof rfb.sendPointerEvent === 'function') {
                    rfb.sendPointerEvent(rx, ry, isDown ? mask : 0);
                    return;
                }
            } catch (e) {
                console.warn("[UbuntuARM64] RFB mouse button error", e);
            }
        }

        var canvas = getCanvas() || document.body;
        if (canvas) {
            var evType = isDown ? 'mousedown' : 'mouseup';
            var ev = new MouseEvent(evType, {
                clientX: lastClientX,
                clientY: lastClientY,
                button: button,
                buttons: isDown ? mask : 0,
                bubbles: true,
                cancelable: true,
                view: window
            });
            canvas.dispatchEvent(ev);
            if (!isDown && button === 0) {
                canvas.dispatchEvent(new MouseEvent('click', {
                    clientX: lastClientX,
                    clientY: lastClientY,
                    button: 0,
                    bubbles: true,
                    cancelable: true,
                    view: window
                }));
            }
        }
    }

    window.__ubuntuClick = function(button) {
        var coords = getRemoteCoords(lastClientX, lastClientY);
        sendMouseMove(coords.rx, coords.ry);
        sendMouseButton(coords.rx, coords.ry, button, true);
        setTimeout(function() {
            sendMouseButton(coords.rx, coords.ry, button, false);
        }, 80);
    };

    window.__ubuntuToggleDrag = function(enable) {
        window.__ubuntuDragMode = !!enable;
        var coords = getRemoteCoords(lastClientX, lastClientY);
        sendMouseButton(coords.rx, coords.ry, 0, window.__ubuntuDragMode);
    };

    window.__ubuntuSetMode = function(mode) {
        window.__ubuntuMouseMode = mode;
        console.log("[UbuntuARM64] Mouse mode set to: " + mode);
    };

    // Disable default noVNC touch handlers that fight with our custom pointer tracking
    function neutralizeNoVncTouch() {
        var rfb = getRfb();
        if (rfb) {
            try {
                // If RFB has its own touch handler object, disable or replace it
                if (rfb._gesture) {
                    rfb._gesture = null;
                }
            } catch (e) {}
        }
    }

    // Capture touch events at the top window level to guarantee clean, smooth translation
    window.addEventListener('touchstart', function(e) {
        neutralizeNoVncTouch();
        if (e.touches.length === 1) {
            var t = e.touches[0];
            activeTouchId = t.identifier;
            startClientX = t.clientX;
            startClientY = t.clientY;
            touchStartTime = Date.now();
            hasMoved = false;

            if (window.__ubuntuMouseMode === 'direct') {
                lastClientX = t.clientX;
                lastClientY = t.clientY;
                var coords = getRemoteCoords(lastClientX, lastClientY);
                sendMouseMove(coords.rx, coords.ry);

                if (window.__ubuntuDragMode) {
                    sendMouseButton(coords.rx, coords.ry, 0, true);
                }
            }

            clearTimeout(longPressTimer);
            // Long-press opens context menu (Right-click) if finger stayed still
            longPressTimer = setTimeout(function() {
                if (!hasMoved && activeTouchId !== null && !window.__ubuntuDragMode) {
                    var c = getRemoteCoords(lastClientX, lastClientY);
                    sendMouseButton(c.rx, c.ry, 2, true);
                    setTimeout(function() {
                        sendMouseButton(c.rx, c.ry, 2, false);
                    }, 80);
                }
            }, 550);
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchmove', function(e) {
        for (var i = 0; i < e.changedTouches.length; i++) {
            var t = e.changedTouches[i];
            if (t.identifier === activeTouchId) {
                var delta = Math.hypot(t.clientX - startClientX, t.clientY - startClientY);
                if (delta > 6) {
                    hasMoved = true;
                    clearTimeout(longPressTimer);
                }

                if (window.__ubuntuMouseMode === 'direct') {
                    lastClientX = t.clientX;
                    lastClientY = t.clientY;
                } else {
                    // Trackpad relative delta with smooth acceleration
                    var dx = t.clientX - startClientX;
                    var dy = t.clientY - startClientY;
                    lastClientX += dx * 1.2;
                    lastClientY += dy * 1.2;
                    startClientX = t.clientX;
                    startClientY = t.clientY;
                }

                var coords = getRemoteCoords(lastClientX, lastClientY);
                sendMouseMove(coords.rx, coords.ry);

                // Stop browser pull-to-refresh or accidental viewport scroll
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
                var duration = Date.now() - touchStartTime;
                var totalDist = Math.hypot(t.clientX - startClientX, t.clientY - startClientY);
                var coords = getRemoteCoords(lastClientX, lastClientY);

                if (window.__ubuntuDragMode) {
                    sendMouseButton(coords.rx, coords.ry, 0, false);
                } else if (!hasMoved && duration < 400 && totalDist < 12) {
                    // Clean, single, crisp left click
                    sendMouseMove(coords.rx, coords.ry);
                    sendMouseButton(coords.rx, coords.ry, 0, true);
                    setTimeout(function() {
                        sendMouseButton(coords.rx, coords.ry, 0, false);
                    }, 60);
                }

                activeTouchId = null;
            }
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchcancel', function() {
        clearTimeout(longPressTimer);
        activeTouchId = null;
    }, { capture: true, passive: false });

    function collapseNoVncBar() {
        try {
            var bar = document.getElementById('noVNC_control_bar');
            if (bar && bar.classList.contains('noVNC_open')) {
                bar.classList.remove('noVNC_open');
            }
            if (window.UI && typeof window.UI.closeControlBar === 'function') {
                window.UI.closeControlBar();
            }
        } catch(e) {}
    }
    collapseNoVncBar();
    setTimeout(collapseNoVncBar, 400);
    setTimeout(collapseNoVncBar, 1200);

    try {
        if (!document.getElementById('ubuntu_custom_fit')) {
            var styleEl = document.createElement('style');
            styleEl.id = 'ubuntu_custom_fit';
            styleEl.innerHTML = 'html, body { overflow: hidden !important; margin: 0 !important; padding: 0 !important; background: #181824 !important; user-select: none !important; -webkit-user-select: none !important; touch-action: none !important; } #noVNC_canvas { object-fit: contain; touch-action: none !important; }';
            document.head.appendChild(styleEl);
        }
    } catch(e) {}

    setInterval(function() {
        try {
            var rfb = getRfb();
            if (rfb) {
                rfb.showDotCursor = true;
            }
        } catch(e) {}
    }, 2000);

    console.log("[UbuntuARM64] Touch-to-mouse bridge refined & ready.");
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

    var isPageLoading by remember { mutableStateOf(true) }

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
                                    isPageLoading = false
                                    view?.evaluateJavascript(TOUCH_TO_MOUSE_JS, null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        // VNC/Websockify might need a moment to accept connections; auto retry
                                        view?.postDelayed({
                                            view.reload()
                                        }, 1200)
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()

                            loadUrl(desktopState.url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Seamless Dark Loading Overlay while WebView connects
                if (isPageLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = UbuntuOrange,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "XFCE4 Masaüstüne Bağlanılıyor...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

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

                            // Quick Left Click
                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ubuntuClick(0);", null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mouse,
                                    contentDescription = "Sol Tık",
                                    tint = Color.White,
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
