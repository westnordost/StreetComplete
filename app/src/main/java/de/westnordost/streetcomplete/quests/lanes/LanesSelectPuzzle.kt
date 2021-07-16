package de.westnordost.streetcomplete.quests.lanes

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.core.graphics.withRotation
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.LanesSelectPuzzleBinding
import de.westnordost.streetcomplete.ktx.getBitmapDrawable
import de.westnordost.streetcomplete.ktx.showTapHint
import de.westnordost.streetcomplete.quests.lanes.LineStyle.*
import kotlin.math.max
import kotlin.random.Random


class LanesSelectPuzzle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), LifecycleObserver {

    private val animator = TimeAnimator()

    private val binding : LanesSelectPuzzleBinding
    private val questionMark: Drawable = context.resources.getDrawable(R.drawable.ic_lanes_unknown)

    var onClickSideListener: ((isRight: Boolean) -> Unit)? = null
        set(value) {
            field = value
            if (value == null) {
                binding.leftSideClickArea.setOnClickListener(null)
                binding.rightSideClickArea.setOnClickListener(null)
                binding.leftSideClickArea.isClickable = false
                binding.rightSideClickArea.isClickable = false
            } else {
                isClickable = false
                binding.leftSideClickArea.setOnClickListener { value.invoke(false) }
                binding.rightSideClickArea.setOnClickListener { value.invoke(true) }
            }
        }

    var onClickListener: (() -> Unit)? = null
        set(value) {
            field = value
            if (value == null) {
                setOnClickListener(null)
                isClickable = false
            } else {
                binding.leftSideClickArea.isClickable = false
                binding.rightSideClickArea.isClickable = false
                setOnClickListener { value.invoke() }
            }
        }

    var isShowingLaneMarkings: Boolean = true
    set(value) {
        if (field != value) {
            field = value
            invalidate()
        }
    }
    var laneCountLeft: Int = 0
    private set

    var laneCountRight: Int = 0
    private set

    var hasCenterLeftTurnLane: Boolean = false

    var isShowingBothSides: Boolean = true
    set(value) {
        if (field != value) {
            field = value
            updateLanes()
        }
    }

    var isForwardTraffic: Boolean = true

    var centerLineColor: Int
    set(value) {
        centerLinePaint.color = value
        invalidate()
    }
    get() = centerLinePaint.color

    var shoulderLineColor: Int
        set(value) {
            shoulderLinePaint.color = value
            invalidate()
        }
        get() = shoulderLinePaint.color

    var shoulderLineStyle: LineStyle = CONTINUOUS
    set(value) {
        field = value
        invalidate()
    }

    private val roadPaint = Paint().also {
        it.color = Color.parseColor("#808080")
        it.style = Paint.Style.FILL
    }

    private val centerLinePaint = Paint().also {
        it.color = Color.WHITE
        it.style = Paint.Style.STROKE
        it.isAntiAlias = true
    }

    private val laneSeparatorLinePaint = Paint().also {
        it.color = Color.WHITE
        it.style = Paint.Style.STROKE
        it.isAntiAlias = true
    }

    private val shoulderLinePaint = Paint().also {
        it.color = Color.WHITE
        it.style = Paint.Style.STROKE
        it.isAntiAlias = true
    }

    private val carsOnLanesLeft = mutableListOf<CarState>()
    private val carsOnLanesRight = mutableListOf<CarState>()

    private val carBitmaps: List<Bitmap>

    private val isShowingOneLaneUnmarked: Boolean get() = !isShowingLaneMarkings && laneCountLeft == 0 && laneCountRight == 1

    private val isShowingOnlyRightSide: Boolean get() = !isShowingBothSides || isShowingOneLaneUnmarked

    private val noSidesAreDefined: Boolean get() = laneCountLeft == 0 && laneCountRight == 0
    private val bothSidesAreDefined: Boolean get() = laneCountLeft > 0 && laneCountRight > 0

    private val laneCountCenter: Int get() = if (hasCenterLeftTurnLane) 1 else 0

    private val leftLanesStart: Float get() = SHOULDER_WIDTH

    private val leftLanesEnd: Float get() = leftLanesStart + when {
        bothSidesAreDefined    -> laneCountLeft
        isShowingOnlyRightSide -> 0
        noSidesAreDefined      -> 1
        else                   -> max(laneCountLeft, laneCountRight)
    }

    private val lanesSpace: Int get() = laneCountCenter + when {
        bothSidesAreDefined    -> laneCountLeft + laneCountRight
        isShowingOnlyRightSide -> max(1, laneCountRight)
        noSidesAreDefined      -> 2
        else                   -> 2 * max(laneCountLeft, laneCountRight)
    }

    private val rightLanesStart: Float get() = leftLanesEnd + laneCountCenter

    private val laneWidth: Float get() = width / (lanesSpace + SHOULDER_WIDTH * 2)

    init {
        setWillNotDraw(false)

        carBitmaps = CAR_RES_IDS.map { resources.getBitmapDrawable(it).bitmap }

        val view = LayoutInflater.from(context).inflate(R.layout.lanes_select_puzzle, this, true)
        binding = LanesSelectPuzzleBinding.bind(view)

        animator.setTimeListener { _, _, deltaTime ->
            moveCars(deltaTime)
            invalidate()
        }

        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                updateLanes()
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME) fun resume() {
        animator.start()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE) fun pause() {
        animator.end()
    }

    fun setLaneCounts(left: Int, right: Int, centerLeftTurn: Boolean) {
        laneCountLeft = left
        laneCountRight = right
        hasCenterLeftTurnLane = centerLeftTurn
        updateCarsOnLanes(left, carsOnLanesLeft, !isForwardTraffic)
        updateCarsOnLanes(right, carsOnLanesRight, isForwardTraffic)

        if ((laneCountLeft <= 0 || laneCountRight <= 0) && !HAS_SHOWN_TAP_HINT) {
            if (laneCountLeft <= 0) binding.leftSideClickArea.showTapHint(300)
            if (laneCountRight <= 0) binding.rightSideClickArea.showTapHint(1200)
            HAS_SHOWN_TAP_HINT = true
        }

        updateLanes()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        canvas.drawColor(Color.parseColor("#33666666"))

        val laneWidth = laneWidth
        val lanesSpace = lanesSpace
        val leftLanesStart = leftLanesStart
        val leftLanesEnd = leftLanesEnd
        val rightLanesStart = rightLanesStart
        val zoom = if (isShowingBothSides && isShowingOneLaneUnmarked) 1.4f / laneWidth else 1f / laneWidth

        val shoulderWidth = SHOULDER_WIDTH * laneWidth

        val lineWidth = LANE_MARKING_WIDTH / zoom

        val dashEffect = DashPathEffect(floatArrayOf(lineWidth * 6, lineWidth * 10), 0f)

        shoulderLinePaint.strokeWidth = lineWidth
        shoulderLinePaint.pathEffect = when(shoulderLineStyle) {
            CONTINUOUS -> null
            DASHES -> dashEffect
            SHORT_DASHES -> DashPathEffect(floatArrayOf(lineWidth * 4, lineWidth * 4), 0f)
        }

        centerLinePaint.strokeWidth = lineWidth

        laneSeparatorLinePaint.strokeWidth = lineWidth
        laneSeparatorLinePaint.pathEffect = dashEffect

        // draw question marks if nothing is selected
        if (laneCountLeft <= 0 && !isShowingOnlyRightSide) {
            val size = (leftLanesEnd * laneWidth).toInt()
            canvas.drawVerticallyRepeating(questionMark, 0, size, isForwardTraffic)
        }
        if (laneCountRight <= 0) {
            val left = (rightLanesStart * laneWidth).toInt()
            canvas.drawVerticallyRepeating(questionMark, left, width - left, !isForwardTraffic)
        }

        // draw background
        val streetStartX = if (laneCountLeft > 0 || isShowingOnlyRightSide) 0f else laneWidth * leftLanesEnd
        val streetEndX = if (laneCountRight > 0) width.toFloat() else laneWidth * rightLanesStart
        canvas.drawRect(streetStartX, 0f, streetEndX, height.toFloat(), roadPaint)

        // draw markings:

        // 1. markings for the shoulders
        if (laneCountLeft > 0 || isShowingOnlyRightSide) {
            canvas.drawVerticalLine(leftLanesStart * laneWidth, shoulderLinePaint)
        }
        if (laneCountRight > 0) {
            canvas.drawVerticalLine(shoulderWidth + lanesSpace * laneWidth, shoulderLinePaint)
        }

        // 2. lane markings
        if (isShowingLaneMarkings) {
            for (x in 1 until laneCountLeft) {
                canvas.drawVerticalLine(shoulderWidth + x * laneWidth, laneSeparatorLinePaint)
            }
            for (x in 1 until laneCountRight) {
                canvas.drawVerticalLine((rightLanesStart + x) * laneWidth, laneSeparatorLinePaint)
            }
        }

        // 3. center line
        if (bothSidesAreDefined && !hasCenterLeftTurnLane && isShowingLaneMarkings) {
            val onlyTwoLanes = laneCountLeft + laneCountRight <= 2
            if (onlyTwoLanes) centerLinePaint.pathEffect = dashEffect
            canvas.drawVerticalLine(leftLanesEnd * laneWidth, centerLinePaint)
            if (onlyTwoLanes) centerLinePaint.pathEffect = null
        }

        // 4. center turn lane markings
        if (hasCenterLeftTurnLane) {
            canvas.drawVerticalLine(leftLanesEnd * laneWidth, centerLinePaint)
            canvas.drawVerticalLine(rightLanesStart * laneWidth, centerLinePaint)
            centerLinePaint.pathEffect = dashEffect
            canvas.drawVerticalLine((leftLanesEnd + 0.125f) * laneWidth, centerLinePaint)
            canvas.drawVerticalLine((rightLanesStart - 0.125f) * laneWidth, centerLinePaint)
            centerLinePaint.pathEffect = null
        }

        // 5. draw cars
        val carWidth = (1f - 2f * CAR_LANE_PADDING) / zoom
        carsOnLanesRight.asReversed().forEachIndexed { index, carState ->
            canvas.drawCar(carState, rightLanesStart + index, laneWidth, carWidth)
        }
        carsOnLanesLeft.forEachIndexed { index, carState ->
            canvas.drawCar(carState, leftLanesStart + index, laneWidth, carWidth)
        }
    }

    /** update the lanes on the street */
    private fun updateLanes() {
        val w = width
        if (w == 0) return
        val laneWidth = laneWidth
        if (laneWidth == 0f) return

        binding.leftSideClickArea.isGone = isShowingOnlyRightSide
        binding.leftSideClickArea.updateLayoutParams {
            width = (leftLanesEnd * laneWidth).toInt()
        }
        binding.rightSideClickArea.updateLayoutParams {
            width = w - (rightLanesStart * laneWidth).toInt()
        }

        invalidate()
    }

    /** initialize/delete car states given the new lane count */
    private fun updateCarsOnLanes(laneCount: Int, carsOnLanes: MutableList<CarState>, forwardDirection: Boolean) {
        while (carsOnLanes.size < laneCount) {
            carsOnLanes.add(CarState(forwardDirection, carBitmaps))
        }
        while (carsOnLanes.size > laneCount) {
            carsOnLanes.removeLast()
        }
    }

    /** Simulate the cars driving up the road */
    private fun moveCars(deltaTime: Long) {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        /* the CAR_SPEED is "lane graphic squares per second". If the lane graphic is not a square
           we need to go faster/slower */
        val ratio = 1f * w / h
        val zoom = max(3, lanesSpace)
        val delta = ratio * deltaTime/1000f / zoom

        for(car in carsOnLanesLeft) {
            car.position += delta * car.speed
            if (car.isOutOfBounds) {
                car.reset(!isForwardTraffic, carBitmaps)
            }
        }
        for(car in carsOnLanesRight) {
            car.position += delta * car.speed
            if (car.isOutOfBounds) {
                if (isShowingBothSides && isShowingOneLaneUnmarked) {
                    car.reset(car.speed < 0, carBitmaps)
                } else {
                    car.reset(isForwardTraffic, carBitmaps)
                }
            }
        }
    }

    companion object {
        private var HAS_SHOWN_TAP_HINT = false
    }
}

private const val SHOULDER_WIDTH = 0.125f // as fraction of lane width
private const val LANE_MARKING_WIDTH = 0.0625f // as fraction of lane width
private const val CAR_LANE_PADDING = 0.10f // how much space there is between car and lane markings, as fraction of lane width
private const val CAR_SPEED = 4f // in "lane graphic squares per second"
private const val CAR_SPEED_VARIATION = 0.2f // as fraction: 1 = 100% variation
private const val CAR_SPARSITY = 1f

private val CAR_RES_IDS = listOf(
    R.drawable.ic_car1,
    R.drawable.ic_car1a,
    R.drawable.ic_car1b,
    R.drawable.ic_car2,
    R.drawable.ic_car2a,
    R.drawable.ic_car3,
    R.drawable.ic_car3a,
    R.drawable.ic_car4,
    R.drawable.ic_car5,
)

private class CarState(forwardDirection: Boolean, bitmaps: List<Bitmap>) {
    lateinit var bitmap: Bitmap
    var position: Float = 0f
    var speed: Float = 0f
    val matrix: Matrix = Matrix()

    val isOutOfBounds: Boolean get() = speed > 0 && position > 1 || speed < 0 && position < 0

    init {
        reset(forwardDirection, bitmaps)
        val r = Random.nextFloat() / 2f
        position = if (forwardDirection) r else 1 - r
    }

    fun reset(forwardDirection: Boolean, bitmaps: List<Bitmap>) {
        bitmap = bitmaps[Random.nextInt(bitmaps.size)]
        val pos = Random.nextFloat() * CAR_SPARSITY
        position = if (forwardDirection) -pos else 1f + pos
        speed = CAR_SPEED * (1f + CAR_SPEED_VARIATION * Random.nextFloat()) * (if (forwardDirection) 1 else -1)
    }
}

private fun Canvas.drawVerticalLine(x: Float, paint: Paint) {
    drawLine(x, 0f, x, height.toFloat(), paint)
}

private fun Canvas.drawCar(carState: CarState, laneIndex: Float, laneWidth: Float, carWidth: Float) {
    val bitmap = carState.bitmap
    val w = bitmap.width
    val h = bitmap.height

    val carHeight = carWidth * h / w

    val x = laneWidth * laneIndex + (laneWidth - carWidth) / 2f
    val y = (carHeight + height) * (1 - carState.position) - carHeight

    carState.matrix.reset()
    if (carState.speed < 0) carState.matrix.postRotate(180f, w / 2f, h / 2f)
    carState.matrix.postScale(1f * carWidth / w, 1f * carHeight / h)
    carState.matrix.postTranslate(x, y)
    drawBitmap(bitmap, carState.matrix, null)
}

private fun Canvas.drawVerticallyRepeating(drawable: Drawable, left: Int, size: Int, reverseY: Boolean) {
    var i = 0
    while (size * i < height) {
        val top = size * i
        val bottom = size * (i + 1)
        val right = left + size
        drawable.setBounds(left, top, right, bottom)
        withRotation(if (reverseY) 180f else 0f, left + size / 2f, top + size / 2f) {
            drawable.draw(this)
        }
        i++
    }
}

enum class LineStyle { CONTINUOUS, DASHES, SHORT_DASHES }
