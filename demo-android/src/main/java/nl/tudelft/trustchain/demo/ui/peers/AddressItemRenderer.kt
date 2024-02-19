package nl.tudelft.trustchain.demo.ui.peers

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import com.mattskala.itemadapter.ItemLayoutRenderer
import nl.tudelft.trustchain.demo.R
import nl.tudelft.trustchain.demo.databinding.ItemPeerBinding
import java.util.*
import kotlin.math.roundToInt

class AddressItemRenderer(
    private val onItemClick: (AddressItem) -> Unit
) : ItemLayoutRenderer<AddressItem, View>(
    AddressItem::class.java) {
    @SuppressLint("SetTextI18n")
    override fun bindView(item: AddressItem, view: View) = with(view) {
        val binding = ItemPeerBinding.bind(view)
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

        setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.item_peer
    }
}
