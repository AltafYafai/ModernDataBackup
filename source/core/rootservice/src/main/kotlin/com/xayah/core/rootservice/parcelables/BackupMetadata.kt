package com.xayah.core.rootservice.parcelables
import android.os.Parcel
import android.os.Parcelable
data class BackupMetadata(
    val appPackage: String,
    val appLabel: String,
    val dataSize: Long,
    val timestamp: Long,
    val isSystemApp: Boolean
) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(appPackage)
        parcel.writeString(appLabel)
        parcel.writeLong(dataSize)
        parcel.writeLong(timestamp)
        parcel.writeInt(if (isSystemApp) 1 else 0)
    }
    companion object CREATOR : Parcelable.Creator<BackupMetadata> {
        override fun createFromParcel(parcel: Parcel): BackupMetadata = BackupMetadata(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readLong(),
            parcel.readLong(),
            parcel.readInt() != 0
        )
        override fun newArray(size: Int): Array<BackupMetadata?> = arrayOfNulls(size)
    }
}
