package green.mobileapps.bopmusicplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import green.mobileapps.bopmusicplayer.databinding.MainActivityBinding
import green.mobileapps.bopmusicplayer.databinding.RecyclerItemFileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

// 1. Data Model for an Audio File
data class AudioFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val duration: Long
)

// 2. RecyclerView Adapter to display the list of files
class MusicAdapter(private val context: Context, private var musicList: List<AudioFile>) :
    RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    inner class MusicViewHolder(private val binding: RecyclerItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: AudioFile) {
            binding.textTitle.text = file.title
            binding.textArtist.text = file.artist

            // Example click listener (where playback logic would go)
            binding.root.setOnClickListener {
                // In a real app, you would start a service or play the music here.
                // For simplicity, we just log a message.
                // Replace with actual player implementation (e.g., MediaPlayer or ExoPlayer)
                println("Playing: ${file.title}")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = RecyclerItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(musicList[position])
    }

    override fun getItemCount(): Int = musicList.size

    fun updateList(newList: List<AudioFile>) {
        musicList = newList
        notifyDataSetChanged()
    }
}

// 3. Main Activity with Permission and Scanning Logic
class MainActivity : AppCompatActivity(), CoroutineScope {

    // Coroutine setup
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: MainActivityBinding
    private lateinit var musicAdapter: MusicAdapter

    // Determine the correct permission based on Android version
    private val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Register the permission request contract
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start scanning
            scanForAudioFiles()
        } else {
            // Permission denied, show a message
            showStatus("Permission denied. Cannot scan local storage.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        // Check and request permission on app open
        checkPermissions()
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(this, emptyList())
        binding.recyclerViewMusic.adapter = musicAdapter
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted
            scanForAudioFiles()
        } else {
            // Permission is not granted, request it
            requestPermissionLauncher.launch(mediaPermission)
        }
    }

    private fun scanForAudioFiles() = launch {
        showStatus("Scanning for audio files...")

        val audioList = withContext(Dispatchers.IO) {
            loadAudioFilesFromStorage()
        }

        if (audioList.isEmpty()) {
            showStatus("No audio files found. Ensure you have MP3s in your music folder.")
        } else {
            musicAdapter.updateList(audioList)
            binding.recyclerViewMusic.visibility = View.VISIBLE
            binding.textStatus.visibility = View.GONE
        }
    }

    // Function to load audio files using ContentResolver (runs on IO dispatcher)
    private fun loadAudioFilesFromStorage(): List<AudioFile> {
        val files = mutableListOf<AudioFile>()
        val contentResolver = applicationContext.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Define which columns we want to retrieve
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // Filter for music files (optional, MediaStore.Audio.Media usually does this)
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

        contentResolver.query(
            uri,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            // Cache column indices
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val duration = cursor.getLong(durationColumn)

                val contentUri: Uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                // Add only files with a reasonable duration (e.g., over 30 seconds) to filter out system sounds
                if (duration > 30000) {
                    files.add(AudioFile(id, contentUri, title, artist, duration))
                }
            }
        }
        return files
    }

    private fun showStatus(message: String) {
        binding.recyclerViewMusic.visibility = View.GONE
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the coroutine job when the activity is destroyed
        job.cancel()
    }

    fun onRateClick(item: MenuItem) {}
    fun onHelpClick(item: MenuItem) {}
    fun showBigFrag(item: MenuItem) {}
}
