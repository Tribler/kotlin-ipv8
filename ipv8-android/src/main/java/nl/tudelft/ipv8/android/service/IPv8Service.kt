package nl.tudelft.ipv8.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.*
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.R

open class IPv8Service : Service(), DefaultLifecycleObserver {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var isForeground = false

    override fun onCreate() {
        super<Service>.onCreate()

        createNotificationChannel()
        showForegroundNotification()

        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        getIPv8().stop()
        scope.cancel()

        ProcessLifecycleOwner.get()
            .lifecycle
            .removeObserver(this)

        super<Service>.onDestroy()

        // We need to kill the app as IPv8 is started in Application.onCreate
        exitProcess(0)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        onBackground()
    }

    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        onForeground()
    }

    private fun onBackground() {
        isForeground = false
        showForegroundNotification()
    }

    private fun onForeground() {
        isForeground = true
        showForegroundNotification()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_CONNECTION,
                getString(R.string.notification_channel_connection_title),
                importance
            )
            channel.description = getString(R.string.notification_channel_connection_description)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val cancelBroadcastIntent = Intent(this, StopIPv8Receiver::class.java)
        val flags = when {
            SDK_INT >= Build.VERSION_CODES.M -> FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
            else -> FLAG_UPDATE_CURRENT
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            cancelBroadcastIntent,
            flags
        )

        val builder = createNotification()

        // Allow cancellation when the app is running in background
        if (!isForeground) {
            builder.addAction(NotificationCompat.Action(0, "Stop", cancelPendingIntent))
        }

        startForeground(
            ONGOING_NOTIFICATION_ID,
            builder.build()
        )
    }

    /**
     * Creates a notification that will be shown when the IPv8 service is running.
     */
    protected open fun createNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_CONNECTION)
            .setContentTitle("IPv8")
            .setContentText("Running")
    }

    /**
     * Returns a running IPv8 instance.
     */
    private fun getIPv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_CONNECTION = "service_notifications"
        private const val ONGOING_NOTIFICATION_ID = 1
    }
}
