package nl.tudelft.ipv8.android.voting.ui.debug

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.fragment_debug.*
import kotlinx.coroutines.*
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.android.voting.R
import nl.tudelft.ipv8.android.voting.ui.BaseFragment
import nl.tudelft.ipv8.util.toHex

class DebugFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        proposeButton.setOnClickListener {
            showNewVoteDialog();
        }

        lifecycleScope.launchWhenStarted {
            while (isActive) {
                updateView()
                delay(1000)
            }
        }
    }

    /**
     * Dialog for a new proposal vote
     */
    private fun showNewVoteDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("New proposal vote")

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setHint("p != np")
        builder.setView(input)

        builder.setPositiveButton("Create") { _, _ ->
            val proposal = input.text.toString()
            getVotingCommunity().startVote(proposal)
            Toast.makeText(this.context, "Start voting procedure", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()

    }

    private fun updateView() {
        val ipv8 = getIpv8()
        val voting = getVotingCommunity()
        txtBootstrap.text = Community.DEFAULT_ADDRESSES.joinToString("\n")
        txtLanAddress.text = voting.myEstimatedLan.toString()
        txtWanAddress.text = voting.myEstimatedWan.toString()
        txtPeerId.text = ipv8.myPeer.mid
        txtPublicKey.text = ipv8.myPeer.publicKey.keyToBin().toHex()
        txtOverlays.text = ipv8.overlays.values.toList().joinToString("\n") {
            it.javaClass.simpleName + " (" + it.getPeers().size + " peers)"
        }

        lifecycleScope.launch {
            val blockCount = withContext(Dispatchers.IO) {
                getTrustChainCommunity().database.getAllBlocks().size
            }
            txtBlockCount.text = blockCount.toString()
        }

        lifecycleScope.launch {
            val chainLength = withContext(Dispatchers.IO) {
                getTrustChainCommunity().getChainLength()
            }
            txtChainLength.text = chainLength.toString()
        }
    }
}
