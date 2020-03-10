package nl.tudelft.ipv8.android.voting

import android.util.Log
import nl.tudelft.ipv8.Address
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import org.json.JSONObject
import java.util.*

class VotingCommunity : Community() {
    override val serviceId = "02313685c1912a141279f8248fc8db5899c5d008"

    val discoveredAddressesContacted: MutableMap<Address, Date> = mutableMapOf()

    protected val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    override fun walkTo(address: Address) {
        super.walkTo(address)
        discoveredAddressesContacted[address] = Date()
    }

    protected fun getTrustChainCommunity(): TrustChainCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected fun getVotingCommunity(): VotingCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("VotingCommunity is not configured")
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    fun startVote(voteSubject: String) {
        // Create a JSON object containing the vote subject
        val voteJSON = JSONObject().put("VOTE_SUBJECT", voteSubject)
        // Put the JSON string in the transaction's 'message' field.
        val transaction = mapOf("message" to voteJSON.toString())

        // Loop through all peers in the voting community and send a proposal.
        for (peer in getVotingCommunity().getPeers()) {
            trustchain.createVoteProposalBlock(
                peer.publicKey.keyToBin(),
                transaction,
                "voting_block"
            )
        }
    }

    fun respondToVote(voteName: String, vote: Boolean, proposalBlock: TrustChainBlock) {
        // Reply to the vote with YES or NO.
        val voteReply = if (vote) "YES" else "NO"

        // Create a JSON object containing the vote subject and the reply.
        val voteJSON = JSONObject()
            .put("VOTE_SUBJECT", voteName)
            .put("VOTE_REPLY", voteReply)

        // Put the JSON string in the transaction's 'message' field.
        val transaction = mapOf("message" to voteJSON.toString())

        trustchain.createAgreementBlock(proposalBlock, transaction)
    }

    fun countVotes(voteName: String, proposerKey: ByteArray): Int {
        // TODO: Implement this function properly.

        // Crawl through chain of proposer and find all votes with voteName.
        Log.e("vote_debug", "Counting votes...")

//        var yesCount = 0
//        var noCount = 0
//
//        for (block in trustchain.getChainByUser(proposerKey)) {
//            Log.e("vote_debug", block.transaction.toString())
//            val items = block.transaction["message"].toString().removePrefix("{").removeSuffix("}").split(",")
//            Log.e("vote_debug", items.toString())
//            val reply = items[1].toString().split("=")[1]
//            val subject = items[0].toString().split("=")[1]
//            if (block.type === "voting_block" &&
//                subject === voteName) {
//                if (reply === "YES") {
//                    yesCount++
//                } else if (reply === "NO") {
//                    noCount++
//                } else {
//                    Log.e("vote_debug", reply.toString())
//                }
//            }
//        }
//
//        Log.e("vote_debug", "Yes: $yesCount")
//        Log.e("vote_debug", "No: $noCount")
//
//        return if (yesCount > noCount) yesCount else noCount
        return 5
    }

    class Factory : Overlay.Factory<VotingCommunity>(VotingCommunity::class.java)

}
