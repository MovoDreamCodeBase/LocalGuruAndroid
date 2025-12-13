package com.core.customviews



import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private val imageMatrixObj = Matrix()
    private val lastPoint = PointF()
    private var scaleFactor = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 4.5f)

                imageMatrixObj.postScale(
                    detector.scaleFactor,
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )

                imageMatrix = imageMatrixObj
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint.set(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastPoint.x
                val dy = event.y - lastPoint.y

                imageMatrixObj.postTranslate(dx, dy)
                imageMatrix = imageMatrixObj

                lastPoint.set(event.x, event.y)
            }
        }
        return true
    }
}
