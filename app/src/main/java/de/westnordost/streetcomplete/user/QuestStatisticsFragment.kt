package de.westnordost.streetcomplete.user

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.user.QuestStatisticsDao
import de.westnordost.streetcomplete.data.user.UserStore
import de.westnordost.streetcomplete.databinding.FragmentQuestStatisticsBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Shows the user's solved quests of each type in some kind of ball pit. Clicking on each opens
 *  a QuestTypeInfoFragment that shows the quest's details. */
class QuestStatisticsFragment : Fragment(R.layout.fragment_quest_statistics),
    QuestStatisticsByQuestTypeFragment.Listener,  QuestStatisticsByCountryFragment.Listener
{
    @Inject internal lateinit var questStatisticsDao: QuestStatisticsDao
    @Inject internal lateinit var userStore: UserStore

    interface Listener {
        fun onClickedQuestType(questType: QuestType<*>, solvedCount: Int, questBubbleView: View)
        fun onClickedCountryFlag(country: String, solvedCount: Int, rank: Int?, countryBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    private val binding by viewBinding(FragmentQuestStatisticsBinding::bind)

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            binding.emptyText.isGone = withContext(Dispatchers.IO) { questStatisticsDao.getTotalAmount() != 0 }
        }

        binding.byQuestTypeButton.setOnClickListener { v -> binding.selectorButton.check(v.id) }
        binding.byCountryButton.setOnClickListener { v -> binding.selectorButton.check(v.id) }

        binding.selectorButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.byQuestTypeButton -> replaceFragment(QuestStatisticsByQuestTypeFragment())
                    R.id.byCountryButton -> replaceFragment(QuestStatisticsByCountryFragment())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (userStore.isSynchronizingStatistics) {
            binding.emptyText.setText(R.string.stats_are_syncing)
        } else {
            binding.emptyText.setText(R.string.quests_empty)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        childFragmentManager.commit {
            setTransition(TRANSIT_FRAGMENT_FADE)
            replace(R.id.questStatisticsFragmentContainer, fragment)
        }
    }

    override fun onClickedQuestType(questType: QuestType<*>, solvedCount: Int, questBubbleView: View) {
        listener?.onClickedQuestType(questType, solvedCount, questBubbleView)
    }

    override fun onClickedCountryFlag(countryCode: String, solvedCount: Int, rank: Int?, countryBubbleView: View) {
        listener?.onClickedCountryFlag(countryCode, solvedCount, rank, countryBubbleView)
    }
}

