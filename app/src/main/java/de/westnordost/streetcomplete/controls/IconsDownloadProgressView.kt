package de.westnordost.streetcomplete.controls

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.RelativeLayout
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewIconsDownloadProgressBinding
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.view.CircularMaskFrameLayout
import java.util.Queue
import java.util.LinkedList

/** view that shows a queue of IconProgressViews moving in from the right and moving out to the
 *  left when they are done */
class IconsDownloadProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CircularMaskFrameLayout(context, attrs, defStyleAttr)  {

    private var currentView: IconProgressView? = null
    private val iconQueue: Queue<Drawable> = LinkedList()
    private val binding : ViewIconsDownloadProgressBinding

    init {
        val view = inflate(context, R.layout.view_icons_download_progress, this)
        binding = ViewIconsDownloadProgressBinding.bind(view)
    }

    /** set the given icon and resets the queue */
    fun setIcon(icon: Drawable) = synchronized(this) {
        iconQueue.clear()
        currentView?.let { binding.iconProgressViewContainer.removeView(it) }
        val newView = createProgressView(icon)
        binding.iconProgressViewContainer.addView(newView)
        newView.showProgressAnimation()
        currentView = newView
    }

    /** sets the next icon to show the progress for. It will be animate in after the next call
     *  to pollIcon or immediately if there is no current icon  */
    fun enqueueIcon(icon: Drawable) = synchronized(this) {
        iconQueue.add(icon)
        if (currentView == null) animateToNextIcon()
    }

    /** executes a finished-animation on the current icon, animates it out and animates in the next
     *  icon, if there is one. */
    fun pollIcon() {
        currentView?.showFinishedAnimation(onFinished = {
            animateToNextIcon()
        })
    }

    private fun animateToNextIcon() = synchronized(this) {
        // move out old icon...
        currentView?.let {
            animateOutIcon(it)
            currentView = null
        }
        // add new icon, if there is one
        val icon = iconQueue.poll()
        if (icon != null) {
            val newView = createProgressView(icon)
            binding.iconProgressViewContainer.addView(newView)
            animateInIcon(newView)
            currentView = newView
        }
    }

    private fun createProgressView(icon: Drawable): IconProgressView {
        val v = IconProgressView(context)
        v.icon = icon
        val size = ICON_SIZE.toPx(context).toInt()
        val layoutParams = RelativeLayout.LayoutParams(size, size)
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        v.layoutParams = layoutParams
        return v
    }

    private fun animateInIcon(view: IconProgressView) {
        view.translationX = ICON_INITIAL_X_OFFSET.toPx(context)
        view.alpha = ICON_INITIAL_ALPHA
        view.scaleX = ICON_INITIAL_SCALE
        view.scaleY = ICON_INITIAL_SCALE
        view.showProgressAnimation()
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .scaleX(1f).scaleY(1f)
            .setDuration(ICON_IN_OUT_DURATION)
            .start()
    }

    private fun animateOutIcon(view: IconProgressView) {
        view.animate()
            .translationX(-(ICON_INITIAL_X_OFFSET).toPx(context))
            .alpha(ICON_INITIAL_ALPHA)
            .scaleX(ICON_INITIAL_SCALE)
            .scaleY(ICON_INITIAL_SCALE)
            .setDuration(ICON_IN_OUT_DURATION)
            .withEndAction { binding.iconProgressViewContainer.removeView(view) }
            .start()
    }

    companion object {
        const val ICON_INITIAL_SCALE = 0.4f
        const val ICON_INITIAL_ALPHA = 0.6f
        const val ICON_INITIAL_X_OFFSET = 64f
        const val ICON_IN_OUT_DURATION = 500L
        const val ICON_SIZE = 64f
    }
}
