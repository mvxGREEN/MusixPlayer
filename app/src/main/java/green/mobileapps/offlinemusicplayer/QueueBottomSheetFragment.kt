package green.mobileapps.offlinemusicplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import green.mobileapps.offlinemusicplayer.databinding.FragmentQueueBinding
import green.mobileapps.offlinemusicplayer.databinding.ItemQueueMusicBinding

class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: QueueAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize ItemTouchHelper
        val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, // Drag directions

            // 👇 CHANGE THIS LINE (Was 0) 👇
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                // 1. Update UI (Repo)
                PlaylistRepository.swapQueueItems(from, to)
                adapter.notifyItemMoved(from, to)

                // 2. Update Player (Service)
                val intent = android.content.Intent(requireContext(), MusicService::class.java).apply {
                    action = "ACTION_REORDER_QUEUE"
                    putExtra("EXTRA_FROM", from)
                    putExtra("EXTRA_TO", to)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition

                if (position != RecyclerView.NO_POSITION) {
                    // 1. Update UI (Repo)
                    PlaylistRepository.removeFromQueue(position)

                    // 2. Update Player (Service)
                    val intent = android.content.Intent(requireContext(), MusicService::class.java).apply {
                        action = "ACTION_REMOVE_FROM_QUEUE"
                        putExtra("EXTRA_QUEUE_INDEX", position)
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent)
                    } else {
                        requireContext().startService(intent)
                    }
                }
            }

            // Allow long press to drag too? true = yes, false = handle only.
            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(itemTouchCallback)

        // 2. Pass the "Start Drag" logic into the adapter
        adapter = QueueAdapter { holder ->
            itemTouchHelper.startDrag(holder)
        }

        binding.recyclerViewQueue.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewQueue.adapter = adapter

        // 3. Attach Helper
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewQueue)

        // Observe Queue Data
        PlaylistRepository.queue.observe(viewLifecycleOwner) { queue ->
            // Prevent stomping on the drag animation by only updating if size changed
            // or if the list content is actually different
            if (adapter.itemCount != queue.size) {
                adapter.submitList(queue)
            }
            binding.textQueueEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Updated Adapter accepts a callback
class QueueAdapter(private val onStartDrag: (RecyclerView.ViewHolder) -> Unit) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {
    private var items: List<AudioFile> = emptyList()

    fun submitList(newItems: List<AudioFile>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QueueViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility") // Suppress accessibility warning for touch listener
    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(items[position])

        // NEW: Detect touch on the handle to start drag immediately
        holder.binding.iconDrag.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    class QueueViewHolder(val binding: ItemQueueMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: AudioFile) {
            binding.textTitle.text = file.title
            binding.textArtist.text = file.artist
        }
    }
}