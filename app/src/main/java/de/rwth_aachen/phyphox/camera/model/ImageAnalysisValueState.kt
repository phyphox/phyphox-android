package de.rwth_aachen.phyphox.camera.model

data class ImageAnalysisValueState(
    val currentTimeStamp: Double = 0.0,
    val luminance : Double = 0.0,
    val colorCode: String = "",
    val imageAnalysisState: ImageAnalysisState = ImageAnalysisState.IMAGE_ANALYSIS_NOT_READY
)

enum class ImageAnalysisState{
    IMAGE_ANALYSIS_NOT_READY,
    IMAGE_ANALYSIS_READY,
    IMAGE_ANALYSIS_STARTED,
    IMAGE_ANALYSIS_FINISHED,
    IMAGE_ANALYSIS_FAILED
}
