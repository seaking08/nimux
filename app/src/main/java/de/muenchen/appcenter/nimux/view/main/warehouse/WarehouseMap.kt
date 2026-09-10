package de.muenchen.appcenter.nimux.view.main.store

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.muenchen.appcenter.nimux.model.warehouse.DockOrientation
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class WarehouseMap @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isEditMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                selectedShelfForEditing = null
                draggedShelf = null
                draggedPillar = null
                isDraggingDock = false
                activeCornerHandle = null
                isRotating = false
                invalidate()
            }
        }

    private var warehouse: Warehouse? = null
    private val shelfPaths = mutableMapOf<Shelf, Path>()
    private var onShelfClickListener: ((Shelf) -> Unit)? = null

    private var draggedShelf: Shelf? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var draggedPillar: de.muenchen.appcenter.nimux.model.warehouse.Pillar? = null
    private var isDraggingDock = false
    private var dockRect = RectF()

    private var selectedShelfForEditing: Shelf? = null
    private var activeCornerHandle: CornerHandle? = null
    private var isRotating = false

    enum class CornerHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

    private var lastClickTime = 0L
    private var lastClickedShelf: Shelf? = null

    private val wallPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }

    private val shelfPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val shelfBorderPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val pillarPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    private val pillarBorderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dockPaint = Paint().apply {
        color = Color.parseColor("#B0BEC5")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    fun setWarehouseData(newWarehouse: Warehouse) {
        this.warehouse = newWarehouse
        invalidate()
    }

    fun setOnShelfClickListener(listener: (Shelf) -> Unit) {
        this.onShelfClickListener = listener
    }

//toDo: subfunctions
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentWarehouse = warehouse ?: return

        shelfPaths.clear()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wallPaint)

        val dockX = currentWarehouse.dockPosition.x
        val dockY = currentWarehouse.dockPosition.y
        val isVertical = currentWarehouse.dockOrientation == DockOrientation.VERTICAL

        val dockWidth = if (isVertical) 30f else 150f
        val dockHeight = if (isVertical) 150f else 30f

        dockRect.set(dockX, dockY, dockX + dockWidth, dockY + dockHeight)

        val activeDockPaint = if (isEditMode) Paint(dockPaint).apply { color = Color.parseColor("#90CAF9") } else dockPaint
        canvas.drawRect(dockRect, activeDockPaint)

        canvas.save()
        val centerX = dockX + (dockWidth / 2f)
        val centerY = dockY + (dockHeight / 2f)

        if (isVertical) {
            canvas.rotate(90f, centerX, centerY)
        }
        canvas.drawText("Türe", centerX, centerY + 10f, textPaint)
        canvas.restore()

        val pillarRadius = 20f
        for (pillar in currentWarehouse.pillars) {
            canvas.drawRect(
                pillar.position.x - pillarRadius, pillar.position.y - pillarRadius,
                pillar.position.x + pillarRadius, pillar.position.y + pillarRadius,
                pillarPaint
            )
            canvas.drawRect(
                pillar.position.x - pillarRadius, pillar.position.y - pillarRadius,
                pillar.position.x + pillarRadius, pillar.position.y + pillarRadius,
                pillarBorderPaint
            )
        }

        for (shelf in currentWarehouse.shelves) {
            val path = Path().apply {
                moveTo(shelf.topLeft.x, shelf.topLeft.y)
                lineTo(shelf.topRight.x, shelf.topRight.y)
                lineTo(shelf.bottomRight.x, shelf.bottomRight.y)
                lineTo(shelf.bottomLeft.x, shelf.bottomLeft.y)
                close()
            }
            shelfPaths[shelf] = path

            val paintToUse = if (shelf == selectedShelfForEditing) {
                Paint(shelfPaint).apply { color = Color.MAGENTA }
            } else {
                shelfPaint
            }

            canvas.drawPath(path, paintToUse)
            canvas.drawPath(path, shelfBorderPaint)

            val centerX = (shelf.topLeft.x + shelf.bottomRight.x) / 2f
            val centerY = (shelf.topLeft.y + shelf.bottomRight.y) / 2f
            val namePaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(shelf.name, centerX, centerY + 10f, namePaint)
        }

        selectedShelfForEditing?.let { shelf ->
            val handlePaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
            val rotatePaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
            val handleBorder = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }

            listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft).forEach { point ->
                canvas.drawCircle(point.x, point.y, 25f, handlePaint)
                canvas.drawCircle(point.x, point.y, 25f, handleBorder)
            }

            val centerX = (shelf.topLeft.x + shelf.topRight.x) / 2f
            val centerY = (shelf.topLeft.y + shelf.topRight.y) / 2f
            val rotX = centerX
            val rotY = centerY - 40f
            canvas.drawLine(centerX, centerY, rotX, rotY, handleBorder)
            canvas.drawCircle(rotX, rotY, 25f, rotatePaint)
            canvas.drawCircle(rotX, rotY, 25f, handleBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentWarehouse = warehouse ?: return super.onTouchEvent(event)
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isEditMode) {
                    if (dockRect.contains(touchX, touchY)) {
                        isDraggingDock = true
                        lastTouchX = touchX
                        lastTouchY = touchY
                        return true
                    }

                    for (pillar in currentWarehouse.pillars) {
                        if (hypot((touchX - pillar.position.x).toDouble(), (touchY - pillar.position.y).toDouble()) <= 25f) {
                            draggedPillar = pillar
                            lastTouchX = touchX
                            lastTouchY = touchY
                            return true
                        }
                    }

                    selectedShelfForEditing?.let { shelf ->
                        val centerX = (shelf.topLeft.x + shelf.topRight.x) / 2f
                        val centerY = (shelf.topLeft.y + shelf.topRight.y) / 2f
                        val rotX = centerX
                        val rotY = centerY - 40f
                        if (hypot((touchX - rotX).toDouble(), (touchY - rotY).toDouble()) <= 40f) {
                            isRotating = true
                            lastTouchX = touchX
                            lastTouchY = touchY
                            return true
                        }

                        val handle = when {
                            hypot((touchX - shelf.topLeft.x).toDouble(), (touchY - shelf.topLeft.y).toDouble()) <= 40f -> CornerHandle.TOP_LEFT
                            hypot((touchX - shelf.topRight.x).toDouble(), (touchY - shelf.topRight.y).toDouble()) <= 40f -> CornerHandle.TOP_RIGHT
                            hypot((touchX - shelf.bottomRight.x).toDouble(), (touchY - shelf.bottomRight.y).toDouble()) <= 40f -> CornerHandle.BOTTOM_RIGHT
                            hypot((touchX - shelf.bottomLeft.x).toDouble(), (touchY - shelf.bottomLeft.y).toDouble()) <= 40f -> CornerHandle.BOTTOM_LEFT
                            else -> null
                        }
                        if (handle != null) {
                            activeCornerHandle = handle
                            return true
                        }
                    }
                }

                for ((shelf, path) in shelfPaths) {
                    if (isPointInPath(touchX, touchY, path)) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 350 && lastClickedShelf == shelf && isEditMode) {
                            selectedShelfForEditing = if (selectedShelfForEditing == shelf) null else shelf
                            invalidate()
                            return true
                        }

                        if (isEditMode) {
                            lastClickTime = currentTime
                            lastClickedShelf = shelf
                            draggedShelf = shelf
                            lastTouchX = touchX
                            lastTouchY = touchY
                        } else {
                            lastClickedShelf = shelf
                        }
                        return true
                    }
                }

                if (selectedShelfForEditing != null && isEditMode) {
                    selectedShelfForEditing = null
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isEditMode) {
                    if (isDraggingDock) {
                        val dockLength = 150f
                        val dockThickness = 30f

                        val distTop = touchY
                        val distBottom = height.toFloat() - touchY
                        val distLeft = touchX
                        val distRight = width.toFloat() - touchX

                        val minDistance = minOf(distTop, distBottom, distLeft, distRight)

                        when (minDistance) {
                            distTop -> {
                                currentWarehouse.dockOrientation = DockOrientation.HORIZONTAL
                                currentWarehouse.dockPosition.y = 0f
                                currentWarehouse.dockPosition.x = (touchX - (dockLength / 2f)).coerceIn(0f, width.toFloat() - dockLength)
                                dockRect.set(currentWarehouse.dockPosition.x, 0f, currentWarehouse.dockPosition.x + dockLength, dockThickness)
                            }
                            distBottom -> {
                                currentWarehouse.dockOrientation = DockOrientation.HORIZONTAL
                                currentWarehouse.dockPosition.y = height.toFloat() - dockThickness
                                currentWarehouse.dockPosition.x = (touchX - (dockLength / 2f)).coerceIn(0f, width.toFloat() - dockLength)
                                dockRect.set(currentWarehouse.dockPosition.x, height.toFloat() - dockThickness, currentWarehouse.dockPosition.x + dockLength, height.toFloat())
                            }
                            distLeft -> {
                                currentWarehouse.dockOrientation = DockOrientation.VERTICAL
                                currentWarehouse.dockPosition.x = 0f
                                currentWarehouse.dockPosition.y = (touchY - (dockLength / 2f)).coerceIn(0f, height.toFloat() - dockLength)
                                dockRect.set(0f, currentWarehouse.dockPosition.y, dockThickness, currentWarehouse.dockPosition.y + dockLength)
                            }
                            distRight -> {
                                currentWarehouse.dockOrientation = DockOrientation.VERTICAL
                                currentWarehouse.dockPosition.x = width.toFloat() - dockThickness
                                currentWarehouse.dockPosition.y = (touchY - (dockLength / 2f)).coerceIn(0f, height.toFloat() - dockLength)
                                dockRect.set(width.toFloat() - dockThickness, currentWarehouse.dockPosition.y, width.toFloat(), currentWarehouse.dockPosition.y + dockLength)
                            }
                        }

                        invalidate()
                        return true
                    }

                    if (draggedPillar != null) {
                        val dx = touchX - lastTouchX
                        val dy = touchY - lastTouchY
                        draggedPillar?.position?.let { pos ->
                            pos.x = (pos.x + dx).coerceIn(20f, width.toFloat() - 20f)
                            pos.y = (pos.y + dy).coerceIn(20f, height.toFloat() - 20f)
                        }
                        lastTouchX = touchX
                        lastTouchY = touchY
                        invalidate()
                        return true
                    }

                    if (isRotating) {
                        selectedShelfForEditing?.let { shelf ->
                            val centerX = (shelf.topLeft.x + shelf.bottomRight.x) / 2f
                            val centerY = (shelf.topLeft.y + shelf.bottomRight.y) / 2f

                            val angleOld = atan2((lastTouchY - centerY).toDouble(), (lastTouchX - centerX).toDouble())
                            val angleNew = atan2((touchY - centerY).toDouble(), (touchX - centerX).toDouble())
                            val deltaAngle = (angleNew - angleOld).toFloat()

                            rotateShelf(shelf, centerX, centerY, deltaAngle)
                            lastTouchX = touchX
                            lastTouchY = touchY
                            invalidate()
                            return true
                        }
                    }

                    activeCornerHandle?.let { handle ->
                        val shelf = selectedShelfForEditing ?: return@let
                        val clampedX = touchX.coerceIn(0f, width.toFloat())
                        val clampedY = touchY.coerceIn(0f, height.toFloat())

                        when (handle) {
                            CornerHandle.TOP_LEFT -> shelf.topLeft.set(clampedX, clampedY)
                            CornerHandle.TOP_RIGHT -> shelf.topRight.set(clampedX, clampedY)
                            CornerHandle.BOTTOM_RIGHT -> shelf.bottomRight.set(clampedX, clampedY)
                            CornerHandle.BOTTOM_LEFT -> shelf.bottomLeft.set(clampedX, clampedY)
                        }
                        invalidate()
                        return true
                    }

                    if (draggedShelf != null) {
                        var dx = touchX - lastTouchX
                        var dy = touchY - lastTouchY

                        draggedShelf?.let { shelf ->
                            val points = listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft)
                            var minDx = -Float.MAX_VALUE
                            var maxDx = Float.MAX_VALUE
                            var minDy = -Float.MAX_VALUE
                            var maxDy = Float.MAX_VALUE

                            for (p in points) {
                                minDx = maxOf(minDx, -p.x)
                                maxDx = minOf(maxDx, width.toFloat() - p.x)
                                minDy = maxOf(minDy, -p.y)
                                maxDy = minOf(maxDy, height.toFloat() - p.y)
                            }

                            dx = dx.coerceIn(minDx, maxDx)
                            dy = dy.coerceIn(minDy, maxDy)

                            shelf.topLeft.offset(dx, dy)
                            shelf.topRight.offset(dx, dy)
                            shelf.bottomRight.offset(dx, dy)
                            shelf.bottomLeft.offset(dx, dy)
                        }

                        lastTouchX = touchX
                        lastTouchY = touchY
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isEditMode && draggedShelf == null && draggedPillar == null && !isDraggingDock && activeCornerHandle == null && !isRotating) {
                    for ((shelf, path) in shelfPaths) {
                        if (isPointInPath(touchX, touchY, path) && lastClickedShelf == shelf) {
                            onShelfClickListener?.invoke(shelf)
                            performClick()
                            break
                        }
                    }
                }
                draggedShelf = null
                draggedPillar = null
                isDraggingDock = false
                activeCornerHandle = null
                isRotating = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun rotateShelf(shelf: Shelf, cx: Float, cy: Float, angle: Float) {
        val points = listOf(shelf.topLeft, shelf.topRight, shelf.bottomRight, shelf.bottomLeft)
        for (p in points) {
            val x = p.x - cx
            val y = p.y - cy
            val cosA = cos(angle.toDouble()).toFloat()
            val sinA = sin(angle.toDouble()).toFloat()
            p.x = cx + (x * cosA - y * sinA)
            p.y = cy + (x * sinA + y * cosA)
        }
    }

    private fun isPointInPath(x: Float, y: Float, path: Path): Boolean {
        val rect = RectF()
        path.computeBounds(rect, true)
        return rect.contains(x, y)
    }


    override fun performClick(): Boolean {
        return super.performClick()
    }
}