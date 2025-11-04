package green.mobileapps.offlinemusicplayer


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
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


class MusicService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSesh: MediaSession? = null
    var session: MediaSessionCompat? = null
    var manager: NotificationManager? = null

    // Constants for channel creation (still needed for Android O+)
    private val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
    private val NOTIFICATION_CHANNEL_NAME = "Music Playback"
    private val TAG = "MusicService"

    // Constant key for intent extra, used in onStartCommand
    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"

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
                    // TODO logic for track completion/skipping
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player Error: ${error.message}", error)
            }
        })


        // 2. Initialize MediaLibrarySession (The Controller Interface)
        mediaSesh = MediaSession.Builder(this, player!!)
            .setCallback(CustomMediaLibrarySessionCallback())
            .setSessionActivity(getMediaSessionActivity()!!) // Forced non-null as requested
            .build()
    }

    // --- MediaLibraryService Overrides ---

    // 3. Provide the Session to the system
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSesh
    }

    // 4. Handle initial playback request from MainActivity (via Intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        manager = this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager?;
        session = MediaSessionCompat(this, "MusicService")
        val notification: Notification? = createMediaNotification() // Implement this method
        startForeground(9876, notification)

        // 4b. Parse the intent and handle playback
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

        } else if (file == null && intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            return super.onStartCommand(intent, flags, startId)
        } else if (player?.playbackState == Player.STATE_IDLE && lastLoadedFile != null) {
            Log.d(TAG, "Restarting last file: ${lastLoadedFile!!.title}")

            // --- CRITICAL FIX: Explicitly set the media list to force metadata sync ---
            player?.setMediaItems(listOf(lastLoadedFile!!.toMediaItem()))

            player?.prepare()
            player?.play()
        }

        return START_STICKY
    }

    // 5. Cleanup when service is destroyed
    override fun onDestroy() {
        mediaSesh?.release()
        player?.release()
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

            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .build()

            // START OF USER EDIT: Changed Builder to AcceptedResultBuilder(session)
            return ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
            // END OF USER EDIT
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
        var builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        builder.setSmallIcon(R.drawable.music_note_24px)
        builder.setAutoCancel(true)
        builder.setContentTitle("Default title")
        builder.setContentText("Default artist")
        builder.setContentIntent(pendingIntent)
        builder.addAction(R.drawable.skip_previous_24px, "Previous", getPendingIntentForAction("PREVIOUS"))
        builder.addAction(R.drawable.pause_24px, "Pause", getPendingIntentForAction("PAUSE")) // or ic_play
        builder.addAction(R.drawable.skip_next_24px, "Next", getPendingIntentForAction("NEXT"))
        builder.setStyle(
            MediaStyle()
                .setMediaSession(session?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        //builder.setSmallIcon()
        builder.setPriority(NotificationCompat.PRIORITY_LOW)
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
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val notificationChannel =
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "3MP_PLAYER_CHANNEL", importance)
            notificationChannel.enableLights(true)
            notificationChannel.setLightColor(Color.RED)
            notificationChannel.enableVibration(false)
            checkNotNull(manager)
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
            manager!!.createNotificationChannel(notificationChannel)
        }

        return builder.build()
    }

    private fun getPendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getAlbumArtBitmap(): Bitmap? {
        // Implement logic to retrieve and return album art as a Bitmap
        return null
    }

}


