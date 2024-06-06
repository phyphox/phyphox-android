package de.rwth_aachen.phyphox.camera.analyzer;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public abstract class OpenGLHelper {
    static public void checkGLError(String tag) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("Camera OpenGL",  "glError at " + tag + ": " + error);
        }
    }

    static void debugShader(int shader) {
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            Log.e("Camera OpenGL", "Shader compilation failed.");
            Log.e("Camera OpenGL", GLES20.glGetShaderInfoLog(shader));
        }
    }
    static void debugProgram(int program) {
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e("Camera OpenGL", "Shader linking failed.");
            Log.e("Camera OpenGL", GLES20.glGetProgramInfoLog(program));
        }
    }

    static int buildProgram(String vertexShader, String fragmentShader) {
        int iVertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(iVertexShader, vertexShader);
        GLES20.glCompileShader(iVertexShader);
        debugShader(iVertexShader);

        int iFragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(iFragmentShader, fragmentShader);
        GLES20.glCompileShader(iFragmentShader);
        debugShader(iFragmentShader);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, iVertexShader);
        GLES20.glAttachShader(program, iFragmentShader);
        GLES20.glLinkProgram(program);
        debugProgram(program);
        checkGLError("Link shaders");

        return program;
    }

    final static String fullScreenVertexShader =
            "precision highp float;" +
            "attribute vec2 vertices;" +
            "attribute vec2 texCoordinates;" +
            "uniform mat4 camMatrix;" +
            "varying vec2 texPosition;" +
            "uniform vec2 passepartoutMin;" +
            "uniform vec2 passepartoutMax;" +
            "varying vec2 positionInPassepartout;" +
            "void main () {" +
            "   texPosition = (camMatrix * vec4(texCoordinates, 0., 1.)).xy;" +
            "   positionInPassepartout = vec2((0.5*(1.0-vertices.x) - passepartoutMin.y)/(passepartoutMax.y-passepartoutMin.y)," +
                                             "(0.5*(1.0-vertices.y) - passepartoutMin.x)/(passepartoutMax.x-passepartoutMin.x));" +
            "   gl_Position = vec4(vertices, 0., 1.);" +
            "}";

    final static String interpolatingFullScreenVertexShader =
            "precision highp float;" +
            "attribute vec2 vertices;" +
            "attribute vec2 texCoordinates;" +
            "uniform vec2 resSource;" +
            "uniform vec2 resTarget;" +
            "varying vec2 texPosition1;" +
            "varying vec2 texPosition2;" +
            "varying vec2 texPosition3;" +
            "varying vec2 texPosition4;" +
            "void main () {" +
            "   float x1 = (4.0*resTarget.x*texCoordinates.x - 1.0)/resSource.x;" +
            "   float x2 = (4.0*resTarget.x*texCoordinates.x + 1.0)/resSource.x;" +
            "   float y1 = (4.0*resTarget.y*texCoordinates.y - 1.0)/resSource.y;" +
            "   float y2 = (4.0*resTarget.y*texCoordinates.y + 1.0)/resSource.y;" +
            "   texPosition1 = vec2(x1, y1);" +
            "   texPosition2 = vec2(x2, y1);" +
            "   texPosition3 = vec2(x1, y2);" +
            "   texPosition4 = vec2(x2, y2);" +
            "   gl_Position = vec4(vertices, 0., 1.);" +
            "}";

    final static float[] fullScreenVertices = {-1.f, -1.f, 1.f, -1.f, -1.f, 1.f, 1.f, 1.f};
    final static float[] fullScreenTexCoordinates = {0.f, 0.f, 1.f, 0.f, 0.f, 1.f, 1.f, 1.f};
    static FloatBuffer fullScreenVertexBuffer, fullScreenTexCoordinateBuffer;
    public static int fullScreenVboVertices, fullScreenVboTexCoordinates;

    static void prepareFullScreenVertices() {
        fullScreenVertexBuffer = ByteBuffer.allocateDirect(fullScreenVertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fullScreenVertexBuffer.put(fullScreenVertices);
        fullScreenVertexBuffer.rewind();
        fullScreenTexCoordinateBuffer = ByteBuffer.allocateDirect(fullScreenTexCoordinates.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fullScreenTexCoordinateBuffer.put(fullScreenTexCoordinates);
        fullScreenTexCoordinateBuffer.rewind();

        int ref[] = new int[2];
        GLES20.glGenBuffers(2, ref, 0);
        fullScreenVboVertices = ref[0];
        fullScreenVboTexCoordinates = ref[1];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fullScreenVertices.length*4, fullScreenVertexBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, fullScreenTexCoordinates.length*4, fullScreenTexCoordinateBuffer, GLES20.GL_STATIC_DRAW);
    }

}

