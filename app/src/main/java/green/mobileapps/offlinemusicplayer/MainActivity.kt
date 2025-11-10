package green.mobileapps.offlinemusicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import green.mobileapps.offlinemusicplayer.databinding.ItemMusicFileBinding
import green.mobileapps.offlinemusicplayer.databinding.MainActivityBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

// 2. RecyclerView Adapter to display the list of files
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

            binding.root.setOnClickListener {
                activity.startMusicPlayback(file)
            }
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

    // Adapter needs access to the activity, so it must be initialized later
    private lateinit var musicAdapter: MusicAdapter

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
                scanForAudioFiles()
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
        scanForAudioFiles()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(this, emptyList())
        binding.recyclerViewMusic.adapter = musicAdapter
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED) {
            // Storage permission granted. Check notification permission next.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            } else {
                scanForAudioFiles()
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
            scanForAudioFiles()
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

        // Define ALL columns we want to retrieve
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,

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

                val contentUri: Uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                // TODO album art ?

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
                    && !album?.contains("Voice Recorder")!!
                    && !isRecording
                    && !isRingtone
                    && !isAlarm
                    && !isNotification) {
                    //Log.d("loadAudioFilesFromStorage", "Adding file: $title, $artist, $album")
                    files.add(
                        AudioFile(
                            id, contentUri, title, artist, duration,
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

    // New function to start the MusicService
    fun startMusicPlayback(file: AudioFile) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = "ACTION_PLAY"
            putExtra("EXTRA_AUDIO_FILE", file)
        }

        // Use startForegroundService for starting the service from the background (e.g., after the app loads)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
