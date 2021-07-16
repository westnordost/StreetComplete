package de.westnordost.streetcomplete.controls

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewUploadButtonBinding

/** A view that shows an upload-icon, with a counter at the top right and an (upload) progress view
 *  */
class UploadButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr)  {

    private val binding : ViewUploadButtonBinding
    var uploadableCount: Int = 0
    set(value) {
        field = value
        binding.textView.text = value.toString()
        binding.textView.isInvisible = value == 0
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.iconView.alpha = if (enabled) 1f else 0.5f
    }

    var showProgress: Boolean = false
    set(value) {
        field = value
        binding.progressView.isInvisible = !value
    }

    init {
        val view = inflate(context, R.layout.view_upload_button, this)
        binding = ViewUploadButtonBinding.bind(view)
        clipToPadding = false
    }
}
