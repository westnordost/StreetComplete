package de.westnordost.streetcomplete.quests.foot

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestButtonpanelYesNoSidewalkBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.foot.ProhibitedForPedestriansAnswer.*

class AddProhibitedForPedestriansForm : AbstractQuestAnswerFragment<ProhibitedForPedestriansAnswer>() {

    override val buttonsResId = R.layout.quest_buttonpanel_yes_no_sidewalk

    override val contentLayoutResId = R.layout.quest_prohibited_for_pedestrians_separate_sidewalk_explanation

    private val binding by viewBinding(QuestButtonpanelYesNoSidewalkBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.yesButton.setOnClickListener { applyAnswer(YES) }
        binding.noButton.setOnClickListener { applyAnswer(NO) }
        binding.sidewalkButton.setOnClickListener { applyAnswer(HAS_SEPARATE_SIDEWALK) }
    }

    // the living street answer stuff is copied from AddMaxSpeedForm
    override val otherAnswers: List<OtherAnswer> get() {
        val result = mutableListOf<OtherAnswer>()

        val highwayTag = osmElement!!.tags["highway"]!!
        if (countryInfo.isLivingStreetKnown && MAYBE_LIVING_STREET.contains(highwayTag)) {
            result.add(OtherAnswer(R.string.quest_maxspeed_answer_living_street) { confirmLivingStreet() })
        }
        return result
    }

    private fun confirmLivingStreet() {
        activity?.let {
            val view = layoutInflater.inflate(R.layout.quest_maxspeed_living_street_confirmation, null, false)
            // this is necessary because the inflated image view uses the activity context rather than
            // the fragment / layout inflater context' resources to access it's drawable
            val img = view.findViewById<ImageView>(R.id.livingStreetImage)
            img.setImageDrawable(resources.getDrawable(R.drawable.ic_living_street))
            AlertDialog.Builder(it)
                .setView(view)
                .setTitle(R.string.quest_maxspeed_answer_living_street_confirmation_title)
                .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(IS_LIVING_STREET) }
                .setNegativeButton(R.string.quest_generic_confirmation_no, null)
                .show()
        }
    }

    companion object {
        private val MAYBE_LIVING_STREET = listOf("residential", "unclassified")
    }
}
