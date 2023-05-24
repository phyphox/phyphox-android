package de.rwth_aachen.phyphox.camera.model

sealed interface PermissionState {
    object Granted : PermissionState
    data class Denied(val shouldShowRational: Boolean) : PermissionState
}
