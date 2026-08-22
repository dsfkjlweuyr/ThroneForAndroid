package io.nekohasekai.sagernet.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R

fun Context.alert(text: String): AlertDialog {
    return MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .create()
}

fun Fragment.alert(text: String) = requireContext().alert(text)

internal fun Context.resolveActivity(): WrappedHostResolution<Activity> = resolveWrappedHost(
    initial = this,
    hostOrNull = { it as? Activity },
    baseOrNull = { (it as? ContextWrapper)?.baseContext },
)

fun AlertDialog.tryToShow() {
    val initialContext = context
    val resolution = initialContext.resolveActivity()
    val activity = resolution.host
    if (activity == null) {
        Logs.w(
            "AlertDialog.tryToShow skipped: no Activity host; " +
                    "initialContext=${initialContext.javaClass.name}, " +
                    "wrapperDepth=${resolution.wrapperDepth}, " +
                    "loopDetected=${resolution.loopDetected}"
        )
        return
    }
    if (activity.isFinishing) {
        Logs.w(
            "AlertDialog.tryToShow skipped: Activity is finishing; " +
                    "initialContext=${initialContext.javaClass.name}, " +
                    "wrapperDepth=${resolution.wrapperDepth}, " +
                    "activity=${activity.javaClass.name}"
        )
        return
    }
    if (activity.isDestroyed) {
        Logs.w(
            "AlertDialog.tryToShow skipped: Activity is destroyed; " +
                    "initialContext=${initialContext.javaClass.name}, " +
                    "wrapperDepth=${resolution.wrapperDepth}, " +
                    "activity=${activity.javaClass.name}"
        )
        return
    }

    Logs.i(
        "AlertDialog.tryToShow resolved host: " +
                "initialContext=${initialContext.javaClass.name}, " +
                "wrapperDepth=${resolution.wrapperDepth}, " +
                "activity=${activity.javaClass.name}"
    )
    try {
        show()
    } catch (e: Exception) {
        Logs.e("AlertDialog.tryToShow failed while showing on a resolved Activity", e)
    }
}
