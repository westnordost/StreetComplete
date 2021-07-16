package de.westnordost.streetcomplete.quests.note_discussion

import android.content.Context
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.doOnLayout

import java.io.File

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.CellImageThumbnailBinding
import de.westnordost.streetcomplete.util.decodeScaledBitmapAndNormalize
import de.westnordost.streetcomplete.view.ListAdapter

class NoteImageAdapter(list: List<String>, private val context: Context) : ListAdapter<String>(list) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<String> {
        val inflater = LayoutInflater.from(parent.context)
        return NoteImageViewHolder(CellImageThumbnailBinding.inflate(inflater, parent, false))
    }

    private inner class NoteImageViewHolder(val binding: CellImageThumbnailBinding) : ViewHolder<String>(binding) {

//        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        init {
            binding.imageView.setOnClickListener {
                val index = adapterPosition
                if (index > -1) onClickDelete(index)
            }
        }

        override fun onBind(with: String) {
            binding.root.doOnLayout {
                val bitmap = decodeScaledBitmapAndNormalize(with, binding.imageView.width, binding.imageView.height)
                binding.imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun onClickDelete(index: Int) {
        AlertDialog.Builder(context)
            .setMessage(R.string.quest_leave_new_note_photo_delete_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> delete(index) }
            .show()
    }

    private fun delete(index: Int) {
        val imagePath = list.removeAt(index)
        val image = File(imagePath)
        if (image.exists()) {
            image.delete()
        }
        notifyItemRemoved(index)
    }
}
