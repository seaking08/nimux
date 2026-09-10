package de.muenchen.appcenter.nimux.model.warehouse

import android.graphics.PointF

data class Shelf(
    var name: String = "",
    var topLeft: PointF = PointF(0f, 0f),
    var topRight: PointF = PointF(0f, 0f),
    var bottomRight: PointF = PointF(0f, 0f),
    var bottomLeft: PointF = PointF(0f, 0f),
    var columnSize: Int = 0,
    var rowSize: Int = 0,
    var items: List<ShelfItem> = emptyList())