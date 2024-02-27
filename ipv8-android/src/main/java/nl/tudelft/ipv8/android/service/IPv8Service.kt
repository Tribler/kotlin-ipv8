package nl.tudelft.ipv8.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.R
import kotlin.system.exitProcess

open class IPv8Service : Service(), LifecycleObserver {
    protected val scope = CoroutineScope(Dispatchers.Default)

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()

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

        super.onDestroy()

        // We need to kill the app as IPv8 is started in Application.onCreate
        exitProcess(0)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onBackground() {
        isForeground = false
        updateNotification()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onForeground() {
        isForeground = true
        updateNotification()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_CONNECTION,
                    getString(R.string.notification_channel_connection_title),
                    importance,
                )
            channel.description = getString(R.string.notification_channel_connection_description)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val builder = createNotification()

        // Allow cancellation when the app is running in background.
        // Should technically not be triggered.
        if (!isForeground) {
            addStopAction(builder)
        }

        if (SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                builder.build(),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                builder.build(),
            )
        }
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
     * Updates the notification to show the current state of the IPv8 service.
     */
    protected fun updateNotification() {
        val builder = createNotification()

        // Allow cancellation when the app is running in background.
        if (!isForeground) {
            addStopAction(builder)
        }

        val mNotificationManager =
            getSystemService<NotificationManager>()
        mNotificationManager?.notify(ONGOING_NOTIFICATION_ID, builder.build())
    }

    /**
     * Adds a stop action to the notification.
     */
    private fun addStopAction(builder: NotificationCompat.Builder) {
        val cancelBroadcastIntent = Intent(this, StopIPv8Receiver::class.java)
        val flags =
            when {
                SDK_INT >= Build.VERSION_CODES.M -> FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                else -> FLAG_UPDATE_CURRENT
            }
        val cancelPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                cancelBroadcastIntent,
                flags,
            )
        builder.addAction(NotificationCompat.Action(0, "Stop", cancelPendingIntent))
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
