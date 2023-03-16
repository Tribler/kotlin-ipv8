package nl.tudelft.trustchain.demo.ui.peers

import androidx.core.view.isVisible
import com.mattskala.itemadapter.BindingItemRenderer
import nl.tudelft.trustchain.demo.databinding.ItemPeerBinding
import java.util.*
import kotlin.math.roundToInt

class AddressItemRenderer(
    private val onItemClick: (AddressItem) -> Unit
) : BindingItemRenderer<AddressItem, ItemPeerBinding>(
    AddressItem::class.java,
    ItemPeerBinding::inflate
) {
    override fun bindView(item: AddressItem, binding: ItemPeerBinding) {
        binding.txtPeerId.text = "?"
        binding.txtAddress.text = item.address.toString()
        val lastRequest = item.contacted
        val lastResponse = item.discovered
        binding.txtBluetoothAddress.isVisible = false

        binding.txtLastSent.text = if (lastRequest != null)
            "" + ((Date().time - lastRequest.time) / 1000.0).roundToInt() + " s" else "?"

        binding.txtLastReceived.text = if (lastResponse != null)
            "" + ((Date().time - lastResponse.time) / 1000.0).roundToInt() + " s" else "?"

        binding.txtAvgPing.text = "? ms"

        binding.root.setOnClickListener {
            onItemClick(item)
        }
    }
}
