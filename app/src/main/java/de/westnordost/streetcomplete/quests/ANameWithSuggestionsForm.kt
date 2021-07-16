package de.westnordost.streetcomplete.quests

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestNameSuggestionBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.util.TextChangedWatcher

abstract class ANameWithSuggestionsForm<T> : AbstractQuestFormAnswerFragment<T>() {

    override val contentLayoutResId = R.layout.quest_name_suggestion

    private val binding by viewBinding(QuestNameSuggestionBinding::bind)

    protected val name get() = binding.nameInput?.text?.toString().orEmpty().trim()

    abstract val suggestions: List<String>?

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        suggestions?.let {
            binding.nameInput.setAdapter(ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line, it
            ))
        }

        binding.nameInput.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })
    }

    override fun isFormComplete() = name.isNotEmpty()
}
