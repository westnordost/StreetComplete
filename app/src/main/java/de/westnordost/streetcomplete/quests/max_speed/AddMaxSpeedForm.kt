package de.westnordost.streetcomplete.quests.max_speed

import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher
import kotlinx.android.synthetic.main.quest_maxspeed.*


class AddMaxSpeedForm : AbstractQuestFormAnswerFragment() {

    private var speedInput: EditText? = null
    private var speedUnitSelect: Spinner? = null
    private var speedType: SpeedType? = null

    private val speed: String get() = speedInput!!.text.toString()

    private enum class SpeedType {
        SIGN, ZONE, ADVISORY, NO_SIGN
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        setContentView(R.layout.quest_maxspeed)

        val couldBeSlowZone = countryInfo.isSlowZoneKnown && POSSIBLY_SLOWZONE_ROADS.contains(osmElement.tags["highway"]!!)
        zone.visibility = if (couldBeSlowZone) View.VISIBLE else View.GONE

        speedTypeSelect.setOnCheckedChangeListener { _, checkedId -> setSpeedType(getSpeedType(checkedId)) }

        addOtherAnswers()

        return view
    }

    private fun addOtherAnswers() {
        val highwayTag = osmElement.tags["highway"]!!
        if (countryInfo.isLivingStreetKnown && MAYBE_LIVING_STREET.contains(highwayTag)) {
            addOtherAnswer(R.string.quest_maxspeed_answer_living_street) {
                confirmLivingStreet {
	                val answer = Bundle()
	                answer.putBoolean(LIVING_STREET, true)
	                applyAnswer(answer)
                }
            }
        }

        if (countryInfo.isAdvisorySpeedLimitKnown) {
            addOtherAnswer(R.string.quest_maxspeed_answer_advisory_speed_limit) {
                speedTypeSelect.clearCheck()
                for (i in 0 until speedTypeSelect.childCount) {
                    speedTypeSelect.getChildAt(i).isEnabled = false
                }
                setSpeedType(SpeedType.ADVISORY)
            }
        }
    }

    override fun onClickOk() {
        if (speedType == SpeedType.NO_SIGN) {
            val couldBeSlowZone = countryInfo.isSlowZoneKnown && POSSIBLY_SLOWZONE_ROADS.contains(osmElement.tags["highway"])

            if (couldBeSlowZone)
	            confirmNoSignSlowZone { determineImplicitMaxspeedType() }
            else
	            confirmNoSign { determineImplicitMaxspeedType() }
        } else {
            if (userSelectedUnusualSpeed())
	            confirmUnusualInput { applySpeedLimitFormAnswer() }
            else
	            applySpeedLimitFormAnswer()
        }
    }

    override fun isFormComplete() = speedType != null && (speedInput == null || speed.isNotEmpty())

    /* ---------------------------------------- With sign --------------------------------------- */

    private fun setSpeedType(speedType: SpeedType?) {
        this.speedType = speedType

        rightSideContainer.removeAllViews()
	    speedType?.layoutResId?.let { layoutInflater.inflate(it, rightSideContainer, true) }

        speedInput = rightSideContainer.findViewById(R.id.maxSpeedInput)
	    speedInput?.requestFocus()
	    speedInput?.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })

        speedUnitSelect = rightSideContainer.findViewById(R.id.speedUnitSelect)
	    val measurementUnits = countryInfo.measurementSystem
	    speedUnitSelect?.visibility = if (measurementUnits.size == 1) View.GONE else View.VISIBLE
	    speedUnitSelect?.adapter = ArrayAdapter(context!!, R.layout.spinner_item_centered, getSpinnerItems(measurementUnits))
	    speedUnitSelect?.setSelection(0)

        checkIsFormComplete()
    }

    private fun getSpinnerItems(units: List<String>) = units.mapNotNull {
	    when(it) {
		    "metric" -> "km/h"
		    "imperial" -> "mph"
		    else -> null
        }
    }

    private fun getSpeedType(@IdRes checkedId: Int) = when (checkedId) {
	    R.id.sign -> SpeedType.SIGN
	    R.id.zone -> SpeedType.ZONE
	    R.id.no_sign -> SpeedType.NO_SIGN
	    else -> null
    }

    private val SpeedType.layoutResId get() = when (this) {
	    AddMaxSpeedForm.SpeedType.SIGN -> R.layout.quest_maxspeed_sign
	    AddMaxSpeedForm.SpeedType.ZONE -> R.layout.quest_maxspeed_zone_sign
	    AddMaxSpeedForm.SpeedType.ADVISORY -> R.layout.quest_maxspeed_advisory
	    else -> 0
    }

    private fun userSelectedUnusualSpeed(): Boolean {
        val speed = speed.toInt()
        val speedUnit = speedUnitSelect?.selectedItem as String?
        val speedInKmh = if (speedUnit == "mph") mphToKmh(speed.toDouble()) else speed.toDouble()
        return speedInKmh > 140 || speed > 20 && speed % 5 != 0
    }

    private fun confirmUnusualInput(onConfirmed: () -> Unit) {
	    activity?.let {
		    AlertDialog.Builder(it)
			    .setTitle(R.string.quest_generic_confirmation_title)
			    .setMessage(R.string.quest_maxspeed_unusualInput_confirmation_description)
			    .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> onConfirmed() }
			    .setNegativeButton(R.string.quest_generic_confirmation_no, null)
			    .show()
	    }
    }

    private fun applySpeedLimitFormAnswer() {
        val speed = speed.toInt()
        var speedStr = speed.toString()

        // km/h is the OSM default, is not mentioned
        val speedUnit = speedUnitSelect!!.selectedItem as String
        if (speedUnit != "km/h") {
            speedStr += " $speedUnit"
        }

        val answer = Bundle()
        if (speedType == SpeedType.ADVISORY) {
            answer.putString(ADVISORY_SPEED, speedStr)
        } else {
            answer.putString(MAX_SPEED, speedStr)
            if (speedType == SpeedType.ZONE) {
                answer.putString(MAX_SPEED_IMPLICIT_COUNTRY, countryInfo.countryCode)
                answer.putString(MAX_SPEED_IMPLICIT_ROADTYPE, "zone$speed")
            }
        }
        applyAnswer(answer)
    }

    /* ----------------------------------------- No sign ---------------------------------------- */

    private fun confirmLivingStreet(onConfirmed: () -> Unit) {
	    activity?.let {
		    val view = layoutInflater.inflate(R.layout.quest_maxspeed_living_street_confirmation, null, false)
		    // this is necessary because the inflated image view uses the activity context rather than
		    // the fragment / layout inflater context' resources to access it's drawable
		    val img = view.findViewById<ImageView>(R.id.livingStreetImage)
		    img.setImageDrawable(resources.getDrawable(R.drawable.ic_living_street))
		    AlertDialog.Builder(it)
			    .setView(view)
			    .setTitle(R.string.quest_maxspeed_answer_living_street_confirmation_title)
			    .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> onConfirmed() }
			    .setNegativeButton(R.string.quest_generic_confirmation_no, null)
			    .show()
	    }
    }

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
        val highwayTag = osmElement.tags["highway"]!!
        if (ROADS_WITH_DEFINITE_SPEED_LIMIT.contains(highwayTag)) {
            applyNoSignAnswer(highwayTag)
        } else {
            if (countryInfo.countryCode == "GB") {
                determineLit(
                    onYes = { applyNoSignAnswer("nsl_restricted") },
                    onNo = {
                        askIsDualCarriageway(
                            onYes = { applyNoSignAnswer("nsl_dual") },
                            onNo = { applyNoSignAnswer("nsl_single") })
                    }
                )
            } else {
                askUrbanOrRural(
                    onUrban = { applyNoSignAnswer("urban") },
                    onRural = { applyNoSignAnswer("rural") })
            }
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
        val lit = osmElement.tags["lit"]
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

    private fun applyNoSignAnswer(roadType: String) {
        val answer = Bundle()
        val countryCode = countryInfo.countryCode
        answer.putString(MAX_SPEED_IMPLICIT_COUNTRY, countryCode)
        answer.putString(MAX_SPEED_IMPLICIT_ROADTYPE, roadType)
        applyAnswer(answer)
    }

    companion object {
        const val MAX_SPEED_IMPLICIT_COUNTRY = "maxspeed_country"
	    const val MAX_SPEED_IMPLICIT_ROADTYPE = "maxspeed_roadtype"
	    const val ADVISORY_SPEED = "advisory_speed"
	    const val MAX_SPEED = "maxspeed"
	    const val LIVING_STREET = "living_street"

        private val POSSIBLY_SLOWZONE_ROADS = listOf("residential", "unclassified", "tertiary" /*#1133*/)
        private val MAYBE_LIVING_STREET = listOf("residential", "unclassified")
        private val ROADS_WITH_DEFINITE_SPEED_LIMIT = listOf("trunk", "motorway", "living_street")

        private fun mphToKmh(mph: Double) = 1.60934 * mph
    }
}
