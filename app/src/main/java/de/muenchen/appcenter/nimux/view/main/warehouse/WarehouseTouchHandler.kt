package de.muenchen.appcenter.nimux.view.main.warehouse

import android.graphics.PointF
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import de.muenchen.appcenter.nimux.model.warehouse.DockOrientation
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import de.muenchen.appcenter.nimux.util.GeometryUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class WarehouseTouchHandler(
    private val view: View,
    private val state: WarehouseState,
    private val renderer: WarehouseRenderer
) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    var onShelfClickListener: ((Shelf) -> Unit)? = null
    var onStateChangeStarted: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val warehouse = state.warehouse ?: return false

            val clickedShelf = renderer.shelfPaths.entries.firstOrNull {
                GeometryUtils.isPointInPath(e.x, e.y, it.value)
            }?.key

            val clickedWall = warehouse.walls.firstOrNull { wall ->
                GeometryUtils.distancePointToLine(e.x, e.y, wall.start.x, wall.start.y, wall.end.x, wall.end.y) <= 30f
            }

            if (state.isEditMode) {
                if (clickedShelf == null && clickedWall == null) {
                    if (state.selectedShelfForEditing != null || state.selectedWallForEditing != null) {
                        state.selectedShelfForEditing = null
                        state.selectedWallForEditing = null
                        view.invalidate()
                        return true
                    }
                }
            } else {
                clickedShelf?.let {
                    onShelfClickListener?.invoke(it)
                    view.performClick()
                    return true
                }
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (state.isEditMode) {
                val clickedShelf = renderer.shelfPaths.entries.firstOrNull {
                    GeometryUtils.isPointInPath(e.x, e.y, it.value)
                }?.key

                if (clickedShelf != null) {
                    state.selectedShelfForEditing = if (state.selectedShelfForEditing == clickedShelf) null else clickedShelf
                    state.selectedWallForEditing = null
                    state.draggedShelf = null
                    view.invalidate()
                    return true
                }
            }
            return false
        }
    })

    fun handleTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        val warehouse = state.warehouse ?: return false
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(x, y, warehouse)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handleActionMove(x, y, warehouse)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                state.resetInteractions()
                view.invalidate()
                return true
            }
        }
        return false
    }

    private fun handleActionDown(x: Float, y: Float, warehouse: Warehouse) {
        if (!state.isEditMode) return
        updateTouchCoordinates(x, y)

        val isVertical = warehouse.dockOrientation == DockOrientation.VERTICAL
        val dockWidth = if (isVertical) 30f else 150f
        val dockHeight = if (isVertical) 150f else 30f
        val dockRect = RectF(warehouse.dockPosition.x, warehouse.dockPosition.y, warehouse.dockPosition.x + dockWidth, warehouse.dockPosition.y + dockHeight)

        if (dockRect.contains(x, y)) {
            state.isDraggingDock = true
            onStateChangeStarted?.invoke()
            return
        }

        val clickedPillar = warehouse.pillars.firstOrNull { hypot(x - it.position.x, y - it.position.y) <= 25f }
        if (clickedPillar != null) {
            state.draggedPillar = clickedPillar
            onStateChangeStarted?.invoke()
            return
        }

        state.selectedShelfForEditing?.let { shelf ->
            //delete
            val delX = shelf.topRight.x + 40f
            val delY = shelf.topRight.y - 40f
            if (hypot(x - delX, y - delY) <= 35f) {
                onStateChangeStarted?.invoke()
                warehouse.shelves.remove(shelf)
                state.selectedShelfForEditing = null
                state.resetInteractions()
                view.invalidate()
                return
            }

            //rotation
            val rotCx = (shelf.topLeft.x + shelf.topRight.x) / 2f
            val rotCy = (shelf.topLeft.y + shelf.topRight.y) / 2f - 40f
            if (hypot(x - rotCx, y - rotCy) <= 40f) {
                state.isRotating = true
                onStateChangeStarted?.invoke()
                return
            }

            //corners
            state.activeCornerHandle = when {
                hypot(x - shelf.topLeft.x, y - shelf.topLeft.y) <= 40f -> WarehouseMap.CornerHandle.TOP_LEFT
                hypot(x - shelf.topRight.x, y - shelf.topRight.y) <= 40f -> WarehouseMap.CornerHandle.TOP_RIGHT
                hypot(x - shelf.bottomRight.x, y - shelf.bottomRight.y) <= 40f -> WarehouseMap.CornerHandle.BOTTOM_RIGHT
                hypot(x - shelf.bottomLeft.x, y - shelf.bottomLeft.y) <= 40f -> WarehouseMap.CornerHandle.BOTTOM_LEFT
                else -> null
            }
            if (state.activeCornerHandle != null) {
                onStateChangeStarted?.invoke()
                return
            }

            //sides
            val midTopX = (shelf.topLeft.x + shelf.topRight.x) / 2f
            val midTopY = (shelf.topLeft.y + shelf.topRight.y) / 2f
            val midRightX = (shelf.topRight.x + shelf.bottomRight.x) / 2f
            val midRightY = (shelf.topRight.y + shelf.bottomRight.y) / 2f
            val midBottomX = (shelf.bottomRight.x + shelf.bottomLeft.x) / 2f
            val midBottomY = (shelf.bottomRight.y + shelf.bottomLeft.y) / 2f
            val midLeftX = (shelf.bottomLeft.x + shelf.topLeft.x) / 2f
            val midLeftY = (shelf.bottomLeft.y + shelf.topLeft.y) / 2f

            state.activeEdgeHandle = when {
                hypot(x - midTopX, y - midTopY) <= 35f -> WarehouseMap.EdgeHandle.TOP
                hypot(x - midRightX, y - midRightY) <= 35f -> WarehouseMap.EdgeHandle.RIGHT
                hypot(x - midBottomX, y - midBottomY) <= 35f -> WarehouseMap.EdgeHandle.BOTTOM
                hypot(x - midLeftX, y - midLeftY) <= 35f -> WarehouseMap.EdgeHandle.LEFT
                else -> null
            }
            if (state.activeEdgeHandle != null) {
                onStateChangeStarted?.invoke()
                return
            }
        }

        //Dragging
        val clickedShelf = renderer.shelfPaths.entries.firstOrNull { GeometryUtils.isPointInPath(x, y, it.value) }?.key
        if (clickedShelf != null) {
            state.draggedShelf = clickedShelf
            onStateChangeStarted?.invoke()
            return
        }

        //wall
        state.selectedWallForEditing?.let { wall ->
            state.activeWallNode = when {
                hypot(x - wall.start.x, y - wall.start.y) <= 40f -> WarehouseMap.WallNode.START
                hypot(x - wall.end.x, y - wall.end.y) <= 40f -> WarehouseMap.WallNode.END
                else -> null
            }
            if (state.activeWallNode != null) {
                onStateChangeStarted?.invoke()
                return
            }
        }

        //wall dragging
        val clickedWall = warehouse.walls.firstOrNull { wall ->
            GeometryUtils.distancePointToLine(x, y, wall.start.x, wall.start.y, wall.end.x, wall.end.y) <= 30f
        }
        if (clickedWall != null) {
            state.selectedWallForEditing = clickedWall
            state.selectedShelfForEditing = null
            state.draggedWall = clickedWall
            onStateChangeStarted?.invoke()
            view.invalidate()
            return
        }
    }

    private fun handleActionMove(x: Float, y: Float, warehouse: Warehouse): Boolean {
        if (!state.isEditMode) return false

        when {
            state.isDraggingDock -> updateDockPosition(x, y, warehouse)
            state.draggedPillar != null -> updatePillarPosition(x, y)
            state.isRotating -> updateShelfRotation(x, y)
            state.activeEdgeHandle != null -> updateEdgeResize(x, y)
            state.activeCornerHandle != null -> updateCornerResizeSymmetric(x, y)
            state.draggedWall != null -> updateWallPosition(x, y)
            state.draggedShelf != null -> updateShelfPosition(x, y)
            state.activeWallNode != null -> {
                updateWallNodePosition(x, y)
                return true
            }
            else -> return false
        }
        return true
    }

    private fun updateDockPosition(x: Float, y: Float, currentWarehouse: Warehouse) {
        val len = 150f
        val thick = 30f
        val w = view.width.toFloat()
        val h = view.height.toFloat()

        val minDistance = minOf(y, h - y, x, w - x)

        if (minDistance == y || minDistance == h - y) {
            currentWarehouse.dockOrientation = DockOrientation.HORIZONTAL
            currentWarehouse.dockPosition.x = (x - len / 2f).coerceIn(0f, w - len)
            currentWarehouse.dockPosition.y = if (minDistance == y) 0f else h - thick
        } else {
            currentWarehouse.dockOrientation = DockOrientation.VERTICAL
            currentWarehouse.dockPosition.x = if (minDistance == x) 0f else w - thick
            currentWarehouse.dockPosition.y = (y - len / 2f).coerceIn(0f, h - len)
        }
        view.invalidate()
    }

    private fun updatePillarPosition(x: Float, y: Float) {
        state.draggedPillar?.position?.apply {
            this.x = (this.x + (x - lastTouchX)).coerceIn(20f, view.width.toFloat() - 20f)
            this.y = (this.y + (y - lastTouchY)).coerceIn(20f, view.height.toFloat() - 20f)
        }
        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateShelfRotation(x: Float, y: Float) {
        state.selectedShelfForEditing?.let { shelf ->
            val cx = (shelf.topLeft.x + shelf.bottomRight.x) / 2f
            val cy = (shelf.topLeft.y + shelf.bottomRight.y) / 2f

            val angleOld = atan2(lastTouchY - cy, lastTouchX - cx)
            val angleNew = atan2(y - cy, x - cx)
            val cosA = cos(angleNew - angleOld)
            val sinA = sin(angleNew - angleOld)

            listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft).forEach { p ->
                val px = p.x - cx
                val py = p.y - cy
                p.x = cx + (px * cosA - py * sinA)
                p.y = cy + (px * sinA + py * cosA)
            }
        }
        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateCornerResizeSymmetric(x: Float, y: Float) {
        val shelf = state.selectedShelfForEditing ?: return

        val cx = (shelf.topLeft.x + shelf.bottomRight.x) / 2f
        val cy = (shelf.topLeft.y + shelf.bottomRight.y) / 2f

        val oldDist = hypot(lastTouchX - cx, lastTouchY - cy)
        val newDist = hypot(x - cx, y - cy)

        if (oldDist == 0f) return
        val scale = newDist / oldDist

        listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft).forEach { p ->
            p.x = cx + (p.x - cx) * scale
            p.y = cy + (p.y - cy) * scale
        }

        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateEdgeResize(x: Float, y: Float) {
        val shelf = state.selectedShelfForEditing ?: return
        val dx = x - lastTouchX
        val dy = y - lastTouchY

        fun moveEdge(p1: PointF, p2: PointF, edgeVectorX: Float, edgeVectorY: Float) {
            val length = hypot(edgeVectorX, edgeVectorY)
            if (length == 0f) return

            val normalX = -edgeVectorY / length
            val normalY = edgeVectorX / length

            val moveDist = (dx * normalX) + (dy * normalY)

            p1.x += normalX * moveDist
            p1.y += normalY * moveDist
            p2.x += normalX * moveDist
            p2.y += normalY * moveDist
        }

        when (state.activeEdgeHandle) {
            WarehouseMap.EdgeHandle.TOP -> moveEdge(shelf.topLeft, shelf.topRight, shelf.topRight.x - shelf.topLeft.x, shelf.topRight.y - shelf.topLeft.y)
            WarehouseMap.EdgeHandle.BOTTOM -> moveEdge(shelf.bottomRight, shelf.bottomLeft, shelf.bottomLeft.x - shelf.bottomRight.x, shelf.bottomLeft.y - shelf.bottomRight.y)
            WarehouseMap.EdgeHandle.LEFT -> moveEdge(shelf.bottomLeft, shelf.topLeft, shelf.topLeft.x - shelf.bottomLeft.x, shelf.topLeft.y - shelf.bottomLeft.y)
            WarehouseMap.EdgeHandle.RIGHT -> moveEdge(shelf.topRight, shelf.bottomRight, shelf.bottomRight.x - shelf.topRight.x, shelf.bottomRight.y - shelf.topRight.y)
            null -> {}
        }

        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateShelfPosition(x: Float, y: Float) {
        state.draggedShelf?.let { shelf ->
            val points = listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft)

            var safeDx = x - lastTouchX
            var safeDy = y - lastTouchY

            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            if (minX + safeDx < 0f) safeDx = -minX
            if (maxX + safeDx > view.width) safeDx = view.width.toFloat() - maxX
            if (minY + safeDy < 0f) safeDy = -minY
            if (maxY + safeDy > view.height) safeDy = view.height.toFloat() - maxY

            points.forEach { it.offset(safeDx, safeDy) }
        }
        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateWallPosition(x: Float, y: Float) {
        val wall = state.draggedWall ?: return
        val dx = x - lastTouchX
        val dy = y - lastTouchY

        wall.start.x += dx
        wall.start.y += dy
        wall.end.x += dx
        wall.end.y += dy

        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateWallNodePosition(x: Float, y: Float) {
        val wall = state.selectedWallForEditing ?: return
        val clampedX = x.coerceIn(0f, view.width.toFloat())
        val clampedY = y.coerceIn(0f, view.height.toFloat())

        when (state.activeWallNode) {
            WarehouseMap.WallNode.START -> wall.start.set(clampedX, clampedY)
            WarehouseMap.WallNode.END -> wall.end.set(clampedX, clampedY)
            null -> {}
        }
        updateTouchCoordinates(x, y)
        view.invalidate()
    }

    private fun updateTouchCoordinates(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
    }
}