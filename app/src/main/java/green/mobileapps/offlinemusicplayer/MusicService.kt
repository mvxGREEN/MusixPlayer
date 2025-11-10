package green.mobileapps.offlinemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat // IMPORTANT: Retaining this import
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
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


// You will likely need to create a simple data class for AudioFile in a separate file,
// or use a placeholder class if it's not provided for compilation testing.
// For the sake of this file, I'll assume the AudioFile class is available/defined elsewhere.

// NOTE: We don't need MediaButtonReceiver.handleIntent anymore, as MediaSessionService handles it.

class MusicService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSesh: MediaSession? = null

    // RE-ADDED: Need MediaSessionCompat instance to generate the specific Token
    // required by androidx.media.app.NotificationCompat.MediaStyle
    private var session: MediaSessionCompat? = null

    var manager: NotificationManager? = null

    // Constants for channel creation (still needed for Android O+)
    private val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
    private val NOTIFICATION_CHANNEL_NAME = "Music Playback"
    private val TAG = "MusicService"
    private val NOTIFICATION_ID = 9876

    // Constant key for intent extra, used in onStartCommand
    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"
    private val EXTRA_PLAYLIST = "EXTRA_PLAYLIST"
    private val EXTRA_START_INDEX = "EXTRA_START_INDEX"

    // Temporary storage for the last loaded file information - NOW THE WHOLE PLAYLIST
    private var currentPlaylist: List<AudioFile> = emptyList()

    // Temporary storage for the last loaded file information
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

    override fun onCreate() {
        // 0. Create Notification Channel (Required for Android O+)
        createNotificationChannel()

        super.onCreate()

        // 1. Initialize ExoPlayer (The Player)
        player = ExoPlayer.Builder(this).build()
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
                updateMediaNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Also update when play/pause changes, even if state is READY
                updateMediaNotification()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player Error: ${error.message}", error)
            }
        })

        // 2a. Initialize MediaSessionCompat (for Notification compatibility only)
        // We use the Media3 player as the underlying engine.
        session = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(getMediaSessionActivity())
            // In a real app, you would also link the MediaSessionCompat to the player's session via its controller.
            // For now, only the token is needed by the notification builder.
        }

        // 2b. Initialize MediaLibrarySession (The primary Media3 Controller Interface)
        mediaSesh = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaLibrarySessionCallback())
            .setSessionActivity(getMediaSessionActivity()!!)
            .build()

        manager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Call startForeground with the initial notification
        startForeground(NOTIFICATION_ID, createMediaNotification())
    }

    /**
     * Updates the existing notification to reflect the current player state (e.g., changes play/pause button).
     */
    private fun updateMediaNotification() {
        // Ensure this method is called after player state changes
        val notification = createMediaNotification()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    // --- MediaLibraryService Overrides ---

    // 3. Provide the Session to the system
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
        }


        // 4b. Parse the intent and handle initial playback
        val file: AudioFile? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_AUDIO_FILE, AudioFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_AUDIO_FILE)
        }

        if (file != null && file != lastLoadedFile) {
            lastLoadedFile = file
            Log.d(TAG, "New file received: ${file.title}")
            // Stop any current playback
            player?.stop()

            // --- CRITICAL FIX: Explicitly set the media list to force metadata sync ---
            player?.setMediaItems(listOf(file.toMediaItem()))

            player?.prepare()
            player?.play()
        } else if (file == null && player?.playbackState == Player.STATE_IDLE && lastLoadedFile != null) {
            Log.d(TAG, "Restarting last file: ${lastLoadedFile!!.title}")

            // --- CRITICAL FIX: Explicitly set the media list to force metadata sync ---
            player?.setMediaItems(listOf(lastLoadedFile!!.toMediaItem()))

            player?.prepare()
            player?.play()
        }

        // 4b. Parse the intent and handle initial playback
        val newPlaylist: List<AudioFile>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableArrayListExtra(EXTRA_PLAYLIST, AudioFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableArrayListExtra(EXTRA_PLAYLIST)
        }

        val startIndex = intent?.getIntExtra(EXTRA_START_INDEX, 0) ?: 0


        if (newPlaylist != null && newPlaylist.isNotEmpty()) {
            // Check if the playlist has actually changed
            if (currentPlaylist != newPlaylist) {
                currentPlaylist = newPlaylist
                Log.d(TAG, "New playlist received with ${currentPlaylist.size} tracks. Starting index: $startIndex")

                // Convert the full playlist to MediaItems
                val mediaItems = currentPlaylist.map { it.toMediaItem() }

                // Stop any current playback
                player?.stop()

                // Set the media list and start from the correct index
                player?.setMediaItems(mediaItems)
                player?.seekToDefaultPosition(startIndex) // Seek to the file that was clicked

                player?.prepare()
                player?.play()
            } else if (player?.currentMediaItemIndex != startIndex) {
                // Same playlist, but user clicked a different track
                Log.d(TAG, "Same playlist, seeking to new index: $startIndex")
                player?.seekTo(startIndex, 0)
                player?.play() // Ensure playback starts if it was paused
            } else {
                // Same playlist, same track - maybe just play/unpause
                if (player?.playbackState == Player.STATE_IDLE || player?.playbackState == Player.STATE_ENDED) {
                    player?.prepare()
                    player?.play()
                } else if (player?.isPlaying == false) {
                    player?.play()
                }
            }
        }

        // Return the result of the default handling for MediaButtonReceiver, which
        // MediaSessionService handles internally by default.
        return super.onStartCommand(intent, flags, startId)
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

    private fun getMediaSessionActivity(): PendingIntent? {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createMediaNotification(): Notification {
        // Determine the current play/pause state
        val isPlaying = player?.isPlaying ?: false
        val playPauseIcon = if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px // Assume you have R.drawable.play_arrow_24px
        val playPauseAction = "PAUSE" // Action remains PAUSE, logic handles toggle

        var builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        // Title and Text Update based on current media
        val currentMediaItem = player?.currentMediaItem
        val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "No Track Loaded"
        val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"

        builder.setSmallIcon(R.drawable.music_note_24px)
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

        // Use the MediaStyle to link the notification to the MediaSession
        builder.setStyle(
            MediaStyle()
                // FIX: Use the MediaSessionCompat Token from the 'session' instance
                .setMediaSession(session?.sessionToken)
                // Show Previous (0), Play/Pause (1), and Next (2) in the compact view
                .setShowActionsInCompactView(0, 1, 2)
        )

        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.setContentIntent(
            PendingIntent.getActivity(
                this, 2939,
                Intent(
                    this,
                    MainActivity::class.java
                ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
        }

        return builder.build()
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