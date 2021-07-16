package de.westnordost.streetcomplete.user

import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewAchievementIconBinding

/** Shows an achievement icon with its frame and level indicator */
class AchievementIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr)  {

    private val binding: ViewAchievementIconBinding

    var icon: Drawable?
        set(value) { binding.iconView.setImageDrawable(value) }
        get() = binding.iconView.drawable

    var level: Int
        set(value) {
            binding.levelText.text = value.toString()
            binding.levelText.isInvisible = value < 2
        }
        get() = binding.levelText.text.toString().toIntOrNull() ?: 0

    init {
        val view = inflate(context, R.layout.view_achievement_icon, this)
        binding = ViewAchievementIconBinding.bind(view)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = AchievementFrameOutlineProvider
        }
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object AchievementFrameOutlineProvider : ViewOutlineProvider() {
    private val points = arrayOf(
        0.45,0.98,
        0.47,0.99,
        0.50,1.00,
        0.53,0.99,
        0.55,0.98,

        0.98,0.55,
        0.99,0.53,
        1.00,0.50,
        0.99,0.47,
        0.98,0.45,

        0.55,0.02,
        0.53,0.01,
        0.50,0.00,
        0.47,0.01,
        0.45,0.02,

        0.02,0.45,
        0.01,0.47,
        0.00,0.50,
        0.01,0.53,
        0.02,0.55,

        0.45,0.98
    )

    override fun getOutline(view: View, outline: Outline) {
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return

        val p = Path()
        p.moveTo((points[0] * w).toFloat(), (points[1] * h).toFloat())
        for (i in 2 until points.size step 2) {
            p.lineTo((points[i] * w).toFloat(), (points[i+1] * h).toFloat())
        }
        outline.setConvexPath(p)
    }
}
