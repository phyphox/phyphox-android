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

        float n2 = (float)n*(float)n;

        fftwf_plan pa, pb, pr;
        pa = fftwf_plan_r2r_1d(n, a, a, FFTW_R2HC, FFTW_ESTIMATE);
        pb = fftwf_plan_r2r_1d(n, b, b, FFTW_R2HC, FFTW_ESTIMATE);
        fftwf_execute(pa);
        fftwf_execute(pb);
        float c, d, e, f;
        a[0] = a[0]*b[0]/n2;
        a[n/2] = a[n/2]*b[n/2]/n2;
        for (int i = 0; i < n/2; i++) {
            c = a[i];
            d = b[i];
            e = a[n-i];
            f = b[n-i];
            a[i] = (c*d + e*f)/n2;
            a[n-i] = (d*e - c*f)/n2;
        }

        pr = fftwf_plan_r2r_1d(n, a, a, FFTW_HC2R, FFTW_ESTIMATE);
        fftwf_execute(pr);

        fftwf_destroy_plan(pa);
        fftwf_destroy_plan(pb);
        fftwf_destroy_plan(pr);

        env->ReleaseFloatArrayElements(x, a, 0);
        env->ReleaseFloatArrayElements(y, b, 0);
    }

    JNIEXPORT void JNICALL Java_de_rwth_1aachen_phyphox_Analysis_fftw3autocorrelation(JNIEnv *env, jobject obj, jfloatArray x, jint n) {
        jfloat *a = env->GetFloatArrayElements(x, 0);

        fftwf_plan pa, pr;
        pa = fftwf_plan_r2r_1d(n, a, a, FFTW_R2HC, FFTW_ESTIMATE);
        fftwf_execute(pa);

        a[0] = a[0]*a[0];
        a[n/2] = a[n/2]*a[n/2];
        for (int i = 0; i < n/2; i++) {
            a[i] = a[i]*a[i] + a[n-i]*a[n-i];
            a[n-i] = 0.f;
        }

        pr = fftwf_plan_r2r_1d(n, a, a, FFTW_HC2R, FFTW_ESTIMATE);

        for (int i = 0; i < n; i++) {
            a[i] /= (float)n;
        }

        fftwf_execute(pr);

        fftwf_destroy_plan(pa);
        fftwf_destroy_plan(pr);

        env->ReleaseFloatArrayElements(x, a, 0);
    }

}