package de.westnordost.streetcomplete.controls

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewAnswersCounterBinding

/** View that displays the user's quest answer counter */
class AnswersCounterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val binding : ViewAnswersCounterBinding
    var uploadedCount: Int = 0
        set(value) {
            field = value
            binding.textView.text = value.toString()
        }

    var showProgress: Boolean = false
        set(value) {
            field = value
            binding.progressView.isInvisible = !value
        }

    init {
        val view = inflate(context, R.layout.view_answers_counter, this)
        binding = ViewAnswersCounterBinding.bind(view)
    }

    fun setUploadedCount(uploadedCount: Int, animate: Boolean) {
        if (this.uploadedCount < uploadedCount && animate) {
            animateChange()
        }
        this.uploadedCount = uploadedCount

    }

    private fun animateChange() {
        binding.textView.animate()
            .scaleX(1.6f).scaleY(1.6f)
            .setInterpolator(DecelerateInterpolator(2f))
            .setDuration(100)
            .withEndAction {
                binding.textView.animate()
                    .scaleX(1f).scaleY(1f)
                    .setInterpolator(AccelerateDecelerateInterpolator()).duration = 100
            }
    }
}
