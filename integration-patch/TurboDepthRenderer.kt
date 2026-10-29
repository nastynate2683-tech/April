package com.example.motogyro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.GLES20
import android.view.SurfaceHolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Safe continuous-surface renderer for uploaded images.
 *
 * The original six-layer renderer remains the fallback/demo path. Uploaded
 * images use a smooth, low-frequency depth field so 100% settings increase
 * controlled parallax instead of turning the image into a rubber sheet.
 *
 * This renderer owns its EGL context/surface/thread and tears all GL resources
 * down when the wallpaper surface stops or the renderer is stopped.
 */
class TurboDepthRenderer(
    private val holder: SurfaceHolder,
    private val motion: MotionController,
    private val settingsProvider: () -> MotionSettings,
    private val imageFile: File
) {
    private val running = AtomicBoolean(false)
    @Volatile private var width = 1
    @Volatile private var height = 1
    private var thread: Thread? = null

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ renderLoop() }, "APRIL-TurboDepth").also { it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        val current = thread
        if (current != null && current !== Thread.currentThread()) {
            try { current.join(1500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        thread = null
    }

    private fun renderLoop() {
        var display: android.opengl.EGLDisplay? = null
        var eglContext: android.opengl.EGLContext? = null
        var surface: android.opengl.EGLSurface? = null
        var texture = 0
        var program = 0
        var initialized = false

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return
            initialized = true

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return
            val config = configs[0] ?: return

            val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return

            val windowSurface = holder.surface
            if (!windowSurface.isValid) return
            surface = EGL14.eglCreateWindowSurface(
                display, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0
            )
            if (surface == EGL14.EGL_NO_SURFACE) return
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return
            if (bitmap.isRecycled) return

            try {
                program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
                texture = uploadTexture(bitmap)
                val mesh = buildMesh(bitmap)
                val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

                GLES20.glUseProgram(program)
                val position = GLES20.glGetAttribLocation(program, "aPosition")
                val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
                val camera = GLES20.glGetUniformLocation(program, "uCamera")
                val depthAmount = GLES20.glGetUniformLocation(program, "uDepthAmount")
                val aspect = GLES20.glGetUniformLocation(program, "uAspect")
                val textureLoc = GLES20.glGetUniformLocation(program, "uTexture")
                GLES20.glUniform1i(textureLoc, 0)
                GLES20.glDisable(GLES20.GL_CULL_FACE)
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)

                val frameBuffer = mesh.buffer
                val stride = 5 * 4
                frameBuffer.position(0)
                GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, stride, frameBuffer)
                GLES20.glEnableVertexAttribArray(position)
                frameBuffer.position(3)
                GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, stride, frameBuffer)
                GLES20.glEnableVertexAttribArray(texCoord)

                var last = System.nanoTime()
                while (running.get() && !Thread.currentThread().isInterrupted && holder.surface.isValid) {
                    val now = System.nanoTime()
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    motion.update(dt)
                    val s = settingsProvider()
                    val p = safeCurve(s.parallaxDepth)
                    val m = safeCurve(s.motionStrength)
                    val g = safeCurve(s.gyroSensitivity)

                    // 100% means maximum controlled travel, never maximum distortion.
                    val cameraX = motion.x * g * m * p * 0.060f
                    val cameraY = motion.y * g * m * p * 0.060f
                    val surfaceAspect = width.toFloat() / height.toFloat()
                    // Convert the source image aspect ratio into normalized device coordinates.
                    // NDC is square, so the limiting axis must remain 1.0 and the
                    // other axis must letterbox/pillarbox. The previous branches
                    // were inverted and applied the surface ratio twice, visibly
                    // compressing uploaded photos on the live wallpaper.
                    val fitX = if (surfaceAspect > imageAspect) imageAspect / surfaceAspect else 1f
                    val fitY = if (surfaceAspect > imageAspect) 1f else surfaceAspect / imageAspect

                    GLES20.glViewport(0, 0, width, height)
                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    GLES20.glUniform2f(camera, cameraX, cameraY)
                    GLES20.glUniform1f(depthAmount, p)
                    GLES20.glUniform2f(aspect, fitX, fitY)
                    GLES20.glDrawElements(
                        GLES20.GL_TRIANGLES,
                        mesh.indexCount,
                        GLES20.GL_UNSIGNED_SHORT,
                        mesh.indices
                    )

                    if (!EGL14.eglSwapBuffers(display, surface)) break
                    try {
                        Thread.sleep(16)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            // Delete GL objects while the context is still current.
            if (display != null && display != EGL14.EGL_NO_DISPLAY &&
                eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT &&
                surface != null && surface != EGL14.EGL_NO_SURFACE &&
                EGL14.eglGetCurrentContext() == eglContext) {
                if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                if (program != 0) GLES20.glDeleteProgram(program)
            }
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                try { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) } catch (_: Exception) { }
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                    try { EGL14.eglDestroySurface(display, surface) } catch (_: Exception) { }
                }
                if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                    try { EGL14.eglDestroyContext(display, eglContext) } catch (_: Exception) { }
                }
                if (initialized) {
                    try { EGL14.eglTerminate(display) } catch (_: Exception) { }
                }
            }
            running.set(false)
        }
    }

    private fun safeCurve(value: Int): Float {
        val normalized = (value.coerceIn(0, 100) / 100f)
        return normalized * normalized * (3f - 2f * normalized)
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    private data class Mesh(val buffer: FloatBuffer, val indices: java.nio.ShortBuffer, val indexCount: Int)

    private fun buildMesh(bitmap: Bitmap): Mesh {
        val cols = 64
        val rows = 64
        val verts = FloatArray((cols + 1) * (rows + 1) * 5)

        // Build a low-frequency depth field. Several samples around each vertex
        // prevent individual bright/dark pixels from producing violent folds.
        for (r in 0..rows) {
            val v = r.toFloat() / rows
            val y = 1f - 2f * v
            for (c in 0..cols) {
                val u = c.toFloat() / cols
                val x = 2f * u - 1f
                // Keep the uploaded photograph on a geometrically flat textured surface.
                // The former luminance-derived vertex depths bent every bright/dark edge,
                // making the entire photo look compressed or melted. Depth controls now
                // affect only bounded camera parallax in the shader, never image geometry.
                val i = (r * (cols + 1) + c) * 5
                verts[i] = x
                verts[i + 1] = y
                verts[i + 2] = 0.42f
                verts[i + 3] = u
                verts[i + 4] = v
            }
        }


        val indices = ShortArray(cols * rows * 6)
        var k = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = r * (cols + 1) + c
            val b = a + 1
            val d = (r + 1) * (cols + 1) + c
            val e = d + 1
            indices[k++] = a.toShort(); indices[k++] = d.toShort(); indices[k++] = b.toShort()
            indices[k++] = b.toShort(); indices[k++] = d.toShort(); indices[k++] = e.toShort()
        }
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        return Mesh(vb, ib, indices.size)
    }

    private fun sampleSmoothLuma(bitmap: Bitmap, u: Float, v: Float): Float {
        val cx = (u * (bitmap.width - 1)).toInt()
        val cy = (v * (bitmap.height - 1)).toInt()
        var sum = 0f
        var count = 0
        for (dy in -2..2 step 2) for (dx in -2..2 step 2) {
            val px = (cx + dx).coerceIn(0, bitmap.width - 1)
            val py = (cy + dy).coerceIn(0, bitmap.height - 1)
            val color = bitmap.getPixel(px, py)
            sum += (android.graphics.Color.red(color) * 0.299f +
                android.graphics.Color.green(color) * 0.587f +
                android.graphics.Color.blue(color) * 0.114f) / 255f
            count++
        }
        return (sum / count).coerceIn(0f, 1f)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException(GLES20.glGetShaderInfoLog(shader))
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException(GLES20.glGetProgramInfoLog(p))
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        return p
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            uniform vec2 uCamera;
            uniform float uDepthAmount;
            uniform vec2 uAspect;
            varying vec2 vTexCoord;
            void main() {
                float z = aPosition.z * uDepthAmount;
                // Only a tiny perspective component is used; almost all movement
                // comes from depth separation, preventing rubber-sheet stretching.
                float scale = 1.0 + z * 0.015;
                vec2 p = aPosition.xy * scale + uCamera * z;
                p *= uAspect;
                gl_Position = vec4(p, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
        """
    }
}
