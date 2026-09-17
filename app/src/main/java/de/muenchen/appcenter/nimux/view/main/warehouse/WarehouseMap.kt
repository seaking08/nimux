package de.muenchen.appcenter.nimux.view.main.warehouse

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse

class WarehouseMap @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class CornerHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }
    enum class EdgeHandle { TOP, RIGHT, BOTTOM, LEFT }
    enum class WallNode { START, END }

    private val state = WarehouseState()
    private val renderer = WarehouseRenderer()
    private val touchHandler = WarehouseTouchHandler(this, state, renderer)

    var isEditMode: Boolean
        get() = state.isEditMode
        set(value) {
            state.isEditMode = value
            if (!value) {
                state.resetInteractions()
                state.selectedShelfForEditing = null
                state.selectedWallForEditing = null
                invalidate()
            }
        }

    fun setWarehouseData(newWarehouse: Warehouse) {
        state.warehouse = newWarehouse
        invalidate()
    }

    fun setOnShelfClickListener(listener: (Shelf) -> Unit) {
        touchHandler.onShelfClickListener = listener
    }

    fun setOnStateChangeStartedListener(listener: () -> Unit) {
        touchHandler.onStateChangeStarted = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas, state, width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = touchHandler.handleTouchEvent(event)
        return if (handled) true else super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}