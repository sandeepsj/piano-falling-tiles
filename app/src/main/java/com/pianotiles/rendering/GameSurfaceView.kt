package com.pianotiles.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer = GameRenderer()

    init {
        // OpenGL ES 3.2 — required for modern shader features
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        // Render continuously (not on-demand) — game loop
        renderMode = RENDERMODE_CONTINUOUSLY

        // Surface.setFrameRate() signals the compositor to keep the display at 120Hz.
        // Must be called in surfaceCreated, not onAttachedToWindow (surface not ready yet).
        // API 31+: CHANGE_FRAME_RATE_ALWAYS prevents the LTPO governor from overriding us.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            h.surface.setFrameRate(
                                120f,
                                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                                Surface.CHANGE_FRAME_RATE_ALWAYS
                            )
                        } else {
                            h.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                        }
                        Log.d("RENDER", "setFrameRate(120, ALWAYS) OK")
                    } catch (e: Exception) {
                        Log.w("RENDER", "setFrameRate failed: ${e.message}")
                    }
                }
                override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: SurfaceHolder) {}
            })
        }
    }
}
