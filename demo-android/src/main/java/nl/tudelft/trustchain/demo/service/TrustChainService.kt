package nl.tudelft.trustchain.demo.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import nl.tudelft.ipv8.android.service.IPv8Service
import nl.tudelft.trustchain.demo.R
import nl.tudelft.trustchain.demo.ui.DemoActivity

class TrustChainService : IPv8Service() {
    override fun createNotification(): NotificationCompat.Builder {
        val trustChainExplorerIntent = Intent(this, DemoActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).addNextIntentWithParentStack(trustChainExplorerIntent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_CONNECTION).setContentTitle("IPv8 Demo")
            .setContentText("Running").setSmallIcon(R.drawable.ic_insert_link_black_24dp)
            .setContentIntent(pendingIntent)
    }
}
