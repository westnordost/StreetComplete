package de.westnordost.streetcomplete.map

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mapzen.tangram.MapView
import com.mapzen.tangram.TouchInput.*
import com.mapzen.tangram.networking.DefaultHttpHandler
import com.mapzen.tangram.networking.HttpHandler
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloadCacheConfig
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.databinding.FragmentMapBinding
import de.westnordost.streetcomplete.ktx.awaitLayout
import de.westnordost.streetcomplete.ktx.containsAll
import de.westnordost.streetcomplete.ktx.setMargins
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.map.components.SceneMapComponent
import de.westnordost.streetcomplete.map.tangram.*
import de.westnordost.streetcomplete.view.insets_animation.respectSystemInsets
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.Version
import javax.inject.Inject

/** Manages a map that remembers its last location*/
open class MapFragment : Fragment(),
    TapResponder, DoubleTapResponder, LongPressResponder,
    PanResponder, ScaleResponder, ShoveResponder, RotateResponder, SharedPreferences.OnSharedPreferenceChangeListener {

    protected lateinit var mapView: MapView
    private set

    private val binding by viewBinding(FragmentMapBinding::bind)

    private val defaultCameraInterpolator = AccelerateDecelerateInterpolator()

    protected var controller: KtMapController? = null
    protected var sceneMapComponent: SceneMapComponent? = null

    private var previousCameraPosition: CameraPosition? = null

    var show3DBuildings: Boolean = true
    set(value) {
        if (field == value) return
        field = value

        val toggle = if (value) "true" else "false"

        lifecycleScope.launch {
            sceneMapComponent?.putSceneUpdates(listOf(
                "layers.buildings.draw.buildings-style.extrude" to toggle,
                "layers.buildings.draw.buildings-outline-style.extrude" to toggle
            ))
            sceneMapComponent?.loadScene()
        }
    }

    @Inject internal lateinit var vectorTileProvider: VectorTileProvider
    @Inject internal lateinit var cacheConfig: MapTilesDownloadCacheConfig
    @Inject internal lateinit var sharedPrefs: SharedPreferences

    interface Listener {
        /** Called when the map has been completely initialized */
        fun onMapInitialized()
        /** Called during camera animation and while the map is being controlled by a user */
        fun onMapIsChanging(position: LatLon, rotation: Float, tilt: Float, zoom: Float)
        /** Called after camera animation or after the map was controlled by a user */
        fun onMapDidChange(position: LatLon, rotation: Float, tilt: Float, zoom: Float)
        /** Called when the user begins to pan the map */
        fun onPanBegin()
        /** Called when the user long-presses the map */
        fun onLongPress(x: Float, y: Float)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    /* ------------------------------------ Lifecycle ------------------------------------------- */

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map)
        mapView.onCreate(savedInstanceState)

        binding.openstreetmapLink.setOnClickListener { showOpenUrlDialog("https://www.openstreetmap.org/copyright") }
        binding.mapTileProviderLink.text = vectorTileProvider.copyrightText
        binding.mapTileProviderLink.setOnClickListener { showOpenUrlDialog(vectorTileProvider.copyrightLink) }

        binding.attributionContainer.respectSystemInsets(View::setMargins)

        lifecycleScope.launch { initMap() }
    }

    private fun showOpenUrlDialog(url: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.open_url)
            .setMessage(url)
            .setPositiveButton(android.R.string.ok) { _,_ ->
                openUrl(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.THEME_BACKGROUND) {
            sceneMapComponent?.isAerialView = sharedPrefs.getString(Prefs.THEME_BACKGROUND, "MAP") == "AERIAL"
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { sceneMapComponent?.loadScene() }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        saveMapState()
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
        mapView.onDestroy()
        controller = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    /* ------------------------------------------- Map  ----------------------------------------- */

    private suspend fun initMap() {
        val ctrl = mapView.initMap(createHttpHandler())
        controller = ctrl
        if (ctrl == null) return
        lifecycle.addObserver(ctrl)
        registerResponders(ctrl)

        sceneMapComponent = SceneMapComponent(resources, ctrl, vectorTileProvider)
        sceneMapComponent?.isAerialView = sharedPrefs.getString(Prefs.THEME_BACKGROUND, "MAP") == "AERIAL"

        onBeforeLoadScene()

        sceneMapComponent?.loadScene()

        ctrl.glViewHolder!!.view.awaitLayout()

        onMapReady()

        listener?.onMapInitialized()
    }

    private fun registerResponders(ctrl: KtMapController) {
        ctrl.setTapResponder(this)
        ctrl.setDoubleTapResponder(this)
        ctrl.setLongPressResponder(this)
        ctrl.setRotateResponder(this)
        ctrl.setPanResponder(this)
        ctrl.setScaleResponder(this)
        ctrl.setShoveResponder(this)
        ctrl.setMapChangingListener(object : MapChangingListener {
            override fun onMapWillChange() {}
            override fun onMapIsChanging() {
                val camera = cameraPosition ?: return
                if (camera == previousCameraPosition) return
                previousCameraPosition = camera
                onMapIsChanging(camera.position, camera.rotation, camera.tilt, camera.zoom)
                listener?.onMapIsChanging(camera.position, camera.rotation, camera.tilt, camera.zoom)
            }
            override fun onMapDidChange() {
                val camera = cameraPosition ?: return
                if (camera == previousCameraPosition) return
                previousCameraPosition = camera
                onMapDidChange(camera.position, camera.rotation, camera.tilt, camera.zoom)
                listener?.onMapDidChange(camera.position, camera.rotation, camera.tilt, camera.zoom)
            }
        })
    }

    private fun createHttpHandler(): HttpHandler {
        val builder = OkHttpClient.Builder().cache(cacheConfig.cache)
        return object : DefaultHttpHandler(builder) {
            override fun configureRequest(url: HttpUrl, builder: Request.Builder) {
                builder
                    .cacheControl(cacheConfig.cacheControl)
                    .header("User-Agent", ApplicationConstants.USER_AGENT + " / " + Version.userAgent())
            }
        }
    }

    /* ----------------------------- Overrideable map callbacks --------------------------------- */

    @CallSuper protected open suspend fun onMapReady() {
        restoreMapState()
    }

    @CallSuper protected open suspend fun onBeforeLoadScene() {}

    protected open fun onMapIsChanging(position: LatLon, rotation: Float, tilt: Float, zoom: Float) {}

    protected open fun onMapDidChange(position: LatLon, rotation: Float, tilt: Float, zoom: Float) {}

    /* ---------------------- Overrideable callbacks for map interaction ------------------------ */

    override fun onPanBegin(): Boolean {
        listener?.onPanBegin()
        return false
    }
    override fun onPan(startX: Float, startY: Float, endX: Float, endY: Float): Boolean { return false }
    override fun onPanEnd(): Boolean { return false }

    override fun onFling(posX: Float, posY: Float, velocityX: Float, velocityY: Float): Boolean { return false }
    override fun onCancelFling(): Boolean { return false }

    override fun onScaleBegin(): Boolean { return false }
    override fun onScale(x: Float, y: Float, scale: Float, velocity: Float): Boolean { return false }
    override fun onScaleEnd(): Boolean { return false }

    override fun onShoveBegin(): Boolean { return false }
    override fun onShove(distance: Float): Boolean { return false }
    override fun onShoveEnd(): Boolean { return false }

    override fun onRotateBegin(): Boolean { return false }
    override fun onRotate(x: Float, y: Float, rotation: Float): Boolean { return false }
    override fun onRotateEnd(): Boolean { return false }

    override fun onSingleTapUp(x: Float, y: Float): Boolean { return false }

    override fun onSingleTapConfirmed(x: Float, y: Float): Boolean { return false }

    override fun onDoubleTap(x: Float, y: Float): Boolean {
        val pos = controller?.screenPositionToLatLon(PointF(x, y))
        if (pos != null) {
            controller?.updateCameraPosition(300L) {
                zoomBy = 1f
                position = pos
            }
        }
        return true
    }

    override fun onLongPress(x: Float, y: Float) {
        listener?.onLongPress(x, y)
    }

    /* -------------------------------- Save and Restore State ---------------------------------- */

    private fun restoreMapState() {
        val camera = loadCameraPosition() ?: return
        controller?.setCameraPosition(camera)
    }

    private fun saveMapState() {
        val camera = controller?.cameraPosition ?: return
        saveCameraPosition(camera, false)
    }

    private fun loadCameraPosition(): CameraPosition? {
        val prefs = activity?.getPreferences(Activity.MODE_PRIVATE) ?: return null
        if (!prefs.containsAll(listOf(PREF_LAT, PREF_LON, PREF_ROTATION, PREF_TILT, PREF_ZOOM))) return null

        return CameraPosition(
            LatLon(
                java.lang.Double.longBitsToDouble(prefs.getLong(PREF_LAT, 0)),
                java.lang.Double.longBitsToDouble(prefs.getLong(PREF_LON, 0))
            ),
            prefs.getFloat(PREF_ROTATION, 0f),
            prefs.getFloat(PREF_TILT, 0f),
            prefs.getFloat(PREF_ZOOM, 0f)
        )
    }

    private fun saveCameraPosition(camera: CameraPosition, saveNow: Boolean) {
        activity?.getPreferences(Activity.MODE_PRIVATE)?.edit(saveNow) {
            putFloat(PREF_ROTATION, camera.rotation)
            putFloat(PREF_TILT, camera.tilt)
            putFloat(PREF_ZOOM, camera.zoom)
            putLong(PREF_LAT, java.lang.Double.doubleToRawLongBits(camera.position.latitude))
            putLong(PREF_LON, java.lang.Double.doubleToRawLongBits(camera.position.longitude))
        }
    }

    /* ------------------------------- Controlling the map -------------------------------------- */

    fun adjustToOffsets(oldOffset: RectF, newOffset: RectF) {
        controller?.screenCenterToLatLon(oldOffset)?.let { pos ->
            controller?.updateCameraPosition {
                position = controller?.getLatLonThatCentersLatLon(pos, newOffset)
            }
        }
    }

    fun getPositionAt(point: PointF): LatLon? = controller?.screenPositionToLatLon(point)

    fun getPointOf(pos: LatLon): PointF? = controller?.latLonToScreenPosition(pos)

    fun getClippedPointOf(pos: LatLon): PointF? {
        val screenPositionOut = PointF()
        controller?.latLonToScreenPosition(pos, screenPositionOut, true) ?: return null
        return screenPositionOut
    }

    val cameraPosition: CameraPosition?
        get() = controller?.cameraPosition

    fun updateCameraPosition(
        duration: Long = 0,
        interpolator: Interpolator = defaultCameraInterpolator,
        builder: CameraUpdate.() -> Unit) {

        controller?.updateCameraPosition(duration, interpolator, builder)
    }

    fun setInitialCameraPosition(camera: CameraPosition) {
        val controller = controller
        if (controller != null) {
            controller.setCameraPosition(camera)
        } else {
            saveCameraPosition(camera, true)
        }
    }

    fun getPositionThatCentersPosition(pos: LatLon, offset: RectF): LatLon? {
        return controller?.getLatLonThatCentersLatLon(pos, offset)
    }

    fun getDisplayedArea(): BoundingBox? = controller?.screenAreaToBoundingBox(RectF())

    companion object {
        const val PREF_ROTATION = "map_rotation"
        const val PREF_TILT = "map_tilt"
        const val PREF_ZOOM = "map_zoom"
        const val PREF_LAT = "map_lat"
        const val PREF_LON = "map_lon"
    }

}
