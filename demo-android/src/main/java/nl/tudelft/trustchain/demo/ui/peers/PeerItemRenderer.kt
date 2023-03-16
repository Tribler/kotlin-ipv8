package nl.tudelft.trustchain.demo.ui.peers

import androidx.core.view.isVisible
import com.mattskala.itemadapter.BindingItemRenderer
import nl.tudelft.trustchain.demo.databinding.ItemPeerBinding
import java.util.*
import kotlin.math.roundToInt

class PeerItemRenderer(
    private val onItemClick: (PeerItem) -> Unit
) : BindingItemRenderer<PeerItem, ItemPeerBinding>(
    PeerItem::class.java,
    ItemPeerBinding::inflate
) {
    override fun bindView(item: PeerItem, binding: ItemPeerBinding) {
        binding.txtPeerId.text = item.peer.mid
        binding.txtAddress.text = item.peer.address.toString()
        binding.txtAddress.isVisible = !item.peer.address.isEmpty()
        binding.txtBluetoothAddress.text = item.peer.bluetoothAddress?.toString()
        binding.txtBluetoothAddress.isVisible = item.peer.bluetoothAddress != null
        val avgPing = item.peer.getAveragePing()
        val lastRequest = item.peer.lastRequest
        val lastResponse = item.peer.lastResponse

        binding.txtLastSent.text = if (lastRequest != null)
            "" + ((Date().time - lastRequest.time) / 1000.0).roundToInt() + " s" else "?"

        binding.txtLastReceived.text = if (lastResponse != null)
            "" + ((Date().time - lastResponse.time) / 1000.0).roundToInt() + " s" else "?"

        binding.txtAvgPing.text =  if (!avgPing.isNaN()) "" + (avgPing * 1000).roundToInt() + " ms" else "? ms"

        binding.root.setOnClickListener {
            onItemClick(item)
        }
    }
}
