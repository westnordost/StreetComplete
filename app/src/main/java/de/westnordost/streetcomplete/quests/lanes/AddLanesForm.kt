package de.westnordost.streetcomplete.quests.lanes

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.AnyThread
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.databinding.QuestLanesSelectTypeBinding
import de.westnordost.streetcomplete.databinding.QuestStreetLanesPuzzleBinding
import de.westnordost.streetcomplete.databinding.ViewLittleCompassBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.StreetSideRotater
import de.westnordost.streetcomplete.quests.lanes.LanesType.*
import de.westnordost.streetcomplete.view.dialogs.ValuePickerDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AddLanesForm : AbstractQuestFormAnswerFragment<LanesAnswer>() {

    private val questLanesSelectBinding by viewBinding(QuestLanesSelectTypeBinding::bind)
    private val questStreetLanesPuzzleBinding by viewBinding(QuestStreetLanesPuzzleBinding::bind)
    private val viewLittleCompassBinding by viewBinding(ViewLittleCompassBinding::bind)

    private var selectedLanesType: LanesType? = null
    private var leftSide: Int = 0
    private var rightSide: Int = 0
    private var hasCenterLeftTurnLane: Boolean = false

    private var lastRotation: Float = 0f
    private var lastTilt: Float = 0f

    override val contentPadding get() = selectedLanesType == null

    private var puzzleView: LanesSelectPuzzle? = null

    private var streetSideRotater: StreetSideRotater? = null

    // just some shortcuts

    private val isLeftHandTraffic get() = countryInfo.isLeftHandTraffic

    private val isOneway get() = isForwardOneway || isReversedOneway

    private val isForwardOneway get() = osmElement!!.tags["oneway"] == "yes" || osmElement!!.tags["junction"] == "roundabout"
    private val isReversedOneway get() = osmElement!!.tags["oneway"] == "-1"

    override val otherAnswers: List<OtherAnswer> get() {
        val answers = mutableListOf<OtherAnswer>()

        if (!isOneway && countryInfo.isCenterLeftTurnLaneKnown) {
            answers.add(OtherAnswer(R.string.quest_lanes_answer_lanes_center_left_turn_lane) {
                selectedLanesType = MARKED_SIDES
                hasCenterLeftTurnLane = true
                setStreetSideLayout()
            })
        }
        return answers
    }

    //region Lifecycle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            selectedLanesType = savedInstanceState.getString(LANES_TYPE)?.let { LanesType.valueOf(it) }
            leftSide = savedInstanceState.getInt(LANES_LEFT, 0)
            rightSide = savedInstanceState.getInt(LANES_RIGHT, 0)
            hasCenterLeftTurnLane = savedInstanceState.getBoolean(CENTER_LEFT_TURN_LANE)
        }

        if (selectedLanesType == null || selectedLanesType == UNMARKED) {
            setSelectLanesTypeLayout()
        } else {
            setStreetSideLayout()
        }
    }

    @AnyThread override fun onMapOrientation(rotation: Float, tilt: Float) {
        streetSideRotater?.onMapOrientation(rotation, tilt)
        lastRotation = rotation
        lastTilt = tilt
    }

    override fun isFormComplete(): Boolean = when (selectedLanesType) {
        null -> false
        UNMARKED -> true
        MARKED_SIDES -> leftSide > 0 && rightSide > 0
        else -> leftSide > 0 || rightSide > 0
    }

    override fun isRejectingClose() = leftSide > 0 || rightSide > 0

    override fun onClickOk() {
        val totalLanes = leftSide + rightSide
        when(selectedLanesType) {
            MARKED -> applyAnswer(MarkedLanes(totalLanes))
            UNMARKED -> applyAnswer(UnmarkedLanes)
            MARKED_SIDES -> {
                val forwardLanes = if (isLeftHandTraffic) leftSide else rightSide
                val backwardLanes = if (isLeftHandTraffic) rightSide else leftSide
                applyAnswer(MarkedLanesSides(forwardLanes, backwardLanes, hasCenterLeftTurnLane))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(LANES_TYPE, selectedLanesType?.name)
        outState.putInt(LANES_LEFT, leftSide)
        outState.putInt(LANES_RIGHT, rightSide)
        outState.putBoolean(CENTER_LEFT_TURN_LANE, hasCenterLeftTurnLane)
    }

    //endregion

    //region Select lanes type

    private fun setSelectLanesTypeLayout() {
        val view = setContentView(R.layout.quest_lanes_select_type)

        val unmarkedLanesButton = questLanesSelectBinding.unmarkedLanesButton

        unmarkedLanesButton.isSelected = selectedLanesType == UNMARKED

        unmarkedLanesButton.setOnClickListener {
            val wasSelected = unmarkedLanesButton.isSelected
            unmarkedLanesButton.isSelected = !wasSelected
            selectedLanesType = if (wasSelected) null else UNMARKED
            checkIsFormComplete()
        }
        questLanesSelectBinding.markedLanesButton.setOnClickListener {
            selectedLanesType = MARKED
            unmarkedLanesButton.isSelected = false
            checkIsFormComplete()
            askLanesAndSwitchToStreetSideLayout()
        }
        questLanesSelectBinding.markedLanesOddButton.isGone = isOneway

        questLanesSelectBinding.markedLanesOddButton.setOnClickListener {
            selectedLanesType = MARKED_SIDES
            unmarkedLanesButton.isSelected = false
            setStreetSideLayout()
        }
    }

    private fun askLanesAndSwitchToStreetSideLayout() {
        lifecycleScope.launch {
            val lanes = askForTotalNumberOfLanes()
            setTotalLanesCount(lanes)
            setStreetSideLayout()
        }
    }

    //endregion

    //region Street side layout

    private fun setStreetSideLayout() {
        puzzleView?.let {
            it.pause()
            lifecycle.removeObserver(it)
        }

        val view = setContentView(R.layout.quest_street_lanes_puzzle)

        puzzleView = questStreetLanesPuzzleBinding.puzzleView
        lifecycle.addObserver(questStreetLanesPuzzleBinding.puzzleView)

        when(selectedLanesType) {
            MARKED -> {
                questStreetLanesPuzzleBinding.puzzleView.onClickListener = this::selectTotalNumberOfLanes
                questStreetLanesPuzzleBinding.puzzleView.onClickSideListener = null
            }
            MARKED_SIDES -> {
                questStreetLanesPuzzleBinding.puzzleView.onClickListener = null
                questStreetLanesPuzzleBinding.puzzleView.onClickSideListener = this::selectNumberOfLanesOnOneSide
            }
        }
        questStreetLanesPuzzleBinding.puzzleView.isShowingLaneMarkings = selectedLanesType in listOf(MARKED, MARKED_SIDES)
        questStreetLanesPuzzleBinding.puzzleView.isShowingBothSides = !isOneway
        questStreetLanesPuzzleBinding.puzzleView.isForwardTraffic = if (isOneway) isForwardOneway else !isLeftHandTraffic

        val shoulderLine = countryInfo.shoulderLine

        questStreetLanesPuzzleBinding.puzzleView.shoulderLineColor =
            if(shoulderLine.contains("yellow")) Color.YELLOW else Color.WHITE
        questStreetLanesPuzzleBinding.puzzleView.shoulderLineStyle =
            if(shoulderLine.contains("dashes"))
                if (shoulderLine.contains("short")) LineStyle.SHORT_DASHES else LineStyle.DASHES
            else
                LineStyle.CONTINUOUS

        questStreetLanesPuzzleBinding.puzzleView.centerLineColor = if(countryInfo.centerLine.contains("yellow")) Color.YELLOW else Color.WHITE

        streetSideRotater = StreetSideRotater(questStreetLanesPuzzleBinding.puzzleViewRotateContainer,
            viewLittleCompassBinding.compassNeedleView, elementGeometry as ElementPolylinesGeometry)
        streetSideRotater?.onMapOrientation(lastRotation, lastTilt)

        updatePuzzleView()
    }

    private fun updatePuzzleView() {
        puzzleView?.setLaneCounts(leftSide, rightSide, hasCenterLeftTurnLane)
        checkIsFormComplete()
    }

    //endregion

    //region Lane selection dialog

    private fun selectNumberOfLanesOnOneSide(isRight: Boolean) {
        lifecycleScope.launch {
            setLanesCount(askForNumberOfLanesOnOneSide(isRight), isRight)
        }
    }

    private suspend fun askForNumberOfLanesOnOneSide(isRight: Boolean): Int {
        val currentLaneCount = if (isRight) rightSide else leftSide
        return showSelectMarkedLanesDialogForOneSide(currentLaneCount)
    }

    private fun setLanesCount(lanes: Int, isRightSide: Boolean) {
        if (isRightSide) rightSide = lanes
        else             leftSide = lanes
        updatePuzzleView()
    }

    private fun selectTotalNumberOfLanes() {
        lifecycleScope.launch {
            setTotalLanesCount(askForTotalNumberOfLanes())
        }
    }

    private suspend fun askForTotalNumberOfLanes(): Int {
        val currentLaneCount = rightSide + leftSide
        return if (selectedLanesType == MARKED) {
            if (isOneway) {
                showSelectMarkedLanesDialogForOneSide(currentLaneCount.takeIf { it > 0 })
            } else {
                showSelectMarkedLanesDialogForBothSides(currentLaneCount.takeIf { it > 0 })
            }
        } else {
            throw IllegalStateException()
        }
    }

    private fun setTotalLanesCount(lanes: Int) {
        if (isOneway) {
            leftSide = 0
            rightSide = lanes
        } else {
            leftSide = lanes / 2
            rightSide = lanes - lanes / 2
        }
        updatePuzzleView()
    }

    private suspend fun showSelectMarkedLanesDialogForBothSides(selectedValue: Int?) = suspendCancellableCoroutine<Int> { cont ->
        ValuePickerDialog(requireContext(),
            listOf(2,4,6,8,10,12,14),
            selectedValue, null,
            R.layout.quest_lanes_select_lanes,
            { cont.resume(it) }
        ).show()
    }

    private suspend fun showSelectMarkedLanesDialogForOneSide(selectedValue: Int?) = suspendCancellableCoroutine<Int> { cont ->
        ValuePickerDialog(requireContext(),
            listOf(1,2,3,4,5,6,7,8),
            selectedValue, null,
            R.layout.quest_lanes_select_lanes_one_side_only,
            { cont.resume(it) }
        ).show()
    }

    // endregion

    companion object {
        private const val LANES_TYPE = "lanes_type"
        private const val LANES_LEFT = "lanes_left"
        private const val LANES_RIGHT = "lanes_right"
        private const val CENTER_LEFT_TURN_LANE = "center_left_turn_lane"
    }
}

private enum class LanesType {
    MARKED, MARKED_SIDES, UNMARKED
}
