#include <jni.h>
#include "../fftw3/api/fftw3.h"

extern "C" {

    JNIEXPORT void JNICALL Java_de_rwth_1aachen_phyphox_Analysis_fftw3complex(JNIEnv *env, jobject obj, jfloatArray xy, jint n) {
        jfloat *a = env->GetFloatArrayElements(xy, 0);
        fftwf_complex *fftwa = (fftwf_complex*)a;

        fftwf_plan p;
        p = fftwf_plan_dft_1d(n, fftwa, fftwa, FFTW_FORWARD, FFTW_ESTIMATE);
        fftwf_execute(p);
        fftwf_destroy_plan(p);

        env->ReleaseFloatArrayElements(xy, a, 0);
    }

}