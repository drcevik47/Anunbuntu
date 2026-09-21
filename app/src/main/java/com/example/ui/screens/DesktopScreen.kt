package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.BugReport
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
    console.log("[UbuntuARM64] Initializing unified single-cursor touch & mouse engine...");

    // 1. Permanently remove duplicate custom SVG cursor if previously injected
    try {
        var oldCursor = document.getElementById('ubuntu_trackpad_cursor');
        if (oldCursor) oldCursor.remove();
    } catch(e) {}

    // Input Mode: true = Touchpad (relative cursor), false = Direct Touch (touchscreen tap)
    if (typeof window.__ubuntuIsTouchpadMode === 'undefined') {
        window.__ubuntuIsTouchpadMode = true;
    }

    window.__ubuntuSetMode = function(isTouchpad) {
        window.__ubuntuIsTouchpadMode = !!isTouchpad;
        console.log("[UbuntuARM64] Input mode set to:", window.__ubuntuIsTouchpadMode ? "Touchpad" : "Direct Touch");
    };

    function getCanvas() {
        return document.getElementById('noVNC_canvas') || document.querySelector('canvas');
    }

    var cachedRfb = null;
    function getRfb() {
        if (cachedRfb && cachedRfb._rfbConnectionState === 'connected') return cachedRfb;
        if (window.rfb) { cachedRfb = window.rfb; return cachedRfb; }
        if (window.UI && window.UI.rfb) { cachedRfb = window.UI.rfb; return cachedRfb; }
        return cachedRfb;
    }

    // Dynamic import to expose UI and rfb from noVNC ES6 module
    async function loadUiModule() {
        try {
            if (!window.UI || !window.rfb) {
                var mod = await import('./app/ui.js');
                if (mod && mod.default) {
                    window.UI = mod.default;
                    if (window.UI.rfb) {
                        window.rfb = window.UI.rfb;
                        cachedRfb = window.UI.rfb;
                    }
                }
            }
        } catch(e) {
            console.log("[UbuntuARM64] Note on ui module import:", e);
        }
    }
    loadUiModule();
    setInterval(loadUiModule, 1500);

    function getFbSize() {
        var rfb = getRfb();
        var canvas = getCanvas();
        var w = (rfb && (rfb._fbWidth || rfb.fbWidth)) || (canvas && canvas.width) || 1280;
        var h = (rfb && (rfb._fbHeight || rfb.fbHeight)) || (canvas && canvas.height) || 720;
        return { w: w, h: h };
    }

    // Cursor position in remote desktop coordinates
    var curX = 640;
    var curY = 360;

    function dispatchCanvasPointer(canvas, type, clientX, clientY, button, buttons) {
        if (!canvas) return;
        try {
            if (window.PointerEvent) {
                var pType = type;
                if (type === 'mousemove') pType = 'pointermove';
                else if (type === 'mousedown') pType = 'pointerdown';
                else if (type === 'mouseup') pType = 'pointerup';

                var pe = new PointerEvent(pType, {
                    bubbles: true,
                    cancelable: true,
                    view: window,
                    clientX: clientX,
                    clientY: clientY,
                    button: button || 0,
                    buttons: buttons || 0,
                    pointerId: 1,
                    pointerType: 'mouse',
                    isPrimary: true
                });
                canvas.dispatchEvent(pe);
            }
            var me = new MouseEvent(type, {
                bubbles: true,
                cancelable: true,
                view: window,
                clientX: clientX,
                clientY: clientY,
                button: button || 0,
                buttons: buttons || 0
            });
            canvas.dispatchEvent(me);
        } catch(e) {}
    }

    function sendPointer(x, y, mask, button) {
        var rx = Math.round(x);
        var ry = Math.round(y);
        var rfb = getRfb();

        // 1. Send via RFB protocol if available
        if (rfb) {
            try {
                if (typeof rfb.sendPointerEvent === 'function') {
                    rfb.sendPointerEvent(rx, ry, mask);
                } else if (typeof rfb._sendPointerEvent === 'function') {
                    rfb._sendPointerEvent(rx, ry, mask);
                } else if (typeof rfb._handleMouseMove === 'function') {
                    rfb._handleMouseMove(rx, ry);
                    if (typeof rfb._handleMouseButton === 'function') {
                        rfb._handleMouseButton(rx, ry, mask > 0 ? 1 : 0, mask);
                    }
                }
            } catch(e) {
                console.warn("[UbuntuARM64] sendPointer RFB error:", e);
            }
        }

        // 2. Also dispatch DOM pointer events directly to the canvas
        var canvas = getCanvas();
        if (canvas) {
            var rect = canvas.getBoundingClientRect();
            var sz = getFbSize();
            if (sz.w > 0 && sz.h > 0 && rect.width > 0 && rect.height > 0) {
                var clientX = rect.left + (rx / sz.w) * rect.width;
                var clientY = rect.top + (ry / sz.h) * rect.height;
                var domType = 'mousemove';
                var domButtons = 0;
                var domBtn = button || 0;
                if (mask > 0) {
                    domButtons = (mask & 4) ? 2 : ((mask & 2) ? 4 : 1);
                    domType = 'mousedown';
                }
                dispatchCanvasPointer(canvas, domType, clientX, clientY, domBtn, domButtons);
            }
        }
    }

    window.__ubuntuClick = function(button) {
        var mask = (button === 2) ? 4 : (button === 1 ? 2 : 1);
        sendPointer(curX, curY, mask, button);
        setTimeout(function() {
            sendPointer(curX, curY, 0, button);
            var canvas = getCanvas();
            if (canvas) {
                var rect = canvas.getBoundingClientRect();
                var sz = getFbSize();
                var clientX = rect.left + (curX / sz.w) * rect.width;
                var clientY = rect.top + (curY / sz.h) * rect.height;
                dispatchCanvasPointer(canvas, 'mouseup', clientX, clientY, button, 0);
                dispatchCanvasPointer(canvas, 'click', clientX, clientY, button, 0);
            }
        }, 80);
    };

    window.__ubuntuDoubleClick = function() {
        window.__ubuntuClick(0);
        setTimeout(function() {
            window.__ubuntuClick(0);
        }, 120);
    };

    window.__ubuntuScroll = function(direction) {
        var mask = direction < 0 ? 8 : 16;
        sendPointer(curX, curY, mask, 0);
        setTimeout(function() {
            sendPointer(curX, curY, 0, 0);
        }, 60);
    };

    window.__ubuntuResetCursor = function() {
        var sz = getFbSize();
        curX = Math.round(sz.w / 2);
        curY = Math.round(sz.h / 2);
        sendPointer(curX, curY, 0, 0);
    };

    // Touch gesture state
    var trackingTouchId = null;
    var prevX = 0, prevY = 0;
    var touchStartTime = 0;
    var totalMoved = 0;
    var longPressTimer = null;
    var lastTapTime = 0;
    var isDragHolding = false;
    var twoFingerPrevY = 0;

    // Strict styles to prevent browser pan/zoom from stealing touch gestures
    try {
        if (!document.getElementById('ubuntu_trackpad_styles')) {
            var st = document.createElement('style');
            st.id = 'ubuntu_trackpad_styles';
            // Strict styles to completely eliminate any noVNC web controls, hints, anchors, panels, or overlays
            st.innerHTML = 'html, body, #noVNC_container {' +
                '  width: 100% !important;' +
                '  height: 100% !important;' +
                '  margin: 0 !important;' +
                '  padding: 0 !important;' +
                '  touch-action: none !important;' +
                '  user-select: none !important;' +
                '  -webkit-user-select: none !important;' +
                '  overscroll-behavior: none !important;' +
                '  overflow: hidden !important;' +
                '  background: #000000 !important;' +
                '  background-image: none !important;' +
                '  border-radius: 0px !important;' +
                '  -webkit-border-radius: 0px !important;' +
                '  border-bottom-right-radius: 0px !important;' +
                '}' +
                '* {' +
                '  border-bottom-right-radius: 0px !important;' +
                '}' +
                '#noVNC_canvas {' +
                '  object-fit: contain !important;' +
                '  background: #000000 !important;' +
                '  border-radius: 0px !important;' +
                '}' +
                '#noVNC_control_bar, #noVNC_control_bar_anchor, #noVNC_control_bar_handle, ' +
                '.noVNC_control_bar_hint, .noVNC_hint_anchor, #noVNC_mobile_buttons, ' +
                '#noVNC_transition, #noVNC_status, .noVNC_button {' +
                '  display: none !important;' +
                '  visibility: hidden !important;' +
                '  opacity: 0 !important;' +
                '  pointer-events: none !important;' +
                '  width: 0 !important;' +
                '  height: 0 !important;' +
                '}' +
                '.noVNC_panel {' +
                '  visibility: hidden !important;' +
                '  opacity: 0 !important;' +
                '  pointer-events: none !important;' +
                '  position: fixed !important;' +
                '  left: -9999px !important;' +
                '  top: -9999px !important;' +
                '}';
            document.head.appendChild(st);
        }
    } catch(e) {}

    // Attach touch listeners once
    if (!window.__ubuntuTouchHandlersAttached) {
        window.__ubuntuTouchHandlersAttached = true;

        window.addEventListener('touchstart', function(e) {
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

                if (!window.__ubuntuIsTouchpadMode) {
                    // Direct Touch Mode: point directly to touched spot
                    var canvas = getCanvas();
                    if (canvas) {
                        var rect = canvas.getBoundingClientRect();
                        var sz = getFbSize();
                        if (sz.w > 0 && sz.h > 0 && rect.width > 0 && rect.height > 0) {
                            curX = Math.max(0, Math.min(sz.w - 1, Math.round(((t.clientX - rect.left) / rect.width) * sz.w)));
                            curY = Math.max(0, Math.min(sz.h - 1, Math.round(((t.clientY - rect.top) / rect.height) * sz.h)));
                            sendPointer(curX, curY, 0, 0);
                        }
                    }
                } else {
                    // Touchpad Mode: check double-tap for drag-and-drop
                    var now = Date.now();
                    if (now - lastTapTime < 290) {
                        isDragHolding = true;
                        sendPointer(curX, curY, 1, 0);
                    }
                }

                // Long-press: 480ms -> Right Click
                clearTimeout(longPressTimer);
                longPressTimer = setTimeout(function() {
                    if (totalMoved < 9 && trackingTouchId !== null && !isDragHolding) {
                        sendPointer(curX, curY, 4, 2);
                        setTimeout(function() {
                            sendPointer(curX, curY, 0, 2);
                        }, 80);
                    }
                }, 480);

                e.preventDefault();
                e.stopPropagation();
            }
        }, { capture: true, passive: false });

        window.addEventListener('touchmove', function(e) {
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

            for (var i = 0; i < e.changedTouches.length; i++) {
                var t = e.changedTouches[i];
                if (t.identifier === trackingTouchId) {
                    var dx = t.clientX - prevX;
                    var dy = t.clientY - prevY;
                    prevX = t.clientX;
                    prevY = t.clientY;

                    var dist = Math.hypot(dx, dy);
                    totalMoved += dist;
                    if (totalMoved > 7) {
                        clearTimeout(longPressTimer);
                    }

                    var sz = getFbSize();
                    if (!window.__ubuntuIsTouchpadMode) {
                        // Direct Touch Mode: move cursor with finger
                        var canvas = getCanvas();
                        if (canvas) {
                            var rect = canvas.getBoundingClientRect();
                            if (sz.w > 0 && sz.h > 0 && rect.width > 0 && rect.height > 0) {
                                curX = Math.max(0, Math.min(sz.w - 1, Math.round(((t.clientX - rect.left) / rect.width) * sz.w)));
                                curY = Math.max(0, Math.min(sz.h - 1, Math.round(((t.clientY - rect.top) / rect.height) * sz.h)));
                            }
                        }
                    } else {
                        // Touchpad Mode: relative trackpad movement with smooth acceleration
                        var accel = dist > 14 ? 1.8 : (dist > 7 ? 1.4 : 1.15);
                        curX = Math.max(0, Math.min(sz.w - 1, curX + (dx * accel)));
                        curY = Math.max(0, Math.min(sz.h - 1, curY + (dy * accel)));
                    }

                    var mask = isDragHolding ? 1 : 0;
                    sendPointer(curX, curY, mask, 0);

                    e.preventDefault();
                    e.stopPropagation();
                    break;
                }
            }
        }, { capture: true, passive: false });

        window.addEventListener('touchend', function(e) {
            clearTimeout(longPressTimer);

            for (var i = 0; i < e.changedTouches.length; i++) {
                var t = e.changedTouches[i];
                if (t.identifier === trackingTouchId) {
                    var duration = Date.now() - touchStartTime;

                    if (isDragHolding) {
                        isDragHolding = false;
                        sendPointer(curX, curY, 0, 0);
                        lastTapTime = 0;
                    } else if (totalMoved < 9 && duration < 320) {
                        // Clean Tap -> Left Click!
                        window.__ubuntuClick(0);
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
                sendPointer(curX, curY, 0, 0);
            }
            trackingTouchId = null;
        }, { capture: true, passive: false });
    }

    // Safely close and keep noVNC control bar hidden without destroying DOM inputs
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
    setTimeout(collapseNoVncBar, 200);
    setTimeout(collapseNoVncBar, 600);
    setTimeout(collapseNoVncBar, 1500);

    // Initial center cursor
    setTimeout(function() {
        window.__ubuntuResetCursor();
    }, 600);

    console.log("[UbuntuARM64] Unified single cursor engine active.");
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
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showToolbar by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(true) }
    var isTouchpadMode by remember { mutableStateOf(true) }
    var connectionFailed by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }

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
                                    view?.evaluateJavascript("if (window.__ubuntuSetMode) window.__ubuntuSetMode($isTouchpadMode);", null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        val desc = error?.description?.toString() ?: "Bağlantı reddedildi veya zaman aşımı"
                                        com.example.engine.AppLogManager.error(
                                            com.example.model.LogCategory.WEBVIEW,
                                            "WebViewConnect",
                                            "WebView yükleme hatası: $desc (Deneme: $retryCount/3)"
                                        )
                                        if (retryCount < 3) {
                                            retryCount++
                                            view?.postDelayed({
                                                view.reload()
                                            }, 1200)
                                        } else {
                                            connectionFailed = true
                                            isPageLoading = false
                                        }
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    if (consoleMessage != null) {
                                        val level = when (consoleMessage.messageLevel()) {
                                            ConsoleMessage.MessageLevel.ERROR -> com.example.model.LogLevel.ERROR
                                            ConsoleMessage.MessageLevel.WARNING -> com.example.model.LogLevel.WARN
                                            else -> com.example.model.LogLevel.INFO
                                        }
                                        if (level == com.example.model.LogLevel.ERROR || level == com.example.model.LogLevel.WARN) {
                                            com.example.engine.AppLogManager.log(
                                                level,
                                                com.example.model.LogCategory.WEBVIEW,
                                                "noVNC-JS",
                                                consoleMessage.message() ?: "",
                                                "Kaynak: ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                                            )
                                        }
                                    }
                                    return super.onConsoleMessage(consoleMessage)
                                }
                            }

                            loadUrl(desktopState.url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Seamless Dark Loading Overlay while WebView connects
                if (isPageLoading && !connectionFailed) {
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

                // Connection Failed Overlay
                if (connectionFailed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF15151F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF261D2E))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DesktopWindows,
                                    contentDescription = null,
                                    tint = UbuntuOrange,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Masaüstü Bağlantısı Kesildi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ubuntu PRoot oturumu durmuş veya VNC sunucusu kapanmış olabilir.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD1C4E9),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            connectionFailed = false
                                            retryCount = 0
                                            isPageLoading = true
                                            onStartDesktop()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Yeniden Başlat", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = onOpenLogs,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8))
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Logları Gör")
                                    }
                                }
                            }
                        }
                    }
                }

                // Top Floating Toolbar (Collapsible & Horizontally scrollable on narrow portrait screens)
                AnimatedVisibility(
                    visible = showToolbar,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    val toolbarScrollState = rememberScrollState()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEE1E1E2E),
                        shadowElevation = 8.dp,
                        modifier = Modifier.widthIn(max = 700.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(toolbarScrollState)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            // Touchpad / Direct Touch Mode Toggle Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isTouchpadMode) UbuntuOrange.copy(alpha = 0.25f) else Color(0xFF26A69A).copy(alpha = 0.25f),
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        isTouchpadMode = !isTouchpadMode
                                        webViewRef?.evaluateJavascript("if (window.__ubuntuSetMode) window.__ubuntuSetMode($isTouchpadMode);", null)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTouchpadMode) Icons.Default.Mouse else Icons.Default.TouchApp,
                                        contentDescription = if (isTouchpadMode) "Touchpad Modu" else "Dokunmatik Mod",
                                        tint = if (isTouchpadMode) UbuntuOrange else Color(0xFF80CBC4),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isTouchpadMode) "Touchpad" else "Dokunmatik",
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
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mouse,
                                    contentDescription = "Sol Tık",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Quick Double Click
                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ubuntuDoubleClick();", null)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Çift Tık",
                                    tint = TerminalGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Quick Right Click
                            IconButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ubuntuClick(2);", null)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdsClick,
                                    contentDescription = "Sağ Tık",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Virtual Keyboard button
                            IconButton(
                                onClick = {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    webViewRef?.requestFocus()
                                    imm?.showSoftInput(webViewRef, InputMethodManager.SHOW_IMPLICIT)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = "Klavye", tint = UbuntuWarmOrange, modifier = Modifier.size(16.dp))
                            }

                            // Refresh button
                            IconButton(
                                onClick = {
                                    webViewRef?.reload()
                                    webViewRef?.evaluateJavascript(TOUCH_TO_MOUSE_JS, null)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }

                            // Fullscreen toggle
                            IconButton(
                                onClick = { onToggleFullscreen(!isFullscreen) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isFullscreen) Color(0xFF4A148C) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Tam Ekrandan Çık" else "Tam Ekran",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Logs shortcut
                            IconButton(
                                onClick = onOpenLogs,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = "Logları Gör", tint = Color(0xFFCE93D8), modifier = Modifier.size(16.dp))
                            }

                            // Minimize toolbar toggle
                            IconButton(
                                onClick = { showToolbar = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Araç Çubuğunu Gizle", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }

                            // Stop server
                            IconButton(
                                onClick = onStopDesktop,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Durdur", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
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

        is DesktopState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1B26))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Hata",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Masaüstü Servisi Başlatılamadı",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = desktopState.message,
                            fontSize = 13.sp,
                            color = Color(0xFFFFCDD2),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onStartDesktop,
                                colors = ButtonDefaults.buttonColors(containerColor = UbuntuOrange),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Yeniden Başlat", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenLogs,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8))
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Logları İncele")
                            }
                        }
                    }
                }
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
