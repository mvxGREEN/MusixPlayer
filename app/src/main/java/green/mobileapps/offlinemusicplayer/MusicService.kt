package green.mobileapps.offlinemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: AudioFile? = null

    companion object {
        const val ACTION_PLAY = "com.example.simplemusicplayer.ACTION_PLAY"
        const val EXTRA_AUDIO_FILE = "com.example.simplemusicplayer.EXTRA_AUDIO_FILE"
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "music_player_channel"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY) {
            // Get the Parcelable AudioFile object
            val file: AudioFile? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_AUDIO_FILE, AudioFile::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_AUDIO_FILE)
            }

            file?.let {
                currentFile = it
                startPlayback(it.uri)
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(uri: Uri) {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(applicationContext, uri)
            // Prepare is asynchronous, playback starts in onPrepared
            mediaPlayer?.prepareAsync()

            // Start the service as foreground immediately
            startForeground(NOTIFICATION_ID, buildNotification())

        } catch (e: Exception) {
            println("Error setting data source or preparing player: ${e.message}")
            stopSelf()
        }
    }

    // --- MediaPlayer Listeners ---

    override fun onPrepared(mp: MediaPlayer?) {
        // Player is ready, start playback
        mp?.start()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        // Playback finished, stop the service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        println("MediaPlayer error: what=$what, extra=$extra")
        stopSelf()
        return true // Indicate that we handled the error
    }

    // --- Notification Handling ---

    private fun buildNotification(): Notification {
        // This is a dummy for demonstration. In a real app, you would use a dedicated icon.
        val largeIconId = R.drawable.music_note_24px

        // Intent to open the MainActivity when the notification is clicked
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingContentIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Notification Channel for Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build the actual notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentFile?.title ?: "No Song Playing")
            .setContentText(currentFile?.artist ?: "Simple Player")
            .setSmallIcon(largeIconId) // Use the music note as the small icon
            .setContentIntent(pendingContentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes it non-dismissible
            //.setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(null)) // Use MediaStyle for transport controls (though we aren't adding them here)
            .build()
    }

    // --- Service Lifecycle ---

    override fun onBind(intent: Intent?): IBinder? {
        // Not using bound service, return null
        return null
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
