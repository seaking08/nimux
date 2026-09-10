package de.muenchen.appcenter.nimux.model.warehouse

import android.graphics.PointF

enum class DockOrientation { HORIZONTAL, VERTICAL }

data class Pillar(
    var position: PointF = PointF(0f,0f)
)

data class Warehouse(
    val name: String = "",
    val length: Int = 0,
    val width: Int = 0,
    val shelves: MutableList<Shelf> = mutableListOf(),
    val pillars: MutableList<Pillar> = mutableListOf(),
    var dockPosition: PointF = PointF(150f, 0f),
    var dockOrientation: DockOrientation = DockOrientation.HORIZONTAL)