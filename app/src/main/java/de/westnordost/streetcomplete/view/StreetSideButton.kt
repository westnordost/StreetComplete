package de.westnordost.streetcomplete.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnPreDraw
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.getBitmapDrawable
import kotlinx.android.synthetic.main.side_select_puzzle.view.*
import kotlinx.android.synthetic.main.view_road_left_right_button.view.*
import kotlin.math.max
import kotlin.math.min

class StreetSideButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var leftImage: Image? = null
    private var rightImage: Image? = null
    private var isLeftImageSet: Boolean = false
    private var isRightImageSet: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_road_left_right_button, this, true)
        doOnPreDraw {
            buttonLeftSideImage.pivotX = buttonLeftSideContainer.width.toFloat()
            buttonRightSideImage.pivotX = 0f
        }

//        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
//            val width = right - left
//            val height = bottom - top
//            val params = buttonContainer.layoutParams
//            if(width != params.width || height != params.height) {
//                params.width = width
//                params.height = height
//                buttonContainer.layoutParams = params
//            }
//
//            val streetWidth = width / 2
//            val leftImage = leftImage
//            if (!isLeftImageSet && leftImage != null) {
//                setStreetDrawable(leftImage, streetWidth, buttonLeftSideImage, true)
//                isLeftImageSet = true
//            }
//            val rightImage = rightImage
//            if (!isRightImageSet && rightImage != null) {
//                setStreetDrawable(rightImage, streetWidth, buttonRightSideImage, false)
//                isRightImageSet = true
//            }
//        }
    }

    fun setLeftSideImage(image: Image?) {
        leftImage = image
        replace(image, buttonLeftSideImage, true)
    }

    fun setRightSideImage(image: Image?) {
        rightImage = image
        replace(image, buttonRightSideImage, false)
    }

    private fun replace(image: Image?, imgView: ImageView, flip180Degrees: Boolean) {
        val width = buttonContainer.width / 2
        if (width == 0) return
        setStreetDrawable(image, width, imgView, flip180Degrees)
    }

    private fun setStreetDrawable(image: Image?, width: Int, imageView: ImageView, flip180Degrees: Boolean) {
        if (image == null) {
            imageView.setImageDrawable(null)
        } else {
            val drawable = scaleToWidth(resources.getBitmapDrawable(image), width, flip180Degrees)
            imageView.setImageDrawable(drawable)
        }
    }

    private fun scaleToWidth(drawable: BitmapDrawable, width: Int, flip180Degrees: Boolean): BitmapDrawable {
        val m = Matrix()
        val scale = width.toFloat() / drawable.intrinsicWidth
        m.postScale(scale, scale)
        if (flip180Degrees) m.postRotate(180f)
        val bitmap = Bitmap.createBitmap(
            drawable.bitmap, 0, 0,
            drawable.intrinsicWidth, drawable.intrinsicHeight, m, true
        )
        return BitmapDrawable(resources, bitmap)
    }
}
