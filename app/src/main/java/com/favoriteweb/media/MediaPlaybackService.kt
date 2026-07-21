package com.favoriteweb.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class MediaPlaybackService : Service() {
    companion object {
        const val actionStart = "com.favoriteweb.media.NATIVE_AUDIO_START"
        const val actionPlay = "com.favoriteweb.media.NATIVE_AUDIO_PLAY"
        const val actionPause = "com.favoriteweb.media.NATIVE_AUDIO_PAUSE"
        const val actionStop = "com.favoriteweb.media.NATIVE_AUDIO_STOP"
        const val actionPlaybackFailed = "com.favoriteweb.media.NATIVE_AUDIO_FAILED"
        const val extraSource = "media_source"
        const val extraPosition = "media_position"
        const val extraCookie = "media_cookie"
        const val extraUserAgent = "media_user_agent"
        const val extraPlaylist = "media_playlist"
        const val extraFramePlayback = "frame_playback"

        @Volatile var currentPositionMs = 0L
            private set
        @Volatile var playbackRequested = false
            private set
        @Volatile var currentSource = ""
            private set
        @Volatile var isActive = false
            private set
        @Volatile var isFramePlayback = false
            private set

        fun updateFrameState(source: String, positionMs: Long, playing: Boolean) {
            if (!isFramePlayback) return
            currentSource = source
            currentPositionMs = positionMs.coerceAtLeast(0L)
            playbackRequested = playing
        }

        private const val channelId = "favorite_media_controls"
        private const val notificationId = 1001
        private const val actionOpen = "com.favoriteweb.media.OPEN"
        private const val actionUiPlay = "com.favoriteweb.media.PLAY"
        private const val actionUiPause = "com.favoriteweb.media.PAUSE"
    }

    private var player: MediaPlayer? = null
    private var playlist = emptyList<String>()
    private var playlistIndex = -1
    private var requestHeaders = emptyMap<String, String>()
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionUpdater = object : Runnable {
        override fun run() {
            player?.let { mediaPlayer ->
                if (runCatching { mediaPlayer.isPlaying }.getOrDefault(false)) {
                    currentPositionMs = runCatching {
                        mediaPlayer.currentPosition.toLong()
                    }.getOrDefault(currentPositionMs)
                }
            }
            if (isActive) {
                positionHandler.postDelayed(this, 250L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            actionStart -> startPlayback(intent)
            actionPlay -> {
                playbackRequested = true
                player?.let { runCatching { it.start() } }
                startForeground(notificationId, buildNotification())
            }
            actionPause -> {
                playbackRequested = false
                player?.let { mediaPlayer ->
                    runCatching {
                        currentPositionMs = mediaPlayer.currentPosition.toLong()
                        mediaPlayer.pause()
                    }
                }
                startForeground(notificationId, buildNotification())
            }
            actionStop -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releasePlayer()
        isActive = false
        isFramePlayback = false
        positionHandler.removeCallbacks(positionUpdater)
        super.onDestroy()
    }

    private fun startPlayback(intent: Intent) {
        val source = intent.getStringExtra(extraSource).orEmpty()
        if (source.isBlank()) {
            stopSelf()
            return
        }
        if (intent.getBooleanExtra(extraFramePlayback, false)) {
            playlist = listOf(source)
            playlistIndex = 0
            currentSource = source
            currentPositionMs = intent.getLongExtra(extraPosition, 0L).coerceAtLeast(0L)
            playbackRequested = true
            isActive = true
            isFramePlayback = true
            releasePlayer()
            startForeground(notificationId, buildNotification())
            return
        }
        isFramePlayback = false
        val requestedPlaylist = intent.getStringArrayListExtra(extraPlaylist)
            .orEmpty()
            .filter { it.isNotBlank() }
            .distinct()
        playlist = if (source in requestedPlaylist) {
            requestedPlaylist
        } else {
            listOf(source) + requestedPlaylist
        }
        playlistIndex = playlist.indexOf(source).coerceAtLeast(0)
        playbackRequested = true
        isActive = true

        val headers = mutableMapOf<String, String>()
        intent.getStringExtra(extraCookie)?.takeIf { it.isNotBlank() }?.let {
            headers["Cookie"] = it
        }
        intent.getStringExtra(extraUserAgent)?.takeIf { it.isNotBlank() }?.let {
            headers["User-Agent"] = it
        }
        requestHeaders = headers
        startForeground(notificationId, buildNotification())
        playSource(source, intent.getLongExtra(extraPosition, 0L).coerceAtLeast(0L))
        positionHandler.removeCallbacks(positionUpdater)
        positionHandler.post(positionUpdater)
    }

    private fun playSource(source: String, positionMs: Long) {
        currentSource = source
        currentPositionMs = positionMs
        releasePlayer()

        val nextPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(this@MediaPlaybackService, Uri.parse(source), requestHeaders)
            setOnPreparedListener { preparedPlayer ->
                if (player !== preparedPlayer) return@setOnPreparedListener
                val startPosition = currentPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    preparedPlayer.seekTo(startPosition.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    @Suppress("DEPRECATION")
                    preparedPlayer.seekTo(startPosition)
                }
                if (playbackRequested) {
                    preparedPlayer.start()
                }
            }
            setOnCompletionListener { completedPlayer ->
                if (player !== completedPlayer) return@setOnCompletionListener
                currentPositionMs = runCatching {
                    completedPlayer.duration.toLong()
                }.getOrDefault(currentPositionMs)
                if (!playNextSource()) {
                    playbackRequested = false
                    startForeground(notificationId, buildNotification())
                }
            }
            setOnErrorListener { failedPlayer, _, _ ->
                if (player === failedPlayer && playbackRequested) {
                    positionHandler.post {
                        if (player === failedPlayer && !playNextSource()) {
                            notifyPlaybackFailed()
                            playbackRequested = false
                            startForeground(notificationId, buildNotification())
                        }
                    }
                }
                true
            }
        }
        player = nextPlayer
        nextPlayer.prepareAsync()
    }

    private fun playNextSource(): Boolean {
        val nextIndex = playlistIndex + 1
        if (nextIndex !in playlist.indices) {
            return false
        }
        playlistIndex = nextIndex
        currentPositionMs = 0L
        playSource(playlist[nextIndex], 0L)
        startForeground(notificationId, buildNotification())
        return true
    }

    private fun notifyPlaybackFailed() {
        sendBroadcast(
            Intent(actionPlaybackFailed).setPackage(packageName).apply {
                putExtra(extraSource, currentSource)
                putExtra(extraPosition, currentPositionMs)
            }
        )
    }

    private fun stopPlayback() {
        player?.let { mediaPlayer ->
            currentPositionMs = runCatching {
                mediaPlayer.currentPosition.toLong()
            }.getOrDefault(currentPositionMs)
        }
        playbackRequested = false
        isActive = false
        isFramePlayback = false
        releasePlayer()
        positionHandler.removeCallbacks(positionUpdater)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releasePlayer() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            mediaPlayer.reset()
            mediaPlayer.release()
        }
        player = null
    }

    private fun buildNotification() = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("Favorite Multimedia")
        .setContentText(
            if (playlistIndex in playlist.indices && playlist.size > 1) {
                "Background audio ${playlistIndex + 1} of ${playlist.size}"
            } else {
                "Background audio playback"
            }
        )
        .setContentIntent(activityPendingIntent(actionOpen, 0))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            android.R.drawable.ic_media_play,
            "Play",
            broadcastPendingIntent(actionUiPlay, 1)
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            "Pause",
            broadcastPendingIntent(actionUiPause, 2)
        )
        .build()

    private fun activityPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(this, requestCode, intent, pendingIntentFlags())
    }

    private fun broadcastPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(action).setPackage(packageName),
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            channelId,
            "Favorite Multimedia Controls",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
