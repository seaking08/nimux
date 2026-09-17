package de.muenchen.appcenter.nimux.view.main.warehouse

import de.muenchen.appcenter.nimux.model.warehouse.Pillar
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Wall
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse

class WarehouseState {
    var warehouse: Warehouse? = null
    var isEditMode: Boolean = false

    var selectedShelfForEditing: Shelf? = null
    var selectedWallForEditing: Wall? = null

    var draggedShelf: Shelf? = null
    var draggedWall: Wall? = null
    var draggedPillar: Pillar? = null
    var isDraggingDock = false
    var isRotating = false

    var activeCornerHandle: WarehouseMap.CornerHandle? = null
    var activeEdgeHandle: WarehouseMap.EdgeHandle? = null
    var activeWallNode: WarehouseMap.WallNode? = null

    fun resetInteractions() {
        draggedShelf = null
        draggedPillar = null
        isDraggingDock = false
        activeCornerHandle = null
        activeEdgeHandle = null
        isRotating = false
        activeWallNode = null
        draggedWall = null
    }
}