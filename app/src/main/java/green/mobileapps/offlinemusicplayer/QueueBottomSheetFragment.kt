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

        // 1. Initialize ItemTouchHelper FIRST so we can reference it in the Adapter
        val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, // Drag directions
            0 // We handle swipe-to-remove in the main list, usually not here, or keep RIGHT/LEFT if desired
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                // Update Repo
                PlaylistRepository.swapQueueItems(from, to)
                // Notify Adapter locally to prevent stutter
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                PlaylistRepository.removeFromQueue(position)
                // Note: The observer in onViewCreated will handle the list update
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