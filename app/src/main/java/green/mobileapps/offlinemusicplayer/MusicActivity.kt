package green.mobileapps.offlinemusicplayer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

// You'll need to create the layout file: activity_music.xml
// For demonstration, assume you have a layout with a PlayerView.

class MusicActivity : AppCompatActivity() {

    private val TAG = "MusicActivity"
    private lateinit var playerView: PlayerView
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Assume you have a layout with a PlayerView with ID 'player_view'
        setContentView(R.layout.activity_music)
        playerView = findViewById(R.id.player_view)

        // This is important for fullscreen-style controls, hides the timeline until media is loaded
        playerView.setShowFastForwardButton(true)
        playerView.setShowRewindButton(true)
        playerView.setShowNextButton(true)
        playerView.setShowPreviousButton(true)
    }

    // --- MediaController Setup ---

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Creating MediaController")

        // 1. Get the SessionToken for your MediaSessionService
        val sessionToken = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        // 2. Build the MediaController asynchronously
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        // 3. Attach the controller to the PlayerView when the connection is successful
        controllerFuture.addListener({
            try {
                // Controller is ready:
                val mediaController = controllerFuture.get()
                playerView.player = mediaController
                Log.d(TAG, "MediaController connected and attached to PlayerView.")

                // You can send commands here, e.g., to request the latest state or play
                // If the service is already playing, the controller will automatically sync.

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        // 4. Detach the controller from the PlayerView and release resources
        // when the activity is no longer visible to prevent memory leaks
        // and allow the service to continue playback independently.
        if (controllerFuture.isDone) {
            val mediaController = controllerFuture.get()
            playerView.player = null
            MediaController.releaseFuture(controllerFuture)
            Log.d(TAG, "MediaController released.")
        }
    }

    // --- Simple Playback Trigger (Example) ---
    /* * In a real app, you'd likely get the AudioFile data from an intent or a repository.
     * This example shows how you might send a command to the service.
     */
    fun startPlayback(file: AudioFile) {
        // Send a custom intent to the service to start playback of a new file.
        // This utilizes the logic you already implemented in MusicService.onStartCommand
        val intent = Intent(this, MusicService::class.java).apply {
            putExtra("EXTRA_AUDIO_FILE", file)
        }
        startService(intent)
    }
}