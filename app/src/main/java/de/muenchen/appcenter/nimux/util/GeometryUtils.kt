package de.muenchen.appcenter.nimux.util

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import kotlin.math.hypot

object GeometryUtils {

    fun distancePointToLine(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        if (l2 == 0f) return hypot(px - x1, py - y1)

        var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
        t = Math.max(0f, Math.min(1f, t))

        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)

        return hypot(px - projX, py - projY)
    }

    fun isPointInPath(x: Float, y: Float, path: Path): Boolean {
        val rectF = RectF()
        path.computeBounds(rectF, true)
        val region = Region()
        region.setPath(path, Region(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt()))
        return region.contains(x.toInt(), y.toInt())
    }
}