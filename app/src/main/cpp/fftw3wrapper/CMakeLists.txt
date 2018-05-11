add_library(fftw3wrapper SHARED
	        fftw3wrapper.cpp)

target_link_libraries(fftw3wrapper
                      fftw3f)
