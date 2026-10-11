package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.os.Parcel
import android.os.Parcelable

/** Parcel round-trip gives vendor containers independent nested Location instances. */
internal fun copyLocationPayload(value: Any): Any? = when (value) {
    is Location -> Location(value)
    is List<*> -> value.map { if (it is Location) Location(it) else it }
    is Parcelable -> {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val creator = value.javaClass.getField("CREATOR").get(null) as Parcelable.Creator<*>
            creator.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }
    else -> null
}
