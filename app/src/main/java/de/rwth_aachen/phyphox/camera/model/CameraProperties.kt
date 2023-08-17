package de.rwth_aachen.phyphox.camera.model

import android.os.Parcel
import android.os.Parcelable
import androidx.camera.view.PreviewView

class CameraProperties() : Parcelable {

    private var cameraSettingLevel = CameraSettingLevel.BASIC

    private var exposure: Int = 0

    private var iso: Int = 0
    private var shutterSpeed : Long = 0L
    private var aperture: Float = 0.0f

    private var whiteBalanceValues : MutableList<Float> = mutableListOf()
    private var whiteBalanceMode : Int = 0

    constructor(
        cameraSettingLevel: CameraSettingLevel,
        exposure: Int
    ) : this() {
        this.cameraSettingLevel = cameraSettingLevel
        this.exposure = exposure
    }

    constructor(
        cameraSettingLevel: CameraSettingLevel,
        iso: Int,
        shutterSpeed: Long,
        aperture: Float,
        whiteBalanceValue: MutableList<Float>,
        whiteBalanceMode: Int
    ) : this() {
        this.cameraSettingLevel = cameraSettingLevel
        this.iso = iso
        this.shutterSpeed = shutterSpeed
        this.aperture = aperture
        this.whiteBalanceValues = whiteBalanceValue
        this.whiteBalanceMode  = whiteBalanceMode

    }

    constructor(parcel: Parcel) : this() {
        //cameraSettingLevel = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CameraProperties> {
        override fun createFromParcel(parcel: Parcel): CameraProperties {
            return CameraProperties(parcel)
        }

        override fun newArray(size: Int): Array<CameraProperties?> {
            return arrayOfNulls(size)
        }
    }
}
