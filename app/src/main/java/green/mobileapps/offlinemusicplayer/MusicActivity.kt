package green.mobileapps.offlinemusicplayer

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadataRetriever // NEW
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns // NEW
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // NEW
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata // NEW
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers // NEW
import kotlinx.coroutines.launch // NEW
import kotlinx.coroutines.withContext // NEW

class MusicActivity : AppCompatActivity() {

    private val TAG = "MusicActivity"
    private lateinit var playerView: PlayerView
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null

    // Store a pending external URI to play once the controller connects
    private var pendingExternalUri: Uri? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            updateMetadataUI(mediaItem)
        }
    }

    private lateinit var textTitle: TextView
    private lateinit var textArtist: TextView
    private lateinit var imageAlbumArt: ImageView

    private val EXTRA_AUDIO_FILE = "EXTRA_AUDIO_FILE"

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.music_activity)

        playerView = findViewById(R.id.player_view)
        textTitle = findViewById(R.id.text_track_title)
        textArtist = findViewById(R.id.text_track_artist)

        playerView.setShowFastForwardButton(true)
        playerView.setShowRewindButton(true)
        playerView.setShowNextButton(true)
        playerView.setShowPreviousButton(true)
        playerView.setShowShuffleButton(true)

        checkForExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkForExternalIntent(intent)

        if (mediaController != null && pendingExternalUri != null) {
            playExternalUri(pendingExternalUri!!)
        }
    }

    private fun checkForExternalIntent(intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            pendingExternalUri = intent.data
        } else if (Intent.ACTION_SEND == intent.action && "audio/" in (intent.type ?: "")) {
            pendingExternalUri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
        }
    }

    // --- NEW: Helper to extract ID3 tags or fallback to filename ---
    private fun extractMediaMetadata(uri: Uri): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        try {
            // Identify the context and URI to extracting metadata
            retriever.setDataSource(this, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ID3 tags", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Fallback 1: If title is missing, try to get filename from ContentResolver
        if (title.isNullOrEmpty()) {
            title = getFileNameFromUri(uri)
        }

        // Fallback 2: If still empty
        if (title.isNullOrEmpty()) {
            title = "External Audio File"
        }

        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: "Unknown Artist")
            .setAlbumTitle(album)
            .build()
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving filename", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    // --- MODIFIED: playExternalUri to use extraction logic ---
    private fun playExternalUri(uri: Uri) {
        // Show temporary loading state
        textTitle.text = "Loading Metadata..."
        textArtist.text = ""

        // Use LifecycleScope to do file reading in background (IO)
        lifecycleScope.launch(Dispatchers.IO) {
            val metadata = extractMediaMetadata(uri)

            // Switch back to Main Thread to update Player and UI
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Playing external URI with metadata: ${metadata.title}")

                // Build MediaItem explicitly with the metadata we found
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()

                mediaController?.let { controller ->
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()

                    pendingExternalUri = null

                    // Manually update UI immediately so we don't have to wait for the player listener
                    textTitle.text = metadata.title
                    val artist = metadata.artist.toString()
                    val album = metadata.albumTitle?.toString()
                    val artistText = if (album.isNullOrBlank()) artist else "$artist • $album"
                    textArtist.text = artistText
                }
            }
        }
    }
    // ---------------------------------------------------------

    private fun updateMetadataUI(mediaItem: MediaItem?) {
        val metadata = mediaItem?.mediaMetadata
        if (metadata != null) {
            val title = metadata.title?.toString() ?: "Unknown Title"
            textTitle.text = title

            val artist = metadata.artist?.toString() ?: "Unknown Artist"
            val album = metadata.albumTitle?.toString()
            val artistText = if (album.isNullOrBlank()) artist else "$artist • $album"
            textArtist.text = artistText
        } else {
            textTitle.text = "No Track Loaded"
            textArtist.text = "Waiting for Media Service"
        }
    }

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
                mediaController = controllerFuture.get()
                playerView.player = mediaController
                mediaController?.addListener(playerListener)
                Log.d(TAG, "MediaController connected.")

                if (pendingExternalUri != null) {
                    playExternalUri(pendingExternalUri!!)
                } else {
                    updateMetadataUI(mediaController?.currentMediaItem)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error connecting MediaController", e)
                textTitle.text = "Connection Failed"
                textArtist.text = "Check if MusicService is running"
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        if (controllerFuture.isDone) {
            mediaController?.removeListener(playerListener)
            playerView.player = null
            MediaController.releaseFuture(controllerFuture)
            Log.d(TAG, "MediaController listener removed and controller released.")
        }
        mediaController = null
    }
}