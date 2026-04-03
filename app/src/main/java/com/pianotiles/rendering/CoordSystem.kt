package com.pianotiles.rendering

/**
 * Pure conversion functions: screen pixels ↔ OpenGL NDC (-1..+1).
 * No GL state. Unit-testable.
 *
 * Android screen: origin top-left, y increases downward.
 * OpenGL NDC:     origin center,   y increases upward.
 */
object CoordSystem {

    fun pxToNdcX(px: Float, screenWidth: Int): Float =
        (px / screenWidth) * 2f - 1f

    fun pxToNdcY(px: Float, screenHeight: Int): Float =
        1f - (px / screenHeight) * 2f

    /**
     * Returns 8 floats (4 vertices × xy) in triangle-strip order:
     * top-left, top-right, bottom-left, bottom-right
     */
    fun rectToVertices(
        left: Float, top: Float, right: Float, bottom: Float,
        screenWidth: Int, screenHeight: Int
    ): FloatArray = floatArrayOf(
        pxToNdcX(left,  screenWidth), pxToNdcY(top,    screenHeight), // TL
        pxToNdcX(right, screenWidth), pxToNdcY(top,    screenHeight), // TR
        pxToNdcX(left,  screenWidth), pxToNdcY(bottom, screenHeight), // BL
        pxToNdcX(right, screenWidth), pxToNdcY(bottom, screenHeight)  // BR
    )
}
