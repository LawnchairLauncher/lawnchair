package ch.deletescape.lawnchair.launcherclient

import android.os.Parcel
import android.os.Parcelable
import android.view.WindowManager

class WindowLayoutParams private constructor() : Parcelable {
    lateinit var layoutParams: WindowManager.LayoutParams

    constructor(layoutParams: WindowManager.LayoutParams) : this() {
        this.layoutParams = layoutParams
    }

    constructor(parcel: Parcel) : this() {
        readFromParcel(parcel)
    }

    fun readFromParcel(parcel: Parcel) {
        layoutParams = parcel.readParcelable(WindowManager.LayoutParams::class.java.classLoader)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(layoutParams, flags)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<WindowLayoutParams> {
        override fun createFromParcel(parcel: Parcel): WindowLayoutParams {
            return WindowLayoutParams(parcel)
        }

        override fun newArray(size: Int): Array<WindowLayoutParams?> {
            return arrayOfNulls(size)
        }
    }

}