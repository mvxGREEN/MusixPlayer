package green.mobileapps.offlinemusicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import green.mobileapps.offlinemusicplayer.databinding.ItemMusicFileBinding
import green.mobileapps.offlinemusicplayer.databinding.MainActivityBinding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

// Helper extension function to safely get a string from a cursor
private fun Cursor.getNullableString(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getString(index) else null
}

// Helper extension function to safely get a long from a cursor
private fun Cursor.getNullableLong(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getLong(index) else null
}

// Helper extension function to safely get an int from a cursor
private fun Cursor.getNullableInt(columnName: String): Int? {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getInt(index) else null
}

// Helper extension function to safely get a boolean from a cursor (converts 0/1 to Boolean)
private fun Cursor.getNullableBoolean(columnName: String): Boolean? {
    val index = getColumnIndex(columnName)
    return if (index != -1 && !isNull(index)) getInt(index) == 1 else null
}

data class AudioFile(
    // Core fields
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val duration: Long,

    // Album Art Metadata (NEW)
    val albumId: Long?,

    // Media Store Metadata
    val album: String?,
    val albumArtist: String?,
    val author: String?,
    val composer: String?,
    val track: Int?,
    val year: Int?,
    val genre: String?,

    // File Metadata
    val size: Long?,
    val dateAdded: Long?,
    val dateModified: Long?,

    // Playback Metadata
    val bookmark: Long?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val bitsPerSample: Int?,

    // Classification Flags (Boolean)
    val isAudiobook: Boolean,
    val isMusic: Boolean,
    val isPodcast: Boolean,
    val isRecording: Boolean,
    val isAlarm: Boolean,
    val isNotification: Boolean,
    val isRingtone: Boolean,

    // Status Flags (Boolean)
    val isDownload: Boolean,
    val isDrm: Boolean,
    val isFavorite: Boolean,
    val isPending: Boolean,
    val isTrashed: Boolean
) : Parcelable {

    // Parcelable implementation boilerplate
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readParcelable(Uri::class.java.classLoader)!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readValue(Long::class.java.classLoader) as? Long, // albumId
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readString(),
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeParcelable(uri, flags)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeLong(duration)
        parcel.writeValue(albumId)
        parcel.writeString(album)
        parcel.writeString(albumArtist)
        parcel.writeString(author)
        parcel.writeString(composer)
        parcel.writeValue(track)
        parcel.writeValue(year)
        parcel.writeString(genre)
        parcel.writeValue(size)
        parcel.writeValue(dateAdded)
        parcel.writeValue(dateModified)
        parcel.writeValue(bookmark)
        parcel.writeValue(sampleRate)
        parcel.writeValue(bitrate)
        parcel.writeValue(bitsPerSample)
        parcel.writeByte(if (isAudiobook) 1 else 0)
        parcel.writeByte(if (isMusic) 1 else 0)
        parcel.writeByte(if (isPodcast) 1 else 0)
        parcel.writeByte(if (isRecording) 1 else 0)
        parcel.writeByte(if (isAlarm) 1 else 0)
        parcel.writeByte(if (isNotification) 1 else 0)
        parcel.writeByte(if (isRingtone) 1 else 0)
        parcel.writeByte(if (isDownload) 1 else 0)
        parcel.writeByte(if (isDrm) 1 else 0)
        parcel.writeByte(if (isFavorite) 1 else 0)
        parcel.writeByte(if (isPending) 1 else 0)
        parcel.writeByte(if (isTrashed) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AudioFile> {
        override fun createFromParcel(parcel: Parcel): AudioFile {
            return AudioFile(parcel)
        }

        override fun newArray(size: Int): Array<AudioFile?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Constructs the Uri for the album art image given the album ID.
 */
fun getAlbumArtUri(albumId: Long): Uri {
    return ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        albumId
    )
}

// 1. Playlist Repository (Singleton) - Acts as the persistent store
// This is accessible by the Service, ViewModel, and Activities.
object PlaylistRepository {
    private val _audioFiles = MutableLiveData<List<AudioFile>>(emptyList())
    val audioFiles: LiveData<List<AudioFile>> = _audioFiles

    // Store the last clicked/currently playing index in the full list
    var currentTrackIndex: Int = -1

    fun setFiles(files: List<AudioFile>) {
        _audioFiles.postValue(files)
    }

    // Utility function for the service to get the current track
    fun getCurrentTrack(): AudioFile? {
        val list = _audioFiles.value
        return if (list != null && currentTrackIndex >= 0 && currentTrackIndex < list.size) {
            list[currentTrackIndex]
        } else {
            null
        }
    }

    // Utility function for the service to get the full list
    fun getFullPlaylist(): List<AudioFile> = _audioFiles.value ?: emptyList()
}


// 2. Music ViewModel - Used by MainActivity to load and filter the data
class MusicViewModel(application: android.app.Application) : AndroidViewModel(application) {

    // The full, unfiltered list from the repository
    private val fullAudioList: LiveData<List<AudioFile>> = PlaylistRepository.audioFiles

    // The list maintained for filtering purposes (displayed in RecyclerView)
    private var musicListFull: List<AudioFile> = emptyList()
    private val _filteredList = MutableLiveData<List<AudioFile>>(emptyList())
    val filteredList: LiveData<List<AudioFile>> = _filteredList

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    // NEW: LiveData to indicate loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Coroutine setup
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    init {
        // Observe the repository's full list to manage internal list and update filtered list
        fullAudioList.observeForever { newList ->
            musicListFull = newList
            // When the full list is updated, apply the last filter (or show the full list if no filter)
            filterList("")
        }
    }

    fun loadAudioFiles(context: Context) {
        if (_isLoading.value == true) return // Prevent multiple simultaneous scans

        _isLoading.postValue(true)
        _statusMessage.postValue("Scanning for audio files...")

        scope.launch {
            val audioList = loadAudioFilesFromStorage(context)
            PlaylistRepository.setFiles(audioList) // Update the repository

            if (audioList.isEmpty()) {
                _statusMessage.postValue("No audio files found. Ensure you have MP3s in your music folder.")
            } else {
                // The filteredList observer handles the UI update
                _statusMessage.postValue("Loaded ${audioList.size} tracks.")
            }

            _isLoading.postValue(false)
        }
    }

    // Function to load audio files using ContentResolver
    private fun loadAudioFilesFromStorage(context: Context): List<AudioFile> {
        val files = mutableListOf<AudioFile>()
        val contentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Define ALL columns we want to retrieve
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,

            // Requested Fields:
            MediaStore.Audio.Media.BOOKMARK,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.SAMPLERATE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.BITS_PER_SAMPLE,
            MediaStore.Audio.Media.IS_AUDIOBOOK,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_PODCAST,
            MediaStore.Audio.Media.IS_RECORDING,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.AUTHOR,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.IS_DOWNLOAD,
            MediaStore.Audio.Media.IS_DRM,
            MediaStore.Audio.Media.IS_FAVORITE,
            MediaStore.Audio.Media.IS_PENDING,
            MediaStore.Audio.Media.IS_TRASHED
        )

        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

        contentResolver.query(
            uri,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->

            while (cursor.moveToNext()) {
                // --- Core Fields ---
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = cursor.getNullableString(MediaStore.Audio.Media.TITLE) ?: "Unknown Title"
                val artist = cursor.getNullableString(MediaStore.Audio.Media.ARTIST) ?: "Unknown Artist"
                val duration = cursor.getNullableLong(MediaStore.Audio.Media.DURATION) ?: 0L
                val albumId = cursor.getNullableLong(MediaStore.Audio.Media.ALBUM_ID)

                val contentUri: Uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                // --- Extended Metadata Extraction using safe helpers ---
                val album = cursor.getNullableString(MediaStore.Audio.Media.ALBUM)
                val albumArtist = cursor.getNullableString(MediaStore.Audio.Media.ALBUM_ARTIST)
                val author = cursor.getNullableString(MediaStore.Audio.Media.AUTHOR)
                val composer = cursor.getNullableString(MediaStore.Audio.Media.COMPOSER)
                val track = cursor.getNullableInt(MediaStore.Audio.Media.TRACK)
                val year = cursor.getNullableInt(MediaStore.Audio.Media.YEAR)
                val genre = cursor.getNullableString(MediaStore.Audio.Media.GENRE)

                val size = cursor.getNullableLong(MediaStore.Audio.Media.SIZE)
                val dateAdded = cursor.getNullableLong(MediaStore.Audio.Media.DATE_ADDED)
                val dateModified = cursor.getNullableLong(MediaStore.Audio.Media.DATE_MODIFIED)

                val bookmark = cursor.getNullableLong(MediaStore.Audio.Media.BOOKMARK)
                val sampleRate = cursor.getNullableInt(MediaStore.Audio.Media.SAMPLERATE)
                val bitrate = cursor.getNullableInt(MediaStore.Audio.Media.BITRATE)
                val bitsPerSample = cursor.getNullableInt(MediaStore.Audio.Media.BITS_PER_SAMPLE)

                // Classification Flags (Defaulting to false if column is missing)
                val isAudiobook = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_AUDIOBOOK) ?: false
                val isMusic = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_MUSIC) ?: false
                val isPodcast = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_PODCAST) ?: false
                val isRecording = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_RECORDING) ?: false
                val isAlarm = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_ALARM) ?: false
                val isNotification = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_NOTIFICATION) ?: false
                val isRingtone = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_RINGTONE) ?: false

                // Status Flags (Defaulting to false if column is missing/for older APIs)
                val isDownload = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_DOWNLOAD) ?: false
                val isDrm = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_DRM) ?: false
                val isFavorite = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_FAVORITE) ?: false
                val isPending = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_PENDING) ?: false
                val isTrashed = cursor.getNullableBoolean(MediaStore.Audio.Media.IS_TRASHED) ?: false


                // Add only files with a reasonable duration (e.g., over 30 seconds)
                if (duration > 30000
                    && album != null // Add null check for album
                    && !album.contains("Voice Recorder")
                    && !isRecording
                    && !isRingtone
                    && !isAlarm
                    && !isNotification) {
                    files.add(
                        AudioFile(
                            id, contentUri, title, artist, duration, albumId,
                            album, albumArtist, author, composer, track, year, genre,
                            size, dateAdded, dateModified,
                            bookmark, sampleRate, bitrate, bitsPerSample,
                            isAudiobook, isMusic, isPodcast, isRecording, isAlarm, isNotification, isRingtone,
                            isDownload, isDrm, isFavorite, isPending, isTrashed
                        )
                    )
                }
            }
        }
        return files
    }

    fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase()
        val newList = if (lowerCaseQuery.isBlank()) {
            musicListFull
        } else {
            musicListFull.filter {
                it.title.lowercase().contains(lowerCaseQuery) ||
                        it.artist.lowercase().contains(lowerCaseQuery)
            }
        }
        _filteredList.value = newList
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}


// recyclerview adapter to display the list of files
class MusicAdapter(private val activity: MainActivity, private var musicList: List<AudioFile>) :
    RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    inner class MusicViewHolder(private val binding: ItemMusicFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: AudioFile) {
            // show text info
            val trackPrefix = if (file.track != null && file.track > 0) "${file.track}. " else ""
            binding.textTitle.text = "$trackPrefix${file.title}"

            val albumInfo = if (file.album != null) " • ${file.album}" else ""
            binding.textArtist.text = "${file.artist}$albumInfo"

            // GLIDE LOAD ALBUM ART
            val albumArtUri = if (file.albumId != null) {
                getAlbumArtUri(file.albumId)
            } else {
                null
            }

            // Use Glide to load the image
            com.bumptech.glide.Glide.with(itemView.context)
                .load(albumArtUri)
                .transform(com.bumptech.glide.load.resource.bitmap.CircleCrop())
                .placeholder(R.drawable.music_note_24px)
                .error(R.drawable.music_note_24px)
                .addListener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        // On failure (or null model), apply the tint to the placeholder
                        binding.imageAlbumArt.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.colorPrimary)
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        // On successful load, remove the tint
                        binding.imageAlbumArt.imageTintList = null
                        return false
                    }
                })
                .into(binding.imageAlbumArt)


            // on click listener
            binding.root.setOnClickListener {
                // Pass the file and its position to the activity's start function.
                // adapterPosition is the index in the *filtered* list.
                activity.startMusicPlayback(file, adapterPosition)
            }

            // todo on long click listener: edit track info, delete track, etc.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicFileBinding.inflate(
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
        // Update the displayed list from the ViewModel's observer
        musicList = newList
        notifyDataSetChanged()
    }

    // Kept for startMusicPlayback but now unnecessary if we use the Repository directly
    fun getCurrentList(): List<AudioFile> = musicList

}

// 4. Main Activity with Permission and Scanning Logic
class MainActivity : AppCompatActivity(), CoroutineScope, SearchView.OnQueryTextListener {
    // Coroutine setup
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: MainActivityBinding
    private lateinit var viewModel: MusicViewModel // New ViewModel instance

    // Adapter needs access to the activity, so it must be initialized later
    public lateinit var musicAdapter: MusicAdapter

    // Determine the correct permission based on Android version
    private val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // For API 33+ (Android 13) we need to request POST_NOTIFICATIONS
    private val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    // Register the permission request contract for storage permission
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Storage granted, now check notification permission (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            } else {
                viewModel.loadAudioFiles(applicationContext) // Call ViewModel to scan files
            }
        } else {
            showStatus("Storage permission denied. Cannot scan local storage.")
        }
    }

    // Register the permission request contract for notification permission
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            println("Notification permission granted.")
        } else {
            println("Notification permission denied. Media controls won't be visible in status bar.")
        }
        // Always proceed to scan regardless of notification permission outcome
        viewModel.loadAudioFiles(applicationContext) // Call ViewModel to scan files
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application))
            .get(MusicViewModel::class.java)

        setupRecyclerView()
        setupSearchView()
        setupSwipeRefresh()
        setupObservers()
        checkPermissions()
    }

    // NEW: Override onResume to ensure focus/keyboard are hidden when returning
    override fun onResume() {
        super.onResume()
        // Ensure the keyboard is hidden and focus is cleared when returning to the activity
        hideKeyboardAndClearFocus()
    }

    private fun setupObservers() {
        // Observe the filtered list and update the adapter
        viewModel.filteredList.observe(this) { audioList ->
            if (audioList.isNotEmpty()) {
                musicAdapter.updateList(audioList)
                binding.recyclerViewMusic.visibility = View.VISIBLE
                binding.textStatus.visibility = View.GONE
            } else if (PlaylistRepository.getFullPlaylist().isNotEmpty()) {
                // List is filtered to empty, but the full list exists
                musicAdapter.updateList(emptyList())
                showStatus("No tracks match your search.")
            }
        }

        // Observe status messages (e.g., scanning, permission denied)
        viewModel.statusMessage.observe(this) { message ->
            // Update status UI if needed
            if (!message.contains("Loaded")) {
                showStatus(message)
            }
        }

        // NEW: Observe loading state to control the refresh indicator
        viewModel.isLoading.observe(this) { isLoading ->
            // Assuming your layout binding has a property named `swipeRefreshLayout`
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        // Observe status messages (e.g., scanning, permission denied)
        viewModel.statusMessage.observe(this) { message ->
            // Update status UI if needed
            if (!message.contains("Loaded")) {
                showStatus(message)
            }
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(this, emptyList())
        binding.recyclerViewMusic.adapter = musicAdapter
    }

    private fun setupSearchView() {
        // Set up the SearchView listener
        binding.searchViewMusic.setOnQueryTextListener(this)
    }

    private fun setupSwipeRefresh() {
        // Assuming your layout binding has a property named `swipeRefreshLayout`
        binding.swipeRefreshLayout.setOnRefreshListener {
            // 1. Clear any existing list display (optional, but good for UX)
            musicAdapter.updateList(emptyList())

            // 2. Trigger the scan to reload all data
            viewModel.loadAudioFiles(applicationContext)
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED) {
            // Storage permission granted. Check notification permission next.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            } else {
                viewModel.loadAudioFiles(applicationContext)
            }
        } else {
            // Request storage permission
            requestStoragePermissionLauncher.launch(mediaPermission)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, notificationPermission) != PackageManager.PERMISSION_GRANTED) {
            // Request notification permission
            requestNotificationPermissionLauncher.launch(notificationPermission)
        } else {
            viewModel.loadAudioFiles(applicationContext)
        }
    }

    // This function is now responsible for setting the current play state in the repository
    // and starting the service without passing large data via Intent.
    fun startMusicPlayback(file: AudioFile, filteredIndex: Int) {
        // Get the full, unfiltered list from the persistent store.
        val fullPlaylist = PlaylistRepository.getFullPlaylist()

        if (fullPlaylist.isEmpty()) {
            Log.e("MainActivity", "Error: Full playlist is empty. Cannot start playback.")
            // You might want to show a Toast message here to the user.
            return
        }

        // Find the index of the clicked file (by ID) in the FULL, unfiltered playlist.
        // This is necessary because the MediaSessionService will be loaded with the full list.
        val actualIndex = fullPlaylist.indexOfFirst { it.id == file.id }

        if (actualIndex == -1) {
            Log.e("MainActivity", "Error: AudioFile not found in the full playlist. Cannot start playback.")
            return
        }

        // 1. Set the current track index in the persistent store
        PlaylistRepository.currentTrackIndex = actualIndex

        // NEW: Hide the keyboard and clear focus when a track is clicked
        hideKeyboardAndClearFocus()

        val intent = Intent(this, MusicService::class.java).apply {
            // 2. Action to tell the service to start playback from the repository state
            action = "ACTION_PLAY_FROM_REPO"
        }

        // Use startForegroundService for starting the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Start MusicActivity without any intent extras for the playlist/file
        val activityIntent = Intent(this, MusicActivity::class.java)
        startActivity(activityIntent)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        // NEW: Hide the soft keyboard and clear focus on submit
        hideKeyboardAndClearFocus()
        return true
    }

    /**
     * Called when the query text is changed by the user. This is where the filtering happens.
     */
    override fun onQueryTextChange(newText: String?): Boolean {
        // Filter the list using the ViewModel
        viewModel.filterList(newText.orEmpty())
        return true
    }

    private fun showStatus(message: String) {
        binding.recyclerViewMusic.visibility = View.GONE
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.text = message
    }

    // NEW: Helper function to hide the keyboard and clear focus
    private fun hideKeyboardAndClearFocus() {
        // 1. Clear focus from the SearchView to hide the cursor
        binding.searchViewMusic.clearFocus()

        // 2. Explicitly hide the keyboard using InputMethodManager
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchViewMusic.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // cancel the coroutine job when the activity is destroyed
        job.cancel()
    }

    fun onRateClick(item: MenuItem) {}
    fun onHelpClick(item: MenuItem) {}
    fun showBigFrag(item: MenuItem) {}
}