#include <jni.h>
#include <math.h>
#include "../fftw3/api/fftw3.h"

extern "C" {

    JNIEXPORT void JNICALL Java_de_rwth_1aachen_phyphox_Analysis_nativePower(JNIEnv *env, jobject obj, jdoubleArray a, jdoubleArray b) {
        int n = env->GetArrayLength(a);
        int m = env->GetArrayLength(b);
        int nm;
        if (n > m)
            nm = n;
        else
            nm = m;
        jdouble *x = env->GetDoubleArrayElements(a, 0);
        jdouble *y = env->GetDoubleArrayElements(b, 0);

        double base = 0.;
        double exponent = 1.;
        for (int i = 0; i < nm; i++) {
            if (i < n)
                base = x[i];
            if (i < m)
                exponent = y[i];
            if (n > m)
                x[i] = pow(base, exponent);
            else
                y[i] = pow(base, exponent);
        }

        env->ReleaseDoubleArrayElements(a, x, 0);
        env->ReleaseDoubleArrayElements(b, y, 0);
    }

    JNIEXPORT void JNICALL Java_de_rwth_1aachen_phyphox_Analysis_fftw3complex(JNIEnv *env, jobject obj, jfloatArray xy, jint n) {
        jfloat *a = env->GetFloatArrayElements(xy, 0);
        fftwf_complex *fftwa = (fftwf_complex*)a;

        fftwf_plan p;
        p = fftwf_plan_dft_1d(n, fftwa, fftwa, FFTW_FORWARD, FFTW_ESTIMATE);
        fftwf_execute(p);
        fftwf_destroy_plan(p);

        env->ReleaseFloatArrayElements(xy, a, 0);
    }

    JNIEXPORT void JNICALL Java_de_rwth_1aachen_phyphox_Analysis_fftw3crosscorrelation(JNIEnv *env, jobject obj, jfloatArray x, jfloatArray y, jint n) {
        jfloat *a = env->GetFloatArrayElements(x, 0);
        jfloat *b = env->GetFloatArrayElements(y, 0);

        //FFTW's transforms are unnormalized: R2HC followed by HC2R scales the data by n, so
        //the spectral product is divided by n exactly once. The result then is the raw
        //correlation sum without any further normalization (see the crosscorrelation docs).
        float norm = (float)n;

        fftwf_plan pa, pb, pr;
        pa = fftwf_plan_r2r_1d(n, a, a, FFTW_R2HC, FFTW_ESTIMATE);
        pb = fftwf_plan_r2r_1d(n, b, b, FFTW_R2HC, FFTW_ESTIMATE);
        fftwf_execute(pa);
        fftwf_execute(pb);
        //Halfcomplex layout for even n: real parts at [0..n/2], imaginary parts at [n-1..n/2+1].
        //Bins 0 and n/2 are purely real and handled outside the loop - the loop must start at 1:
        //at i = 0 it would read a[n] and b[n] out of bounds and corrupt bin 0 with the result,
        //which offsets every value of the inverse transform.
        float c, d, e, f;
        a[0] = a[0]*b[0]/norm;
        a[n/2] = a[n/2]*b[n/2]/norm;
        for (int i = 1; i < n/2; i++) {
            c = a[i];
            d = b[i];
            e = a[n-i];
            f = b[n-i];
            a[i] = (c*d + e*f)/norm;
            a[n-i] = (d*e - c*f)/norm;
        }

        pr = fftwf_plan_r2r_1d(n, a, a, FFTW_HC2R, FFTW_ESTIMATE);
        fftwf_execute(pr);

        fftwf_destroy_plan(pa);
        fftwf_destroy_plan(pb);
        fftwf_destroy_plan(pr);

        env->ReleaseFloatArrayElements(x, a, 0);
        env->ReleaseFloatArrayElements(y, b, 0);
    }

}