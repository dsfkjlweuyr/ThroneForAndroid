package moe.matsuri.nb4a.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ui.MainActivity

class ConnectionTestNotification(val context: Context, val title: String) {
    private val channelId = "connection-test"
    private val notificationId = 1001

    fun updateNotification(progress: Int, max: Int, finished: Boolean, detail: String? = null) {
        try {
            if (finished) {
                SagerNet.notification.cancel(notificationId)
                return
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_throne_tile)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setContentText(detail ?: "$progress / $max")
                .setStyle(detail?.let { NotificationCompat.BigTextStyle().bigText(it) })
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setProgress(max, progress, false)
            SagerNet.notification.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}
