package de.westnordost.streetcomplete.tutorial

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FragmentTutorialBinding
import de.westnordost.streetcomplete.ktx.toDp
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.location.LocationState
import de.westnordost.streetcomplete.view.insets_animation.respectSystemInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shows a short tutorial for first-time users */
class TutorialFragment : Fragment(R.layout.fragment_tutorial) {

    private var currentPage: Int = 0

    private val binding by viewBinding(FragmentTutorialBinding::bind)

    interface Listener {
        fun onTutorialFinished()
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.respectSystemInsets(View::setPadding)

        updateIndicatorDots()

        binding.nextButton.setOnClickListener {
            when(currentPage) {
                0 -> {
                    currentPage = 1
                    step1Transition()
                }
                1 -> {
                    currentPage = 2
                    step2Transition()
                }
                MAX_PAGE_INDEX -> {
                    binding.nextButton.isEnabled = false
                    listener?.onTutorialFinished()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun step1Transition() = lifecycleScope.launch {
        val ctx = requireContext()

        updateIndicatorDots()

        // magnifier flies towards viewer and fades out
        binding.magnifierImageView.animate()
            .setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .scaleX(6f).scaleY(6f)
            .alpha(0f)
            .start()


        // map zooms in and tilts
        val mapTranslate = (-50f).toPx(ctx)
        val mapRotate = 50f
        val mapScale = 1.5f

        binding.mapImageView.animate()
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .rotationX(mapRotate)
            .scaleY(mapScale).scaleX(mapScale)
            .translationY(mapTranslate)
            .start()

        binding.mapLightingImageView.animate()
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .rotationX(mapRotate)
            .alpha(0f)
            .scaleY(mapScale).scaleX(mapScale)
            .translationY(mapTranslate)
            .start()

        // 1st text fade out
        binding.tutorialStepIntro.animate()
            .setDuration(300)
            .alpha(0f)
            .translationY(100f.toDp(ctx))
            .withEndAction { binding.tutorialStepIntro.visibility = View.GONE }
            .start()

        delay(200)

        // flashing GPS button appears
        binding.tutorialGpsButton.state = LocationState.SEARCHING
        binding.tutorialGpsButton.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        delay(400)

        // 2nd text fade in
        binding.tutorialStepSolvingQuests.translationY = (-100f).toDp(ctx)
        binding.tutorialStepSolvingQuests.animate()
            .withStartAction { binding.tutorialStepSolvingQuests.visibility = View.VISIBLE }
            .setDuration(300)
            .alpha(1f)
            .translationY(0f)
            .start()


        delay(1400)

        // ...and after a few seconds, stops flashing
        binding.tutorialGpsButton?.state = LocationState.UPDATING

        delay(800)

        // quest pins fall into place
        listOf(binding.questPin1, binding.questPin2, binding.questPin3).forEach { pin ->

            delay(400)

            pin.translationY = (-200f).toDp(ctx)
            pin.animate()
                .setInterpolator(BounceInterpolator())
                .setDuration(400)
                .translationY(0f)
                .alpha(1f)
                .start()
        }
    }

    private fun step2Transition() = lifecycleScope.launch {
        val ctx = requireContext()

        updateIndicatorDots()
        binding.nextButton.setText(R.string.letsgo)

        // 2nd text fade out
        binding.tutorialStepSolvingQuests.animate()
            .setDuration(300)
            .alpha(0f)
            .translationY(100f.toDp(ctx))
            .withEndAction { binding.tutorialStepSolvingQuests.visibility = View.GONE }
            .start()

        // 3rd text fade in
        binding.tutorialStepStaySafe.translationY = (-100f).toDp(ctx)

        delay(400)

        binding.tutorialStepStaySafe.animate()
            .withStartAction { binding.tutorialStepStaySafe.visibility = View.VISIBLE }
            .setDuration(300)
            .alpha(1f)
            .translationY(0f)
            .start()

        // quest pins fade out
        listOf(binding.questPin1, binding.questPin2, binding.questPin3).forEach { pin ->
            pin.animate()
                .setInterpolator(AccelerateInterpolator())
                .setDuration(300)
                .alpha(0f)
                .start()
        }

        delay(1400)
        // checkmark fades in and animates

        binding.checkmarkView.animate()
            .setDuration(600)
            .alpha(1f)
            .start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (binding.checkmarkView.drawable as? AnimatedVectorDrawable)?.start()
        }
    }

    private fun updateIndicatorDots() {
        listOf(binding.dot1,binding.dot2,binding.dot3).forEachIndexed { index, dot ->
            dot.setImageResource(
                if(currentPage == index) R.drawable.indicator_dot_selected
                else R.drawable.indicator_dot_default
            )
        }
    }

    companion object {
        private const val MAX_PAGE_INDEX = 2
    }
}
