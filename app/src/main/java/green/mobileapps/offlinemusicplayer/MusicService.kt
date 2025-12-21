package green.mobileapps.offlinemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.media3.session.DefaultMediaNotificationProvider
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

    // Constant key for intent extra - ONLY keeping the file extra for potential fallback/initial load,
    // and removing the playlist/index extras.
    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"
    // private val EXTRA_PLAYLIST = "EXTRA_PLAYLIST" // REMOVED
    // private val EXTRA_START_INDEX = "EXTRA_START_INDEX" // REMOVED
    private val ACTION_PLAY_FROM_REPO = "ACTION_PLAY_FROM_REPO" // New action constant

    // Removed currentPlaylist as the repository holds the data
    // var currentPlaylist: List<AudioFile> = emptyList()

    // Temporary storage for the last loaded file information - Kept for safe transitions/fallbacks
    private var lastLoadedFile: AudioFile? = null

    // Helper extension to convert our AudioFile model to Media3's MediaItem
    private fun AudioFile.toMediaItem(): MediaItem {
        Log.d(TAG, "Converting AudioFile to MediaItem: $title")
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        // 0. Create Notification Channel (Required for Android O+)
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
        player?.repeatMode = Player.REPEAT_MODE_ALL

        // Add a listener for debugging and state management
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Playback State Changed: $stateName")
                if (playbackState == Player.STATE_ENDED) {
                    // Automatically go to the next track if available
                    player?.seekToNext()
                }

                // Update the notification whenever the state changes (e.g., Play to Pause)
                //updateMediaNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Also update when play/pause changes, even if state is READY
                //updateMediaNotification()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player Error: ${error.message}", error)
            }
        })

        var ctx = this

        // 2a. Initialize MediaSessionCompat (for Notification compatibility only)
        session = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(getMediaSessionActivity(ctx))
        }

        // 2b. Initialize MediaLibrarySession (The primary Media3 Controller Interface)
        mediaSesh = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaLibrarySessionCallback())
            .setSessionActivity(getMediaSessionActivity(this)!!)
            .build()

        //manager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Call startForeground with the initial notification
        //startForeground(NOTIFICATION_ID, createMediaNotification())
    }

    /**
     * Updates the existing notification to reflect the current player state (e.g., changes play/pause button).
     */
    private fun updateMediaNotification() {
        val notification = createMediaNotification()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSesh
    }

    // 4. Handle initial playback request from MainActivity (via Intent) AND media button clicks
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        // --- Media Control Handling ---
        when (intent?.action) {
            "PAUSE" -> {
                Log.d(TAG, "PAUSE clicked via custom intent")
                if (player?.isPlaying == true) {
                    player?.pause()
                } else {
                    player?.play() // Toggle functionality
                }
                return START_STICKY
            }
            "PREVIOUS" -> {
                Log.d(TAG, "PREVIOUS clicked via custom intent")
                player?.seekToPrevious()
                return START_STICKY
            }
            "NEXT" -> {
                Log.d(TAG, "NEXT clicked via custom intent")
                player?.seekToNext()
                return START_STICKY
            }
            ACTION_PLAY_FROM_REPO -> {
                Log.d(TAG, "ACTION_PLAY_FROM_REPO received.")
                handleNewPlaybackRequest() // New method to read from repository
                return START_STICKY
            }
        }


        // Fallback for single file start (old method) - largely unnecessary now
        val file: AudioFile? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_AUDIO_FILE, AudioFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_AUDIO_FILE)
        }

        // Removed old intent parsing for EXTRA_PLAYLIST and EXTRA_START_INDEX

        // Removed old playback logic that relied on intent extras

        // Return the result of the default handling for MediaButtonReceiver, which
        // MediaSessionService handles internally by default.
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Handles starting playback based on the state in the PlaylistRepository.
     */
    private fun handleNewPlaybackRequest() {
        val newPlaylist = PlaylistRepository.getFullPlaylist()
        val startIndex = PlaylistRepository.currentTrackIndex

        if (newPlaylist.isEmpty()) {
            Log.w(TAG, "Cannot start playback: PlaylistRepository is empty.")
            return
        }

        val currentMediaIds = getAllMediaItems(player) // Now returns IDs
        val newMediaIds = newPlaylist.map { it.id.toString() }

        // Check if the playlist has actually changed by comparing media IDs list
        val playlistChanged = currentMediaIds != newMediaIds // Direct comparison of ID lists

        if (playlistChanged) {
            Log.d(TAG, "Playlist changed or initialized. Loading ${newPlaylist.size} tracks. Starting index: $startIndex")

            // Convert the full playlist to MediaItems
            val mediaItems = newPlaylist.map { it.toMediaItem() }

            player?.stop()
            player?.clearMediaItems() // Clear existing items

            // Set the media list and start from the correct index
            player?.setMediaItems(mediaItems)
            player?.seekToDefaultPosition(startIndex) // Seek to the file that was clicked

            player?.prepare()
            player?.play() // <--- CRITICAL: Start playback
        } else if (player?.currentMediaItemIndex != startIndex) {
            // Same playlist, but user clicked a different track
            Log.d(TAG, "Same playlist, seeking to new index: $startIndex")
            player?.seekTo(startIndex, 0)
            player?.play() // <--- CRITICAL: Ensure playback starts
        } else {
            // Same playlist, same track - maybe just play/unpause
            Log.d(TAG, "Same playlist, same track. Toggling play/pause if needed.")
            if (player?.isPlaying == false) {
                player?.play() // <--- CRITICAL: Play if paused
            }
            // If it's already playing, do nothing.
        }
        // Update the lastLoadedFile for initial notification setup
        lastLoadedFile = PlaylistRepository.getCurrentTrack()

        // Force a notification update immediately after starting playback
        //updateMediaNotification()
    }

    fun getAllMediaItems(player: ExoPlayer?): List<String> {
        val mediaIds = mutableListOf<String>()
        val count = player?.mediaItemCount ?: 0 // Get the total number of media items in the playlist

        for (i in 0 until count) {
            player?.getMediaItemAt(i)?.let { mediaItem ->
                // Extract and add the media ID string
                mediaIds.add(mediaItem.mediaId)
            }
        }
        return mediaIds
    }


    // 5. Cleanup when service is destroyed
    override fun onDestroy() {
        mediaSesh?.release()
        player?.release()
        session?.release() // Release the compat session
        super.onDestroy()
    }

    // --- Media3 Custom Callback for Handling System/Controller Requests ---

    @OptIn(UnstableApi::class)
    private inner class CustomMediaLibrarySessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            val availableCommands = SessionCommands.Builder()
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                .build()

            // Ensure commands for NEXT/PREVIOUS/PLAY_PAUSE are available for the notification
            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .addAllCommands()
                .build()

            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return super.onAddMediaItems(mediaSession, controller, mediaItems)
        }
    }

    // --- Helper Functions ---

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
        // Determine the current play/pause state
        val isPlaying = player?.isPlaying ?: false
        val playPauseIcon = if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
        val playPauseAction = "PAUSE" // Action remains PAUSE, logic handles toggle

        var builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        // Title and Text Update based on current media
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
        // --------------------------------------------------

        builder.setSmallIcon(R.drawable.musi_v2)
        builder.setAutoCancel(false) // Notification should stay visible
        builder.setOngoing(true)
        builder.setContentTitle(title)
        builder.setContentText(artist)

        // Set the album artwork if available (You'll need to implement getAlbumArtBitmap() properly)
        val albumArt = getAlbumArtBitmap()
        if (albumArt != null) {
            builder.setLargeIcon(albumArt)
        }

        // Action Buttons: Previous, Play/Pause, Next
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
        // Use FLAG_UPDATE_CURRENT for consistency with session updates
        return PendingIntent.getActivity(
            context,
            0, // Use a fixed request code (e.g., 0)
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Creates a PendingIntent that sends an action to this service itself.
     */
    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        // Use PendingIntent.FLAG_UPDATE_CURRENT to ensure the action is updated.
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getAlbumArtBitmap(): Bitmap? {
        // TODO: Implement logic to retrieve and return album art as a Bitmap
        // For now, return null or a generic placeholder bitmap if available.
        return null
    }

}