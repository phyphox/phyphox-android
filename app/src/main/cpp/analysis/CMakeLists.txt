

add_library(analysis SHARED analysis.cpp)

target_link_libraries(analysis fftw3f)
