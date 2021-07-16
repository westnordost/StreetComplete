package de.westnordost.streetcomplete.controls

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewNotificationButtonBinding

/** View that shows a notification-button with a little counter at the top right */
class NotificationButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr)  {

    private val binding : ViewNotificationButtonBinding

    var notificationsCount: Int = 0
    set(value) {
        field = value
        binding.textView.text = value.toString()
        binding.textView.isInvisible = value == 0
    }

    init {
        val view = inflate(context, R.layout.view_notification_button, this)
        binding = ViewNotificationButtonBinding.bind(view)
        clipToPadding = false
    }
}
