package nl.tudelft.ipv8.android

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.android.messaging.bluetooth.BluetoothLeEndpoint
import nl.tudelft.ipv8.android.messaging.bluetooth.GattServerManager
import nl.tudelft.ipv8.android.messaging.bluetooth.IPv8BluetoothLeAdvertiser
import nl.tudelft.ipv8.android.messaging.bluetooth.IPv8BluetoothLeScanner
import nl.tudelft.ipv8.android.messaging.udp.AndroidUdpEndpoint
import nl.tudelft.ipv8.android.service.IPv8Service
import nl.tudelft.ipv8.android.util.AndroidEncodingUtils
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.util.defaultEncodingUtils
import java.net.InetAddress

object IPv8Android {
    private var ipv8: IPv8? = null
    internal var serviceClass: Class<out IPv8Service>? = null

    fun getInstance(): IPv8 {
        return ipv8 ?: throw IllegalStateException("IPv8 is not initialized")
    }

    class Factory(
        private val application: Application,
    ) {

        private var privateKey: PrivateKey? = null
        private var identityPrivateKeySmall: BonehPrivateKey? = null
        private var identityPrivateKeyBig: BonehPrivateKey? = null
        private var identityPrivateKeyHuge: BonehPrivateKey? = null

        private var configuration: IPv8Configuration? = null
        private var serviceClass: Class<out IPv8Service> = IPv8Service::class.java

        fun setPrivateKey(key: PrivateKey): Factory {
            this.privateKey = key
            return this
        }

        fun setIdentityKeySmall(key: BonehPrivateKey): Factory {
            this.identityPrivateKeySmall = key
            return this
        }

        fun setIdentityKeyBig(key: BonehPrivateKey): Factory {
            this.identityPrivateKeyBig = key
            return this
        }

        fun setIdentityKeyHuge(key: BonehPrivateKey): Factory {
            this.identityPrivateKeyHuge = key
            return this
        }

        fun setConfiguration(configuration: IPv8Configuration): Factory {
            this.configuration = configuration
            return this
        }

        fun setServiceClass(serviceClass: Class<out IPv8Service>): Factory {
            this.serviceClass = serviceClass
            return this
        }

        fun init(): IPv8 {
            val ipv8 = create()

            if (!ipv8.isStarted()) {
                ipv8.start()
                startAndroidService(application)
            }

            IPv8Android.ipv8 = ipv8
            IPv8Android.serviceClass = serviceClass

            defaultCryptoProvider = AndroidCryptoProvider
            defaultEncodingUtils = AndroidEncodingUtils

            return ipv8
        }

        private fun create(): IPv8 {
            val privateKey = privateKey
                ?: throw IllegalStateException("Private key is not set")

            val configuration = configuration
                ?: throw IllegalStateException("Configuration is not set")

            val connectivityManager = application.getSystemService<ConnectivityManager>()
                ?: throw IllegalStateException("ConnectivityManager not found")

            val udpEndpoint = AndroidUdpEndpoint(
                8090,
                InetAddress.getByName("0.0.0.0"),
                connectivityManager
            )

            val bluetoothManager = application.getSystemService<BluetoothManager>()
                ?: throw IllegalStateException("BluetoothManager not found")

            val myPeer = Peer(
                privateKey,
                identityPrivateKeySmall = this.identityPrivateKeySmall,
                identityPrivateKeyBig = this.identityPrivateKeyBig,
                identityPrivateKeyHuge = this.identityPrivateKeyHuge
            )

            val network = Network()

            val gattServer = GattServerManager(application, myPeer)
            val bleAdvertiser = IPv8BluetoothLeAdvertiser(bluetoothManager)
            val bleScanner = IPv8BluetoothLeScanner(bluetoothManager, network)
            val bluetoothEndpoint = if (
                bluetoothManager.adapter != null && Build.VERSION.SDK_INT >= 24
            ) BluetoothLeEndpoint(
                application, bluetoothManager, gattServer, bleAdvertiser, bleScanner, network, myPeer
            ) else null

            val endpointAggregator = EndpointAggregator(
                udpEndpoint,
                bluetoothEndpoint
            )

            return IPv8(endpointAggregator, configuration, myPeer, network)
        }

        private fun startAndroidService(context: Context) {
            val serviceIntent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
