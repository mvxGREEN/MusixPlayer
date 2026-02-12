package green.mobileapps.offlinemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSesh: MediaSession? = null
    private var session: MediaSessionCompat? = null
    var manager: NotificationManager? = null

    // Constants for channel creation
    private val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
    private val NOTIFICATION_CHANNEL_NAME = "Music Playback"
    private val TAG = "MusicService"
    private val NOTIFICATION_ID = 9876

    // Intent Actions & Extras
    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"
    private val ACTION_PLAY_FROM_REPO = "ACTION_PLAY_FROM_REPO"
    private val ACTION_ADD_TO_QUEUE = "ACTION_ADD_TO_QUEUE"
    private val ACTION_REORDER_QUEUE = "ACTION_REORDER_QUEUE"
    private val ACTION_REMOVE_FROM_QUEUE = "ACTION_REMOVE_FROM_QUEUE"

    // --- Persistence Constants ---
    private val PREFS_NAME = "MusicServicePrefs"
    private val KEY_LAST_URI = "last_uri"
    private val KEY_LAST_TITLE = "last_title"
    private val KEY_LAST_ARTIST = "last_artist"
    private val KEY_LAST_POSITION = "last_position"
    private val KEY_SHUFFLE_STATE = "last_shuffle_state"
    private val KEY_REPEAT_MODE = "last_repeat_mode"

    // Variable to remember user's shuffle preference while queue is playing
    // (We force false during queue playback, but restore this value after)
    private var storedShuffleState = false

    // Temporary storage for the last loaded file information
    private var lastLoadedFile: AudioFile? = null

    // Helper extension to convert our AudioFile model to Media3's MediaItem
    private fun AudioFile.toMediaItem(): MediaItem {
        Log.d(TAG, "Converting AudioFile to MediaItem: $title")
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()

        // Default repeat to OFF, will be restored later
        player?.repeatMode = Player.REPEAT_MODE_OFF

        player?.addListener(object : Player.Listener {

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                // 1. Queue Logic: If playing a queued item, update the repo
                if (mediaItem != null && PlaylistRepository.hasQueue()) {
                    val nextInQueue = PlaylistRepository.queue.value?.firstOrNull()

                    if (nextInQueue != null && mediaItem.mediaId == nextInQueue.id.toString()) {
                        Log.d(TAG, "Now playing queued track: ${nextInQueue.title}. Removing from UI queue.")
                        PlaylistRepository.popNextInQueue()

                        // If Queue is now empty, restore the user's shuffle preference
                        if (!PlaylistRepository.hasQueue()) {
                            // Only restore if the user actually wanted it on
                            if (storedShuffleState) {
                                player?.shuffleModeEnabled = true
                                Log.d(TAG, "Queue finished. Restoring shuffle state to TRUE")
                            }
                        }
                    }
                }

                // 2. Save state whenever track changes
                savePlaybackState()
                updateMediaNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (!isPlaying) {
                    savePlaybackState()
                }
                updateMediaNotification()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                savePlaybackState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                super.onShuffleModeEnabledChanged(shuffleModeEnabled)

                if (PlaylistRepository.hasQueue()) {
                    // If queue is active, we MUST stay in linear mode (shuffle=false).
                    if (shuffleModeEnabled) {
                        // User tried to turn it ON.
                        // 1. Remember they want it ON later.
                        storedShuffleState = true
                        // 2. Force it back OFF immediately.
                        player?.shuffleModeEnabled = false
                    } else {
                        // User turned it OFF. Update preference.
                        storedShuffleState = false
                    }
                } else {
                    // Normal operation: just sync our state variable
                    storedShuffleState = shuffleModeEnabled
                }

                // Save state so preference persists across app restarts
                savePlaybackState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                super.onRepeatModeChanged(repeatMode)
                savePlaybackState()
            }
        })

        var ctx = this

        // Initialize MediaSessionCompat (for Notification compatibility)
        session = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(getMediaSessionActivity(ctx))
        }

        // Initialize MediaLibrarySession (Media3)
        mediaSesh = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaLibrarySessionCallback())
            .setSessionActivity(getMediaSessionActivity(this)!!)
            .build()

        // Restore last known state (track, position, shuffle, repeat)
        restorePlaybackState()
    }

    // --- Persistence Logic ---

    private fun savePlaybackState() {
        val currentItem = player?.currentMediaItem ?: return
        val currentPos = player?.currentPosition ?: 0L

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val metadata = currentItem.mediaMetadata
        val uri = currentItem.localConfiguration?.uri // Get the URI

        if (uri != null) {
            prefs.edit().apply {
                putString(KEY_LAST_URI, uri.toString())
                putString(KEY_LAST_TITLE, metadata.title?.toString() ?: "Unknown")
                putString(KEY_LAST_ARTIST, metadata.artist?.toString() ?: "Unknown")
                putLong(KEY_LAST_POSITION, currentPos)
                putBoolean(KEY_SHUFFLE_STATE, storedShuffleState)
                putInt(KEY_REPEAT_MODE, player?.repeatMode ?: Player.REPEAT_MODE_OFF)
                apply()
            }
            Log.d(TAG, "Saved State: ${metadata.title} @ $currentPos ms | Shuffle: $storedShuffleState")
        }
    }

    private fun restorePlaybackState() {
        // Only restore if the player is currently empty
        if (player?.currentMediaItem != null) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_LAST_URI, null) ?: return
        val title = prefs.getString(KEY_LAST_TITLE, "Unknown Title")
        val artist = prefs.getString(KEY_LAST_ARTIST, "Unknown Artist")
        val position = prefs.getLong(KEY_LAST_POSITION, 0L)
        val repeatMode = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)

        // Restore stored shuffle preference
        storedShuffleState = prefs.getBoolean(KEY_SHUFFLE_STATE, false)

        Log.d(TAG, "Restoring State: $title @ $position ms")

        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uriString))
            .setMediaMetadata(metadata)
            .build()

        player?.setMediaItem(mediaItem)
        player?.seekTo(position)
        player?.repeatMode = repeatMode

        // If queue exists (unlikely on fresh start, but possible if repo persisted), force false.
        // Otherwise restore user preference.
        player?.shuffleModeEnabled = if (PlaylistRepository.hasQueue()) false else storedShuffleState

        player?.prepare()
        player?.pause() // Start paused
    }

    // --- Notification & Session Handling ---

    private fun updateMediaNotification() {
        val notification = createMediaNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSesh
    }

    // --- Command Handling ---

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            "PAUSE" -> {
                Log.d(TAG, "PAUSE clicked via custom intent")
                if (player?.isPlaying == true) {
                    player?.pause()
                } else {
                    player?.play() // Toggle
                }
                return START_STICKY
            }
            "PREVIOUS" -> {
                player?.seekToPrevious()
                return START_STICKY
            }
            "NEXT" -> {
                player?.seekToNext()
                return START_STICKY
            }
            ACTION_PLAY_FROM_REPO -> {
                Log.d(TAG, "ACTION_PLAY_FROM_REPO received.")
                handleNewPlaybackRequest()
                return START_STICKY
            }
            ACTION_ADD_TO_QUEUE -> {
                val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("EXTRA_QUEUE_FILE", AudioFile::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("EXTRA_QUEUE_FILE")
                }

                if (file != null) {
                    Log.d(TAG, "Adding to ExoPlayer queue: ${file.title}")

                    // 1. Force Linear Playback so the queue works
                    if (player?.shuffleModeEnabled == true) {
                        storedShuffleState = true
                        player?.shuffleModeEnabled = false
                    }

                    // 2. Calculate insertion index
                    val currentIndex = player?.currentMediaItemIndex ?: 0
                    val currentPlaylistSize = player?.mediaItemCount ?: 0

                    // Logic: Queue items go after current song + existing queue items
                    // We rely on Repo size because Repo updates first
                    val queueSize = PlaylistRepository.queue.value?.size ?: 1
                    var insertIndex = currentIndex + queueSize

                    if (insertIndex > currentPlaylistSize) {
                        insertIndex = currentPlaylistSize
                    }

                    player?.addMediaItem(insertIndex, file.toMediaItem())
                }
                return START_STICKY
            }
            ACTION_REORDER_QUEUE -> {
                val fromQueueIndex = intent.getIntExtra("EXTRA_FROM", -1)
                val toQueueIndex = intent.getIntExtra("EXTRA_TO", -1)

                if (fromQueueIndex != -1 && toQueueIndex != -1) {
                    val currentIndex = player?.currentMediaItemIndex ?: 0
                    // Player Index = Current Index + 1 (Start of queue) + Offset
                    val playerFromIndex = currentIndex + 1 + fromQueueIndex
                    val playerToIndex = currentIndex + 1 + toQueueIndex

                    val playlistSize = player?.mediaItemCount ?: 0
                    if (playerFromIndex < playlistSize && playerToIndex < playlistSize) {
                        player?.moveMediaItem(playerFromIndex, playerToIndex)
                    }
                }
                return START_STICKY
            }
            ACTION_REMOVE_FROM_QUEUE -> {
                val queueIndex = intent.getIntExtra("EXTRA_QUEUE_INDEX", -1)

                if (queueIndex != -1) {
                    val currentIndex = player?.currentMediaItemIndex ?: 0
                    val indexToRemove = currentIndex + 1 + queueIndex
                    val playlistSize = player?.mediaItemCount ?: 0
                    if (indexToRemove < playlistSize) {
                        player?.removeMediaItem(indexToRemove)
                    }
                }
                return START_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleNewPlaybackRequest() {
        val newPlaylist = PlaylistRepository.getFullPlaylist()
        val startIndex = PlaylistRepository.currentTrackIndex

        if (newPlaylist.isEmpty()) {
            Log.w(TAG, "Cannot start playback: PlaylistRepository is empty.")
            return
        }

        val currentMediaIds = getAllMediaItems(player)
        val newMediaIds = newPlaylist.map { it.id.toString() }

        // Check if the playlist content has changed
        val playlistChanged = currentMediaIds != newMediaIds

        if (playlistChanged) {
            Log.d(TAG, "Playlist changed. Loading ${newPlaylist.size} tracks.")
            val mediaItems = newPlaylist.map { it.toMediaItem() }

            player?.stop()
            player?.clearMediaItems()
            player?.setMediaItems(mediaItems)
            player?.seekTo(startIndex, 0)
            player?.prepare()
            player?.play()
        } else if (player?.currentMediaItemIndex != startIndex) {
            // Same list, just skip to the clicked song
            player?.seekTo(startIndex, 0)
            player?.play()
        } else {
            // Same song, just toggle play if paused
            if (player?.isPlaying == false) player?.play()
        }

        // Restore Queue if exists
        if (PlaylistRepository.hasQueue()) {
            val queueItems = PlaylistRepository.queue.value ?: return

            Log.d(TAG, "Restoring queue of ${queueItems.size} items.")
            val queueMediaItems = queueItems.map { it.toMediaItem() }
            val current = player?.currentMediaItemIndex ?: 0
            val insertIndex = current + 1
            player?.addMediaItems(insertIndex, queueMediaItems)

            // Force shuffle off if queue exists
            if (player?.shuffleModeEnabled == true) {
                storedShuffleState = true
                player?.shuffleModeEnabled = false
            }
        }

        lastLoadedFile = PlaylistRepository.getCurrentTrack()
    }

    private fun getAllMediaItems(player: ExoPlayer?): List<String> {
        val mediaIds = mutableListOf<String>()
        val count = player?.mediaItemCount ?: 0
        for (i in 0 until count) {
            player?.getMediaItemAt(i)?.let { mediaItem ->
                mediaIds.add(mediaItem.mediaId)
            }
        }
        return mediaIds
    }

    override fun onDestroy() {
        // Save state one last time
        savePlaybackState()

        mediaSesh?.release()
        player?.release()
        session?.release()
        super.onDestroy()
    }

    // --- Custom Media3 Callback ---

    @OptIn(UnstableApi::class)
    private inner class CustomMediaLibrarySessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            val availableCommands = SessionCommands.Builder()
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                .build()

            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .addAllCommands()
                .build()

            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }
    }

    // --- Notification Helpers ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createMediaNotification(): Notification {
        val isPlaying = player?.isPlaying ?: false
        val playPauseIcon = if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
        val playPauseAction = "PAUSE"

        var builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        val currentMediaItem = player?.currentMediaItem
        val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "No Track Loaded"
        val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"

        val activityIntent = Intent(this, MusicActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            2939,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setSmallIcon(R.drawable.musi_v2)
        builder.setAutoCancel(false)
        builder.setOngoing(isPlaying)
        builder.setContentTitle(title)
        builder.setContentText(artist)
        builder.setContentIntent(contentIntent)

        // Try to get art for notification
        val albumArt = getAlbumArtBitmap(currentMediaItem?.localConfiguration?.uri)
        if (albumArt != null) {
            builder.setLargeIcon(albumArt)
        }

        builder.addAction(R.drawable.skip_previous_24px, "Previous", getPendingIntentForAction("PREVIOUS"))
        builder.addAction(playPauseIcon, if(isPlaying) "Pause" else "Play", getPendingIntentForAction(playPauseAction))
        builder.addAction(R.drawable.skip_next_24px, "Next", getPendingIntentForAction("NEXT"))

        builder.setStyle(
            MediaStyle()
                .setMediaSession(session?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )

        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
        }

        return builder.build()
    }

    private fun getMediaSessionActivity(context: Context): PendingIntent? {
        val intent = Intent(context, MusicActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getAlbumArtBitmap(uri: Uri?): Bitmap? {
        if (uri == null) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val data = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }
}