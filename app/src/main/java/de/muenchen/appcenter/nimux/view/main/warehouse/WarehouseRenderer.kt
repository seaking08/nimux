package de.muenchen.appcenter.nimux.view.main.warehouse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import de.muenchen.appcenter.nimux.model.warehouse.DockOrientation
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import de.muenchen.appcenter.nimux.model.warehouse.Shelf

class WarehouseRenderer {
    // --- PAINTS ---
    private val edgeHandlePaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
    private val wallPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 12f }
    private val shelfPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private val selectedShelfPaint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL }
    private val shelfBorderPaint = Paint().apply { color = Color.BLACK; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val pillarPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }
    private val pillarBorderPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val dockPaint = Paint().apply { color = Color.parseColor("#B0BEC5"); style = Paint.Style.FILL }
    private val activeDockPaint = Paint().apply { color = Color.parseColor("#90CAF9"); style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.BLACK; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val namePaint = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val handlePaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val rotatePaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val handleBorderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val deleteHandlePaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    private val xIconPaint = Paint().apply { color = Color.WHITE; strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    private val internalWallPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
    }

    private val dockRect = RectF()

    val shelfPaths = mutableMapOf<Shelf, Path>()

    fun draw(canvas: Canvas, state: WarehouseState, viewWidth: Int, viewHeight: Int) {
        val warehouse = state.warehouse ?: return
        shelfPaths.clear()

        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), wallPaint)

        drawDock(warehouse, canvas, state)
        drawPillars(warehouse, canvas)
        drawWalls(warehouse, canvas, state)
        drawShelves(warehouse, canvas, state)
    }

    private fun drawDock(currentWarehouse: Warehouse, canvas: Canvas, state: WarehouseState) {
        val isVertical = currentWarehouse.dockOrientation == DockOrientation.VERTICAL
        val dockWidth = if (isVertical) 30f else 150f
        val dockHeight = if (isVertical) 150f else 30f
        val x = currentWarehouse.dockPosition.x
        val y = currentWarehouse.dockPosition.y

        dockRect.set(x, y, x + dockWidth, y + dockHeight)

        val paintToUse = if (state.isEditMode) activeDockPaint else dockPaint
        canvas.drawRect(dockRect, paintToUse)

        canvas.save()
        val cx = x + dockWidth / 2f
        val cy = y + dockHeight / 2f
        if (isVertical) canvas.rotate(90f, cx, cy)
        canvas.drawText("Türe", cx, cy + 10f, textPaint)
        canvas.restore()
    }

    private fun drawPillars(currentWarehouse: Warehouse, canvas: Canvas) {
        currentWarehouse.pillars.forEach { pillar ->
            val px = pillar.position.x
            val py = pillar.position.y
            canvas.drawRect(px - 20f, py - 20f, px + 20f, py + 20f, pillarPaint)
            canvas.drawRect(px - 20f, py - 20f, px + 20f, py + 20f, pillarBorderPaint)
        }
    }

    private fun drawWalls(currentWarehouse: Warehouse, canvas: Canvas, state: WarehouseState) {
        currentWarehouse.walls.forEach { wall ->
            val paint = if (wall == state.selectedWallForEditing) Paint(internalWallPaint).apply { color = Color.MAGENTA } else internalWallPaint
            canvas.drawLine(wall.start.x, wall.start.y, wall.end.x, wall.end.y, paint)
        }

        state.selectedWallForEditing?.let { wall ->
            listOf(wall.start, wall.end).forEach { p ->
                canvas.drawCircle(p.x, p.y, 25f, handlePaint)
                canvas.drawCircle(p.x, p.y, 25f, handleBorderPaint)
            }
        }
    }

    private fun drawShelves(currentWarehouse: Warehouse, canvas: Canvas, state: WarehouseState) {
        currentWarehouse.shelves.forEach { shelf ->
            val path = Path().apply {
                moveTo(shelf.topLeft.x, shelf.topLeft.y)
                lineTo(shelf.topRight.x, shelf.topRight.y)
                lineTo(shelf.bottomRight.x, shelf.bottomRight.y)
                lineTo(shelf.bottomLeft.x, shelf.bottomLeft.y)
                close()
            }
            shelfPaths[shelf] = path

            val paint = if (shelf == state.selectedShelfForEditing) selectedShelfPaint else shelfPaint
            canvas.drawPath(path, paint)
            canvas.drawPath(path, shelfBorderPaint)

            val cx = (shelf.topLeft.x + shelf.bottomRight.x) / 2f
            val cy = (shelf.topLeft.y + shelf.bottomRight.y) / 2f
            canvas.drawText(shelf.name, cx, cy + 10f, namePaint)
        }

        state.selectedShelfForEditing?.let { shelf ->
            listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft).forEach { p ->
                canvas.drawCircle(p.x, p.y, 25f, handlePaint)
                canvas.drawCircle(p.x, p.y, 25f, handleBorderPaint)
            }

            val midTopX = (shelf.topLeft.x + shelf.topRight.x) / 2f
            val midTopY = (shelf.topLeft.y + shelf.topRight.y) / 2f
            val midRightX = (shelf.topRight.x + shelf.bottomRight.x) / 2f
            val midRightY = (shelf.topRight.y + shelf.bottomRight.y) / 2f
            val midBottomX = (shelf.bottomRight.x + shelf.bottomLeft.x) / 2f
            val midBottomY = (shelf.bottomRight.y + shelf.bottomLeft.y) / 2f
            val midLeftX = (shelf.bottomLeft.x + shelf.topLeft.x) / 2f
            val midLeftY = (shelf.bottomLeft.y + shelf.topLeft.y) / 2f

            listOf(
                Pair(midTopX, midTopY), Pair(midRightX, midRightY),
                Pair(midBottomX, midBottomY), Pair(midLeftX, midLeftY)
            ).forEach { (x, y) ->
                canvas.drawCircle(x, y, 20f, edgeHandlePaint)
                canvas.drawCircle(x, y, 20f, handleBorderPaint)
            }

            val rotY = midTopY - 40f
            canvas.drawLine(midTopX, midTopY, midTopX, rotY, handleBorderPaint)
            canvas.drawCircle(midTopX, rotY, 25f, rotatePaint)
            canvas.drawCircle(midTopX, rotY, 25f, handleBorderPaint)

            val delX = shelf.topRight.x + 40f
            val delY = shelf.topRight.y - 40f

            canvas.drawCircle(delX, delY, 25f, deleteHandlePaint)
            canvas.drawCircle(delX, delY, 25f, handleBorderPaint)

            canvas.drawLine(delX - 10f, delY - 10f, delX + 10f, delY + 10f, xIconPaint)
            canvas.drawLine(delX - 10f, delY + 10f, delX + 10f, delY - 10f, xIconPaint)
        }
    }
}