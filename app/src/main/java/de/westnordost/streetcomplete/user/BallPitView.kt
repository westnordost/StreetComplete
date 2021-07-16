package de.westnordost.streetcomplete.user

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ViewBallPitBinding
import de.westnordost.streetcomplete.ktx.awaitPreDraw
import de.westnordost.streetcomplete.ktx.sumByFloat
import kotlinx.coroutines.*
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import kotlin.math.*

/** Shows the contained views in a physics simulated ball pit of some kind. */
class BallPitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    LifecycleObserver {

    private val binding: ViewBallPitBinding
    private val sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val physicsController = PhysicsWorldController(Vec2(0f,-10f))
    private var minPixelsPerMeter = 1f
    private val bubbleBodyDef: BodyDef
    private lateinit var worldBounds: RectF

    private var isSceneSetup = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (display == null) return
            if (event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_LOW) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            physicsController.gravity = when (display.rotation) {
                Surface.ROTATION_90 -> Vec2(y,-x)
                Surface.ROTATION_180 -> Vec2(x,y)
                Surface.ROTATION_270 -> Vec2(-y,x)
                else -> Vec2(-x,-y)
            }
        }
    }

    init {
        val view = inflate(context, R.layout.view_ball_pit, this)
        binding = ViewBallPitBinding.bind(view)

        physicsController.listener = object : PhysicsWorldController.Listener {
            override fun onWorldStep() {
                binding.physicsView?.postInvalidate()
            }
        }

        bubbleBodyDef = BodyDef()
        bubbleBodyDef.type = BodyType.DYNAMIC
        bubbleBodyDef.fixedRotation = false

        sensorManager = context.getSystemService()!!
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME) fun onResume() {
        accelerometer?.let { sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME, mainHandler) }
        physicsController.resume()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE) fun onPause() {
        sensorManager.unregisterListener(sensorEventListener)
        mainHandler.removeCallbacksAndMessages(null)
        physicsController.pause()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY) fun onDestroy() {
        physicsController.destroy()
        lifecycleScope.cancel()
    }

    fun setViews(viewsAndSizes: List<Pair<View, Int>>) {
        synchronized(isSceneSetup) {
            check(!isSceneSetup) { "Views can only be set once!" }
            isSceneSetup = true
        }

        val areaInMeters = max(1f, viewsAndSizes.map { it.second }.sumByFloat { getBubbleArea(it) })
        lifecycleScope.launch {
            setupScene(areaInMeters / BALLPIT_FILL_FACTOR)
            addBubblesToScene(viewsAndSizes)
        }
    }

    /* --------------------------------- Set up physics layout  --------------------------------- */

    private suspend fun setupScene(areaInMeters: Float) {
        binding.physicsView.awaitPreDraw()

        val width = binding.physicsView.width.toFloat()
        val height = binding.physicsView.height.toFloat()
        minPixelsPerMeter = sqrt(width * height / areaInMeters)
        binding.physicsView.pixelsPerMeter = minPixelsPerMeter

        val widthInMeters = width / minPixelsPerMeter
        val heightInMeters = height / minPixelsPerMeter
        worldBounds = RectF(0f,0f, widthInMeters, heightInMeters)

        createWorldBounds(worldBounds)
    }

    private suspend fun createWorldBounds(rect: RectF): Body {
        val bodyDef = BodyDef()
        bodyDef.type = BodyType.STATIC

        val shape = ChainShape()
        shape.createLoop(arrayOf(
            Vec2(0f, 0f),
            Vec2(rect.width(), 0f),
            Vec2(rect.width(), rect.height()),
            Vec2(0f, rect.height())
        ), 4)

        return physicsController.createBody(bodyDef, shape, 0f)
    }

    private suspend fun addBubblesToScene(viewsAndSizes: List<Pair<View, Int>>) {
        // add the biggest quest bubbles first so that the smaller ones have a higher z rank
        // because they are added later. So, they will still be clickable
        val sortedBySize = viewsAndSizes.sortedByDescending { it.second }
        for ((view, size) in sortedBySize) {
            val radius = getBubbleRadius(size)
            val spawnPos = Vec2(
                radius + Math.random().toFloat() * (worldBounds.width() - 2 * radius),
                radius + Math.random().toFloat() * (worldBounds.height() - 2 * radius))
            addBubble(view, size, spawnPos)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private suspend fun addBubble(view: View, size: Int, position: Vec2) {
        val radius = min(getBubbleRadius(size), min(worldBounds.width(), worldBounds.height())/3 )
        val body = createBubbleBody(position, radius)

        startInflatingAnimation(view, size, position.y)
        view.setOnTouchListener(object : SimpleGestureListener(view) {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                view.performClick()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                val a = view.rotation / 180.0 * PI
                val vx = (cos(a) * velocityX - sin(a) * velocityY).toFloat()
                val vy = (cos(a) * velocityY + sin(a) * velocityX).toFloat()
                onFlingBubbleBody(body, vx, vy)
                return true
            }
        })
        binding.physicsView.addView(view, body)
    }

    private suspend fun createBubbleBody(position: Vec2, radius: Float): Body {
        val shape = CircleShape()
        shape.radius = radius
        // bubbles behave like balls, not circles
        val density = 4f/3f * radius
        bubbleBodyDef.position = position
        return physicsController.createBody(bubbleBodyDef, shape, density)
    }

    private fun startInflatingAnimation(view: View, size: Int, yPosition: Float) {
        view.scaleX = 0.3f
        view.alpha = 0f
        view.scaleY = 0.3f
        // bigger bubbles take longer to "inflate", also those further down inflate first
        view.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setStartDelay((1600 * yPosition / worldBounds.height()).toLong())
            .setDuration((200 + (size * 150.0).pow(0.5)).toLong())
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun getBubbleRadius(size: Int): Float =
        (3.0 * size * BUBBLE_BASE_SIZE_IN_M3 / 4.0 / PI).pow(1.0 / 3.0).toFloat()

    private fun getBubbleArea(size: Int): Float {
        val r = getBubbleRadius(size)
        return (PI * r * r).toFloat()
    }

    /* ---------------------------- Interaction with quest bubbles  ----------------------------- */

    private fun onFlingBubbleBody(body: Body, velocityX: Float, velocityY: Float) {
        val pixelsPerMeter = binding.physicsView?.pixelsPerMeter ?: return
        val vx = FLING_SPEED_FACTOR * velocityX / pixelsPerMeter
        val vy = FLING_SPEED_FACTOR * -velocityY / pixelsPerMeter
        body.linearVelocity = Vec2(vx, vy).addLocal(body.linearVelocity)
    }

    companion object {
        private const val BUBBLE_BASE_SIZE_IN_M3 = 0.01f
        private const val BALLPIT_FILL_FACTOR = 0.55f
        private const val FLING_SPEED_FACTOR = 0.3f
    }
}

private open class SimpleGestureListener(private val view: View) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    private val gestureDetector = GestureDetector(view.context, this)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when(event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.isPressed = true
            MotionEvent.ACTION_UP -> view.isPressed = false
        }
        v?.parent?.requestDisallowInterceptTouchEvent(true)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent?): Boolean = true
}
