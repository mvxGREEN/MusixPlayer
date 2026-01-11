package green.mobileapps.offlinemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueAdapter()
        binding.recyclerViewQueue.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewQueue.adapter = adapter

        // Observe Queue Data
        PlaylistRepository.queue.observe(viewLifecycleOwner) { queue ->
            adapter.submitList(queue)
            binding.textQueueEmpty.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
        }

        // Setup Drag and Drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                PlaylistRepository.swapQueueItems(from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                PlaylistRepository.removeFromQueue(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewQueue)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class QueueAdapter : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {
    private var items: List<AudioFile> = emptyList()

    fun submitList(newItems: List<AudioFile>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class QueueViewHolder(private val binding: ItemQueueMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: AudioFile) {
            binding.textTitle.text = file.title
            binding.textArtist.text = file.artist
        }
    }
}