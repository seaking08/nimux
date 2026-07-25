package de.muenchen.appcenter.nimux.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class Role(
    var name: String = "",
    var superRole: Boolean = false,
) : Parcelable