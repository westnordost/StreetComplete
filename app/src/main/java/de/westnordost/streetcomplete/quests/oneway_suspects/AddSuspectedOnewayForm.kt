package de.westnordost.streetcomplete.quests.oneway_suspects

import android.os.Bundle
import androidx.annotation.AnyThread
import android.view.View
import androidx.lifecycle.lifecycleScope

import javax.inject.Inject

import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.databinding.QuestStreetSidePuzzleBinding
import de.westnordost.streetcomplete.databinding.ViewLittleCompassBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AYesNoQuestAnswerFragment
import de.westnordost.streetcomplete.quests.StreetSideRotater
import de.westnordost.streetcomplete.quests.oneway_suspects.data.WayTrafficFlowDao
import de.westnordost.streetcomplete.view.ResImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddSuspectedOnewayForm : AYesNoQuestAnswerFragment<SuspectedOnewayAnswer>() {

    override val contentLayoutResId = R.layout.quest_oneway
    override val contentPadding = false

    private val questStreetSidePuzzleBinding by viewBinding(QuestStreetSidePuzzleBinding::bind)
    private val viewLittleCompassBinding by viewBinding(ViewLittleCompassBinding::bind)

    private var streetSideRotater: StreetSideRotater? = null

    @Inject internal lateinit var db: WayTrafficFlowDao

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        questStreetSidePuzzleBinding.puzzleView.showOnlyRightSide()

        lifecycleScope.launch {
            val isForward = withContext(Dispatchers.IO) { db.isForward(osmElement!!.id)!! }

            questStreetSidePuzzleBinding.puzzleView.setRightSideImage(ResImage(
                if (isForward) R.drawable.ic_oneway_lane
                else R.drawable.ic_oneway_lane_reverse
            ))
        }

        streetSideRotater = StreetSideRotater(questStreetSidePuzzleBinding.puzzleView, viewLittleCompassBinding.compassNeedleView, elementGeometry as ElementPolylinesGeometry)
    }

    override fun onClick(answer: Boolean) {
        // the quest needs the way ID of the element to find out the direction of the oneway
        applyAnswer(SuspectedOnewayAnswer(answer, osmElement!!.id))
    }

    @AnyThread
    override fun onMapOrientation(rotation: Float, tilt: Float) {
        streetSideRotater?.onMapOrientation(rotation, tilt)
    }
}
