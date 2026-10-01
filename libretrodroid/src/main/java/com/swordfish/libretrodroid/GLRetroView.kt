/*
 *     Copyright (C) 2022  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

import android.app.ActivityManager
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.coroutineScope
import com.swordfish.libretrodroid.gamepad.GamepadsManager
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.properties.Delegates
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class GLRetroView(
    context: Context,
    private val data: GLRetroViewData,
) : GLSurfaceView(context), LifecycleObserver {

    var audioEnabled: Boolean by Delegates.observable(true) { _, _, value ->
        LibretroDroid.setAudioEnabled(value)
    }

    // KaizoCore patch: sensors. The core's requests as a mask (1 accel, 2 gyro,
    // 4 illuminance); the app feeds normalised values while any bit is set.
    fun sensorMask(): Int = LibretroDroid.getSensorMask()
    fun setSensorValues(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, lux: Float) =
        LibretroDroid.setSensorValues(ax, ay, az, gx, gy, gz, lux)

    // KaizoCore patch: slow motion divisor (1 = normal, 2 = half speed, 4 = quarter).
    var slowMotion: Int by Delegates.observable(1) { _, _, value ->
        LibretroDroid.setSlowMotion(value)
    }

    var frameSpeed: Int by Delegates.observable(1) { _, _, value ->
        LibretroDroid.setFrameSpeed(value)
    }

    var shader: ShaderConfig by Delegates.observable(data.shader) { _, _, value ->
        LibretroDroid.setShaderConfig(buildShader(value))
    }

    var viewport: RectF by Delegates.observable(RectF(0f, 0f, 1f, 1f)) { _, _, value ->
        runOnEmulationThread(true, Unit) {
            LibretroDroid.setViewport(value.left, value.top, value.width(), value.height())
        }
    }

    private val openGLESVersion: Int

    private var isGameLoaded = false
    private var isEmulationReady = false
    private var isAborted = false

    private val retroGLEventsSubject = MutableSharedFlow<GLRetroEvents>(1)
    private val retroGLIssuesErrors = MutableSharedFlow<Int>(1)

    private val rumbleEventsSubject = MutableSharedFlow<RumbleEvent>()

    private var lifecycle: Lifecycle? = null
    // KaizoCore patch: the render observer is registered on the ACTIVITY's
    // lifecycle and was never removed, so every destroyed view left one
    // behind. The next activity pause (file picker, HOME, screen off) then
    // called LibretroDroid.pause() against a torn-down core: SIGSEGV at
    // Audio::stop(), fault addr 0x68, reproduced 2026-09-05 by opening the
    // ROM picker after leaving the Play tab.
    private var renderObserver: RenderLifecycleObserver? = null

    init {
        openGLESVersion = getGLESVersion(context)
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(openGLESVersion)
        setRenderer(Renderer())
        keepScreenOn = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate(lifecycleOwner: LifecycleOwner) = catchExceptions {
        lifecycle = lifecycleOwner.lifecycle
        LibretroDroid.create(
            openGLESVersion,
            data.coreFilePath,
            data.systemDirectory,
            data.savesDirectory,
            data.variables,
            buildShader(data.shader),
            getDefaultRefreshRate(),
            data.preferLowLatencyAudio,
            data.gameVirtualFiles.isNotEmpty(),
            data.enableMicrophone,
            data.skipDuplicateFrames,
            data.immersiveMode,
            getDeviceLanguage()
        )
        LibretroDroid.setRumbleEnabled(data.rumbleEventsEnabled)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() = catchExceptions {
        renderObserver?.let { lifecycle?.removeObserver(it) }
        renderObserver = null
        LibretroDroid.destroy()
        lifecycle = null
    }

    private fun getDeviceLanguage() = Locale.getDefault().language

    private fun getDefaultRefreshRate(): Float {
        return (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    }

    fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0) {
        queueEvent { LibretroDroid.onKeyEvent(port, action, keyCode) }
    }

    fun sendMotionEvent(source: Int, xAxis: Float, yAxis: Float, port: Int = 0) {
        queueEvent { LibretroDroid.onMotionEvent(port, source, xAxis, yAxis) }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val position = when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                normalizeTouchCoordinates(event.x, event.y)
            }

            MotionEvent.ACTION_UP -> {
                TOUCH_EVENT_OUTSIDE
            }

            else -> null
        }

        if (position != null) {
            LibretroDroid.onTouchEvent(position.x, position.y)
        }

        return true
    }

    private fun clamp(x: Float, min: Float, max: Float) = minOf(maxOf(x, min), max)

    private fun normalizeTouchCoordinates(x: Float, y: Float): PointF {
        val x = clamp(2f * x / width - 1f, -1f, +1f)
        val y = clamp(2f * y / height - 1f, -1f, +1f)
        return PointF(x, y)
    }

    /**
     * IronMON One patch: read [length] bytes of emulated memory at [address] (bus
     * addresses, e.g. GBA EWRAM 0x02000000). Empty result = unmapped, not zeroes.
     * Safe from any thread; a torn read during a frame is acceptable for tracking.
     */
    fun readMemory(address: Long, length: Int): ByteArray =
        LibretroDroid.readMemory(address, length)

    /** IronMON One patch: write [data] at [address]. Returns bytes written (0 = refused). */
    fun writeMemory(address: Long, data: ByteArray): Int =
        LibretroDroid.writeMemory(address, data)

    fun serializeState(useEmulationThread: Boolean = true): ByteArray {
        return runOnEmulationThread(useEmulationThread, ByteArray(0)) {
            LibretroDroid.serializeState()
        }
    }

    // KaizoCore patch: resetCheat had a native binding and no wrapper.
    fun resetCheat(useEmulationThread: Boolean = true) {
        runOnEmulationThread(useEmulationThread, Unit) { LibretroDroid.resetCheat() }
    }

    fun setCheat(index: Int, enable: Boolean, code: String, useEmulationThread: Boolean = true) {
        runOnEmulationThread(useEmulationThread, Unit) {
            LibretroDroid.setCheat(index, enable, code)
        }
    }

    /**
     * KaizoCore patch (2026-09-30): told before and after every state load, whoever loads it (a slot, undo,
     * rewind, a restore point, the battle retry, the crash resume), so the app can keep the in-game save a
     * core would otherwise roll back (melonDS writes the state's copy of the save to its .sav).
     */
    interface StateLoadListener {
        fun beforeStateLoad()
        fun afterStateLoad(loaded: Boolean)
    }

    @Volatile var stateLoadListener: StateLoadListener? = null

    fun unserializeState(data: ByteArray, useEmulationThread: Boolean = true): Boolean {
        val listener = stateLoadListener
        runCatching { listener?.beforeStateLoad() }
        val loaded = runOnEmulationThread(useEmulationThread, false) {
            LibretroDroid.unserializeState(data)
        }
        runCatching { listener?.afterStateLoad(loaded) }
        return loaded
    }

    fun serializeSRAM(useEmulationThread: Boolean = true): ByteArray {
        return runOnEmulationThread(useEmulationThread, ByteArray(0)) {
            LibretroDroid.serializeSRAM()
        }
    }

    fun unserializeSRAM(data: ByteArray, useEmulationThread: Boolean = true): Boolean {
        return runOnEmulationThread(useEmulationThread, false) {
            LibretroDroid.unserializeSRAM(data)
        }
    }

    fun reset(useEmulationThread: Boolean = true) = runOnEmulationThread(useEmulationThread, Unit) {
        LibretroDroid.reset()
    }

    fun getGLRetroEvents(): Flow<GLRetroEvents> {
        return retroGLEventsSubject
    }

    fun getGLRetroErrors(): Flow<Int> {
        return retroGLIssuesErrors
    }

    fun getRumbleEvents(): Flow<RumbleEvent> {
        return rumbleEventsSubject
    }

    fun getControllers(): Array<Array<Controller>> {
        return LibretroDroid.getControllers()
    }

    fun setControllerType(port: Int, type: Int) {
        LibretroDroid.setControllerType(port, type)
    }

    fun getVariables(): Array<Variable> {
        return LibretroDroid.getVariables()
    }

    fun updateVariables(vararg variables: Variable) {
        variables.forEach {
            LibretroDroid.updateVariable(it)
        }
    }

    fun getAvailableDisks(useEmulationThread: Boolean = true): Int {
        return runOnEmulationThread(useEmulationThread, 0) { LibretroDroid.availableDisks() }
    }

    fun getCurrentDisk(useEmulationThread: Boolean = true): Int {
        return runOnEmulationThread(useEmulationThread, 0) { LibretroDroid.currentDisk() }
    }

    fun changeDisk(index: Int, useEmulationThread: Boolean = true) {
        runOnEmulationThread(useEmulationThread, Unit) { LibretroDroid.changeDisk(index) }
    }

    private fun getGLESVersion(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
            3
        } else {
            2
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_DOWN, mappedKey, port)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_UP, mappedKey, port)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val port = (event?.device?.controllerNumber ?: 0) - 1
        if (port >= 0) {
            when (event?.source) {
                InputDevice.SOURCE_JOYSTICK -> {
                    sendMotionEvent(
                        MOTION_SOURCE_DPAD,
                        event.getAxisValue(MotionEvent.AXIS_HAT_X),
                        event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_LEFT,
                        event.getAxisValue(MotionEvent.AXIS_X),
                        event.getAxisValue(MotionEvent.AXIS_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_RIGHT,
                        event.getAxisValue(MotionEvent.AXIS_Z),
                        event.getAxisValue(MotionEvent.AXIS_RZ),
                        port
                    )
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // These functions are called only after the GLSurfaceView has been created.
    // IronMON One patch: @OnLifecycleEvent annotations were REMOVED in androidx
    // lifecycle 2.8, so the original observer never fired - native resume() never ran,
    // Input was never constructed, and every key event was silently dropped at the
    // `if (input)` guard. DefaultLifecycleObserver is the supported replacement.
    private inner class RenderLifecycleObserver : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            catchExceptions {
                LibretroDroid.resume()
                this@GLRetroView.onResume()
                isEmulationReady = true
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            catchExceptions {
                isEmulationReady = false
                this@GLRetroView.onPause()
                LibretroDroid.pause()
            }
        }
    }

    inner class Renderer : GLSurfaceView.Renderer {
        override fun onDrawFrame(gl: GL10) = catchExceptions {
            if (isEmulationReady) {
                if (!holdSteps) LibretroDroid.step(this@GLRetroView)
                if (stepHooks.isNotEmpty()) for (hook in stepHooks) runCatching { hook.afterStep(this@GLRetroView) }
                lifecycle?.coroutineScope?.launch {
                    retroGLEventsSubject.emit(GLRetroEvents.FrameRendered)
                }
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) = catchExceptions {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            LibretroDroid.onSurfaceChanged(width, height)
        }


        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) = catchExceptions {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            initializeCore()
            lifecycle?.coroutineScope?.launch {
                retroGLEventsSubject.emit(GLRetroEvents.SurfaceCreated)
            }
        }
    }

    // These functions are called from the GL thread.
    private fun initializeCore() = catchExceptions {
        if (isGameLoaded) return@catchExceptions
        when {
            data.gameFilePath != null -> loadGameFromPath(data.gameFilePath!!)
            data.gameFileBytes != null -> loadGameFromBytes(data.gameFileBytes!!)
            data.gameVirtualFiles.isNotEmpty() -> loadGameFromVirtualFiles(data.gameVirtualFiles)
        }
        data.saveRAMState?.let {
            LibretroDroid.unserializeSRAM(data.saveRAMState)
            data.saveRAMState = null
        }
        LibretroDroid.onSurfaceCreated()
        isGameLoaded = true

        KtUtils.runOnUIThread {
            if (renderObserver == null) {
                renderObserver = RenderLifecycleObserver().also { lifecycle?.addObserver(it) }
            }
        }
    }

    private fun loadGameFromVirtualFiles(virtualFiles: List<VirtualFile>) {
        val detachedVirtualFiles = virtualFiles
            .map { DetachedVirtualFile(it.virtualPath, it.fileDescriptor.detachFd()) }
        LibretroDroid.loadGameFromVirtualFiles(detachedVirtualFiles)
    }

    private fun loadGameFromBytes(gameFileBytes: ByteArray) {
        LibretroDroid.loadGameFromBytes(gameFileBytes)
    }

    private fun loadGameFromPath(gameFilePath: String) {
        LibretroDroid.loadGameFromPath(gameFilePath)
    }

    private fun catchExceptions(block: () -> Unit) {
        try {
            if (isAborted) return
            block()
        } catch (e: RetroException) {
            GlobalScope.launch {
                retroGLIssuesErrors.emit(e.errorCode)
            }
            isAborted = true
        } catch (e: Exception) {
            Log.e(TAG_LOG, "Error in GLRetroView", e)
            GlobalScope.launch {
                retroGLIssuesErrors.emit(LibretroDroid.ERROR_GENERIC)
            }
        }
    }

    /**
     * Runs [block] on the emulation thread and waits for it, [EMULATION_WAIT_MS] at most.
     *
     * KaizoCore patch (2026-09-30): this waited forever. The app calls it from the main thread (Time Machine every
     * 15 seconds, the battle-start snapshot, save and load, cheats), and an emulation thread that never answers (its
     * view torn down, or stalled while the window moves to another display) froze the whole app. A player's AYN
     * Thor reported exactly that ANR: "Input dispatching timed out (Application does not have a focused window)".
     * Now a job that never started is cancelled and [fallback] comes back; one that started is waited for a
     * moment longer, since it is finishing.
     */
    private fun <T> runOnEmulationThread(useEmulationThread: Boolean, fallback: T, block: () -> T): T {
        if (!useEmulationThread || Thread.currentThread().name.startsWith("GLThread")) {
            return block()
        }

        val latch = CountDownLatch(1)
        // 0 waiting, 1 started, 2 cancelled: whichever side moves it off 0 first decides, so a job either runs
        // in full or not at all, and the caller knows which.
        val phase = java.util.concurrent.atomic.AtomicInteger(0)
        var result: T? = null
        queueEvent {
            if (phase.compareAndSet(0, 1)) result = block()
            latch.countDown()
        }

        if (!latch.await(EMULATION_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            if (phase.compareAndSet(0, 2) || !latch.await(EMULATION_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.w(TAG_LOG, "The emulation thread did not answer in time; giving up on this call.")
                return fallback
            }
        }
        @Suppress("UNCHECKED_CAST")
        return if (phase.get() == 1) result as T else fallback
    }

    private fun buildShader(config: ShaderConfig): GLRetroShader {
        return when (config) {
            is ShaderConfig.Default -> GLRetroShader(LibretroDroid.SHADER_DEFAULT)
            is ShaderConfig.CRT -> GLRetroShader(LibretroDroid.SHADER_CRT)
            is ShaderConfig.LCD -> GLRetroShader(LibretroDroid.SHADER_LCD)
            is ShaderConfig.Sharp -> GLRetroShader(LibretroDroid.SHADER_SHARP)
            is ShaderConfig.CUT -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_VALUE to toParam(config.edgeMinValue),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_CONTRAST to toParam(config.edgeMinContrast),
                )
            )

            is ShaderConfig.CUT2 -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT2,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING to toParam(config.softEdgesSharpening),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING_AMOUNT to toParam(config.softEdgesSharpeningAmount),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_HARD_EDGES_SEARCH_MAX_ERROR to toParam(config.hardEdgesSearchMaxError),
                )
            )

            is ShaderConfig.CUT3 -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT3,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING to toParam(config.softEdgesSharpening),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING_AMOUNT to toParam(config.softEdgesSharpeningAmount),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_ERROR to toParam(config.hardEdgesSearchMaxError),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_DISTANCE to toParam(config.hardEdgesSearchMaxDistance),
                )
            )
        }
    }

    private fun toParam(param: Float): String {
        return param.toString()
    }

    private fun toParam(param: Boolean): String {
        return if (param) {
            "1"
        } else {
            "0"
        }
    }

    private fun toParam(param: Int): String {
        return param.toString()
    }

    private fun buildParams(vararg pairs: Pair<String, String?>): Map<String, String> {
        return pairs
            .filter { (key, value) -> value != null }
            .associate { (key, value) -> key to value!! }
    }

    // KaizoCore patch: RetroAchievements bridge. The JNI step drains the native
    // client's queues through these; the app installs a listener.
    interface CheevosListener {
        fun onServerCall(id: Int, url: String, postData: String, contentType: String)
        fun onEvent(type: Int, title: String, description: String, points: Int, badgeUrl: String, result: Int)
    }
    @Volatile var cheevosListener: CheevosListener? = null

    /** Called from the jni side (step). */
    private fun cheevosServerCall(id: Int, url: String, postData: String, contentType: String) {
        cheevosListener?.onServerCall(id, url, postData, contentType)
    }

    /** Called from the jni side (step). */
    private fun cheevosEvent(type: Int, title: String, description: String, points: Int, badgeUrl: String, result: Int) {
        cheevosListener?.onEvent(type, title, description, points, badgeUrl, result)
    }

    /** This function gets called from the jni side.*/
    private fun sendRumbleEvent(port: Int, strengthWeak: Float, strengthStrong: Float) {
        lifecycle?.coroutineScope?.launch {
            rumbleEventsSubject.emit(RumbleEvent(port, strengthWeak, strengthStrong))
        }
    }

    private fun refreshAspectRatio() {
        runOnEmulationThread(true, Unit) {
            LibretroDroid.refreshAspectRatio()
        }
    }

    sealed class GLRetroEvents {
        object FrameRendered : GLRetroEvents()
        object SurfaceCreated : GLRetroEvents()
    }

    /**
     * KaizoCore patch: runs on the emulation thread right after every step, between two frames of the game.
     * Only the debug build's test bot adds one (app/src/debug, bot/BotPort.kt); a players' build never does, so
     * a step costs one empty-list check.
     */
    fun interface StepHook {
        fun afterStep(view: GLRetroView)
    }

    companion object {
        private val TAG_LOG = GLRetroView::class.java.simpleName

        /**
         * KaizoCore patch: how long a call waits for the emulation thread before giving up, and how much longer
         * once its job has started. Together under Android's 5 seconds for input, so a stalled emulation thread
         * can never freeze the app into "not responding".
         */
        const val EMULATION_WAIT_MS = 2_000L
        const val EMULATION_GRACE_MS = 1_500L

        /** KaizoCore patch: see [StepHook]. */
        @JvmField
        val stepHooks = java.util.concurrent.CopyOnWriteArrayList<StepHook>()

        /**
         * KaizoCore patch: while true the render loop runs no frame of its own and only calls the step hooks.
         * Set only by the debug build's test bot while it plays frame by frame (it runs each frame itself, from
         * its hook); a players' build never sets it.
         */
        @JvmField
        @Volatile
        var holdSteps = false

        const val MOTION_SOURCE_DPAD = LibretroDroid.MOTION_SOURCE_DPAD
        const val MOTION_SOURCE_ANALOG_LEFT = LibretroDroid.MOTION_SOURCE_ANALOG_LEFT
        const val MOTION_SOURCE_ANALOG_RIGHT = LibretroDroid.MOTION_SOURCE_ANALOG_RIGHT
        const val MOTION_SOURCE_POINTER = LibretroDroid.MOTION_SOURCE_POINTER

        const val ERROR_LOAD_LIBRARY = LibretroDroid.ERROR_LOAD_LIBRARY
        const val ERROR_LOAD_GAME = LibretroDroid.ERROR_LOAD_GAME
        const val ERROR_GL_NOT_COMPATIBLE = LibretroDroid.ERROR_GL_NOT_COMPATIBLE
        const val ERROR_SERIALIZATION = LibretroDroid.ERROR_SERIALIZATION
        const val ERROR_CHEAT = LibretroDroid.ERROR_CHEAT
        const val ERROR_GENERIC = LibretroDroid.ERROR_GENERIC

        private val TOUCH_EVENT_OUTSIDE = PointF(-10f, 10f)
    }
}
