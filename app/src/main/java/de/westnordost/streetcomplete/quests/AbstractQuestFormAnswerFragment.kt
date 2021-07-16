package de.westnordost.streetcomplete.quests

import android.os.Bundle
import android.view.View

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FragmentQuestAnswerBinding
import de.westnordost.streetcomplete.ktx.popIn
import de.westnordost.streetcomplete.ktx.popOut
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.ktx.viewBinding

/** Abstract base class for dialogs in which the user answers a quest with a form he has to fill
 * out  */
abstract class AbstractQuestFormAnswerFragment<T> : AbstractQuestAnswerFragment<T>() {

    private val binding by viewBinding(FragmentQuestAnswerBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.okButton.setOnClickListener {
            if (!isFormComplete()) {
                activity?.toast(R.string.no_changes)
            } else {
                onClickOk()
            }
        }
    }

    protected fun checkIsFormComplete() {
        if (isFormComplete()) {
            binding.okButton.popIn()
        } else {
            binding.okButton.popOut()
        }
    }

    protected abstract fun onClickOk()

    abstract fun isFormComplete(): Boolean

    override fun isRejectingClose() = isFormComplete()
}
