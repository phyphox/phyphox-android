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

    static void logCurrentOutput() {
        int [] viewport = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
        int reduce = Math.max(viewport[2], viewport[3]) / 20;
        Log.d("LOGOUTPUT", "Viewport: " + viewport[0] + " " + viewport[1] + " " + viewport[2] + " " + viewport[3] + " (reduce: " + reduce + ")");

        int buffersize = viewport[2]*viewport[3];
        ByteBuffer data = ByteBuffer.allocateDirect(buffersize * 4).order(ByteOrder.nativeOrder());
        data.rewind();

        GLES20.glReadPixels(0, 0, viewport[2], viewport[3], GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        data.rewind();

        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);

        for (int y = 0; y < viewport[3]; y+=reduce) {
            String line = "";
            for (int x = 0; x < viewport[2]; x+=reduce) {
                int i = 4*(x + viewport[2]*y);
                int r = bytes[i] & 0xff;
                int g = bytes[i+1] & 0xff;
                int b = bytes[i+2] & 0xff;
                int a = bytes[i+3] & 0xff;
                line += String.format("%02x%02x%02x%02x ", r, g, b, a);
            }
            Log.d("LOGOUTPUT", line);
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

    static void deleteProgram(int program) {
        int[] count = new int[1];
        int[] shaders = new int[2];
        GLES20.glGetAttachedShaders(program, 2, count, 0, shaders, 0);
        for (int i = 0; i < count[0]; i++)
            GLES20.glDeleteShader(shaders[i]);
        GLES20.glDeleteProgram(program);
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

    //Photometric sums travel as 16-bit means, (value hi, value lo, coverage hi, coverage lo), unpacked as
    //hi + lo/255. The coarse part stays a plain 8-bit channel: a sub-LSB error of the texture filter then
    //remains a sub-LSB error, whereas a carry count multiplied back by 255 would amplify it 255-fold.
    final static String packedMeanFunctions =
            "vec2 unpackMean(vec4 s) { return vec2(s.r + s.g / 255.0, s.b + s.a / 255.0); }" +
            "vec4 packMean(vec2 vc) {" +
            "  float vh = floor(vc.x * 255.0 + 1e-4);" +
            "  float ch = floor(vc.y * 255.0 + 1e-4);" +
            "  return vec4(vh / 255.0, vc.x * 255.0 - vh, ch / 255.0, vc.y * 255.0 - ch);" +
            "}";

    //White balance by white point on the software path: the camera is held at a daylight white point and the
    //Bradford adaptation to the requested one is applied here, in linear sRGB. Outputs defined on the gamma-encoded
    //pipeline output (luma, the colour channels, HSV, the preview) go through linear space and back.
    //whiteBalance == 0 leaves the camera output untouched.
    final static String whiteBalanceFunctions =
            "uniform int whiteBalance;" +
            "uniform mat3 whiteBalanceMatrix;" +
            "float linearize(float x) {" +
            "  if (x < 0.04045) " +
            "    return x/12.92;" +
            "  else" +
            "    return pow((x+0.055)/1.055, 2.4);" +
            "}" +
            "vec3 linearize3(vec3 c) { return vec3(linearize(c.r), linearize(c.g), linearize(c.b)); }" +
            "float encode(float x) {" +
            "  if (x <= 0.0031308)" +
            "    return 12.92*x;" +
            "  else" +
            "    return 1.055*pow(x, 1.0/2.4) - 0.055;" +
            "}" +
            "vec3 encode3(vec3 c) { return vec3(encode(c.r), encode(c.g), encode(c.b)); }" +
            "vec3 balanceLinear(vec3 lin) {" +
            "  if (whiteBalance > 0)" +
            "    return clamp(whiteBalanceMatrix * lin, 0.0, 1.0);" +
            "  else" +
            "    return lin;" +
            "}" +
            "vec3 balanceGamma(vec3 g) {" +
            "  if (whiteBalance > 0)" +
            "    return encode3(clamp(whiteBalanceMatrix * linearize3(g), 0.0, 1.0));" +
            "  else" +
            "    return g;" +
            "}";

    static void setWhiteBalanceUniforms(int flagHandle, int matrixHandle) {
        float[] matrix = AnalyzingModule.whiteBalanceMatrix;
        GLES20.glUniform1i(flagHandle, matrix != null ? 1 : 0);
        if (matrix != null)
            GLES20.glUniformMatrix3fv(matrixHandle, 1, false, matrix, 0);
    }

    //A bilinear sample on the texture edge averages a real texel with its clamped copy and counts half, one
    //beyond the edge counts nothing - otherwise the edge rows and columns of a region touching the frame
    //edge would be counted several times over, once more per reduction step.
    final static String edgeWeightFunction =
            "uniform vec2 resSource;" +
            "float weight(float p, float res) {" +
            "   float halfTexel = 0.5 / res;" +
            "   return p <= 1.0 - halfTexel ? 1.0 : (p <= 1.0 + halfTexel ? 0.5 : 0.0);" +
            "}";

    //One 2D reduction step: four bilinear samples, i.e. sixteen texels, to one packed mean of value and coverage
    final static String meanDownsamplingFragmentShader =
            "precision highp float;" +
            "uniform sampler2D texture;" +
            "varying vec2 texPosition1;" +
            "varying vec2 texPosition2;" +
            "varying vec2 texPosition3;" +
            "varying vec2 texPosition4;" +
            packedMeanFunctions +
            edgeWeightFunction +
            "void main () {" +
            "   float wx1 = weight(texPosition1.x, resSource.x);" +
            "   float wx2 = weight(texPosition2.x, resSource.x);" +
            "   float wy1 = weight(texPosition1.y, resSource.y);" +
            "   float wy2 = weight(texPosition3.y, resSource.y);" +
            "   vec2 sum = wx1 * wy1 * unpackMean(texture2D(texture, texPosition1))" +
            "            + wx2 * wy1 * unpackMean(texture2D(texture, texPosition2))" +
            "            + wx1 * wy2 * unpackMean(texture2D(texture, texPosition3))" +
            "            + wx2 * wy2 * unpackMean(texture2D(texture, texPosition4));" +
            "   gl_FragColor = packMean(sum / 4.0);" +
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

    final static String interpolatingHeightFullScreenVertexShader =
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
                    "   float x1 = texCoordinates.x;" +
                    "   float y1 = (4.0 * resTarget.y * texCoordinates.y - 1.5) / resSource.y;" +
                    "   float y2 = (4.0 * resTarget.y * texCoordinates.y - 0.5) / resSource.y;" +
                    "   float y3 = (4.0 * resTarget.y * texCoordinates.y + 0.5) / resSource.y;" +
                    "   float y4 = (4.0 * resTarget.y * texCoordinates.y + 1.5) / resSource.y;" +
                    "   texPosition1 = vec2(x1, y1);" +
                    "   texPosition2 = vec2(x1, y2);" +
                    "   texPosition3 = vec2(x1, y3);" +
                    "   texPosition4 = vec2(x1, y4);" +
                    "   gl_Position = vec4(vertices, 0., 1.);" +
                    "}";

    final static String interpolatingWidthFullScreenVertexShader =
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
                    "   float y1 = texCoordinates.y;" +
                    "   float x1 = (4.0 * resTarget.x * texCoordinates.x - 1.5) / resSource.x;" +
                    "   float x2 = (4.0 * resTarget.x * texCoordinates.x - 0.5) / resSource.x;" +
                    "   float x3 = (4.0 * resTarget.x * texCoordinates.x + 0.5) / resSource.x;" +
                    "   float x4 = (4.0 * resTarget.x * texCoordinates.x + 1.5) / resSource.x;" +
                    "   texPosition1 = vec2(x1, y1);" +
                    "   texPosition2 = vec2(x2, y1);" +
                    "   texPosition3 = vec2(x3, y1);" +
                    "   texPosition4 = vec2(x4, y1);" +
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

