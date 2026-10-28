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

/** Continuous-surface renderer for uploaded images. The original six-layer renderer remains the fallback path. */
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
        if (!running.getAndSet(false)) return
        thread?.join(500)
        thread = null
    }

    private fun renderLoop() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            EGL14.eglTerminate(display); return
        }
        val config = configs[0] ?: run { EGL14.eglTerminate(display); return }
        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) { EGL14.eglTerminate(display); return }
        val surface = EGL14.eglCreateWindowSurface(display, config, holder.surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, eglContext); EGL14.eglTerminate(display); return
        }
        if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, eglContext); EGL14.eglTerminate(display); return
        }
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null || bitmap.isRecycled) return
            val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            val texture = uploadTexture(bitmap)
            val mesh = buildMesh(bitmap)
            val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            bitmap.recycle()
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
            while (running.get() && holder.surface.isValid) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                motion.update(dt)
                val s = settingsProvider()
                val p = (s.parallaxDepth / 100f).coerceIn(0f, 1f)
                val m = (s.motionStrength / 100f).coerceIn(0f, 1f)
                val g = (s.gyroSensitivity / 100f).coerceIn(0f, 1f)
                // Parallax controls depth separation. Gyro controls response and
                // motion controls travel. The whole image is never translated.
                val cameraX = motion.x * g * m * p * 0.055f
                val cameraY = motion.y * g * m * p * 0.055f
                val surfaceAspect = width.toFloat() / height.toFloat()
                val fitX = if (surfaceAspect > imageAspect) 1f else surfaceAspect / imageAspect
                val fitY = if (surfaceAspect > imageAspect) imageAspect / surfaceAspect else 1f
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glUniform2f(camera, cameraX, cameraY)
                GLES20.glUniform1f(depthAmount, p)
                GLES20.glUniform2f(aspect, fitX, fitY)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indices)
                EGL14.eglSwapBuffers(display, surface)
                Thread.sleep(16)
            }
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
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
        for (r in 0..rows) {
            val v = r.toFloat() / rows
            val y = 1f - 2f * v
            for (c in 0..cols) {
                val u = c.toFloat() / cols
                val x = 2f * u - 1f
                val px = (u * (bitmap.width - 1)).toInt()
                val py = (v * (bitmap.height - 1)).toInt()
                val color = bitmap.getPixel(px, py)
                val lum = (android.graphics.Color.red(color) * 0.299f + android.graphics.Color.green(color) * 0.587f + android.graphics.Color.blue(color) * 0.114f) / 255f
                val center = 1f - max(abs(x), abs(y))
                val z = (0.12f + 0.58f * lum + 0.18f * center).coerceIn(0.06f, 0.90f)
                val i = (r * (cols + 1) + c) * 5
                verts[i] = x; verts[i + 1] = y; verts[i + 2] = z; verts[i + 3] = u; verts[i + 4] = v
            }
        }
        val indices = ShortArray(cols * rows * 6)
        var k = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = r * (cols + 1) + c; val b = a + 1
            val d = (r + 1) * (cols + 1) + c; val e = d + 1
            indices[k++] = a.toShort(); indices[k++] = d.toShort(); indices[k++] = b.toShort()
            indices[k++] = b.toShort(); indices[k++] = d.toShort(); indices[k++] = e.toShort()
        }
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        return Mesh(vb, ib, indices.size)
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
                float scale = 1.0 + z * 0.08;
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
