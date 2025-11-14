package green.mobileapps.offlinemusicplayer

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MusicActivity : AppCompatActivity() {

    private val TAG = "MusicActivity"
    private lateinit var playerView: PlayerView
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    // UI Elements for metadata
    private lateinit var textTitle: TextView
    private lateinit var textArtist: TextView
    private lateinit var imageAlbumArt: ImageView // New Album Art ImageView

    // Stored Audio File
    private var currentAudioFile: AudioFile? = null

    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.music_activity)

        // --- UI Initialization ---
        playerView = findViewById(R.id.player_view)
        textTitle = findViewById(R.id.text_track_title)
        textArtist = findViewById(R.id.text_track_artist)

        // Setting a default icon if no actual album art is available
        // Note: You would replace this logic if you extract album art from the AudioFile
        //imageAlbumArt.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note_24px))
        //imageAlbumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE // Keep icon centered

        // The XML now handles the PlayerView sizing, but we ensure all buttons are visible
        playerView.setShowFastForwardButton(true)
        playerView.setShowRewindButton(true)
        playerView.setShowNextButton(true)
        playerView.setShowPreviousButton(true)
        playerView.setShowShuffleButton(true)

        // --- Retrieve AudioFile from Intent ---
        currentAudioFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_AUDIO_FILE, AudioFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_AUDIO_FILE)
        }

        displayTrackMetadata()
    }

    /**
     * Updates the UI with metadata from the received AudioFile.
     */
    private fun displayTrackMetadata() {
        if (currentAudioFile != null) {
            val file = currentAudioFile!!
            val trackPrefix = if (file.track != null && file.track > 0) "${file.track}. " else ""
            // Display Title
            textTitle.text = "$trackPrefix${file.title}"

            // Display Artist and Album, separated by a distinct dot
            val albumInfo = if (file.album != null) " • ${file.album}" else ""
            textArtist.text = "${file.artist}$albumInfo"

            // TODO: Add logic here to load actual album art into imageAlbumArt if available in AudioFile
        } else {
            textTitle.text = "Track Not Found"
            textArtist.text = "No Metadata"
        }
    }

    // --- MediaController Setup ---

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Creating MediaController")

        val sessionToken = SessionToken(
            this,
            ComponentName(this, MusicService::class.java)
        )

        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val mediaController = controllerFuture.get()
                playerView.player = mediaController
                Log.d(TAG, "MediaController connected and attached to PlayerView.")

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        if (controllerFuture.isDone) {
            val mediaController = controllerFuture.get()
            playerView.player = null
            MediaController.releaseFuture(controllerFuture)
            Log.d(TAG, "MediaController released.")
        }
    }
}