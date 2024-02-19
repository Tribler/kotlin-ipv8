package nl.tudelft.trustchain.demo.ui

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.mattskala.itemadapter.ItemAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.demo.DemoCommunity
import nl.tudelft.trustchain.demo.R
import nl.tudelft.trustchain.demo.databinding.FragmentPeersBinding
import nl.tudelft.trustchain.demo.ui.peers.AddressItem
import nl.tudelft.trustchain.demo.ui.peers.AddressItemRenderer
import nl.tudelft.trustchain.demo.ui.peers.PeerItem
import nl.tudelft.trustchain.demo.ui.peers.PeerItemRenderer

class DemoActivity : AppCompatActivity() {
    private lateinit var binding: FragmentPeersBinding
    private val adapter = ItemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentPeersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter.registerRenderer(PeerItemRenderer {
            // NOOP
        })

        adapter.registerRenderer(AddressItemRenderer {
            // NOOP
        })

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayout.VERTICAL))

        loadNetworkInfo()
    }

    private fun loadNetworkInfo() {
        lifecycleScope.launchWhenStarted {
            while (isActive) {
                val demoCommunity = IPv8Android.getInstance().getOverlay<DemoCommunity>()!!
                val peers = demoCommunity.getPeers()

                val discoveredAddresses = demoCommunity.network.getWalkableAddresses(demoCommunity.serviceId)

                val discoveredBluetoothAddresses =
                    demoCommunity.network.getNewBluetoothPeerCandidates().map { it.address }

                val peerItems = peers.map {
                    PeerItem(
                        it
                    )
                }

                val addressItems = discoveredAddresses.map { address ->
                    val contacted = demoCommunity.discoveredAddressesContacted[address]
                    AddressItem(
                        address, null, contacted
                    )
                }

                val bluetoothAddressItems = discoveredBluetoothAddresses.map { address ->
                    AddressItem(
                        address, null, null
                    )
                }

                val items = peerItems + bluetoothAddressItems + addressItems

                adapter.updateItems(items)
                binding.txtCommunityName.text = demoCommunity.javaClass.simpleName
                binding.txtPeerCount.text = "${peers.size} peers"
                val textColorResId = if (peers.isNotEmpty()) R.color.green else R.color.red
                val textColor = ResourcesCompat.getColor(resources, textColorResId, null)
                binding.txtPeerCount.setTextColor(textColor)
                binding.imgEmpty.isVisible = items.isEmpty()

                delay(1000)
            }
        }
    }
}
