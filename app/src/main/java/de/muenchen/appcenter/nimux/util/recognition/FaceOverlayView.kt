package de.muenchen.appcenter.nimux.util.recognition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face
import timber.log.Timber

class FaceOverlayView(
    context: Context,
    attrs: AttributeSet?
) : View(context, attrs) {

    private var faces: List<Face> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var rotationDegrees = 0
    private var isFrontCamera = false

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun setFaces(
        faces: List<Face>,
        imgWidth: Int,
        imgHeight: Int,
        rotation: Int,
        isFront: Boolean
    ) {
        this.faces = faces
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        this.rotationDegrees = rotation
        this.isFrontCamera = isFront
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth == 0 || imageHeight == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        Timber.d("rotation:" + rotationDegrees.toString())
        val rotatedWidth =
            if (rotationDegrees == 90 || rotationDegrees == 270)
                imageHeight else imageWidth

        val rotatedHeight =
            if (rotationDegrees == 90 || rotationDegrees == 270)
                imageWidth else imageHeight

        val scale = maxOf(
            viewWidth / rotatedWidth.toFloat(),
            viewHeight / rotatedHeight.toFloat()
        )

        val scaledWidth = rotatedWidth * scale
        val scaledHeight = rotatedHeight * scale

        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f

        val matrix = Matrix()

        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        // Spiegelung
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
        }

        for (face in faces) {
            val rect = RectF(face.boundingBox)
            matrix.mapRect(rect)
            canvas.drawRect(rect, paint)
        }
    }
}