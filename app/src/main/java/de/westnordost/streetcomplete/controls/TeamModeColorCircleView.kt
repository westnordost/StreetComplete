package de.westnordost.streetcomplete.controls

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewTeamModeColorCircleBinding

class TeamModeColorCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding : ViewTeamModeColorCircleBinding

    fun setIndexInTeam(index: Int) {
        val color = context.resources.getColor(colors[index])
        val brightness = getColorBrightness(color)

        binding.teamModeColorCircleBackground.setColorFilter(color)
        binding.teamModeColorCircleText.text = (index + 'A'.toInt()).toChar().toString()
        binding.teamModeColorCircleText.setTextColor(if (brightness > 0.7) Color.BLACK else Color.WHITE)
    }

    private fun getColorBrightness(@ColorInt color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2]
    }

    init {
        val view = inflate(context, R.layout.view_team_mode_color_circle, this)
        binding = ViewTeamModeColorCircleBinding.bind(view)
        setIndexInTeam(0)
    }

    companion object {
        private val colors = listOf(
            R.color.team_0,
            R.color.team_1,
            R.color.team_2,
            R.color.team_3,
            R.color.team_4,
            R.color.team_5,
            R.color.team_6,
            R.color.team_7,
            R.color.team_8,
            R.color.team_9,
            R.color.team_10,
            R.color.team_11
        )

        val MAX_TEAM_SIZE get() = colors.size
    }
}

