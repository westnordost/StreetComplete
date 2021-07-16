package de.westnordost.streetcomplete.quests

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtLinePosition
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtPoint
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitPolylineAtPosition
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.databinding.FragmentSplitWayBinding
import de.westnordost.streetcomplete.ktx.*
import de.westnordost.streetcomplete.util.SoundFx
import de.westnordost.streetcomplete.util.alongTrackDistanceTo
import de.westnordost.streetcomplete.util.crossTrackDistanceTo
import de.westnordost.streetcomplete.util.distanceTo
import de.westnordost.streetcomplete.view.RoundRectOutlineProvider
import de.westnordost.streetcomplete.view.insets_animation.respectSystemInsets
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.abs

/** Fragment that lets the user split an OSM way */
class SplitWayFragment : Fragment(R.layout.fragment_split_way),
    IsCloseableBottomSheet, IsShowingQuestDetails {

    private val splits: MutableList<Pair<SplitPolylineAtPosition, LatLon>> = mutableListOf()

    private val binding by viewBinding(FragmentSplitWayBinding::bind)

    @Inject internal lateinit var soundFx: SoundFx

    override val questKey: QuestKey get() = osmQuestKey

    private lateinit var osmQuestKey: OsmQuestKey
    private lateinit var way: Way
    private lateinit var positions: List<LatLon>
    private var clickPos: PointF? = null

    private val hasChanges get() = splits.isNotEmpty()
    private val isFormComplete get() = splits.size >= if (way.isClosed) 2 else 1

    interface Listener {
        fun onAddSplit(point: LatLon)
        fun onRemoveSplit(point: LatLon)
        fun onSplittedWay(osmQuestKey: OsmQuestKey, splits: List<SplitPolylineAtPosition>)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        osmQuestKey = Json.decodeFromString(args.getString(ARG_OSM_QUEST_KEY)!!)
        way = Json.decodeFromString(args.getString(ARG_WAY)!!)
        val elementGeometry: ElementPolylinesGeometry = Json.decodeFromString(args.getString(ARG_ELEMENT_GEOMETRY)!!)
        positions = elementGeometry.polylines.single().map { LatLon(it.latitude, it.longitude) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bottomSheetContainer.respectSystemInsets(View::setMargins)

        binding.splitWayRoot.setOnTouchListener { _, event ->
            clickPos = PointF(event.x, event.y)
            false
        }

        binding.okButton.setOnClickListener { onClickOk() }
        binding.cancelButton.setOnClickListener { activity?.onBackPressed() }
        binding.undoButton.setOnClickListener { onClickUndo() }

        binding.undoButton.isInvisible = !hasChanges
        binding.okButton.isInvisible = !isFormComplete

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cornerRadius = resources.getDimension(R.dimen.speech_bubble_rounded_corner_radius)
            val margin = resources.getDimensionPixelSize(R.dimen.horizontal_speech_bubble_margin)
            binding.speechbubbleContentContainer.outlineProvider = RoundRectOutlineProvider(
                cornerRadius, margin, margin, margin, margin
            )
        }

        if (savedInstanceState == null) {
            view.findViewById<View>(R.id.speechbubbleContentContainer).startAnimation(
                AnimationUtils.loadAnimation(context, R.anim.inflate_answer_bubble)
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // see rant comment in AbstractBottomSheetFragment
        resources.updateConfiguration(newConfig, resources.displayMetrics)

        binding.bottomSheetContainer.updateLayoutParams { width = resources.getDimensionPixelSize(R.dimen.quest_form_width) }
    }

    private fun onClickOk() {
        if (splits.size > 2) {
            confirmManySplits { onSplittedWayConfirmed() }
        } else {
            onSplittedWayConfirmed()
        }
    }

    private fun onSplittedWayConfirmed() {
        listener?.onSplittedWay(osmQuestKey, splits.map { it.first })
    }

    private fun confirmManySplits(callback: () -> (Unit)) {
        context?.let {
            AlertDialog.Builder(it)
                .setTitle(R.string.quest_generic_confirmation_title)
                .setMessage(R.string.quest_split_way_many_splits_confirmation_description)
                .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> callback() }
                .setNegativeButton(R.string.quest_generic_confirmation_no, null)
                .show()
        }
    }

    private fun onClickUndo() {
        if (splits.isNotEmpty()) {
            val item = splits.removeAt(splits.lastIndex)
            animateButtonVisibilities()
            lifecycleScope.launch { soundFx.play(R.raw.plop2) }
            listener?.onRemoveSplit(item.second)
        }
    }

    @UiThread
    override fun onClickMapAt(position: LatLon, clickAreaSizeInMeters: Double): Boolean {

        val splitWayCandidates = createSplits(position, clickAreaSizeInMeters)
        if (splitWayCandidates.isEmpty()) return true

        // show toast only if it is possible to zoom in further
        if (splitWayCandidates.size > 1 && clickAreaSizeInMeters > CLICK_AREA_SIZE_AT_MAX_ZOOM) {
            context?.toast(R.string.quest_split_way_too_imprecise)
            return true
        }
        val splitWay = splitWayCandidates.minByOrNull { it.pos.distanceTo(position) }!!
        val splitPosition = splitWay.pos

        // new split point is too close to existing split points
        if (splits.any { it.second.distanceTo(splitPosition) < clickAreaSizeInMeters } ) {
            context?.toast(R.string.quest_split_way_too_imprecise)
        } else {
            splits.add(Pair(splitWay, splitPosition))
            animateButtonVisibilities()
            animateScissors()
            listener?.onAddSplit(splitPosition)
        }

        // always consume event. User should press the cancel button to exit
        return true
    }


    private fun animateScissors() {
        val scissorsPos = clickPos ?: return

        (binding.scissors.drawable as? Animatable)?.start()

        binding.scissors.updateLayoutParams<RelativeLayout.LayoutParams> {
            leftMargin = (scissorsPos.x - binding.scissors.width/2).toInt()
            topMargin = (scissorsPos.y - binding.scissors.height/2).toInt()
        }
        binding.scissors.alpha = 1f
        val animator = AnimatorInflater.loadAnimator(context, R.animator.scissors_snip)
        animator.setTarget(binding.scissors)
        animator.start()

        lifecycleScope.launch { soundFx.play(R.raw.snip) }
    }

    private fun createSplits(clickPosition: LatLon, clickAreaSizeInMeters: Double): Set<SplitPolylineAtPosition> {
        val splitWaysAtNodes = createSplitsAtNodes(clickPosition, clickAreaSizeInMeters)
        // if a split on a node is possible, do that and don't even check if a split on a way is also possible
        if (splitWaysAtNodes.isNotEmpty()) return splitWaysAtNodes
        return createSplitsForLines(clickPosition, clickAreaSizeInMeters)
    }

    private fun createSplitsAtNodes(clickPosition: LatLon, clickAreaSizeInMeters: Double): Set<SplitAtPoint> {
        // ignore first and last node (cannot be split at the very start or end)
        val result = mutableSetOf<SplitAtPoint>()
        for (pos in positions.subList(1, positions.size - 1)) {
            val nodeDistance = clickPosition.distanceTo(pos)
            if (clickAreaSizeInMeters > nodeDistance) {
                result.add(SplitAtPoint(pos))
            }
        }
        return result
    }

    private fun createSplitsForLines(clickPosition: LatLon, clickAreaSizeInMeters: Double): Set<SplitAtLinePosition> {
        val result = mutableSetOf<SplitAtLinePosition>()
        positions.forEachLine { first, second ->
            val crossTrackDistance = abs(clickPosition.crossTrackDistanceTo(first, second))
            if (clickAreaSizeInMeters > crossTrackDistance) {
                val alongTrackDistance = clickPosition.alongTrackDistanceTo(first, second)
                val distance = first.distanceTo(second)
                if (distance > alongTrackDistance && alongTrackDistance > 0) {
                    val delta = alongTrackDistance / distance
                    result.add(SplitAtLinePosition(first, second, delta))
                }
            }
        }
        return result
    }

    @UiThread override fun onClickClose(onConfirmed: () -> Unit) {
        if (!hasChanges) {
            onConfirmed()
        } else {
            activity?.let {
                AlertDialog.Builder(it)
                    .setMessage(R.string.confirmation_discard_title)
                    .setPositiveButton(R.string.confirmation_discard_positive) { _, _ ->
                        onConfirmed()
                    }
                    .setNegativeButton(R.string.confirmation_discard_negative, null)
                    .show()
            }
        }
    }

    private fun animateButtonVisibilities() {
        if (isFormComplete) binding.okButton.popIn() else binding.okButton.popOut()
        if (hasChanges) binding.undoButton.popIn() else binding.undoButton.popOut()
    }

    companion object {
        private const val CLICK_AREA_SIZE_AT_MAX_ZOOM = 2.6

        private const val ARG_OSM_QUEST_KEY = "osmQuestKey"
        private const val ARG_WAY = "way"
        private const val ARG_ELEMENT_GEOMETRY = "elementGeometry"

        fun create(osmQuestKey: OsmQuestKey, way: Way, elementGeometry: ElementPolylinesGeometry): SplitWayFragment {
            val f = SplitWayFragment()
            f.arguments = bundleOf(
                ARG_OSM_QUEST_KEY to Json.encodeToString(osmQuestKey),
                ARG_WAY to Json.encodeToString(way),
                ARG_ELEMENT_GEOMETRY to Json.encodeToString(elementGeometry)
            )
            return f
        }
    }
}
