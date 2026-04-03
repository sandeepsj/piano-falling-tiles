package com.pianotiles.rendering

import android.opengl.GLES32
import android.util.Log

/**
 * Compiles and links a vertex + fragment shader pair.
 * Logs any errors. Call use() before setting uniforms.
 */
class ShaderProgram(vertSrc: String, fragSrc: String) {

    val programId: Int

    init {
        val vertId = compile(GLES32.GL_VERTEX_SHADER, vertSrc)
        val fragId = compile(GLES32.GL_FRAGMENT_SHADER, fragSrc)
        programId = GLES32.glCreateProgram().also { prog ->
            GLES32.glAttachShader(prog, vertId)
            GLES32.glAttachShader(prog, fragId)
            GLES32.glLinkProgram(prog)
            val status = IntArray(1)
            GLES32.glGetProgramiv(prog, GLES32.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("SHADER", "Link error: ${GLES32.glGetProgramInfoLog(prog)}")
            } else {
                Log.d("SHADER", "Shader program linked OK (id=$prog)")
            }
        }
        GLES32.glDeleteShader(vertId)
        GLES32.glDeleteShader(fragId)
    }

    fun use() = GLES32.glUseProgram(programId)

    fun attrib(name: String)  = GLES32.glGetAttribLocation(programId, name)
    fun uniform(name: String) = GLES32.glGetUniformLocation(programId, name)

    private fun compile(type: Int, src: String): Int {
        val id = GLES32.glCreateShader(type)
        GLES32.glShaderSource(id, src)
        GLES32.glCompileShader(id)
        val status = IntArray(1)
        GLES32.glGetShaderiv(id, GLES32.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("SHADER", "Compile error (type=$type): ${GLES32.glGetShaderInfoLog(id)}")
        }
        return id
    }
}
