package de.westnordost.streetcomplete.quests.place_name

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.view.View
import androidx.core.os.ConfigurationCompat
import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.StringUtils
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestPlacenameBinding
import de.westnordost.streetcomplete.ktx.geometryType
import de.westnordost.streetcomplete.ktx.toTypedArray
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.shop_type.SearchAdapter
import de.westnordost.streetcomplete.util.TextChangedWatcher


class AddPlaceNameForm : AbstractQuestFormAnswerFragment<PlaceNameAnswer>() {

    override val contentLayoutResId = R.layout.quest_placename

    private val binding by viewBinding(QuestPlacenameBinding::bind)

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_generic_answer_noSign) { confirmNoName() }
    )

    private val placeName get() = binding.nameInput?.text?.toString().orEmpty().trim()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.nameInput.setAdapter(SearchAdapter(requireContext(), { term -> getFeatures(term) }, { it.name }))
        binding.nameInput.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })
    }

    override fun onClickOk() {
        val feature = getSelectedFeature()
        if (feature != null) {
            applyAnswer(BrandFeature(feature.addTags))
        } else {
            applyAnswer(PlaceName(placeName))
        }
    }

    private fun confirmNoName() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.quest_generic_confirmation_title)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(NoPlaceNameSign) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
    }

    override fun isFormComplete() = placeName.isNotEmpty()

    private fun getSelectedFeature(): Feature? {
        val input = placeName
        return getFeatures(input).firstOrNull()?.takeIf { it.canonicalName == StringUtils.canonicalize(input) }
    }

    private fun getFeatures(startsWith: String): List<Feature> {
        val elementFeature = getOsmElementFeature() ?: return emptyList()
        val localeList = ConfigurationCompat.getLocales(requireContext().resources.configuration)
        return featureDictionary
            .byTerm(startsWith.trim())
            .forGeometry(osmElement!!.geometryType)
            .inCountry(countryInfo.countryCode)
            .forLocale(*localeList.toTypedArray())
            .isSuggestion(true)
            .find()
            // filter to those brands that fit on how the thing is tagged now
            .filter { feature ->
                elementFeature.tags.all { feature.tags[it.key] == it.value }
            }
    }

    private fun getOsmElementFeature(): Feature? {
        return featureDictionary
            .byTags(osmElement!!.tags)
            .forGeometry(osmElement!!.geometryType)
            .isSuggestion(false)
            .find()
            .firstOrNull()
    }
}
