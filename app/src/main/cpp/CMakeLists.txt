cmake_minimum_required(VERSION 3.10.2)

option (BUILD_TESTS "Build tests" ON)
option (ENABLE_FLOAT "single-precision" OFF)

SET (BUILD_TESTS OFF)
SET (ENABLE_FLOAT ON)

add_subdirectory(fftw3)
add_subdirectory(analysis)


