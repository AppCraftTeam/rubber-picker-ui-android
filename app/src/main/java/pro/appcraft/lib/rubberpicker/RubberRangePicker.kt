package pro.appcraft.lib.rubberpicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.concurrent.ArrayBlockingQueue
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

class RubberRangePicker : View {
    private val paint: Paint by lazy {
        val tempPaint = Paint()
        tempPaint.style = Paint.Style.STROKE
        tempPaint.color = normalTrackColor
        tempPaint.strokeWidth = 5f
        tempPaint.isAntiAlias = true
        tempPaint
    }
    private var path: Path = Path()
    private var startThumbSpringAnimation: SpringAnimation? = null
    private var endThumbSpringAnimation: SpringAnimation? = null
    private var startThumbX: Float = -1f
    private var startThumbY: Float = -1f
    private var endThumbX: Float = -1f
    private var endThumbY: Float = -1f
    private val initialStartThumbXPositionQueue = ArrayBlockingQueue<Int>(1)
    private val initialEndThumbXPositionQueue = ArrayBlockingQueue<Int>(1)
    // Used to determine the start and end points of the track.
    // Useful for drawing and also for other calculations.
    private val trackStartX: Float
        get() {
            return if (drawableThumb != null) {
                setDrawableHalfWidthAndHeight()
                drawableThumbHalfWidth
            } else {
                drawableThumbRadius
            }
        }
    private val trackEndX: Float
        get() {
            return if (drawableThumb != null) {
                setDrawableHalfWidthAndHeight()
                width - drawableThumbHalfWidth
            } else {
                width - drawableThumbRadius
            }
        }
    private val trackY: Float
        get() {
            return height.toFloat() / 2
        }

    private var x1: Float = 0f
    private var y1: Float = 0f
    private var x2: Float = 0f
    private var y2: Float = 0f

    private var stretchRange: Float = -1f

    private var elasticBehavior: ElasticBehavior = ElasticBehavior.CUBIC

    private var drawableThumb: Drawable? = null
    private var drawableThumbHalfWidth: Float = 0f
    private var drawableThumbHalfHeight: Float = 0f
    private var startDrawableThumbSelected: Boolean = false
    private var endDrawableThumbSelected: Boolean = false

    private var drawableThumbRadius: Float = 0.0f
    private var normalTrackWidth: Float = 0.0f
    private var highlightTrackWidth: Float = 0.0f

    private var normalTrackColor: Int = 0
    private var highlightTrackColor: Int = 0
    private var highlightThumbOnTouchColor: Int = 0
    private var dampingRatio: Float = 0f
    private var stiffness: Float = 0f

    private var minValue: Int = 0
    private var maxValue: Int = 100

    private var onChangeListener: OnRubberRangePickerChangeListener? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context) :
            super(context) {
        init(null)
    }

    private fun init(attrs: AttributeSet?) {
        stretchRange = context.convertDpToPx(24f)
        drawableThumbRadius = context.convertDpToPx(16f)
        normalTrackWidth = context.convertDpToPx(2f)
        highlightTrackWidth = context.convertDpToPx(4f)
        normalTrackColor = Color.GRAY
        highlightTrackColor = 0xFF38ACEC.toInt()
        highlightThumbOnTouchColor = 0xFF82CAFA.toInt()
        dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
        stiffness = SpringForce.STIFFNESS_LOW

        attrs?.let {
            val typedArray =
                context.obtainStyledAttributes(attrs, R.styleable.RubberRangePicker, 0, 0)
            stretchRange = typedArray.getDimensionPixelSize(
                R.styleable.RubberRangePicker_stretchRange,
                context.convertDpToPx(24f).toInt()
            ).toFloat()
            drawableThumbRadius = typedArray.getDimensionPixelSize(
                R.styleable.RubberRangePicker_defaultThumbRadius,
                context.convertDpToPx(16f).toInt()
            ).toFloat()
            normalTrackWidth = typedArray.getDimensionPixelSize(
                R.styleable.RubberRangePicker_normalTrackWidth,
                context.convertDpToPx(2f).toInt()
            ).toFloat()
            highlightTrackWidth = typedArray.getDimensionPixelSize(
                R.styleable.RubberRangePicker_highlightTrackWidth,
                context.convertDpToPx(4f).toInt()
            ).toFloat()
            drawableThumb = typedArray.getDrawable(R.styleable.RubberRangePicker_thumbDrawable)
            normalTrackColor =
                typedArray.getColor(R.styleable.RubberRangePicker_normalTrackColor, Color.GRAY)
            highlightTrackColor =
                typedArray.getColor(
                    R.styleable.RubberRangePicker_highlightTrackColor,
                    0xFF38ACEC.toInt()
                )
            highlightThumbOnTouchColor =
                typedArray.getColor(
                    R.styleable.RubberRangePicker_highlightDefaultThumbOnTouchColor,
                    0xFF82CAFA.toInt()
                )
            dampingRatio =
                typedArray.getFloat(
                    R.styleable.RubberRangePicker_dampingRatio,
                    SpringForce.DAMPING_RATIO_HIGH_BOUNCY
                )
            stiffness = typedArray.getFloat(
                R.styleable.RubberRangePicker_stiffness,
                SpringForce.STIFFNESS_LOW
            )
            minValue = typedArray.getInt(R.styleable.RubberRangePicker_minValue, 0)
            maxValue = typedArray.getInt(R.styleable.RubberRangePicker_maxValue, 100)
            elasticBehavior =
                typedArray.getInt(R.styleable.RubberRangePicker_elasticBehavior, 1).run {
                    when (this) {
                        0 -> ElasticBehavior.LINEAR
                        1 -> ElasticBehavior.CUBIC
                        2 -> ElasticBehavior.RIGID
                        else -> ElasticBehavior.CUBIC
                    }
                }
            typedArray.recycle()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (startThumbX < trackStartX) {
            if (initialStartThumbXPositionQueue.isEmpty()) {
                startThumbX = trackStartX
            } else {
                initialStartThumbXPositionQueue.poll()?.let {
                    setCurrentStartValue(it)
                }
            }
            startThumbY = trackY
        }
        if (endThumbX < trackStartX) {
            if (initialEndThumbXPositionQueue.isEmpty()) {
                endThumbX = trackStartX + getThumbWidth()
            } else {
                initialEndThumbXPositionQueue.poll()?.let {
                    setCurrentEndValue(it)
                }
            }
            endThumbY = trackY
        }
    }

    private fun getThumbWidth(): Float {
        return if (drawableThumb != null) {
            setDrawableHalfWidthAndHeight()
            2 * drawableThumbHalfWidth
        } else {
            2 * drawableThumbRadius
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minimumHeight: Int = if (drawableThumb != null) {
            setDrawableHalfWidthAndHeight()
            (drawableThumbHalfHeight * 2).toInt()
        } else {
            (drawableThumbRadius * 2).toInt()
        }
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            measureDimension(minimumHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    private fun measureDimension(desiredSize: Int, measureSpec: Int): Int {
        var result: Int
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize
        } else {
            result = desiredSize
            if (specMode == MeasureSpec.AT_MOST) {
                result = min(result, specSize)
            }
        }

        return result
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        // Try to figure out a better way to overcome view clipping
        // Workaround since Region.Op.REPLACE won't work in Android P & above.
        // Region.Op.REPLACE also doesn't work properly (at times) even in devices below Android P.
        (parent as? ViewGroup)?.clipChildren = false
        (parent as? ViewGroup)?.clipToPadding = false

        drawTrack(canvas)
        drawThumb(canvas)
    }

    private fun drawThumb(canvas: Canvas?) {
        if (elasticBehavior == ElasticBehavior.RIGID) {
            startThumbY = trackY
            endThumbY = trackY
        }
        if (drawableThumb != null) {
            canvas?.let {
                canvas.translate(startThumbX, startThumbY)
                drawableThumb?.draw(canvas)
                canvas.translate(endThumbX, endThumbY)
                drawableThumb?.draw(canvas)
            }
        } else {
            drawThumbCircles(canvas, startThumbX, startThumbY, startDrawableThumbSelected)
            drawThumbCircles(canvas, endThumbX, endThumbY, endDrawableThumbSelected)
        }
    }

    private fun drawThumbCircles(
        canvas: Canvas?,
        posX: Float,
        posY: Float,
        thumbSelected: Boolean
    ) {
        paint.color = highlightTrackColor
        paint.style = Paint.Style.FILL
        canvas?.drawCircle(posX, posY, drawableThumbRadius, paint)
        if (thumbSelected) {
            paint.color = highlightThumbOnTouchColor
        } else {
            paint.color = highlightTrackColor
        }
        canvas?.drawCircle(posX, posY, drawableThumbRadius - highlightTrackWidth, paint)
        paint.style = Paint.Style.STROKE
    }

    private fun drawTrack(canvas: Canvas?) {
        if (startThumbY == trackY && endThumbY == trackY) {
            drawRigidTrack(canvas)
            return
        }

        path.reset()
        path.moveTo(0f, trackY)

        when (elasticBehavior) {
            ElasticBehavior.LINEAR -> drawLinearTrack(canvas)
            ElasticBehavior.CUBIC -> drawBezierTrack(canvas)
            ElasticBehavior.RIGID -> drawRigidTrack(canvas)
        }
    }

    private fun drawRigidTrack(canvas: Canvas?) {
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        canvas?.drawLine(0f, trackY, startThumbX, trackY, paint)
        paint.color = highlightTrackColor
        paint.strokeWidth = highlightTrackWidth
        canvas?.drawLine(startThumbX, trackY, endThumbX, trackY, paint)
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        canvas?.drawLine(endThumbX, trackY, width.toFloat(), trackY, paint)
    }

    private fun drawBezierTrack(canvas: Canvas?) {
        x1 = (startThumbX + 0) / 2
        y1 = trackY
        x2 = x1
        y2 = startThumbY
        path.cubicTo(x1, y1, x2, y2, startThumbX, startThumbY)
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        canvas?.drawPath(path, paint)

        path.reset()
        path.moveTo(startThumbX, startThumbY)
        x1 = (startThumbX + endThumbX) / 2
        y1 = startThumbY
        x2 = x1
        y2 = endThumbY
        path.cubicTo(x1, y1, x2, y2, endThumbX, endThumbY)
        paint.color = highlightTrackColor
        paint.strokeWidth = highlightTrackWidth
        canvas?.drawPath(path, paint)

        path.reset()
        path.moveTo(endThumbX, endThumbY)
        x1 = (endThumbX + width) / 2
        y1 = endThumbY
        x2 = x1
        y2 = trackY
        path.cubicTo(x1, y1, x2, y2, width.toFloat(), trackY)
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        canvas?.drawPath(path, paint)
    }

    private fun drawLinearTrack(canvas: Canvas?) {
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        path.lineTo(startThumbX, startThumbY)
        canvas?.drawPath(path, paint)

        path.reset()
        path.moveTo(startThumbX, startThumbY)
        paint.color = highlightTrackColor
        paint.strokeWidth = highlightTrackWidth
        path.lineTo(endThumbX, endThumbY)
        canvas?.drawPath(path, paint)

        path.reset()
        path.moveTo(endThumbX, endThumbY)
        paint.color = normalTrackColor
        paint.strokeWidth = normalTrackWidth
        path.lineTo(width.toFloat(), trackY)
        canvas?.drawPath(path, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onTouchEvent(event)
        }
        var thumbXReference: KMutableProperty0<Float>? = null
        var thumbYReference: KMutableProperty0<Float>? = null
        var thumbSelectedReference: KMutableProperty0<Boolean>? = null
        var thumbSpringAnimationReference: KMutableProperty0<SpringAnimation?>? = null
        var startX: Float = trackStartX
        var endX: Float = trackEndX
        if (startDrawableThumbSelected) {
            thumbXReference = this::startThumbX
            thumbYReference = this::startThumbY
            thumbSelectedReference = this::startDrawableThumbSelected
            thumbSpringAnimationReference = this::startThumbSpringAnimation
            endX = endThumbX - getThumbWidth()
        } else if (endDrawableThumbSelected) {
            thumbXReference = this::endThumbX
            thumbYReference = this::endThumbY
            thumbSelectedReference = this::endDrawableThumbSelected
            thumbSpringAnimationReference = this::endThumbSpringAnimation
            startX = startThumbX + getThumbWidth()
        }
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchPointInDrawableThumb(x, y, startThumbX, startThumbY)) {
                    thumbXReference = this::startThumbX
                    thumbYReference = this::startThumbY
                    thumbSelectedReference = this::startDrawableThumbSelected
                    thumbSpringAnimationReference = this::startThumbSpringAnimation

                } else if (isTouchPointInDrawableThumb(x, y, endThumbX, endThumbY)) {
                    thumbXReference = this::endThumbX
                    thumbYReference = this::endThumbY
                    thumbSelectedReference = this::endDrawableThumbSelected
                    thumbSpringAnimationReference = this::endThumbSpringAnimation
                }
                if (thumbXReference != null) {
                    thumbSpringAnimationReference?.get()?.cancel()
                    thumbSelectedReference?.set(true)
//                    thumbXReference.set(x.coerceHorizontal(startX, endX))
//                    thumbYReference?.set(y.coerceVertical().coerceToStretchRange(thumbXReference.get(), startX, endX))
                    onChangeListener?.onStartTrackingTouch(this, startX == trackStartX)
//                    onChangeListener?.onProgressChanged(this, getCurrentStartValue(), getCurrentEndValue(), true)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (thumbSelectedReference?.get() == true && thumbXReference != null) {
                    thumbXReference.set(
                        x
                            .coerceAtLeast(if (startX == trackStartX) trackStartX else trackStartX + getThumbWidth())
                            .coerceAtMost(if (startX == trackStartX) trackEndX - getThumbWidth() else trackEndX)
                    )
                    adjustStartEndThumbXPositions(startX == trackStartX)
                    if (endThumbX - startThumbX > getThumbWidth() - 1 && endThumbX - startThumbX < getThumbWidth() + 1) {
                        thumbYReference?.set(trackY)
                    } else {
                        thumbYReference?.set(
                            y
                                .coerceAtMost(trackY + stretchRange)
                                .coerceAtLeast(trackY - stretchRange)
                                .coerceToStretchRange(thumbXReference.get(), startX, endX)
                        )
                    }
                    onChangeListener?.onProgressChanged(
                        this,
                        getCurrentStartValue(),
                        getCurrentEndValue(),
                        true
                    )
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (thumbSelectedReference?.get() == true && thumbXReference != null && thumbYReference != null) {
                    thumbSelectedReference.set(false)
//                    thumbXReference.set(x.coerceHorizontal(startX, endX))
//                    thumbYReference.set(y.coerceVertical().coerceToStretchRange(thumbXReference.get(), startX, endX))
//                    onChangeListener?.onProgressChanged(this, getCurrentStartValue(), getCurrentEndValue(), true)
                    onChangeListener?.onStopTrackingTouch(this, startX == trackStartX)
                    thumbSpringAnimationReference?.set(
                        SpringAnimation(FloatValueHolder(trackY))
                            .setStartValue(thumbYReference.get())
                            .setSpring(
                                SpringForce(trackY)
                                    .setDampingRatio(dampingRatio)
                                    .setStiffness(stiffness)
                            )
                            .addUpdateListener { _, value, _ ->
                                thumbYReference.set(value)
                                invalidate()
                            }
                            .addEndListener { _, _, _, _ ->
                                thumbYReference.set(trackY)
                                invalidate()
                            })
                    thumbSpringAnimationReference?.get()?.start()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun adjustStartEndThumbXPositions(basedOnStartThumb: Boolean) {
        if (basedOnStartThumb) {
            endThumbX = endThumbX.coerceAtLeast(startThumbX + getThumbWidth())
        } else {
            startThumbX = startThumbX.coerceAtMost(endThumbX - getThumbWidth())
        }
    }

    private fun isTouchPointInDrawableThumb(
        x: Float,
        y: Float,
        thumbX: Float,
        thumbY: Float
    ): Boolean {
        if (drawableThumb != null) {
            drawableThumb?.let {
                setDrawableHalfWidthAndHeight()
                if (x > thumbX - drawableThumbHalfWidth && x < thumbX + drawableThumbHalfWidth &&
                    y > thumbY - drawableThumbHalfHeight && x < thumbY + drawableThumbHalfHeight
                ) {
                    return true
                }
            }
        } else {
            if ((x - thumbX) * (x - thumbX) +
                (y - thumbY) * (y - thumbY) <= drawableThumbRadius * drawableThumbRadius * 4
            ) {
                return true
            }
        }
        return false
    }

    private fun setDrawableHalfWidthAndHeight() {
        if (drawableThumbHalfWidth != 0f && drawableThumbHalfHeight != 0f) {
            return
        }
        drawableThumb?.let {
            drawableThumbHalfWidth =
                ((it.bounds.right - it.bounds.left).absoluteValue).toFloat() / 2
            drawableThumbHalfHeight =
                ((it.bounds.bottom - it.bounds.top).absoluteValue).toFloat() / 2
        }
    }

    private fun Float.coerceToStretchRange(x: Float, startX: Float, endX: Float): Float {
        return if (this <= height / 2) {
            this.coerceAtLeast(
                when {
                    x < (endX + startX) / 2 -> -(((2 * (stretchRange + height / 2) - height) * (x - startX)) / ((endX + startX) - (2 * startX))) + (height / 2)
                    else -> -(((2 * (stretchRange + height / 2) - height) * (x - endX)) / ((endX + startX) - (2 * endX))) + (height / 2)
                }
            )
        } else {
            this.coerceAtMost(
                when {
                    x < (endX + startX) / 2 -> (((2 * (stretchRange + height / 2) - height) * (x - startX)) / ((endX + startX) - (2 * startX))) + (height / 2)
                    else -> (((2 * (stretchRange + height / 2) - height) * (x - endX)) / ((endX + startX) - (2 * endX))) + (height / 2)
                }
            )
        }
    }

    //region Public functions
    /**
     * Set the Elastic Behavior for the RangePicker.
     */
    fun setElasticBehavior(elasticBehavior: ElasticBehavior) {
        this.elasticBehavior = elasticBehavior
        if (elasticBehavior == ElasticBehavior.RIGID) {
            startThumbSpringAnimation?.cancel()
            endThumbSpringAnimation?.cancel()
        }
        invalidate()
    }

    /**
     * Set the maximum Stretch Range in dp.
     */
    @Throws(IllegalArgumentException::class)
    fun setStretchRange(stretchRangeInDp: Float) {
        require(stretchRangeInDp >= 0) { "Stretch range value can not be negative" }
        this.stretchRange = context.convertDpToPx(stretchRangeInDp)
        invalidate()
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setThumbRadius(dpValue: Float) {
        require(dpValue > 0) { "Thumb radius must be non-negative" }
        check(drawableThumb == null) { "Thumb radius can not be set when drawable is used as thumb" }
        val oldY = trackY
        val oldStartThumbX = getCurrentStartValue()
        val oldEndThumbX = getCurrentEndValue()
        drawableThumbRadius = context.convertDpToPx(dpValue)
        setCurrentStartValue(oldStartThumbX)
        startThumbY = (startThumbY * drawableThumbRadius) / oldY
        if (startThumbSpringAnimation?.isRunning == true) startThumbSpringAnimation?.animateToFinalPosition(
            drawableThumbRadius
        )
        setCurrentEndValue(oldEndThumbX)
        endThumbY = (endThumbY * drawableThumbRadius) / oldY
        if (endThumbSpringAnimation?.isRunning == true) endThumbSpringAnimation?.animateToFinalPosition(
            drawableThumbRadius
        )
        invalidate()
        requestLayout()
    }

    fun setNormalTrackWidth(dpValue: Float) {
        normalTrackWidth = context.convertDpToPx(dpValue)
        invalidate()
    }

    fun setHighlightTrackWidth(dpValue: Float) {
        highlightTrackWidth = context.convertDpToPx(dpValue)
        invalidate()
    }

    fun setNormalTrackColor(value: Int) {
        normalTrackColor = value
        invalidate()
    }

    fun setHighlightTrackColor(value: Int) {
        highlightTrackColor = value
        invalidate()
    }

    fun setHighlightThumbOnTouchColor(value: Int) {
        highlightThumbOnTouchColor = value
        invalidate()
    }

    @Throws(java.lang.IllegalArgumentException::class)
    fun setDampingRatio(value: Float) {
        require(value >= 0.0f) { "Damping ratio must be non-negative" }
        dampingRatio = value
        startThumbSpringAnimation?.spring?.dampingRatio = dampingRatio
        endThumbSpringAnimation?.spring?.dampingRatio = dampingRatio
        if (startThumbSpringAnimation?.isRunning == true) startThumbSpringAnimation?.animateToFinalPosition(
            trackY
        )
        if (endThumbSpringAnimation?.isRunning == true) endThumbSpringAnimation?.animateToFinalPosition(
            trackY
        )
        invalidate()
    }

    @Throws(java.lang.IllegalArgumentException::class)
    fun setStiffness(value: Float) {
        require(value > 0.0f) { "Spring stiffness constant must be positive" }
        stiffness = value
        startThumbSpringAnimation?.spring?.stiffness = stiffness
        endThumbSpringAnimation?.spring?.stiffness = stiffness
        if (startThumbSpringAnimation?.isRunning == true) startThumbSpringAnimation?.animateToFinalPosition(
            trackY
        )
        if (endThumbSpringAnimation?.isRunning == true) endThumbSpringAnimation?.animateToFinalPosition(
            trackY
        )
        invalidate()
    }

    @Throws(java.lang.IllegalArgumentException::class)
    fun setMin(value: Int) {
        require(value < maxValue) { "Min value must be smaller than max value" }
        val oldStartValue = getCurrentStartValue()
        val oldEndValue = getCurrentEndValue()
        minValue = value
        if (minValue > oldStartValue) {
            setCurrentEndValue(oldEndValue)
            setCurrentStartValue(minValue)
        } else {
            setCurrentEndValue(oldEndValue)
            setCurrentStartValue(oldStartValue)
        }
    }

    @Throws(java.lang.IllegalArgumentException::class)
    fun setMax(value: Int) {
        require(value > minValue) { "Max value must be greater than min value" }
        val oldStartValue = getCurrentStartValue()
        val oldEndValue = getCurrentEndValue()
        maxValue = value
        if (maxValue < oldEndValue) {
            setCurrentStartValue(oldStartValue)
            setCurrentEndValue(maxValue)
        } else {
            setCurrentStartValue(oldStartValue)
            setCurrentEndValue(oldEndValue)
        }
    }

    fun getCurrentStartValue(): Int {
        if (startThumbX <= trackStartX) {
            return minValue
        } else if (startThumbX >= trackEndX - getThumbWidth()) {
            return maxValue
        }
        return (((startThumbX - trackStartX) / ((trackEndX - getThumbWidth()) - trackStartX)) * (maxValue - minValue)).roundToInt() + minValue
    }

    fun setCurrentStartValue(value: Int) {
        val validValue = value.coerceAtLeast(minValue).coerceAtMost(maxValue)
        if (trackEndX < 0) {
            //If this function gets called before the view gets layed out and learns what it's width value is
            initialStartThumbXPositionQueue.offer(validValue)
            return
        }
        startThumbX =
            (((validValue - minValue).toFloat() / (maxValue - minValue)) * ((trackEndX - getThumbWidth()) - trackStartX)) + trackStartX
        adjustStartEndThumbXPositions(true)
        onChangeListener?.onProgressChanged(
            this,
            getCurrentStartValue(),
            getCurrentEndValue(),
            false
        )
        invalidate()
    }

    fun getCurrentEndValue(): Int {
        if (endThumbX <= trackStartX + getThumbWidth()) {
            return minValue
        } else if (endThumbX >= trackEndX) {
            return maxValue
        }
        return (((endThumbX - (trackStartX + getThumbWidth())) / (trackEndX - (trackStartX + getThumbWidth()))) * (maxValue - minValue)).roundToInt() + minValue
    }

    fun setCurrentEndValue(value: Int) {
        val validValue = value.coerceAtLeast(minValue).coerceAtMost(maxValue)
        if (trackEndX < 0) {
            //If this function gets called before the view gets layed out and learns what it's width value is
            initialEndThumbXPositionQueue.offer(validValue)
            return
        }
        endThumbX =
            (((validValue - minValue).toFloat() / (maxValue - minValue)) * (trackEndX - (trackStartX + getThumbWidth()))) + (trackStartX + getThumbWidth())
        adjustStartEndThumbXPositions(false)
        onChangeListener?.onProgressChanged(
            this,
            getCurrentStartValue(),
            getCurrentEndValue(),
            false
        )
        invalidate()
    }

    fun setOnRubberRangePickerChangeListener(listener: OnRubberRangePickerChangeListener) {
        onChangeListener = listener
    }
    //endregion

    //region Interfaces
    /**
     * Based on the RubberSeekBar.onSeekBarChangeListener
     */
    interface OnRubberRangePickerChangeListener {
        fun onProgressChanged(
            rangePicker: RubberRangePicker,
            startValue: Int,
            endValue: Int,
            fromUser: Boolean
        )

        fun onStartTrackingTouch(rangePicker: RubberRangePicker, isStartThumb: Boolean)
        fun onStopTrackingTouch(rangePicker: RubberRangePicker, isStartThumb: Boolean)
    }
    //endregion

    fun getMin(): Int {
        return minValue
    }

    fun getMax(): Int {
        return maxValue
    }
}