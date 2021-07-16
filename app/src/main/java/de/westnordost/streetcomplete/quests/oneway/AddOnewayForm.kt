package de.westnordost.streetcomplete.quests.oneway

import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.AnyThread
import android.view.View

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.databinding.QuestOnewayBinding
import de.westnordost.streetcomplete.databinding.QuestStreetSidePuzzleBinding
import de.westnordost.streetcomplete.databinding.ViewLittleCompassBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.StreetSideRotater
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.*
import de.westnordost.streetcomplete.util.getOrientationAtCenterLineInDegrees
import de.westnordost.streetcomplete.view.DrawableImage
import de.westnordost.streetcomplete.view.ResImage
import de.westnordost.streetcomplete.view.ResText
import de.westnordost.streetcomplete.view.RotatedCircleDrawable
import de.westnordost.streetcomplete.view.image_select.*
import kotlin.math.PI

class AddOnewayForm : AbstractQuestFormAnswerFragment<OnewayAnswer>() {

    override val contentLayoutResId = R.layout.quest_oneway
    override val contentPadding = false

    private val questStreetSidePuzzleBinding by viewBinding(QuestStreetSidePuzzleBinding::bind)
    private val viewLittleCompassBinding by viewBinding(ViewLittleCompassBinding::bind)

    private var streetSideRotater: StreetSideRotater? = null

    private var selection: OnewayAnswer? = null

    private var mapRotation: Float = 0f
    private var wayRotation: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getString(SELECTION)?.let { selection = valueOf(it) }

        wayRotation = (elementGeometry as ElementPolylinesGeometry).getOrientationAtCenterLineInDegrees()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        questStreetSidePuzzleBinding.puzzleView.showOnlyRightSide()
        questStreetSidePuzzleBinding.puzzleView.onClickSideListener = { showDirectionSelectionDialog() }

        val defaultResId = R.drawable.ic_oneway_unknown

        questStreetSidePuzzleBinding.puzzleView.setRightSideImage(ResImage(selection?.iconResId ?: defaultResId))

        questStreetSidePuzzleBinding.puzzleView.setRightSideText(selection?.titleResId?.let { resources.getString(it) })
        if (selection == null && !HAS_SHOWN_TAP_HINT) {
            questStreetSidePuzzleBinding.puzzleView.showRightSideTapHint()
            HAS_SHOWN_TAP_HINT = true
        }

        streetSideRotater = StreetSideRotater(questStreetSidePuzzleBinding.puzzleView, viewLittleCompassBinding.compassNeedleView, elementGeometry as ElementPolylinesGeometry)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selection?.let { outState.putString(SELECTION, it.name) }
    }

    override fun isFormComplete() = selection != null

    override fun onClickOk() {
        applyAnswer(selection!!)
    }

    @AnyThread override fun onMapOrientation(rotation: Float, tilt: Float) {
        streetSideRotater?.onMapOrientation(rotation, tilt)
        mapRotation = (rotation * 180 / PI).toFloat()
    }

    private fun showDirectionSelectionDialog() {
        val ctx = context ?: return
        val items = OnewayAnswer.values().map { it.toItem(resources, wayRotation + mapRotation) }
        ImageListPickerDialog(ctx, items, R.layout.labeled_icon_button_cell, 3) { selected ->
            val oneway = selected.value!!
            questStreetSidePuzzleBinding.puzzleView.replaceRightSideImage(ResImage(oneway.iconResId))
            questStreetSidePuzzleBinding.puzzleView.setRightSideText(resources.getString(oneway.titleResId))
            selection = oneway
            checkIsFormComplete()
        }.show()
    }

    companion object {
        private const val SELECTION = "selection"
        private var HAS_SHOWN_TAP_HINT = false
    }
}

private fun OnewayAnswer.toItem(resources: Resources, rotation: Float): DisplayItem<OnewayAnswer> {
    val drawable = RotatedCircleDrawable(resources.getDrawable(iconResId))
    drawable.rotation = rotation
    return Item2(this, DrawableImage(drawable), ResText(titleResId))
}

private val OnewayAnswer.titleResId: Int get() = when(this) {
    FORWARD -> R.string.quest_oneway2_dir
    BACKWARD -> R.string.quest_oneway2_dir
    NO_ONEWAY -> R.string.quest_oneway2_no_oneway
}

private val OnewayAnswer.iconResId: Int get() = when(this) {
    FORWARD -> R.drawable.ic_oneway_lane
    BACKWARD -> R.drawable.ic_oneway_lane_reverse
    NO_ONEWAY -> R.drawable.ic_oneway_lane_no
}
