package de.westnordost.streetcomplete.user

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.achievements.Link
import de.westnordost.streetcomplete.databinding.RowLinkItemBinding
import de.westnordost.streetcomplete.view.ListAdapter

/** Adapter for a list of links */
class LinksAdapter(links: List<Link>, private val onClickLink: (url: String) -> Unit)
    : ListAdapter<Link>(links) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(RowLinkItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    inner class ViewHolder(val binding: RowLinkItemBinding) : ListAdapter.ViewHolder<Link>(binding) {
        override fun onBind(with: Link) {
            if (with.icon != null) {
                binding.linkIconImageView.setImageResource(with.icon)
            } else {
                binding.linkIconImageView.setImageDrawable(null)
            }
            binding.linkTitleTextView.text = with.title
            if (with.description != null) {
                binding.linkDescriptionTextView.setText(with.description)
            } else {
                binding.linkDescriptionTextView.text = ""
            }
            itemView.setOnClickListener { onClickLink(with.url) }
        }
    }
}
