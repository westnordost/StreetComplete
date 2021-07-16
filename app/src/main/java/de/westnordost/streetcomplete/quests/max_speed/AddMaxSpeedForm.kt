package de.westnordost.streetcomplete.quests.max_speed

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isGone
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestMaxspeedBinding
import de.westnordost.streetcomplete.ktx.numberOrNull
import de.westnordost.streetcomplete.ktx.showKeyboard
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.max_speed.SpeedMeasurementUnit.KILOMETERS_PER_HOUR
import de.westnordost.streetcomplete.quests.max_speed.SpeedMeasurementUnit.MILES_PER_HOUR
import de.westnordost.streetcomplete.quests.max_speed.SpeedType.*
import de.westnordost.streetcomplete.util.TextChangedWatcher


class AddMaxSpeedForm : AbstractQuestFormAnswerFragment<MaxSpeedAnswer>() {

    override val contentLayoutResId = R.layout.quest_maxspeed

    private val binding by viewBinding(QuestMaxspeedBinding::bind)

    override val otherAnswers: List<OtherAnswer> get() {
        val result = mutableListOf<OtherAnswer>()
        if (countryInfo.isAdvisorySpeedLimitKnown) {
            result.add(OtherAnswer(R.string.quest_maxspeed_answer_advisory_speed_limit) { switchToAdvisorySpeedLimit() })
        }
        return result
    }

    private var speedInput: EditText? = null
    private var speedUnitSelect: Spinner? = null
    private var speedType: SpeedType? = null

    private val speedUnits get() = countryInfo.speedUnits.map { it.toSpeedMeasurementUnit() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val highwayTag = osmElement!!.tags["highway"]!!

        val couldBeSlowZone = countryInfo.isSlowZoneKnown && POSSIBLY_SLOWZONE_ROADS.contains(highwayTag)
        binding.zone.isGone = !couldBeSlowZone

        val couldBeLivingStreet = countryInfo.isLivingStreetKnown && MAYBE_LIVING_STREET.contains(highwayTag)
        binding.livingStreet.isGone = !couldBeLivingStreet

        val couldBeNSL = countryInfo.countryCode == "GB"
        binding.nsl.isGone = !couldBeNSL

        binding.speedTypeSelect.setOnCheckedChangeListener { _, checkedId -> setSpeedType(getSpeedType(checkedId)) }
    }

    override fun onClickOk() {
        if (speedType == NO_SIGN) {
            val couldBeSlowZone = countryInfo.isSlowZoneKnown
                    && POSSIBLY_SLOWZONE_ROADS.contains(osmElement!!.tags["highway"])

            if (couldBeSlowZone)
                confirmNoSignSlowZone { determineImplicitMaxspeedType() }
            else
                confirmNoSign { determineImplicitMaxspeedType() }
        } else if (speedType == LIVING_STREET) {
            applyAnswer(IsLivingStreet)
        } else if (speedType == NSL) {
            askIsDualCarriageway(
                onYes = { applyNoSignAnswer("nsl_dual") },
                onNo = { applyNoSignAnswer("nsl_single") })
        } else {
            if (userSelectedUnusualSpeed())
                confirmUnusualInput { applySpeedLimitFormAnswer() }
            else
                applySpeedLimitFormAnswer()
        }
    }

    override fun isFormComplete() =
        speedType == NO_SIGN || speedType == LIVING_STREET || speedType == NSL || getSpeedFromInput() != null

    /* ---------------------------------------- With sign --------------------------------------- */

    private fun setSpeedType(speedType: SpeedType?) {
        this.speedType = speedType

        binding.rightSideContainer.removeAllViews()
        speedType?.layoutResId?.let { layoutInflater.inflate(it, binding.rightSideContainer, true) }

        // this is necessary because the inflated image view uses the activity context rather than
        // the fragment / layout inflater context' resources to access its drawable
        val imgLiving = binding.rightSideContainer.findViewById<ImageView>(R.id.livingStreetImage)
        imgLiving?.setImageDrawable(resources.getDrawable(R.drawable.ic_living_street))

        val imgNSL = binding.rightSideContainer.findViewById<ImageView>(R.id.nationalSpeedLimitImage)
        imgNSL?.setImageDrawable(resources.getDrawable(R.drawable.ic_national_speed_limit))

        speedInput = binding.rightSideContainer.findViewById(R.id.maxSpeedInput)

        speedInput?.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })

        speedUnitSelect = binding.rightSideContainer.findViewById(R.id.speedUnitSelect)
        speedUnitSelect?.isGone = speedUnits.size == 1
        speedUnitSelect?.adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, speedUnits)
        speedUnitSelect?.setSelection(0)

        if (speedType == ZONE && LAST_INPUT_SLOW_ZONE != null) {
            speedInput?.setText(LAST_INPUT_SLOW_ZONE.toString())
        } else {
            speedInput?.requestFocus()
            speedInput?.showKeyboard()
        }

        checkIsFormComplete()
    }

    private fun getSpeedType(@IdRes checkedId: Int) = when (checkedId) {
        R.id.sign          -> SIGN
        R.id.zone          -> ZONE
        R.id.living_street -> LIVING_STREET
        R.id.nsl           -> NSL
        R.id.no_sign       -> NO_SIGN
        else -> null
    }

    private val SpeedType.layoutResId get() = when (this) {
        SIGN          -> R.layout.quest_maxspeed_sign
        ZONE          -> R.layout.quest_maxspeed_zone_sign
        LIVING_STREET -> R.layout.quest_maxspeed_living_street_sign
        NSL           -> R.layout.quest_maxspeed_national_speed_limit_sign
        ADVISORY      -> R.layout.quest_maxspeed_advisory
        else -> null
    }

    private fun userSelectedUnusualSpeed(): Boolean {
        val speed = getSpeedFromInput() ?: return false
        val kmh = speed.toKmh()
        return kmh > 140 || kmh > 20 && speed.toValue() % 5 != 0 || kmh < 10
    }

    private fun switchToAdvisorySpeedLimit() {
        binding.speedTypeSelect.clearCheck()
        for (child in binding.speedTypeSelect.children) {
            child.isEnabled = false
        }
        setSpeedType(ADVISORY)
    }

    private fun confirmUnusualInput(onConfirmed: () -> Unit) {
        activity?.let { AlertDialog.Builder(it)
            .setTitle(R.string.quest_generic_confirmation_title)
            .setMessage(R.string.quest_maxspeed_unusualInput_confirmation_description)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> onConfirmed() }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
        }
    }

    private fun applySpeedLimitFormAnswer() {
        val speed = getSpeedFromInput()!!
        when (speedType) {
            ADVISORY      -> applyAnswer(AdvisorySpeedSign(speed))
            ZONE          -> {
                val zoneX = speed.toValue()
                LAST_INPUT_SLOW_ZONE = zoneX
                applyAnswer(MaxSpeedZone(speed, countryInfo.countryCode, "zone$zoneX"))
            }
            SIGN          -> applyAnswer(MaxSpeedSign(speed))
            else          -> throw IllegalStateException()
        }
    }

    private fun getSpeedFromInput(): SpeedMeasure? {
        val value = speedInput?.numberOrNull?.toInt() ?: return null
        val unit = speedUnitSelect?.selectedItem as SpeedMeasurementUnit? ?: speedUnits.first()
        return when(unit) {
            KILOMETERS_PER_HOUR -> Kmh(value)
            MILES_PER_HOUR -> Mph(value)
        }
    }

    /* ----------------------------------------- No sign ---------------------------------------- */

    private fun confirmNoSign(onConfirmed: () -> Unit) {
        activity?.let {
            AlertDialog.Builder(it)
                .setTitle(R.string.quest_maxspeed_answer_noSign_confirmation_title)
                .setMessage(R.string.quest_maxspeed_answer_noSign_confirmation)
                .setPositiveButton(R.string.quest_maxspeed_answer_noSign_confirmation_positive) { _, _ -> onConfirmed() }
                .setNegativeButton(R.string.quest_generic_confirmation_no, null)
                .show()
        }
    }

    private fun confirmNoSignSlowZone(onConfirmed: () -> Unit) {
        activity?.let {
            val view = layoutInflater.inflate(R.layout.quest_maxspeed_no_sign_no_slow_zone_confirmation, null, false)
            val input = view.findViewById<EditText>(R.id.maxSpeedInput)
            input.setText("××")
            input.inputType = EditorInfo.TYPE_NULL

            AlertDialog.Builder(it)
                .setTitle(R.string.quest_maxspeed_answer_noSign_confirmation_title)
                .setView(view)
                .setPositiveButton(R.string.quest_maxspeed_answer_noSign_confirmation_positive) { _, _ -> onConfirmed() }
                .setNegativeButton(R.string.quest_generic_confirmation_no, null)
                .show()
        }
    }

    private fun determineImplicitMaxspeedType() {
        val highwayTag = osmElement!!.tags["highway"]!!
        if (countryInfo.countryCode == "GB") {
            if (ROADS_WITH_DEFINITE_SPEED_LIMIT_GB.contains(highwayTag)) {
                applyNoSignAnswer(highwayTag)
            } else {
                askIsDualCarriageway(
                    onYes = { applyNoSignAnswer("nsl_dual") },
                    onNo = {
                        determineLit(
                            onYes = { applyNoSignAnswer("nsl_restricted", true) },
                            onNo = { applyNoSignAnswer("nsl_single", false) }
                        )
                    }
                )
            }
        } else if (ROADS_WITH_DEFINITE_SPEED_LIMIT.contains(highwayTag) ) {
            applyNoSignAnswer(highwayTag)
        } else {
            askUrbanOrRural(
                onUrban = { applyNoSignAnswer("urban") },
                onRural = { applyNoSignAnswer("rural") })
        }
    }

    private fun askUrbanOrRural(onUrban: () -> Unit, onRural: () -> Unit) {
        activity?.let {
            AlertDialog.Builder(it)
                .setTitle(R.string.quest_maxspeed_answer_noSign_info_urbanOrRural)
                .setMessage(R.string.quest_maxspeed_answer_noSign_urbanOrRural_description)
                .setPositiveButton(R.string.quest_maxspeed_answer_noSign_urbanOk) { _, _ -> onUrban() }
                .setNegativeButton(R.string.quest_maxspeed_answer_noSign_ruralOk) { _, _ -> onRural() }
                .show()
        }
    }

    private fun determineLit(onYes: () -> Unit, onNo: () -> Unit) {
        val lit = osmElement!!.tags["lit"]
        when (lit) {
            "yes" -> onYes()
            "no" -> onNo()
            else -> askLit(onYes, onNo)
        }
    }

    private fun askLit(onYes: () -> Unit, onNo: () -> Unit) {
        activity?.let {
            AlertDialog.Builder(it)
                .setMessage(R.string.quest_way_lit_road_title)
                .setPositiveButton(R.string.quest_generic_hasFeature_yes) { _, _ -> onYes() }
                .setNegativeButton(R.string.quest_generic_hasFeature_no) { _, _ -> onNo() }
                .show()
        }
    }

    private fun askIsDualCarriageway(onYes: () -> Unit, onNo: () -> Unit) {
        activity?.let {
            AlertDialog.Builder(it)
                .setMessage(R.string.quest_maxspeed_answer_noSign_singleOrDualCarriageway_description)
                .setPositiveButton(R.string.quest_generic_hasFeature_yes) { _, _ -> onYes() }
                .setNegativeButton(R.string.quest_generic_hasFeature_no) { _, _ -> onNo() }
                .show()
        }
    }

    private fun applyNoSignAnswer(roadType: String, lit: Boolean? = null) {
        applyAnswer(ImplicitMaxSpeed(countryInfo.countryCode, roadType, lit))
    }

    companion object {
        private val POSSIBLY_SLOWZONE_ROADS = listOf("residential", "unclassified", "tertiary" /*#1133*/)
        private val MAYBE_LIVING_STREET = listOf("residential", "unclassified")
        private val ROADS_WITH_DEFINITE_SPEED_LIMIT = listOf("trunk", "motorway", "living_street")
        private val ROADS_WITH_DEFINITE_SPEED_LIMIT_GB = listOf("motorway", "living_street") /*#2750*/

        private var LAST_INPUT_SLOW_ZONE: Int? = null
    }
}

private enum class SpeedType {
    SIGN, ZONE, LIVING_STREET, ADVISORY, NO_SIGN, NSL
}
