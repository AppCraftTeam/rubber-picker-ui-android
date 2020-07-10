package pro.appcraft.lib.rubberpicker

import android.content.Context
import android.util.TypedValue

fun Context.convertDpToPx(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )
}
