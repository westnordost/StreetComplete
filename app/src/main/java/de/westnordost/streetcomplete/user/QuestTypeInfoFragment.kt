package de.westnordost.streetcomplete.user

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnStart
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.databinding.FragmentQuestTypeInfoDialogBinding
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.view.CircularOutlineProvider
import kotlin.math.min
import kotlin.math.pow

/** Shows the details for a certain quest type as a fake-dialog. */
class QuestTypeInfoFragment : AbstractInfoFakeDialogFragment(R.layout.fragment_quest_type_info_dialog) {

    // need to keep the animators here to be able to clear them on cancel
    private var counterAnimation: ValueAnimator? = null

    private val binding by viewBinding(FragmentQuestTypeInfoDialogBinding::bind)

    /* ---------------------------------------- Lifecycle --------------------------------------- */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.titleView.outlineProvider = CircularOutlineProvider
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        counterAnimation?.cancel()
        counterAnimation = null
    }

    /* ---------------------------------------- Interface --------------------------------------- */

    fun show(questType: QuestType<*>, questCount: Int, questBubbleView: View) {
        if (!show(questBubbleView)) return
        binding.titleView.setImageResource(questType.icon)
        binding.questTitleText.text = resources.getString(questType.title, *Array(10){"…"})
        binding.solvedQuestsText.text = ""
        val scale = (0.4 + min( questCount / 100.0, 1.0)*0.6).toFloat()
        binding.solvedQuestsContainer.visibility = View.INVISIBLE
        binding.solvedQuestsContainer.scaleX = scale
        binding.solvedQuestsContainer.scaleY = scale
        binding.solvedQuestsContainer.setOnClickListener { counterAnimation?.end() }
        binding.wikiLinkButton.isInvisible = questType !is OsmElementQuestType || questType.wikiLink == null
        if (questType is OsmElementQuestType && questType.wikiLink != null) {
            binding.wikiLinkButton.setOnClickListener {
                openUrl("https://wiki.openstreetmap.org/wiki/${questType.wikiLink}")
            }
        }

        counterAnimation?.cancel()
        val anim = ValueAnimator.ofInt(0, questCount)

        anim.doOnStart { binding.solvedQuestsContainer.visibility = View.VISIBLE }
        anim.duration = 300 + (questCount * 500.0).pow(0.6).toLong()
        anim.addUpdateListener { binding.solvedQuestsText?.text = it.animatedValue.toString() }
        anim.interpolator = DecelerateInterpolator()
        anim.startDelay = ANIMATION_TIME_IN_MS
        anim.start()
        counterAnimation = anim
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }
}
