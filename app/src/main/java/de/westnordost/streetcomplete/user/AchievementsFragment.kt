package de.westnordost.streetcomplete.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.UserStore
import de.westnordost.streetcomplete.data.user.achievements.Achievement
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsSource
import de.westnordost.streetcomplete.databinding.CellAchievementBinding
import de.westnordost.streetcomplete.databinding.FragmentAchievementsBinding
import de.westnordost.streetcomplete.ktx.awaitLayout
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.view.GridLayoutSpacingItemDecoration
import de.westnordost.streetcomplete.view.ListAdapter
import kotlinx.coroutines.*
import javax.inject.Inject

/** Shows the icons for all achieved achievements and opens a AchievementInfoFragment to show the
 *  details on click. */
class AchievementsFragment : Fragment(R.layout.fragment_achievements) {

    @Inject internal lateinit var userAchievementsSource: UserAchievementsSource
    @Inject internal lateinit var userStore: UserStore

    private val binding by viewBinding(FragmentAchievementsBinding::bind)

    private var actualCellWidth: Int = 0

    init {
        Injector.applicationComponent.inject(this)
    }

    interface Listener {
        fun onClickedAchievement(achievement: Achievement, level: Int, achievementBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    /* --------------------------------------- Lifecycle ---------------------------------------- */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val minCellWidth = 144f.toPx(ctx)
        val itemSpacing = ctx.resources.getDimensionPixelSize(R.dimen.achievements_item_margin)

        viewLifecycleOwner.lifecycleScope.launch {
            view.awaitLayout()

            binding.emptyText.visibility = View.GONE

            val spanCount = (view.width / (minCellWidth + itemSpacing)).toInt()
            actualCellWidth = (view.width.toFloat() / spanCount - itemSpacing).toInt()

            val layoutManager = GridLayoutManager(ctx, spanCount, RecyclerView.VERTICAL, false)
            binding.achievementsList.layoutManager = layoutManager
            binding.achievementsList.addItemDecoration(GridLayoutSpacingItemDecoration(itemSpacing))
            binding.achievementsList.clipToPadding = false

            val achievements = withContext(Dispatchers.IO) {
                userAchievementsSource.getAchievements()
            }
            binding.achievementsList.adapter = AchievementsAdapter(achievements)

            binding.emptyText.isGone = achievements.isNotEmpty()
        }
    }

    override fun onStart() {
        super.onStart()

        if (userStore.isSynchronizingStatistics) {
            binding.emptyText.setText(R.string.stats_are_syncing)
        } else {
            binding.emptyText.setText(R.string.achievements_empty)
        }
    }

    /* -------------------------------------- Interaction --------------------------------------- */

    private inner class AchievementsAdapter(achievements: List<Pair<Achievement, Int>>
    ) : ListAdapter<Pair<Achievement, Int>>(achievements) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = CellAchievementBinding.inflate(LayoutInflater.from(parent.context),parent, false)
            binding.root.updateLayoutParams {
                width = actualCellWidth
                height = actualCellWidth
            }
            return ViewHolder(binding)
        }

        inner class ViewHolder(val binding : CellAchievementBinding) : ListAdapter.ViewHolder<Pair<Achievement, Int>>(binding) {
            override fun onBind(with: Pair<Achievement, Int>) {
                val achievement = with.first
                val level = with.second
                binding.achievementIconView.icon = resources.getDrawable(achievement.icon)
                binding.achievementIconView.level = level
                binding.achievementIconView.setOnClickListener {
                    listener?.onClickedAchievement(achievement, level, binding.achievementIconView)
                }
            }
        }
    }
}
