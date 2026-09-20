package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
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
    if (window.__ubuntuTouchpadInstalled) {
        console.log("[UbuntuARM64] Trackpad engine already active.");
        if (typeof window.__ubuntuResetCursor === 'function') window.__ubuntuResetCursor();
        return;
    }
    window.__ubuntuTouchpadInstalled = true;
    console.log("[UbuntuARM64] Initializing High-Precision Full-Screen Trackpad Engine...");

    function getCanvas() {
        return document.getElementById('noVNC_canvas') || document.querySelector('canvas');
    }

    function getRfb() {
        if (window.UI && window.UI.rfb) return window.UI.rfb;
        if (window.rfb) return window.rfb;
        return null;
    }

    function getFbSize() {
        var rfb = getRfb();
        var canvas = getCanvas();
        var w = (rfb && rfb._fbWidth) || (canvas && canvas.width) || 1280;
        var h = (rfb && rfb._fbHeight) || (canvas && canvas.height) || 720;
        return { w: w, h: h };
    }

    // Virtual cursor in remote desktop coordinates
    var curX = 640;
    var curY = 360;

    function sendPointer(x, y, mask) {
        var rfb = getRfb();
        if (!rfb) return;
        try {
            var rx = Math.round(x);
            var ry = Math.round(y);
            if (typeof rfb.sendPointerEvent === 'function') {
                rfb.sendPointerEvent(rx, ry, mask);
            } else if (typeof rfb._handleMouseMove === 'function') {
                rfb._handleMouseMove(rx, ry);
                if (typeof rfb._handleMouseButton === 'function') {
                    rfb._handleMouseButton(rx, ry, mask > 0 ? 1 : 0, mask);
                }
            }
        } catch(e) {
            console.warn("[UbuntuARM64] sendPointer error:", e);
        }
    }

    // Sleek, high-contrast visual cursor for 0-latency feedback
    var cursorEl = document.getElementById('ubuntu_trackpad_cursor');
    if (!cursorEl) {
        cursorEl = document.createElement('div');
        cursorEl.id = 'ubuntu_trackpad_cursor';
        cursorEl.innerHTML = '<svg width="22" height="22" viewBox="0 0 24 24" style="filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.85)); display: block;"><path d="M3 2l10 16-3.8-1-2.4 5.3-2.6-1.2 2.4-5.2L3 17V2z" fill="#E95420" stroke="#FFFFFF" stroke-width="1.3"/></svg>';
        cursorEl.style.position = 'fixed';
        cursorEl.style.pointerEvents = 'none';
        cursorEl.style.zIndex = '999999';
        cursorEl.style.transform = 'translate(-2px, -2px)';
        cursorEl.style.transition = 'transform 0.1s ease';
        document.body.appendChild(cursorEl);
    }

    function updateCursorVisual() {
        var canvas = getCanvas();
        if (!canvas || !cursorEl) return;
        var rect = canvas.getBoundingClientRect();
        var sz = getFbSize();
        if (sz.w <= 0 || sz.h <= 0 || rect.width <= 0) return;
        var screenX = rect.left + (curX / sz.w) * rect.width;
        var screenY = rect.top + (curY / sz.h) * rect.height;
        cursorEl.style.left = screenX + 'px';
        cursorEl.style.top = screenY + 'px';
    }

    window.__ubuntuResetCursor = function() {
        var sz = getFbSize();
        curX = Math.round(sz.w / 2);
        curY = Math.round(sz.h / 2);
        sendPointer(curX, curY, 0);
        updateCursorVisual();
    };

    // Public click API for toolbar buttons
    window.__ubuntuClick = function(button) {
        var mask = (button === 2) ? 4 : (button === 1 ? 2 : 1);
        sendPointer(curX, curY, mask);
        if (cursorEl) {
            cursorEl.style.transform = 'translate(-2px, -2px) scale(0.85)';
        }
        setTimeout(function() {
            sendPointer(curX, curY, 0);
            if (cursorEl) cursorEl.style.transform = 'translate(-2px, -2px) scale(1.0)';
        }, 75);
    };

    window.__ubuntuDoubleClick = function() {
        window.__ubuntuClick(0);
        setTimeout(function() {
            window.__ubuntuClick(0);
        }, 110);
    };

    window.__ubuntuScroll = function(direction) {
        var mask = direction < 0 ? 8 : 16;
        sendPointer(curX, curY, mask);
        setTimeout(function() {
            sendPointer(curX, curY, 0);
        }, 50);
    };

    // Trackpad gesture state
    var trackingTouchId = null;
    var prevX = 0, prevY = 0;
    var touchStartTime = 0;
    var totalMoved = 0;
    var longPressTimer = null;
    var lastTapTime = 0;
    var isDragHolding = false;
    var twoFingerPrevY = 0;

    // Silence conflicting native touch gestures from noVNC
    function neutralizeNoVncGestures() {
        var rfb = getRfb();
        if (rfb && rfb._gesture) {
            try { rfb._gesture = null; } catch(e) {}
        }
    }

    // Apply strict touch styles to page container
    try {
        if (!document.getElementById('ubuntu_trackpad_styles')) {
            var st = document.createElement('style');
            st.id = 'ubuntu_trackpad_styles';
            st.innerHTML = 'html, body, #noVNC_container, #noVNC_canvas {' +
                '  touch-action: none !important;' +
                '  user-select: none !important;' +
                '  -webkit-user-select: none !important;' +
                '  overscroll-behavior: none !important;' +
                '  overflow: hidden !important;' +
                '  background: #181824 !important;' +
                '} #noVNC_canvas { object-fit: contain; }';
            document.head.appendChild(st);
        }
    } catch(e) {}

    // TOUCH START
    window.addEventListener('touchstart', function(e) {
        neutralizeNoVncGestures();

        // 2-finger scroll
        if (e.touches.length === 2) {
            clearTimeout(longPressTimer);
            twoFingerPrevY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        if (e.touches.length === 1) {
            var t = e.touches[0];
            trackingTouchId = t.identifier;
            prevX = t.clientX;
            prevY = t.clientY;
            touchStartTime = Date.now();
            totalMoved = 0;

            // Double-tap detection -> Drag & Drop lock
            var now = Date.now();
            if (now - lastTapTime < 290) {
                isDragHolding = true;
                sendPointer(curX, curY, 1);
                if (cursorEl) cursorEl.style.transform = 'translate(-2px, -2px) scale(1.3)';
            }

            // Long-press detection (500ms still -> Right Click)
            clearTimeout(longPressTimer);
            longPressTimer = setTimeout(function() {
                if (totalMoved < 7 && trackingTouchId !== null && !isDragHolding) {
                    sendPointer(curX, curY, 4);
                    setTimeout(function() {
                        sendPointer(curX, curY, 0);
                    }, 80);
                    if (cursorEl) {
                        cursorEl.style.transform = 'translate(-2px, -2px) scale(1.4)';
                        setTimeout(function() { cursorEl.style.transform = 'translate(-2px, -2px) scale(1.0)'; }, 160);
                    }
                }
            }, 500);

            e.preventDefault();
            e.stopPropagation();
        }
    }, { capture: true, passive: false });

    // TOUCH MOVE (Relative Touchpad Motion)
    window.addEventListener('touchmove', function(e) {
        // Handle 2-finger scroll
        if (e.touches.length === 2) {
            var currentY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
            var scrollDy = currentY - twoFingerPrevY;
            if (Math.abs(scrollDy) > 16) {
                window.__ubuntuScroll(scrollDy > 0 ? -1 : 1);
                twoFingerPrevY = currentY;
            }
            e.preventDefault();
            e.stopPropagation();
            return;
        }

        // 1-finger relative trackpad movement
        for (var i = 0; i < e.changedTouches.length; i++) {
            var t = e.changedTouches[i];
            if (t.identifier === trackingTouchId) {
                var dx = t.clientX - prevX;
                var dy = t.clientY - prevY;
                prevX = t.clientX;
                prevY = t.clientY;

                var dist = Math.hypot(dx, dy);
                totalMoved += dist;
                if (totalMoved > 6) {
                    clearTimeout(longPressTimer);
                }

                // Smooth dynamic acceleration
                // Slow: 1.15x for pixel precision; Fast: up to 1.9x for rapid traversal
                var accel = dist > 14 ? 1.9 : (dist > 7 ? 1.45 : 1.15);

                var sz = getFbSize();
                curX = Math.max(0, Math.min(sz.w - 1, curX + (dx * accel)));
                curY = Math.max(0, Math.min(sz.h - 1, curY + (dy * accel)));

                var mask = isDragHolding ? 1 : 0;
                sendPointer(curX, curY, mask);
                updateCursorVisual();

                e.preventDefault();
                e.stopPropagation();
                break;
            }
        }
    }, { capture: true, passive: false });

    // TOUCH END
    window.addEventListener('touchend', function(e) {
        clearTimeout(longPressTimer);

        for (var i = 0; i < e.changedTouches.length; i++) {
            var t = e.changedTouches[i];
            if (t.identifier === trackingTouchId) {
                var duration = Date.now() - touchStartTime;

                if (isDragHolding) {
                    isDragHolding = false;
                    sendPointer(curX, curY, 0);
                    if (cursorEl) cursorEl.style.transform = 'translate(-2px, -2px) scale(1.0)';
                    lastTapTime = 0;
                } else if (totalMoved < 7 && duration < 320) {
                    // Crisp Single Tap -> Left Click!
                    sendPointer(curX, curY, 1);
                    setTimeout(function() {
                        sendPointer(curX, curY, 0);
                    }, 65);

                    if (cursorEl) {
                        cursorEl.style.transform = 'translate(-2px, -2px) scale(0.85)';
                        setTimeout(function() { cursorEl.style.transform = 'translate(-2px, -2px) scale(1.0)'; }, 120);
                    }
                    lastTapTime = Date.now();
                } else {
                    lastTapTime = 0;
                }

                trackingTouchId = null;
                e.preventDefault();
                e.stopPropagation();
                break;
            }
        }
    }, { capture: true, passive: false });

    window.addEventListener('touchcancel', function() {
        clearTimeout(longPressTimer);
        if (isDragHolding) {
            isDragHolding = false;
            sendPointer(curX, curY, 0);
            if (cursorEl) cursorEl.style.transform = 'translate(-2px, -2px) scale(1.0)';
        }
        trackingTouchId = null;
    }, { capture: true, passive: false });

    // Collapse noVNC side control bar automatically
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

    // Position cursor at center on start
    setTimeout(function() {
        window.__ubuntuResetCursor();
    }, 600);

    console.log("[UbuntuARM64] Trackpad Engine successfully mounted.");
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
                            overScrollMode = View.OVER_SCROLL_NEVER
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false

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

                            // Touchpad Mode Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = UbuntuOrange.copy(alpha = 0.25f),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mouse,
                                        contentDescription = null,
                                        tint = UbuntuOrange,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Touchpad",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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

                            // Quick Double Click
                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ubuntuDoubleClick();", null)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Çift Tık",
                                    tint = TerminalGreen,
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
