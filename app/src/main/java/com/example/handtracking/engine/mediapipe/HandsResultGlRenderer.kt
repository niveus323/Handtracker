package com.example.handtracking.engine.mediapipe

import android.opengl.GLES20
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutioncore.ResultGlRenderer
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class HandsResultGlRenderer: ResultGlRenderer<HandsResult> {
    private val TAG = "HandsResultGlRenderer"

    private val LEFT_HAND_CONNECTION_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)
    private val RIGHT_HAND_CONNECTION_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
    private val CONNECTION_THICKNESS = 25.0f
    private val LEFT_HAND_HOLLOW_CIRCLE_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)
    private val RIGHT_HAND_HOLLOW_CIRCLE_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
    private val HOLLOW_CIRCLE_RADIUS = 0.01f
    private val LEFT_HAND_LANDMARK_COLOR = floatArrayOf(1f, 0.2f, 0.2f, 1f)
    private val RIGHT_HAND_LANDMARK_COLOR = floatArrayOf(0.2f, 1f, 0.2f, 1f)
    private val LANDMARK_RADIUS = 0.008f
    private val NUM_SEGMENTS = 120
    private val VERTEX_SHADER = "uniform mat4 uProjectionMatrix;"+
                                "attribute vec4 vPosition;"+
                                "void main() {"+
                                "  gl_Position = uProjectionMatrix * vPosition;"+
                                "}"
    private val FRAGMENT_SHADER = "precision mediump float;"+
                                  "uniform vec4 uColor;"+
                                  "void main() {"+
                                  "  gl_FragColor = uColor;"+
                                  "}"
    private var program = 0
    private var positionHandle = 0
    private var projectionMatrixHandle = 0
    private var colorHandle = 0

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    override fun setupRendering() {
        program = GLES20.glCreateProgram()
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    override fun renderResult(result: HandsResult?, projectionMatrix: FloatArray?) {
        if (result == null) {
            return
        }
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0)
        GLES20.glLineWidth(CONNECTION_THICKNESS)
        val numHands = result.multiHandLandmarks().size
        for (i in 0 until numHands) {
            val isLeftHand = result.multiHandedness()[i].label == "Left"
            drawConnections(
                result.multiHandLandmarks()[i].landmarkList,
                if (isLeftHand) LEFT_HAND_CONNECTION_COLOR else RIGHT_HAND_CONNECTION_COLOR
            )
            for (landmark in result.multiHandLandmarks()[i].landmarkList) {
                // Draws the landmark.
                drawCircle(
                    landmark.x,
                    landmark.y,
                    if (isLeftHand) LEFT_HAND_LANDMARK_COLOR else RIGHT_HAND_LANDMARK_COLOR
                )
                // Draws a hollow circle around the landmark.
                drawHollowCircle(
                    landmark.x,
                    landmark.y,
                    if (isLeftHand) LEFT_HAND_HOLLOW_CIRCLE_COLOR else RIGHT_HAND_HOLLOW_CIRCLE_COLOR
                )
            }
        }
    }

    /**
     * Deletes the shader program.
     *
     *
     * This is only necessary if one wants to release the program while keeping the context around.
     */
    fun release() {
        GLES20.glDeleteProgram(program)
    }

    private fun drawConnections(
        handLandmarkList: List<NormalizedLandmark>,
        colorArray: FloatArray
    ) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        for (c in Hands.HAND_CONNECTIONS) {
            val start = handLandmarkList[c.start()]
            val end = handLandmarkList[c.end()]
            val vertex = floatArrayOf(start.x, start.y, end.x, end.y)
            val vertexBuffer = ByteBuffer.allocateDirect(vertex.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertex)
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }
    }

    private fun drawCircle(x: Float, y: Float, colorArray: FloatArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        val vertexCount = NUM_SEGMENTS + 2
        val vertices = FloatArray(vertexCount * 3)
        vertices[0] = x
        vertices[1] = y
        vertices[2] = 0F
        for (i in 1 until vertexCount) {
            val angle = 2.0f * i * Math.PI.toFloat() / NUM_SEGMENTS
            val currentIndex = 3 * i
            vertices[currentIndex] = x + (LANDMARK_RADIUS * cos(angle.toDouble())).toFloat()
            vertices[currentIndex + 1] =
                y + (LANDMARK_RADIUS * sin(angle.toDouble())).toFloat()
            vertices[currentIndex + 2] = 0F
        }
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount)
    }

    private fun drawHollowCircle(x: Float, y: Float, colorArray: FloatArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0)
        val vertexCount = NUM_SEGMENTS + 1
        val vertices = FloatArray(vertexCount * 3)
        for (i in 0 until vertexCount) {
            val angle = 2.0f * i * Math.PI.toFloat() / NUM_SEGMENTS
            val currentIndex = 3 * i
            vertices[currentIndex] =
                x + (HOLLOW_CIRCLE_RADIUS * cos(angle.toDouble())).toFloat()
            vertices[currentIndex + 1] =
                y + (HOLLOW_CIRCLE_RADIUS * sin(angle.toDouble())).toFloat()
            vertices[currentIndex + 2] = 0F
        }
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount)
    }
}