package de.westnordost.streetcomplete.user

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.user.CountryStatisticsDao
import de.westnordost.streetcomplete.databinding.FragmentQuestStatisticsBallPitBinding
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Shows the user's solved quests of each type in some kind of ball pit.  */
class QuestStatisticsByCountryFragment : Fragment(R.layout.fragment_quest_statistics_ball_pit)
{
    @Inject internal lateinit var countryStatisticsDao: CountryStatisticsDao

    interface Listener {
        fun onClickedCountryFlag(countryCode: String, solvedCount: Int, rank: Int?, countryBubbleView: View)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    private val binding by viewBinding(FragmentQuestStatisticsBallPitBinding::bind)

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycle.addObserver(binding.ballPitView)

        lifecycleScope.launch {
            val countriesStatistics = withContext(Dispatchers.IO) { countryStatisticsDao.getAll() }

            binding.ballPitView.setViews(countriesStatistics.map {
                createCountryBubbleView(it.countryCode, it.solvedCount, it.rank) to it.solvedCount
            })
        }
    }

    private fun createCountryBubbleView(countryCode: String, solvedCount: Int, rank: Int?): View {
        val ctx = requireContext()
        val countryBubbleView = CircularFlagView(ctx)
        countryBubbleView.id = View.generateViewId()
        countryBubbleView.layoutParams = ViewGroup.LayoutParams(240,240)
        countryBubbleView.countryCode = countryCode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            countryBubbleView.elevation = 6f.toPx(ctx)
        }
        countryBubbleView.setOnClickListener { v ->
            listener?.onClickedCountryFlag(countryCode, solvedCount, rank, v)
        }
        return countryBubbleView
    }
}

