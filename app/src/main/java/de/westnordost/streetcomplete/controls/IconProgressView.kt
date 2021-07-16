package de.westnordost.streetcomplete.controls

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.widget.RelativeLayout
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewIconProgressBinding

/** Shows an icon, surrounded by a circular progress bar and a finished-checkmark */
class IconProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr)  {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wobbleAnimator: Animator? = null
    private val binding : ViewIconProgressBinding

    var icon: Drawable?
        set(value) { binding.iconView.setImageDrawable(value) }
        get() = binding.iconView.drawable


    private val animatorDurationScale: Float get() =
       Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun showProgressAnimation() {
        wobbleAnimator = AnimatorInflater.loadAnimator(context, R.animator.progress_wobble).apply {
            setTarget(binding.iconView)
            start()
        }
        binding.progressView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    fun showFinishedAnimation(onFinished: () -> Unit) {
        wobbleAnimator?.cancel()
        binding.progressView.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        binding.checkmarkView.animate()
            .setDuration(300)
            .alpha(1f)
            .start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (binding.checkmarkView.drawable as? AnimatedVectorDrawable)?.start()
        }

        val hardcodedCheckmarkAnimationDuration = (animatorDurationScale * 650).toLong()
        mainHandler.postDelayed(onFinished, hardcodedCheckmarkAnimationDuration)
    }

    init {
        val view = inflate(context, R.layout.view_icon_progress, this)
        binding = ViewIconProgressBinding.bind(view)
    }
}
