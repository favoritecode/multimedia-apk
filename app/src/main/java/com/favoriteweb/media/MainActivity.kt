package com.favoriteweb.media

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    private val webViewStateKey = "favorite_webview_state"
    private val currentPageUrlKey = "favorite_current_page_url"
    private val actionOpen = "com.favoriteweb.media.OPEN"
    private val actionPlay = "com.favoriteweb.media.PLAY"
    private val actionPause = "com.favoriteweb.media.PAUSE"
    private val actionPip = "com.favoriteweb.media.PIP"
    private val actionAudioOnly = "com.favoriteweb.media.AUDIO_ONLY"
    private val mediaChannelId = "favorite_media_controls"
    private val mediaNotificationId = 1001
    private val startUrl = "https://media.favoriteweb.net/"
    private var currentPageUrl = startUrl
    private var lastUserNavigationAt = 0L
    private val userNavigationWindowMs = 15_000L
    private val adBlockEnabled = true
    private lateinit var mainWebView: WebView
    private var externalWebView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var audioOnlyMode = false
    private var youtubeAudioOnlyMode = false
    private var mediaPlaying = false
    private var pipAspectRatio = Rational(16, 9)
    private var enterPipWhenResumed = false
    private var observedMediaSrc = ""
    private var observedMediaTime = 0.0
    private var observedMediaPlaying = false
    private var observedMediaIsVideo = false
    private var lockedMediaSrc = ""
    private var lockedMediaTime = 0.0
    private var lockedMediaWasPlaying = false
    private var lockedMediaIsVideo = false
    private var mediaContinuityLocked = false
    private var continuityRestorePending = false
    private var continuityGeneration = 0
    private var allowAutoNextSource = false
    private var endedMediaSrc = ""
    private var autoNextGeneration = 0
    private var audioPlaybackRequested = false
    private var nativeAudioStarted = false
    private var frameAudioOnlyMode = false
    private var frameMediaSrc = ""
    private var frameMediaTime = 0.0
    private var frameMediaPlaying = false
    private var frameMediaIsVideo = false
    private var frameMediaOrigin = ""
    private var frameMediaUpdatedAt = 0L
    private var frameMediaReplyProxy: JavaScriptReplyProxy? = null
    private var pendingFrameAudioFallbackSrc = ""
    private var pendingFrameAudioFallbackTime = 0.0
    private var mediaReceiverRegistered = false
    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleMediaIntent(intent)
        }
    }

    private val blockedHosts = setOf(
        "2mdn.net",
        "3lift.com",
        "a-ads.com",
        "ad-delivery.net",
        "ad.gt",
        "ad.plus",
        "ad.doubleclick.net",
        "ad.style",
        "adform.net",
        "adgrx.com",
        "adhigh.net",
        "adkernel.com",
        "adlightning.com",
        "admaven.com",
        "adnami.io",
        "adservice.google.com",
        "adservice.google.com.bd",
        "ads-twitter.com",
        "ads.google.com",
        "adskeeper.com",
        "ads.pubmatic.com",
        "ads.yahoo.com",
        "adsafeprotected.com",
        "adsco.re",
        "adsrvr.org",
        "adsterra.com",
        "adsterra.org",
        "adtelligent.com",
        "adthrive.com",
        "advangelists.com",
        "advertising.com",
        "adzerk.net",
        "adxcel-ec2.com",
        "adcash.com",
        "adnxs.com",
        "adroll.com",
        "adtrafficquality.google",
        "algovid.com",
        "amung.us",
        "amazon-adsystem.com",
        "aniview.com",
        "analytics.google.com",
        "applovin.com",
        "aps.amazon.com",
        "app-measurement.com",
        "bidgear.com",
        "bidr.io",
        "bidvertiser.com",
        "bongacams.com",
        "bluekai.com",
        "casalemedia.com",
        "chartbeat.com",
        "clickadu.com",
        "clickaine.com",
        "clicksor.com",
        "cloudfront-labs.amazonaws.com",
        "cointraffic.io",
        "contextweb.com",
        "criteo.com",
        "criteo.net",
        "demdex.net",
        "districtm.io",
        "doubleclick.net",
        "dtscout.com",
        "exelator.com",
        "exoclick.com",
        "exosrv.com",
        "fwmrm.net",
        "facebook.com/tr",
        "flashtalking.com",
        "freewheel.tv",
        "gemius.pl",
        "go2cloud.org",
        "google-analytics.com",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "gstaticadssl.l.google.com",
        "histats.com",
        "highperformancecpmgate.com",
        "highperformancedformats.com",
        "highrevenuegate.com",
        "hotjar.com",
        "imasdk.googleapis.com",
        "imrworldwide.com",
        "inmobi.com",
        "innovid.com",
        "lijit.com",
        "lkqd.net",
        "mgid.com",
        "monetag.com",
        "monetizer101.com",
        "moatads.com",
        "mookie1.com",
        "nativeads.com",
        "nielsen.com",
        "omnitagjs.com",
        "openx.net",
        "onclickalgo.com",
        "onclickperformance.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "popcashjs.b-cdn.net",
        "popmyads.com",
        "pagead2.googlesyndication.com",
        "partner.googleadservices.com",
        "propellerads.com",
        "propeller-tracking.com",
        "push.house",
        "pushengage.com",
        "pubmatic.com",
        "quantserve.com",
        "revcontent.com",
        "r.adroll.com",
        "rubiconproject.com",
        "sharethrough.com",
        "smartadserver.com",
        "smartclip.net",
        "scorecardresearch.com",
        "serving-sys.com",
        "skimresources.com",
        "sonobi.com",
        "spotxchange.com",
        "stickyadstv.com",
        "themoneytizer.com",
        "taboola.com",
        "trafficjunky.net",
        "trafficstars.com",
        "tribalfusion.com",
        "triplelift.com",
        "tsyndicate.com",
        "tynt.com",
        "vidoomy.com",
        "voluumtrk.com",
        "vungle.com",
        "zedo.com",
        "zemanta.com",
        "yieldmo.com",
        "yieldtraffic.com",
        "yllix.com",
        "yadro.ru"
    )

    private val blockedPathParts = setOf(
        "/ad/",
        "/ads/",
        "/adserver",
        "/adserve",
        "/advert",
        "/banner",
        "/banners",
        "/clickunder",
        "/creative/",
        "/delivery/",
        "/doubleclick/",
        "/floatads",
        "/googleads.",
        "/interstitial",
        "/monetize",
        "/native-ad",
        "/pagead/",
        "/pop/",
        "/popads",
        "/popunder",
        "/popup",
        "/redirect?",
        "/redirect/",
        "/prebid",
        "/promoads",
        "/pushads",
        "/sponsor",
        "/vast/",
        "/vpaid/",
        "ad_placement",
        "adclick",
        "adclicks",
        "ad_iframe",
        "ad_script",
        "adformat=",
        "adprovider",
        "adserver",
        "adsbygoogle",
        "adsystem",
        "adtag",
        "advertisement",
        "affiliate_id=",
        "bannerid=",
        "banner_ad",
        "clickid=",
        "clickunder",
        "directlink",
        "displayads",
        "exo_",
        "googletag",
        "interstitial",
        "jsads",
        "monetag",
        "popunder",
        "popupad",
        "preroll",
        "propeller",
        "push_subscribe",
        "push-notification",
        "smartlink",
        "sponsored",
        "taboola",
        "vast.xml",
        "vpaid"
    )

    private val allowedHosts = setOf(
        "media.favoriteweb.net",
        "favoriteweb.net",
        "khan.favoriteweb.net"
    )

    private val emptyResponse
        get() = WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )

    private val transparentImageResponse
        get() = WebResourceResponse(
            "image/gif",
            "utf-8",
            ByteArrayInputStream(
                byteArrayOf(
                    71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0,
                    -1, -1, -1, 33, -7, 4, 1, 0, 0, 0, 0, 44, 0, 0, 0, 0, 1,
                    0, 1, 0, 0, 2, 2, 68, 1, 0, 59
                )
            )
        )

    private val safeBrowsingHosts = setOf(
        "ads.google.com",
        "doubleclick.net"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        createMediaNotificationChannel()
        requestNotificationPermissionIfNeeded()
        registerMediaActionReceiver()

        val webView = findViewById<WebView>(R.id.webView)
        mainWebView = webView

        configureWebView(webView)
        webView.addJavascriptInterface(MediaStateBridge(), "FavoriteMediaBridge")

        webView.webChromeClient = createWebChromeClient { target, isUserGesture, request ->
            if (isUserGesture || requestHasGesture(request)) {
                lastUserNavigationAt = SystemClock.elapsedRealtime()
            }
            if (adBlockEnabled && isBlocked(target)) {
                return@createWebChromeClient true
            }
            if (adBlockEnabled && !isUserGesture && isAutoExternalNavigation(target)) {
                return@createWebChromeClient true
            }
            openExternalTab(target.toString())
            true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val target = request.url
                if (requestHasGesture(request)) {
                    lastUserNavigationAt = SystemClock.elapsedRealtime()
                }
                if (adBlockEnabled && request.isForMainFrame && isBlocked(target)) {
                    return true
                }
                if (adBlockEnabled && request.isForMainFrame && isAutoExternalNavigation(target)) {
                    return true
                }
                if (request.isForMainFrame && !isAllowedHost(target)) {
                    openExternalTab(target.toString())
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (adBlockEnabled && isBlocked(request.url)) {
                    return if (request.requestHeaders["Accept"]?.contains("image") == true) {
                        transparentImageResponse
                    } else {
                        emptyResponse
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                val uri = Uri.parse(url)
                if (!isBlocked(uri)) {
                    currentPageUrl = url
                }
                installMediaStateObserver(view)
                installAdLayerHider(view)
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true && request.url.host in safeBrowsingHosts) {
                    return
                }
                super.onReceivedError(view, request, error)
            }
        }

        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            lastUserNavigationAt = SystemClock.elapsedRealtime()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })

        if (savedInstanceState != null) {
            currentPageUrl = savedInstanceState.getString(currentPageUrlKey, startUrl)
            val webViewState = savedInstanceState.getBundle(webViewStateKey)
            if (webViewState != null) {
                webView.restoreState(webViewState)
            } else {
                webView.loadUrl(currentPageUrl)
            }
        } else {
            webView.loadUrl(currentPageUrl)
        }
        handleMediaIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        mainWebView.saveState(webViewState)
        outState.putBundle(webViewStateKey, webViewState)
        outState.putString(currentPageUrlKey, currentPageUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMediaIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!isInPictureInPictureMode) {
            setPipPresentation(false)
            if (!audioOnlyMode && !nativeAudioStarted) {
                setVideoVisible(true)
            }
        }
        updatePictureInPictureParams()
        restoreViewportAfterPip(100L)
        restoreViewportAfterPip(500L)
        if (mediaContinuityLocked && !audioOnlyMode) {
            ensureMediaContinuity(150L)
        }
        if (enterPipWhenResumed) {
            enterPipWhenResumed = false
            window.decorView.post { enterPipModeIfPossible() }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        refreshMainMediaState {
            if (
                !audioOnlyMode &&
                !nativeAudioStarted &&
                mediaPlaying &&
                !activeMediaIsVideo() &&
                activeMediaSource().isNotBlank()
            ) {
                enableAudioOnlyModeNow()
                return@refreshMainMediaState
            }
            if (
                !audioOnlyMode &&
                !nativeAudioStarted &&
                !youtubeAudioOnlyMode &&
                !frameAudioOnlyMode &&
                prepareVideoPipPresentation()
            ) {
                if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
                    enterPipNow()
                }
            }
            showMediaNotification()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!nativeAudioStarted) {
            showMediaNotification()
        }
    }

    override fun onPause() {
        if (audioOnlyMode || nativeAudioStarted || !activeMediaIsVideo()) {
            setPipPresentation(false)
            updatePictureInPictureParams()
        } else if (mediaPlaying) {
            lockMediaContinuity()
            setPipPresentation(true)
        }
        super.onPause()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            if (activeMediaIsVideo()) {
                lockMediaContinuity()
                setPipPresentation(true)
            } else {
                setPipPresentation(false)
            }
        } else {
            if (youtubeAudioOnlyMode) {
                youtubeAudioOnlyMode = false
                audioOnlyMode = false
                setYoutubeAudioPresentation(false)
            }
            setPipPresentation(false)
            restoreViewportAfterPip(200L)
            restoreViewportAfterPip(650L)
            if (!audioOnlyMode) {
                ensureMediaContinuity(100L)
                ensureMediaContinuity(650L)
                refreshNativeMediaControls(1_100L)
                releaseMediaContinuity(6_000L)
            }
        }
        if (!nativeAudioStarted) {
            showMediaNotification()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            cancelMediaNotification()
        }
        if (mediaReceiverRegistered) {
            unregisterReceiver(mediaActionReceiver)
            mediaReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val webView = mainWebView
        if (keyCode == KeyEvent.KEYCODE_BACK && fullscreenView != null) {
            hideFullscreenVideo()
            return true
        }
        externalWebView?.let { external ->
            if (keyCode == KeyEvent.KEYCODE_BACK && external.canGoBack()) {
                external.goBack()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeExternalTab()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.keepScreenOn = true
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.setSupportMultipleWindows(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        CookieManager.getInstance().setAcceptCookie(true)
        configureFrameMediaBridge(webView)
        webView.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            lastUserNavigationAt = SystemClock.elapsedRealtime()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })
    }

    private fun configureFrameMediaBridge(webView: WebView) {
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return
        }
        val allowedOrigins = setOf("*")
        WebViewCompat.addWebMessageListener(
            webView,
            "FavoriteFrameMedia",
            allowedOrigins
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (isMainFrame) return@addWebMessageListener
            val scheme = sourceOrigin.scheme.orEmpty().lowercase()
            if (scheme != "http" && scheme != "https") return@addWebMessageListener
            val data = message.data ?: return@addWebMessageListener
            val payload = runCatching { JSONObject(data) }.getOrNull()
                ?: return@addWebMessageListener
            runOnUiThread {
                handleFrameMediaState(payload, replyProxy, sourceOrigin.toString())
            }
        }
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            """
                (function(){
                  var isChildFrame = false;
                  try { isChildFrame = window.top !== window; } catch (_) { isChildFrame = true; }
                  if (!isChildFrame) return;
                  if (window.__favoriteFrameMediaInstalled) return;
                  window.__favoriteFrameMediaInstalled = true;

                  function activeMedia() {
                    var all = Array.from(document.querySelectorAll('video, audio'));
                    return all.find(function(item){
                      return !item.paused && !item.ended;
                    }) || all[0] || null;
                  }

                  function report(eventName) {
                    var media = activeMedia();
                    if (!media) return;
                    try {
                      FavoriteFrameMedia.postMessage(JSON.stringify({
                        type: 'state',
                        event: eventName || 'update',
                        src: media.currentSrc || media.src || '',
                        time: media.currentTime || 0,
                        duration: Number.isFinite(media.duration) ? media.duration : 0,
                        playing: !media.paused && !media.ended,
                        ended: media.ended,
                        width: media.videoWidth || media.clientWidth || 16,
                        height: media.videoHeight || media.clientHeight || 9,
                        kind: String(media.tagName || '').toLowerCase(),
                        title: document.title || '',
                        frameUrl: location.href || ''
                      }));
                    } catch (_) {}
                  }

                  function bind(media) {
                    if (media.__favoriteFrameMediaBound) return;
                    media.__favoriteFrameMediaBound = true;
                    [
                      'play', 'playing', 'pause', 'timeupdate', 'seeking', 'seeked',
                      'loadedmetadata', 'loadeddata', 'durationchange', 'ended', 'error'
                    ].forEach(function(eventName){
                      media.addEventListener(eventName, function(){ report(eventName); }, {
                        passive: true
                      });
                    });
                    report('bound');
                  }

                  function scan() {
                    document.querySelectorAll('video, audio').forEach(bind);
                    report('scan');
                  }

                  function handleNativeMessage(event) {
                    var request;
                    try { request = JSON.parse(event.data); } catch (_) { return; }
                    var media = activeMedia();
                    if (!media || !request) return;
                    if (request.command === 'play') {
                      var playResult = media.play();
                      if (playResult && playResult.catch) playResult.catch(function(){});
                    } else if (request.command === 'pause') {
                      media.pause();
                    } else if (
                      request.command === 'seek' &&
                      Number.isFinite(Number(request.position))
                    ) {
                      try { media.currentTime = Math.max(0, Number(request.position)); } catch (_) {}
                    }
                    window.setTimeout(function(){ report('command'); }, 50);
                  }

                  FavoriteFrameMedia.onmessage = handleNativeMessage;
                  if (FavoriteFrameMedia.addEventListener) {
                    FavoriteFrameMedia.addEventListener('message', handleNativeMessage);
                  }

                  function start() {
                    if (!document.documentElement) {
                      window.setTimeout(start, 0);
                      return;
                    }
                    new MutationObserver(scan).observe(document.documentElement, {
                      childList: true,
                      subtree: true
                    });
                    scan();
                    window.setInterval(function(){ report('timer'); }, 750);
                  }
                  start();
                })();
            """.trimIndent(),
            allowedOrigins
        )
    }

    private fun handleFrameMediaState(
        payload: JSONObject,
        replyProxy: JavaScriptReplyProxy,
        frameOrigin: String
    ) {
        if (payload.optString("type") != "state") return
        val source = payload.optString("src")
        if (source.isBlank()) return
        val event = payload.optString("event")
        val time = payload.optDouble("time", 0.0).coerceAtLeast(0.0)
        val playing = payload.optBoolean("playing", false)
        val isVideo = payload.optString("kind").equals("video", ignoreCase = true)
        val now = SystemClock.elapsedRealtime()
        if (
            source != frameMediaSrc &&
            !playing &&
            frameMediaPlaying &&
            now - frameMediaUpdatedAt < 2_500L &&
            event in setOf("bound", "scan", "loadedmetadata", "loadeddata", "durationchange")
        ) {
            return
        }
        frameMediaSrc = source
        frameMediaTime = time
        frameMediaPlaying = playing
        frameMediaIsVideo = isVideo
        frameMediaOrigin = frameOrigin
        frameMediaUpdatedAt = now
        frameMediaReplyProxy = replyProxy

        if (mediaContinuityLocked && lockedMediaSrc == source) {
            lockedMediaTime = time
            lockedMediaWasPlaying = playing
            lockedMediaIsVideo = isVideo
        } else if (!mediaContinuityLocked) {
            observedMediaSrc = source
            observedMediaTime = time
            observedMediaPlaying = playing
            observedMediaIsVideo = isVideo
        }
        mediaPlaying = playing
        MediaPlaybackService.updateFrameState(
            source,
            (time * 1_000.0).toLong(),
            playing
        )
        val width = payload.optInt("width", 16)
        val height = payload.optInt("height", 9)
        if (width > 0 && height > 0) {
            pipAspectRatio = aspectRatioFor(width, height)
        }
        updatePictureInPictureParams()
    }

    private fun openExternalTab(url: String) {
        val existing = externalWebView
        if (existing != null) {
            existing.loadUrl(url)
            return
        }

        val container = mainWebView.parent as? FrameLayout ?: return
        val external = WebView(this)
        configureWebView(external)
        external.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        external.webViewClient = externalTabClient()
        external.webChromeClient = createWebChromeClient { target, _, _ ->
            if (adBlockEnabled && isBlocked(target)) {
                return@createWebChromeClient true
            }
            external.loadUrl(target.toString())
            true
        }
        externalWebView = external
        container.addView(external)
        external.loadUrl(url)
    }

    private fun closeExternalTab() {
        val external = externalWebView ?: return
        externalWebView = null
        (external.parent as? ViewGroup)?.removeView(external)
        external.stopLoading()
        external.destroy()
    }

    private fun externalTabClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val target = request.url
            if (requestHasGesture(request)) {
                lastUserNavigationAt = SystemClock.elapsedRealtime()
            }
            return adBlockEnabled && isBlocked(target)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (adBlockEnabled && isBlocked(request.url)) {
                return if (request.requestHeaders["Accept"]?.contains("image") == true) {
                    transparentImageResponse
                } else {
                    emptyResponse
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            installAdLayerHider(view)
            super.onPageFinished(view, url)
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit app?")
            .setMessage("Do you want to close Favorite Multimedia?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun createWebChromeClient(
        handlePopupNavigation: (Uri, Boolean, WebResourceRequest) -> Boolean
    ) = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val popupView = WebView(this@MainActivity)
            configureWebView(popupView)
            popupView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    popup: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val handled = handlePopupNavigation(request.url, isUserGesture, request)
                    popup.post {
                        popup.stopLoading()
                        popup.destroy()
                    }
                    return handled
                }
            }
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            transport.webView = popupView
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }

            lockMediaContinuity()
            originalOrientation = requestedOrientation
            fullscreenView = view
            fullscreenCallback = callback

            (mainWebView.parent as? FrameLayout)?.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            mainWebView.visibility = View.GONE
            externalWebView?.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            enterFullscreenSystemUi()
        }

        override fun onHideCustomView() {
            hideFullscreenVideo()
        }
    }

    private fun hideFullscreenVideo() {
        val view = fullscreenView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenView = null
        mainWebView.visibility = View.VISIBLE
        externalWebView?.visibility = View.VISIBLE
        requestedOrientation = originalOrientation
        exitFullscreenSystemUi()
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        ensureMediaContinuity(100L)
        ensureMediaContinuity(600L)
        refreshNativeMediaControls(1_000L)
        releaseMediaContinuity(6_000L)
    }

    private fun activeWebView(): WebView {
        return externalWebView ?: mainWebView
    }

    private fun handleMediaIntent(intent: Intent?) {
        when (intent?.action) {
            actionPlay -> {
                audioPlaybackRequested = true
                lockedMediaWasPlaying = true
                observedMediaPlaying = true
                if (frameAudioOnlyMode) {
                    sendFrameMediaCommand("play")
                    sendNativeAudioCommand(MediaPlaybackService.actionPlay)
                } else if (nativeAudioStarted) {
                    sendNativeAudioCommand(MediaPlaybackService.actionPlay)
                } else {
                    runMediaCommand("play")
                }
            }
            actionPause -> {
                audioPlaybackRequested = false
                lockedMediaWasPlaying = false
                observedMediaPlaying = false
                if (frameAudioOnlyMode) {
                    sendFrameMediaCommand("pause")
                    sendNativeAudioCommand(MediaPlaybackService.actionPause)
                } else if (nativeAudioStarted) {
                    sendNativeAudioCommand(MediaPlaybackService.actionPause)
                } else {
                    runMediaCommand("pause")
                }
            }
            actionPip -> enterPipModeIfPossible()
            actionAudioOnly -> enableAudioOnlyMode()
            MediaPlaybackService.actionPlaybackFailed -> handleNativeAudioFailure(intent)
            Intent.ACTION_MAIN -> {
                if (audioOnlyMode || nativeAudioStarted || MediaPlaybackService.isActive) {
                    restoreFromNativeAudio()
                }
            }
            actionOpen -> {
                restoreFromNativeAudio()
            }
        }
    }

    private fun activeMediaSource(): String {
        if (mediaContinuityLocked && lockedMediaSrc.isNotBlank()) return lockedMediaSrc
        if (observedMediaSrc.isNotBlank()) return observedMediaSrc
        if (
            frameMediaSrc.isNotBlank() &&
            SystemClock.elapsedRealtime() - frameMediaUpdatedAt < 10_000L
        ) {
            return frameMediaSrc
        }
        return MediaPlaybackService.currentSource
    }

    private fun activeMediaTime(source: String): Double {
        return when {
            source.isNotBlank() && source == frameMediaSrc -> frameMediaTime
            source.isNotBlank() && source == lockedMediaSrc -> lockedMediaTime
            source.isNotBlank() && source == observedMediaSrc -> observedMediaTime
            MediaPlaybackService.currentSource == source -> {
                MediaPlaybackService.currentPositionMs.coerceAtLeast(0L) / 1_000.0
            }
            else -> lockedMediaTime.takeIf { it > 0.0 } ?: observedMediaTime
        }.coerceAtLeast(0.0)
    }

    private fun activeMediaIsVideo(): Boolean {
        val source = activeMediaSource()
        return when {
            source.startsWith("youtube:") -> true
            source.isNotBlank() && source == lockedMediaSrc -> lockedMediaIsVideo
            source.isNotBlank() && source == observedMediaSrc -> observedMediaIsVideo
            source.isNotBlank() && source == frameMediaSrc -> frameMediaIsVideo
            else -> false
        }
    }

    private fun refreshMainMediaState(afterRefresh: () -> Unit) {
        if (!::mainWebView.isInitialized || isDestroyed) {
            afterRefresh()
            return
        }
        activeWebView().evaluateJavascript(
            """
                (function(){
                  var items = Array.from(document.querySelectorAll('video, audio'));
                  var media = items.find(function(item){
                    return !item.paused && !item.ended;
                  }) || items.find(function(item){
                    return (item.currentSrc || item.src || '').length > 0;
                  }) || items[0];
                  if (!media) return JSON.stringify({src: ''});
                  return JSON.stringify({
                    src: media.currentSrc || media.src || '',
                    time: media.currentTime || 0,
                    playing: !media.paused && !media.ended,
                    kind: String(media.tagName || '').toLowerCase(),
                    width: media.videoWidth || media.clientWidth || 16,
                    height: media.videoHeight || media.clientHeight || 9
                  });
                })();
            """.trimIndent()
        ) { raw ->
            applyMainMediaSnapshot(raw)
            afterRefresh()
        }
    }

    private fun applyMainMediaSnapshot(raw: String?) {
        val text = runCatching {
            JSONTokener(raw.orEmpty()).nextValue() as? String
        }.getOrNull().orEmpty()
        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
        val source = payload.optString("src")
        if (source.isBlank()) return
        val time = payload.optDouble("time", 0.0).coerceAtLeast(0.0)
        val playing = payload.optBoolean("playing", false)
        val isVideo = payload.optString("kind").equals("video", ignoreCase = true)
        val shouldAdoptSnapshot = !mediaContinuityLocked ||
            lockedMediaSrc.isBlank() ||
            (playing && !audioOnlyMode && !nativeAudioStarted)
        if (mediaContinuityLocked && lockedMediaSrc == source) {
            lockedMediaTime = time
            lockedMediaWasPlaying = playing
            lockedMediaIsVideo = isVideo
        } else if (shouldAdoptSnapshot) {
            if (mediaContinuityLocked && lockedMediaSrc != source) {
                mediaContinuityLocked = false
                continuityRestorePending = false
            }
            observedMediaSrc = source
            observedMediaTime = time
            observedMediaPlaying = playing
            observedMediaIsVideo = isVideo
        }
        mediaPlaying = playing
        if (isVideo) {
            val width = payload.optInt("width", 16)
            val height = payload.optInt("height", 9)
            if (width > 0 && height > 0) {
                pipAspectRatio = aspectRatioFor(width, height)
            }
        }
    }

    private fun handleNativeAudioFailure(intent: Intent?) {
        val failedSource = intent?.getStringExtra(MediaPlaybackService.extraSource).orEmpty()
        if (
            failedSource.isBlank() ||
            failedSource != pendingFrameAudioFallbackSrc ||
            failedSource != frameMediaSrc
        ) {
            return
        }
        fallbackToFrameAudioOnly(
            failedSource,
            pendingFrameAudioFallbackTime.coerceAtLeast(frameMediaTime)
        )
    }

    private fun runMediaCommand(command: String) {
        val source = lockedMediaSrc.ifBlank { observedMediaSrc }
        if (source.isNotBlank() && source == frameMediaSrc) {
            sendFrameMediaCommand(command)
            showMediaNotification()
            return
        }
        val expectedSrc = JSONObject.quote(lockedMediaSrc.ifBlank { observedMediaSrc })
        val script = when (command) {
            "play" -> """
                (function(){
                  var expected = $expectedSrc;
                  var allMedia = Array.from(document.querySelectorAll('video, audio'));
                  var media = allMedia.find(function(item){
                    return expected && (item.currentSrc || item.src) === expected;
                  }) || allMedia.find(function(item){
                    return !item.paused && !item.ended;
                  }) || allMedia[0];
                  if (media) { media.play(); }
                })();
            """.trimIndent()
            "pause" -> """
                (function(){
                  var expected = $expectedSrc;
                  var allMedia = Array.from(document.querySelectorAll('video, audio'));
                  var media = allMedia.find(function(item){
                    return expected && (item.currentSrc || item.src) === expected;
                  }) || allMedia.find(function(item){
                    return !item.paused && !item.ended;
                  }) || allMedia[0];
                  if (media) { media.pause(); }
                })();
            """.trimIndent()
            else -> return
        }
        activeWebView().evaluateJavascript(script, null)
        showMediaNotification()
    }

    private fun enableAudioOnlyMode() {
        refreshMainMediaState {
            enableAudioOnlyModeNow()
        }
    }

    private fun enableAudioOnlyModeNow() {
        enterPipWhenResumed = false
        setPipPresentation(false)
        audioOnlyMode = true
        updatePictureInPictureParams()
        audioPlaybackRequested = true
        lockMediaContinuity()
        lockedMediaWasPlaying = true
        val source = activeMediaSource()
        if (source.isBlank()) {
            audioOnlyMode = false
            updatePictureInPictureParams()
            return
        }
        val sourceIsVideo = activeMediaIsVideo()
        if (lockedMediaSrc.isBlank()) {
            lockedMediaSrc = source
            lockedMediaTime = activeMediaTime(source)
            lockedMediaIsVideo = sourceIsVideo
            mediaContinuityLocked = true
        }
        if (source.startsWith("youtube:")) {
            youtubeAudioOnlyMode = true
            mediaPlaying = true
            observedMediaPlaying = true
            lockedMediaWasPlaying = true
            lockedMediaIsVideo = true
            setYoutubeAudioPresentation(true)
            updatePictureInPictureParams()
            showMediaNotification()
            return
        }
        if (source == frameMediaSrc) {
            val positionSeconds = activeMediaTime(source)
            pendingFrameAudioFallbackSrc = source
            pendingFrameAudioFallbackTime = positionSeconds
            sendFrameMediaCommand("pause")
            startNativeAudioPlayback(
                source,
                (positionSeconds * 1_000.0).toLong(),
                arrayListOf(source)
            )
            return
        }
        pendingFrameAudioFallbackSrc = ""
        pendingFrameAudioFallbackTime = 0.0
        val positionMs = (activeMediaTime(source) * 1_000.0).toLong()
        val expectedSrc = JSONObject.quote(source)
        mainWebView.evaluateJavascript(
            """
                (function(){
                  var expectedSrc = $expectedSrc;
                  var videos = Array.from(document.querySelectorAll('video, audio'));
                  var media = videos.find(function(item){
                    return (item.currentSrc || item.src) === expectedSrc;
                  }) || videos.find(function(item){
                    return !item.paused && !item.ended;
                  }) || videos[0];
                  if (media) {
                    media.pause();
                    media.muted = true;
                    media.style.opacity = '0';
                  }
                  var episodeList = [];
                  try {
                    if (typeof episodes !== 'undefined' && Array.isArray(episodes)) {
                      episodeList = episodes;
                    } else if (Array.isArray(window.episodes)) {
                      episodeList = window.episodes;
                    }
                  } catch (_) {}
                  var preferredIndex = parseInt(
                    localStorage.getItem('preferredServerIndex') || '0',
                    10
                  );
                  if (!Number.isFinite(preferredIndex)) preferredIndex = 0;
                  return episodeList.map(function(episode){
                    if (!episode) return '';
                    var sources = episode.videos;
                    if (sources && typeof sources === 'object') {
                      var keys = Object.keys(sources);
                      if (keys.length) {
                        var index = Math.min(preferredIndex, keys.length - 1);
                        return sources[keys[index]] || sources[keys[0]] || '';
                      }
                    }
                    return episode.video || '';
                  }).filter(function(item){ return typeof item === 'string' && item; });
                })();
            """.trimIndent(),
        ) { rawPlaylist ->
            val sources = runCatching {
                val values = JSONArray(rawPlaylist)
                ArrayList<String>(values.length()).apply {
                    for (index in 0 until values.length()) {
                        values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.getOrDefault(arrayListOf())
            if (source !in sources) {
                sources.add(0, source)
            }
            startNativeAudioPlayback(source, positionMs, sources)
        }
    }

    private fun startNativeAudioPlayback(
        source: String,
        positionMs: Long,
        playlist: ArrayList<String>
    ) {
        enterPipWhenResumed = false
        setPipPresentation(false)
        audioOnlyMode = true
        updatePictureInPictureParams()
        val serviceIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.actionStart
            putExtra(MediaPlaybackService.extraSource, source)
            putExtra(MediaPlaybackService.extraPosition, positionMs)
            putStringArrayListExtra(MediaPlaybackService.extraPlaylist, playlist)
            putExtra(
                MediaPlaybackService.extraCookie,
                CookieManager.getInstance().getCookie(source).orEmpty()
            )
            putExtra(MediaPlaybackService.extraUserAgent, mainWebView.settings.userAgentString)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        nativeAudioStarted = true
        moveTaskToBack(true)
    }

    private fun fallbackToFrameAudioOnly(source: String, positionSeconds: Double) {
        enterPipWhenResumed = false
        setPipPresentation(false)
        frameAudioOnlyMode = true
        audioOnlyMode = true
        audioPlaybackRequested = true
        lockedMediaSrc = source
        lockedMediaTime = positionSeconds
        lockedMediaWasPlaying = true
        lockedMediaIsVideo = frameMediaIsVideo
        mediaContinuityLocked = true
        val serviceIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.actionStart
            putExtra(MediaPlaybackService.extraSource, source)
            putExtra(
                MediaPlaybackService.extraPosition,
                (positionSeconds.coerceAtLeast(0.0) * 1_000.0).toLong()
            )
            putExtra(MediaPlaybackService.extraFramePlayback, true)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        nativeAudioStarted = true
        sendFrameMediaCommand("seek", positionSeconds)
        sendFrameMediaCommand("play")
        moveTaskToBack(true)
    }

    private fun sendNativeAudioCommand(command: String) {
        startService(Intent(this, MediaPlaybackService::class.java).apply { action = command })
    }

    private fun stopBackgroundAudioForForegroundPlayback() {
        if (!nativeAudioStarted && !MediaPlaybackService.isActive) return
        sendNativeAudioCommand(MediaPlaybackService.actionStop)
        nativeAudioStarted = false
        frameAudioOnlyMode = false
        youtubeAudioOnlyMode = false
        audioOnlyMode = false
        pendingFrameAudioFallbackSrc = ""
        pendingFrameAudioFallbackTime = 0.0
        setVideoVisible(true)
    }

    private fun restoreFromNativeAudio() {
        if (youtubeAudioOnlyMode) {
            youtubeAudioOnlyMode = false
            audioOnlyMode = false
            setYoutubeAudioPresentation(false)
            restoreViewportAfterPip(100L)
            return
        }
        if (frameAudioOnlyMode || MediaPlaybackService.isFramePlayback) {
            val shouldPlay = MediaPlaybackService.playbackRequested
            val positionSeconds = MediaPlaybackService.currentPositionMs.coerceAtLeast(0L) / 1_000.0
            sendNativeAudioCommand(MediaPlaybackService.actionStop)
            nativeAudioStarted = false
            frameAudioOnlyMode = false
            pendingFrameAudioFallbackSrc = ""
            pendingFrameAudioFallbackTime = 0.0
            audioOnlyMode = false
            audioPlaybackRequested = shouldPlay
            lockedMediaTime = positionSeconds
            lockedMediaWasPlaying = shouldPlay
            lockedMediaIsVideo = frameMediaIsVideo
            sendFrameMediaCommand("seek", positionSeconds)
            sendFrameMediaCommand(if (shouldPlay) "play" else "pause")
            setPipPresentation(false)
            restoreViewportAfterPip(100L)
            restoreViewportAfterPip(500L)
            releaseMediaContinuity(3_000L)
            return
        }
        if (!nativeAudioStarted && !MediaPlaybackService.isActive) {
            audioOnlyMode = false
            setVideoVisible(true)
            restoreViewportAfterPip(100L)
            return
        }
        val positionSeconds = MediaPlaybackService.currentPositionMs.coerceAtLeast(0L) / 1_000.0
        val shouldPlay = MediaPlaybackService.playbackRequested
        val source = MediaPlaybackService.currentSource.ifBlank {
            lockedMediaSrc.ifBlank { observedMediaSrc }
        }
        val restoredSourceIsVideo = when (source) {
            frameMediaSrc -> frameMediaIsVideo
            observedMediaSrc -> observedMediaIsVideo
            lockedMediaSrc -> lockedMediaIsVideo
            else -> false
        }
        sendNativeAudioCommand(MediaPlaybackService.actionStop)
        nativeAudioStarted = false
        pendingFrameAudioFallbackSrc = ""
        pendingFrameAudioFallbackTime = 0.0
        audioOnlyMode = false
        audioPlaybackRequested = shouldPlay
        lockedMediaSrc = source
        lockedMediaTime = positionSeconds
        lockedMediaWasPlaying = shouldPlay
        lockedMediaIsVideo = restoredSourceIsVideo
        mediaContinuityLocked = source.isNotBlank()
        syncWebPlaylistSelection(source) {
            ensureMediaContinuity(0L)
            ensureMediaContinuity(500L)
        }
        setVideoVisible(true)
        restoreViewportAfterPip(100L)
        restoreViewportAfterPip(500L)
        refreshNativeMediaControls(1_000L)
        releaseMediaContinuity(6_000L)
    }

    private fun syncWebPlaylistSelection(source: String, afterSync: () -> Unit) {
        mainWebView.evaluateJavascript(
            """
                (function(){
                  var expectedSrc = ${JSONObject.quote(source)};
                  var episodeList = [];
                  try {
                    if (typeof episodes !== 'undefined' && Array.isArray(episodes)) {
                      episodeList = episodes;
                    } else if (Array.isArray(window.episodes)) {
                      episodeList = window.episodes;
                    }
                  } catch (_) {}
                  var matchingEpisode = episodeList.find(function(episode){
                    if (!episode) return false;
                    if (episode.video === expectedSrc) return true;
                    return episode.videos && Object.keys(episode.videos).some(function(key){
                      return episode.videos[key] === expectedSrc;
                    });
                  });
                  if (!matchingEpisode) return false;
                  var card = Array.from(document.querySelectorAll('.episode-card')).find(function(item){
                    var title = item.querySelector('.episode-title');
                    return title && title.textContent.trim() === String(matchingEpisode.episode).trim();
                  });
                  if (card && !card.classList.contains('active')) {
                    card.click();
                    return true;
                  }
                  return false;
                })();
            """.trimIndent()
        ) {
            mainWebView.postDelayed(afterSync, 100L)
        }
    }

    private fun setVideoVisible(visible: Boolean) {
        val opacity = if (visible) "1" else "0"
        activeWebView().evaluateJavascript(
            """
                (function(){
                  document.querySelectorAll('video').forEach(function(video){
                    video.style.opacity = '$opacity';
                    if ($visible) {
                      video.style.removeProperty('visibility');
                      video.style.removeProperty('display');
                      video.muted = false;
                      video.controls = true;
                    }
                  });
                })();
            """.trimIndent(),
            null
        )
    }

    private fun restoreForegroundVideoPresentation() {
        if (youtubeAudioOnlyMode) {
            youtubeAudioOnlyMode = false
            setYoutubeAudioPresentation(false)
        }
        frameAudioOnlyMode = false
        if (!nativeAudioStarted) {
            audioOnlyMode = false
        }
        setPipPresentation(false)
        setVideoVisible(true)
    }

    private fun sendFrameMediaCommand(command: String, positionSeconds: Double? = null) {
        val payload = JSONObject().put("command", command)
        positionSeconds?.let { payload.put("position", it.coerceAtLeast(0.0)) }
        runCatching { frameMediaReplyProxy?.postMessage(payload.toString()) }
    }

    private fun setYoutubeAudioPresentation(enabled: Boolean) {
        if (!::mainWebView.isInitialized) {
            return
        }
        mainWebView.evaluateJavascript(
            """
                (function(){
                  var root = document.documentElement;
                  var playYoutubeFrames = function() {
                    document.querySelectorAll(
                      "iframe[src*='youtube.com/embed/'], iframe[src*='youtube-nocookie.com/embed/']"
                    ).forEach(function(frame){
                      try {
                        frame.contentWindow.postMessage(JSON.stringify({
                          event: 'command',
                          func: 'unMute',
                          args: []
                        }), '*');
                        frame.contentWindow.postMessage(JSON.stringify({
                          event: 'command',
                          func: 'setVolume',
                          args: [100]
                        }), '*');
                        frame.contentWindow.postMessage(JSON.stringify({
                          event: 'command',
                          func: 'playVideo',
                          args: []
                        }), '*');
                      } catch (_) {}
                    });
                  };
                  root.classList.remove('favorite-youtube-audio');
                  document.querySelectorAll(
                    "iframe[src*='youtube.com/embed/'], iframe[src*='youtube-nocookie.com/embed/']"
                  ).forEach(function(frame){
                    frame.style.removeProperty('opacity');
                  });
                  if (${if (enabled) "true" else "false"}) {
                    playYoutubeFrames();
                    window.setTimeout(playYoutubeFrames, 200);
                    window.setTimeout(playYoutubeFrames, 700);
                  }
                })();
            """.trimIndent(),
            null
        )
    }

    private fun setPipPresentation(enabled: Boolean) {
        if (!::mainWebView.isInitialized) {
            return
        }
        val activeFrameOrigin = frameMediaOrigin
        mainWebView.evaluateJavascript(
            """
                (function(){
                  var styleId = 'favorite-pip-presentation';
                  var style = document.getElementById(styleId);
                  if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `
                      html.favorite-pip-active,
                      html.favorite-pip-active body {
                        width: 100% !important;
                        height: 100% !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        overflow: hidden !important;
                        background: #000 !important;
                      }
                      html.favorite-pip-active .fixed-top-video,
                      html.favorite-pip-active .entry-video,
                      html.favorite-pip-active .temabanua-post-video,
                      html.favorite-pip-active .youtube-responsive {
                        position: fixed !important;
                        inset: 0 !important;
                        width: var(--favorite-pip-width, 100vw) !important;
                        height: var(--favorite-pip-height, 100vh) !important;
                        min-width: 0 !important;
                        min-height: 0 !important;
                        max-width: none !important;
                        max-height: none !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        transform: none !important;
                        border: 0 !important;
                        border-radius: 0 !important;
                        background: #000 !important;
                        z-index: 2147483646 !important;
                      }
                      html.favorite-pip-active video,
                      html.favorite-pip-active iframe.favorite-pip-frame-target,
                      html.favorite-pip-active iframe[src*='youtube.com/embed/'],
                      html.favorite-pip-active iframe[src*='youtube-nocookie.com/embed/'] {
                        position: fixed !important;
                        display: block !important;
                        visibility: visible !important;
                        opacity: 1 !important;
                        inset: 0 !important;
                        width: var(--favorite-pip-width, 100vw) !important;
                        height: var(--favorite-pip-height, 100vh) !important;
                        min-width: 0 !important;
                        min-height: 0 !important;
                        max-width: none !important;
                        max-height: none !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        box-sizing: border-box !important;
                        object-fit: contain !important;
                        object-position: center center !important;
                        background: #000 !important;
                        z-index: 2147483647 !important;
                      }
                      html.favorite-pip-active #back-top,
                      html.favorite-pip-active .back-top,
                      html.favorite-pip-active .back-button,
                      html.favorite-pip-active [title='Back to Homepage'],
                      html.favorite-pip-active [class*='scroll-to-top'],
                      html.favorite-pip-active [class*='back-to-top'] {
                        display: none !important;
                        visibility: hidden !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                      }
                    `;
                    document.head.appendChild(style);
                  }
                  var root = document.documentElement;
                  var activeFrameOrigin = ${JSONObject.quote(activeFrameOrigin)};
                  var markActiveFrame = function() {
                    var frames = Array.from(document.querySelectorAll('iframe'));
                    frames.forEach(function(frame){
                      frame.classList.remove('favorite-pip-frame-target');
                    });
                    if (!root.classList.contains('favorite-pip-active')) return;
                    var bestFrame = null;
                    var bestArea = 0;
                    frames.forEach(function(frame){
                      var raw = frame.getAttribute('src') ||
                        frame.getAttribute('data-src') ||
                        (frame.dataset && frame.dataset.src) ||
                        '';
                      if (!raw || !activeFrameOrigin) return;
                      try {
                        var frameOrigin = new URL(raw, location.href).origin;
                        if (frameOrigin !== activeFrameOrigin) return;
                        var rect = frame.getBoundingClientRect();
                        var area = Math.max(0, rect.width) * Math.max(0, rect.height);
                        if (area > bestArea) {
                          bestArea = area;
                          bestFrame = frame;
                        }
                      } catch (_) {}
                    });
                    if (!bestFrame) {
                      frames.forEach(function(frame){
                        var rect = frame.getBoundingClientRect();
                        var area = Math.max(0, rect.width) * Math.max(0, rect.height);
                        if (area > bestArea) {
                          bestArea = area;
                          bestFrame = frame;
                        }
                      });
                    }
                    if (bestFrame && bestArea > 1000) {
                      bestFrame.classList.add('favorite-pip-frame-target');
                    }
                  };
                  var updatePipSize = function() {
                    if (!root.classList.contains('favorite-pip-active')) return;
                    root.style.setProperty('--favorite-pip-width', window.innerWidth + 'px');
                    root.style.setProperty('--favorite-pip-height', window.innerHeight + 'px');
                    markActiveFrame();
                  };
                  if (!window.__favoritePipResizeInstalled) {
                    window.__favoritePipResizeInstalled = true;
                    window.addEventListener('resize', updatePipSize, { passive: true });
                  }
                  root.classList.toggle(
                    'favorite-pip-active',
                    ${if (enabled) "true" else "false"}
                  );
                  if (${if (enabled) "true" else "false"}) {
                    var playYoutubeFrames = function() {
                      document.querySelectorAll(
                        "iframe[src*='youtube.com/embed/'], iframe[src*='youtube-nocookie.com/embed/']"
                      ).forEach(function(frame){
                        try {
                          frame.contentWindow.postMessage(JSON.stringify({
                            event: 'command',
                            func: 'playVideo',
                            args: []
                          }), '*');
                        } catch (_) {}
                      });
                    };
                    markActiveFrame();
                    updatePipSize();
                    window.requestAnimationFrame(updatePipSize);
                    window.setTimeout(markActiveFrame, 200);
                    window.setTimeout(markActiveFrame, 700);
                    playYoutubeFrames();
                    window.requestAnimationFrame(playYoutubeFrames);
                    window.setTimeout(playYoutubeFrames, 200);
                    window.setTimeout(playYoutubeFrames, 700);
                  } else {
                    markActiveFrame();
                    root.style.removeProperty('--favorite-pip-width');
                    root.style.removeProperty('--favorite-pip-height');
                  }
                })();
            """.trimIndent(),
            null
        )
        if (enabled && lockedMediaWasPlaying && lockedMediaSrc == frameMediaSrc) {
            sendFrameMediaCommand("play")
        }
    }

    private fun lockMediaContinuity() {
        if (observedMediaSrc.isBlank()) {
            return
        }
        if (!mediaContinuityLocked || lockedMediaSrc != observedMediaSrc) {
            lockedMediaSrc = observedMediaSrc
            lockedMediaTime = observedMediaTime
            lockedMediaWasPlaying = observedMediaPlaying
            lockedMediaIsVideo = observedMediaIsVideo
            mediaContinuityLocked = true
            continuityGeneration++
        }
    }

    private fun ensureMediaContinuity(delayMs: Long) {
        if (!mediaContinuityLocked || lockedMediaSrc.isBlank() || !::mainWebView.isInitialized) {
            return
        }
        val expectedSrc = lockedMediaSrc
        val expectedTime = lockedMediaTime.coerceAtLeast(0.0)
        val shouldPlay = lockedMediaWasPlaying
        val hideVideo = audioOnlyMode
        mainWebView.postDelayed({
            if (!mediaContinuityLocked || lockedMediaSrc != expectedSrc || isDestroyed) {
                return@postDelayed
            }
            continuityRestorePending = true
            mainWebView.evaluateJavascript(
                """
                    (function(){
                      var expectedSrc = ${JSONObject.quote(expectedSrc)};
                      var expectedTime = $expectedTime;
                      var mediaItems = Array.from(document.querySelectorAll('video, audio'));
                      var media = mediaItems.find(function(item){
                        return (item.currentSrc || item.src) === expectedSrc;
                      }) || mediaItems.find(function(item){
                        return !item.paused && !item.ended;
                      }) || mediaItems[0];
                      if (!media) return false;

                      var currentSrc = media.currentSrc || media.src || '';
                      var sourceChanged = currentSrc !== expectedSrc;
                      var isVideo = String(media.tagName || '').toLowerCase() === 'video';
                      var restore = function(){
                        try {
                          if (sourceChanged || (media.currentTime || 0) + 1.5 < expectedTime) {
                            media.currentTime = expectedTime;
                          }
                        } catch(e) {}
                        media.muted = false;
                        if (isVideo) {
                          media.style.opacity = ${if (hideVideo) "'0'" else "'1'"};
                        }
                        if ($shouldPlay && media.paused) media.play();
                      };

                      if (sourceChanged) {
                        media.pause();
                        media.src = expectedSrc;
                        media.load();
                        media.addEventListener('loadedmetadata', restore, { once: true });
                      } else if (media.readyState > 0) {
                        restore();
                      } else {
                        media.addEventListener('loadedmetadata', restore, { once: true });
                      }
                      return true;
                    })();
                """.trimIndent()
            ) {
                continuityRestorePending = false
            }
        }, delayMs)
    }

    private fun releaseMediaContinuity(delayMs: Long) {
        val generation = continuityGeneration
        mainWebView.postDelayed({
            if (generation != continuityGeneration || audioOnlyMode) {
                return@postDelayed
            }
            mediaContinuityLocked = false
            continuityRestorePending = false
            observedMediaSrc = lockedMediaSrc.ifBlank { observedMediaSrc }
            observedMediaTime = lockedMediaTime
            observedMediaPlaying = lockedMediaWasPlaying
            observedMediaIsVideo = lockedMediaIsVideo
        }, delayMs)
    }

    private fun restoreViewportAfterPip(delayMs: Long) {
        if (!::mainWebView.isInitialized) {
            return
        }
        mainWebView.postDelayed({
            if (isInPictureInPictureMode || isFinishing || isDestroyed) {
                return@postDelayed
            }
            mainWebView.evaluateJavascript(
                "(window.visualViewport && window.visualViewport.scale) || 1"
            ) { rawScale ->
                val scale = rawScale?.toDoubleOrNull() ?: return@evaluateJavascript
                if (scale > 1.01 || scale < 0.99) {
                    mainWebView.zoomBy((1.0 / scale).toFloat())
                }
                mainWebView.requestLayout()
                mainWebView.invalidate()
            }
        }, delayMs)
    }

    private fun refreshNativeMediaControls(delayMs: Long) {
        if (!::mainWebView.isInitialized) {
            return
        }
        mainWebView.postDelayed({
            if (isInPictureInPictureMode || isFinishing || isDestroyed) {
                return@postDelayed
            }
            val expectedSrc = lockedMediaSrc.ifBlank { observedMediaSrc }
            mainWebView.evaluateJavascript(
                """
                    (function(){
                      var expectedSrc = ${JSONObject.quote(expectedSrc)};
                      var videos = Array.from(document.querySelectorAll('video'));
                      var video = videos.find(function(item){
                        return expectedSrc && (item.currentSrc || item.src) === expectedSrc;
                      }) || videos.find(function(item){
                        return !item.paused && !item.ended;
                      }) || videos[0];
                      if (!video || !video.controls) return;
                      var wasPlaying = !video.paused && !video.ended;
                      video.controls = false;
                        requestAnimationFrame(function(){
                          video.controls = true;
                          if (wasPlaying && video.paused) video.play();
                        });
                    })();
                """.trimIndent(),
                null
            )
        }, delayMs)
    }

    private fun enterPipModeIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) {
            return
        }
        refreshMainMediaState {
            enterPipModeAfterRefresh()
        }
    }

    private fun enterPipModeAfterRefresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) {
            return
        }
        if (!activeMediaIsVideo()) {
            enableAudioOnlyModeNow()
            return
        }
        if (!hasWindowFocus()) {
            enterPipWhenResumed = true
            return
        }
        enterPipNow()
    }

    private fun enterPipNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) {
            return
        }
        if (!activeMediaIsVideo()) {
            enableAudioOnlyModeNow()
            return
        }
        if (!prepareVideoPipPresentation()) {
            return
        }
        runCatching { enterPictureInPictureMode(buildPictureInPictureParams(mediaPlaying)) }
        mainWebView.postDelayed({
            if (isInPictureInPictureMode && activeMediaIsVideo()) {
                setVideoVisible(true)
                setPipPresentation(true)
                if (lockedMediaWasPlaying || observedMediaPlaying || mediaPlaying) {
                    runMediaCommand("play")
                }
            }
        }, 150L)
    }

    private fun prepareVideoPipPresentation(): Boolean {
        if (audioOnlyMode || youtubeAudioOnlyMode || frameAudioOnlyMode) {
            restoreForegroundVideoPresentation()
        }
        if (nativeAudioStarted || MediaPlaybackService.isActive) {
            restoreFromNativeAudio()
            return false
        }
        if (!mediaPlaying || !activeMediaIsVideo()) {
            return false
        }
        lockMediaContinuity()
        setVideoVisible(true)
        setPipPresentation(true)
        return true
    }

    private fun aspectRatioFor(width: Int, height: Int): Rational {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val value = safeWidth.toDouble() / safeHeight.toDouble()
        return when {
            value > 2.39 -> Rational(239, 100)
            value < (1.0 / 2.39) -> Rational(100, 239)
            else -> Rational(safeWidth, safeHeight)
        }
    }

    private fun buildPictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspectRatio)
            .setActions(pipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(
                autoEnter && !audioOnlyMode && !nativeAudioStarted && activeMediaIsVideo()
            )
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isFinishing || isDestroyed) {
            return
        }
        runCatching {
            setPictureInPictureParams(buildPictureInPictureParams(mediaPlaying))
        }
    }

    private inner class MediaStateBridge {
        @JavascriptInterface
        fun ended(src: String?) {
            runOnUiThread {
                val endedSrc = src.orEmpty()
                val activeSrc = if (mediaContinuityLocked) lockedMediaSrc else observedMediaSrc
                if (endedSrc.isBlank() || endedSrc != activeSrc) {
                    return@runOnUiThread
                }
                endedMediaSrc = endedSrc
                allowAutoNextSource = true
                val generation = ++autoNextGeneration
                mainWebView.postDelayed({
                    if (generation == autoNextGeneration) {
                        allowAutoNextSource = false
                        endedMediaSrc = ""
                    }
                }, 8_000L)
            }
        }

        @JavascriptInterface
        fun update(
            playing: Boolean,
            width: Int,
            height: Int,
            src: String?,
            time: Double,
            kind: String?
        ) {
            runOnUiThread {
                val currentSrc = src.orEmpty()
                val currentIsVideo = kind.equals("video", ignoreCase = true)
                if (
                    playing &&
                    currentSrc.isNotBlank() &&
                    !continuityRestorePending &&
                    (nativeAudioStarted || MediaPlaybackService.isActive)
                ) {
                    stopBackgroundAudioForForegroundPlayback()
                }
                if (
                    currentSrc.isBlank() &&
                    frameMediaSrc.isNotBlank() &&
                    SystemClock.elapsedRealtime() - frameMediaUpdatedAt < 2_500L
                ) {
                    return@runOnUiThread
                }
                val acceptAutoNext = mediaContinuityLocked &&
                    allowAutoNextSource &&
                    endedMediaSrc.isNotBlank() &&
                    currentSrc.isNotBlank() &&
                    currentSrc != endedMediaSrc
                val acceptYoutubeTransition = mediaContinuityLocked &&
                    currentSrc.startsWith("youtube:") &&
                    currentSrc != lockedMediaSrc &&
                    playing &&
                    !isInPictureInPictureMode &&
                    hasWindowFocus()
                val clearClosedYoutube = mediaContinuityLocked &&
                    lockedMediaSrc.startsWith("youtube:") &&
                    currentSrc.isBlank() &&
                    !isInPictureInPictureMode &&
                    hasWindowFocus()
                if (acceptAutoNext) {
                    allowAutoNextSource = false
                    endedMediaSrc = ""
                    autoNextGeneration++
                    continuityRestorePending = false
                    lockedMediaSrc = currentSrc
                    lockedMediaTime = time.coerceAtLeast(0.0)
                    lockedMediaWasPlaying = playing
                    lockedMediaIsVideo = currentIsVideo
                    observedMediaSrc = currentSrc
                    observedMediaTime = time.coerceAtLeast(0.0)
                    observedMediaPlaying = playing
                    observedMediaIsVideo = currentIsVideo
                    mediaPlaying = playing
                    continuityGeneration++
                } else if (acceptYoutubeTransition || clearClosedYoutube) {
                    mediaContinuityLocked = false
                    continuityRestorePending = false
                    lockedMediaSrc = ""
                    lockedMediaTime = 0.0
                    lockedMediaWasPlaying = false
                    observedMediaSrc = currentSrc
                    observedMediaTime = time.coerceAtLeast(0.0)
                    observedMediaPlaying = playing
                    observedMediaIsVideo = currentIsVideo
                    mediaPlaying = playing
                } else if (mediaContinuityLocked) {
                    if (currentSrc == lockedMediaSrc) {
                        lockedMediaTime = time.coerceAtLeast(0.0)
                        if (playing || !audioOnlyMode) {
                            lockedMediaWasPlaying = playing
                        }
                        lockedMediaIsVideo = currentIsVideo
                        mediaPlaying = playing
                    } else if (currentSrc.isNotBlank() && !continuityRestorePending) {
                        ensureMediaContinuity(0L)
                    }
                } else {
                    observedMediaSrc = currentSrc
                    observedMediaTime = time.coerceAtLeast(0.0)
                    observedMediaPlaying = playing
                    observedMediaIsVideo = currentIsVideo
                    mediaPlaying = playing
                }
                if (currentIsVideo && width > 0 && height > 0) {
                    pipAspectRatio = aspectRatioFor(width, height)
                }
                updatePictureInPictureParams()
            }
        }
    }

    private fun installMediaStateObserver(webView: WebView) {
        if (webView !== mainWebView) {
            return
        }
        webView.evaluateJavascript(
            """
                (function(){
                  if (window.__favoriteMediaObserverInstalled) return;
                  window.__favoriteMediaObserverInstalled = true;

                  function report() {
                    var mediaItems = Array.from(document.querySelectorAll('video, audio'));
                    var video = mediaItems.find(function(item){
                      return !item.paused && !item.ended;
                    }) || mediaItems[0];
                    if (!video) {
                      var youtubeFrame = Array.from(document.querySelectorAll('iframe')).find(function(frame){
                        if (!/youtube(?:-nocookie)?\.com\/embed\//i.test(frame.src || '')) {
                          return false;
                        }
                        var rect = frame.getBoundingClientRect();
                        var style = window.getComputedStyle(frame);
                        return rect.width > 0 && rect.height > 0 &&
                          style.display !== 'none' &&
                          style.visibility !== 'hidden' &&
                          (style.opacity !== '0' ||
                            document.documentElement.classList.contains('favorite-youtube-audio'));
                      });
                      if (youtubeFrame) {
                        var match = (youtubeFrame.src || '').match(/\/embed\/([^?&#]+)/i);
                        FavoriteMediaBridge.update(
                          true,
                          16,
                          9,
                          'youtube:' + (match ? match[1] : youtubeFrame.src),
                          0,
                          'video'
                        );
                        return;
                      }
                      FavoriteMediaBridge.update(false, 16, 9, '', 0, '');
                      return;
                    }
                    FavoriteMediaBridge.update(
                      !video.paused && !video.ended,
                      video.videoWidth || video.clientWidth || 16,
                      video.videoHeight || video.clientHeight || 9,
                      video.currentSrc || video.src || '',
                      video.currentTime || 0,
                      String(video.tagName || '').toLowerCase()
                    );
                  }

                  function bind(media) {
                    if (media.__favoriteMediaBound) return;
                    media.__favoriteMediaBound = true;
                    ['play', 'playing', 'pause', 'loadedmetadata', 'loadeddata', 'resize', 'timeupdate', 'seeking', 'seeked', 'emptied'].forEach(function(eventName){
                      media.addEventListener(eventName, report, { passive: true });
                    });
                    media.addEventListener('ended', function(){
                      FavoriteMediaBridge.ended(media.currentSrc || media.src || '');
                      report();
                    }, { passive: true });
                  }

                  function bindYoutube(frame) {
                    if (frame.__favoriteYoutubeBound) return;
                    if (!/youtube(?:-nocookie)?\.com\/embed\//i.test(frame.src || '')) return;
                    var url;
                    try {
                      url = new URL(frame.src, window.location.href);
                    } catch (_) {
                      return;
                    }
                    if (url.searchParams.get('enablejsapi') !== '1') {
                      url.searchParams.set('enablejsapi', '1');
                      url.searchParams.set('origin', window.location.origin);
                      frame.src = url.toString();
                      return;
                    }
                    frame.__favoriteYoutubeBound = true;
                    frame.addEventListener('load', function(){
                      report();
                      try {
                        frame.contentWindow.postMessage(JSON.stringify({
                          event: 'listening',
                          id: 'favorite-youtube-player'
                        }), '*');
                      } catch (_) {}
                    }, { passive: true });
                  }

                  function scan() {
                    document.querySelectorAll('video, audio').forEach(bind);
                    document.querySelectorAll('iframe').forEach(bindYoutube);
                    report();
                  }

                  new MutationObserver(scan).observe(document.documentElement, {
                    childList: true,
                    subtree: true
                  });
                  scan();
                  window.setInterval(report, 750);
                })();
            """.trimIndent(),
            null
        )
    }

    private fun installAdLayerHider(webView: WebView) {
        if (!adBlockEnabled) return
        webView.evaluateJavascript(
            """
                (function(){
                  if (window.__favoriteAdLayerHiderInstalled) return;
                  window.__favoriteAdLayerHiderInstalled = true;

                  var adText = /(adsbygoogle|adsterra|ad-container|ad_container|ad-wrapper|ad_wrapper|ad-banner|ad_banner|adframe|ad_iframe|advertisement|banner-ad|banner_ad|floatads|float-ads|popup-ad|popunder|sponsor|taboola|outbrain|propeller|directlink|interstitial|modal-ad|overlay-ad)/i;
                  var adSrc = /(doubleclick|googlesyndication|googleadservices|adservice|adsterra|adnxs|adskeeper|propellerads|popads|popmyads|onclick|taboola|outbrain|adserver|adserve|highrevenuegate|highperformancedformats|trafficjunky|tsyndicate|yllix|vast|vpaid)/i;

                  function safeFrameSource(src) {
                    return /youtube(?:-nocookie)?\.com\/embed|youtu\.be|youtube\.com\/watch|abyssplayer\.com|dood(?:stream|watch|\.(?:to|so|la|re|pm|wf))|doods?\.(?:to|so|la|re|pm|wf|watch)|player|jwplayer|stream|video/i.test(src || '');
                  }

                  function hasProtectedPlayer(node) {
                    if (!node || node.nodeType !== 1) return false;
                    if (node.querySelector && node.querySelector('video,audio')) return true;
                    if (node.querySelectorAll) {
                      return Array.from(node.querySelectorAll('iframe')).some(function(frame){
                        return safeFrameSource(frame.src || frame.getAttribute('data-src') || '');
                      });
                    }
                    return false;
                  }

                  function restoreProtectedPlayers() {
                    Array.from(document.querySelectorAll('video,audio,iframe')).forEach(function(node){
                      var src = node.src || node.currentSrc || node.getAttribute('data-src') || '';
                      if (!node.matches('video,audio') && !safeFrameSource(src)) return;
                      var current = node;
                      var depth = 0;
                      while (current && current.nodeType === 1 && depth < 4) {
                        if (current.__favoriteAdHidden) {
                          current.__favoriteAdHidden = false;
                          current.style.removeProperty('display');
                          current.style.removeProperty('visibility');
                          current.style.removeProperty('pointer-events');
                        }
                        current = current.parentElement;
                        depth++;
                      }
                    });
                  }

                  function isMediaElement(node) {
                    if (!node || node.nodeType !== 1) return false;
                    if (node.matches && node.matches('video,audio')) return true;
                    if (hasProtectedPlayer(node)) return true;
                    if (node.tagName === 'IFRAME') {
                      var src = node.src || node.getAttribute('data-src') || '';
                      return safeFrameSource(src);
                    }
                    return false;
                  }

                  function hide(node) {
                    if (!node || node.__favoriteAdHidden || isMediaElement(node)) return;
                    node.__favoriteAdHidden = true;
                    node.style.setProperty('display', 'none', 'important');
                    node.style.setProperty('visibility', 'hidden', 'important');
                    node.style.setProperty('pointer-events', 'none', 'important');
                  }

                  function scan() {
                    restoreProtectedPlayers();
                    var viewportArea = Math.max(1, window.innerWidth * window.innerHeight);
                    Array.from(document.querySelectorAll('iframe, ins, aside, div, section')).forEach(function(node){
                      if (isMediaElement(node)) return;
                      var text = [
                        node.id || '',
                        node.className || '',
                        node.getAttribute('aria-label') || '',
                        node.getAttribute('role') || '',
                        node.getAttribute('src') || '',
                        node.getAttribute('data-src') || ''
                      ].join(' ');
                      if (adText.test(text) || adSrc.test(text)) {
                        hide(node);
                        return;
                      }
                      var style = window.getComputedStyle(node);
                      var position = style.position;
                      if (position !== 'fixed' && position !== 'sticky' && position !== 'absolute') {
                        return;
                      }
                      var z = parseInt(style.zIndex || '0', 10);
                      if (!Number.isFinite(z) || z < 999) return;
                      var rect = node.getBoundingClientRect();
                      var area = Math.max(0, rect.width) * Math.max(0, rect.height);
                      var almostTransparent = parseFloat(style.opacity || '1') < 0.08;
                      var noVisibleText = (node.innerText || '').trim().length < 2;
                      if (
                        area > viewportArea * 0.12 &&
                        noVisibleText &&
                        (almostTransparent || style.backgroundColor === 'rgba(0, 0, 0, 0)') &&
                        !node.querySelector('textarea, input, form, button, video, audio, iframe')
                      ) {
                        hide(node);
                        return;
                      }
                      if (area > viewportArea * 0.18 && !node.querySelector('textarea, input, form')) {
                        hide(node);
                      }
                    });
                  }

                  scan();
                  new MutationObserver(scan).observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class', 'style', 'src']
                  });
                  window.setInterval(scan, 1000);
                })();
            """.trimIndent(),
            null
        )
    }

    private fun pipActions(): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return emptyList()
        }
        val actions = mutableListOf(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                "Play",
                "Play",
                mediaActionPendingIntent(actionPlay, 11)
            ),
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Pause",
                "Pause",
                mediaActionPendingIntent(actionPause, 12)
            )
        )
        val source = activeMediaSource()
        if (!source.startsWith("youtube:")) {
            actions += RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode_off),
                "Audio",
                "Audio only",
                mediaActivityPendingIntent(actionAudioOnly, 13)
            )
        }
        return actions
    }

    private fun showMediaNotification() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val source = activeMediaSource()
        val builder = NotificationCompat.Builder(this, mediaChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Favorite Multimedia")
            .setContentText("Background audio/video controls")
            .setContentIntent(mediaActivityPendingIntent(actionOpen, 0))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, "Play", mediaActionPendingIntent(actionPlay, 1))
            .addAction(android.R.drawable.ic_media_pause, "Pause", mediaActionPendingIntent(actionPause, 2))
        if (!audioOnlyMode && !nativeAudioStarted && activeMediaIsVideo()) {
            builder.addAction(android.R.drawable.ic_menu_view, "PiP", mediaActivityPendingIntent(actionPip, 3))
        }
        if (!source.startsWith("youtube:")) {
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                "Audio",
                mediaActionPendingIntent(actionAudioOnly, 4)
            )
        }
        val notification = builder.build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(mediaNotificationId, notification)
    }

    private fun mediaActivityPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, requestCode, intent, flags)
    }

    private fun mediaActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags)
    }

    private fun registerMediaActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(actionPlay)
            addAction(actionPause)
            addAction(actionAudioOnly)
            addAction(MediaPlaybackService.actionPlaybackFailed)
        }
        ContextCompat.registerReceiver(
            this,
            mediaActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        mediaReceiverRegistered = true
    }

    private fun createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            mediaChannelId,
            "Favorite Multimedia Controls",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun cancelMediaNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(mediaNotificationId)
    }

    private fun enterFullscreenSystemUi() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun exitFullscreenSystemUi() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun requestHasGesture(request: WebResourceRequest): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.hasGesture()
    }

    private fun isAutoExternalNavigation(uri: Uri): Boolean {
        val recentlyClicked = SystemClock.elapsedRealtime() - lastUserNavigationAt < userNavigationWindowMs
        return !isAllowedHost(uri) && !recentlyClicked
    }

    private fun isAllowedHost(uri: Uri): Boolean {
        val host = uri.host.orEmpty().lowercase()
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun isBlocked(uri: Uri): Boolean {
        if (!adBlockEnabled) {
            return false
        }

        val host = uri.host.orEmpty().lowercase()
        val url = uri.toString().lowercase()

        if (isAllowedHost(uri)) {
            return false
        }

        if (blockedHosts.any { host == it || host.endsWith(".$it") || url.contains(it) }) {
            return true
        }

        return blockedPathParts.any { url.contains(it) }
    }
}
