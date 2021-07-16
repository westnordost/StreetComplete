package de.westnordost.streetcomplete.quests.postbox_ref

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestRefBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.util.TextChangedWatcher

class AddPostboxRefForm : AbstractQuestFormAnswerFragment<PostboxRefAnswer>() {

    override val contentLayoutResId = R.layout.quest_ref

    private val binding by viewBinding(QuestRefBinding::bind)

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_ref_answer_noRef) { confirmNoRef() }
    )

    private val ref get() = binding.refInput?.text?.toString().orEmpty().trim()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.refInput.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })
    }

    override fun onClickOk() {
        applyAnswer(Ref(ref))
    }

    private fun confirmNoRef() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.quest_generic_confirmation_title)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(NoRefVisible) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
    }

    override fun isFormComplete() = ref.isNotEmpty()
}
