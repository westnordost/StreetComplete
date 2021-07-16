package de.westnordost.streetcomplete.user

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.achievements.Link
import de.westnordost.streetcomplete.data.user.achievements.LinkCategory
import de.westnordost.streetcomplete.databinding.RowLinkCategoryItemBinding
import de.westnordost.streetcomplete.databinding.RowLinkItemBinding

/** Adapter for a list of links, grouped by category */
class GroupedLinksAdapter(links: List<Link>, private val onClickLink: (url: String) -> Unit)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val groupedLinks: List<Item> = links
        .groupBy { it.category }
        .flatMap { entry ->
            val category = entry.key
            val linksInCategory = entry.value
            listOf(CategoryItem(category)) + linksInCategory.map { LinkItem(it) }
        }

    private val itemCount = groupedLinks.size

    override fun getItemCount(): Int = itemCount

    override fun getItemViewType(position: Int): Int = when (groupedLinks[position]) {
        is CategoryItem -> CATEGORY
        is LinkItem -> LINK
    }

    fun shouldItemSpanFullWidth(position: Int): Boolean = groupedLinks[position] is CategoryItem

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when(viewType) {
            CATEGORY -> CategoryViewHolder(RowLinkCategoryItemBinding.inflate(inflater, parent, false))
            LINK -> LinkViewHolder(RowLinkItemBinding.inflate(inflater, parent, false))
            else -> throw IllegalStateException("Unexpected viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = groupedLinks[position]) {
            is CategoryItem -> (holder as CategoryViewHolder).onBind(item.category)
            is LinkItem ->  (holder as LinkViewHolder).onBind(item.link)
        }
    }

    inner class LinkViewHolder(val binding: RowLinkItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBind(with: Link) {
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
            binding.root.setOnClickListener { onClickLink(with.url) }
        }
    }

    inner class CategoryViewHolder(val binding: RowLinkCategoryItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun onBind(with: LinkCategory) {
            binding.linkCategoryTitleTextView.setText(with.title)
            val description = with.description
            binding.linkCategoryDescriptionTextView.isGone = description == null
            if (description != null) {
                binding.linkCategoryDescriptionTextView.setText(description)
            } else {
                binding.linkCategoryDescriptionTextView.text = ""
            }
        }
    }

    companion object {
        private const val LINK = 0
        private const val CATEGORY = 1
    }
}

private sealed class Item

private data class CategoryItem(val category: LinkCategory) : Item()
private data class LinkItem(val link: Link) : Item()

private val LinkCategory.title: Int get() = when(this) {
    LinkCategory.INTRO -> R.string.link_category_intro_title
    LinkCategory.EDITORS -> R.string.link_category_editors_title
    LinkCategory.MAPS -> R.string.link_category_maps_title
    LinkCategory.SHOWCASE -> R.string.link_category_showcase_title
    LinkCategory.GOODIES -> R.string.link_category_goodies_title
}

private val LinkCategory.description: Int? get() = when(this) {
    LinkCategory.INTRO -> R.string.link_category_intro_description
    LinkCategory.EDITORS -> R.string.link_category_editors_description
    LinkCategory.SHOWCASE -> R.string.link_category_showcase_description
    LinkCategory.MAPS -> R.string.link_category_maps_description
    LinkCategory.GOODIES -> R.string.link_category_goodies_description
}
