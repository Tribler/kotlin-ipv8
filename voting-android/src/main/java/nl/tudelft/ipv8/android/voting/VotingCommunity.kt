package nl.tudelft.ipv8.android.voting

import android.util.Log
import nl.tudelft.ipv8.Address
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import org.json.JSONArray
import org.json.JSONException
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
        // TODO: Add vote ID to increase probability of uniqueness.

        // Get all peers in the community and create a JSON array of their public keys.
        val peers = getVotingCommunity().getPeers()
        val voteList = JSONArray(peers.map { it.publicKey.toString() })

        // Create a JSON object containing the vote subject
        val voteJSON = JSONObject()
            .put("VOTE_SUBJECT", voteSubject)
            .put("VOTE_LIST", voteList)

        // Put the JSON string in the transaction's 'message' field.
        val transaction = mapOf("message" to voteJSON.toString())

        // Loop through all peers in the voting community and send a proposal.
        for (peer in peers) {
            trustchain.createVoteProposalBlock(
                peer.publicKey.keyToBin(),
                transaction,
                "voting_block"
            )
        }

        // Update the JSON to include a VOTE_END message.
        voteJSON.put("VOTE_END", "True")
        val endTransaction = mapOf("message" to voteJSON.toString())

        // Add the VOTE_END transaction to the proposer's chain and self-sign it.
        trustchain.createVoteProposalBlock(
            myPeer.publicKey.keyToBin(),
            endTransaction, "voting_block"
        )
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

    /**
     * Return the tally on a vote proposal in a pair(yes, no).
     */
    fun countVotes(voteName: String, proposerKey: ByteArray): Pair<Int, Int> {

        var voters: MutableList<String> = ArrayList()

        var yesCount = 0
        var noCount = 0

        // Crawl the chain of the proposer.
        for (it in trustchain.getChainByUser(proposerKey)) {
            
            if (voters.contains(it.publicKey.contentToString())){
                continue
            }

            // Skip all blocks which are not voting blocks
            // and don't have a 'message' field in their transaction.
            if (it.type != "voting_block" || !it.transaction.containsKey("message")) {
                continue
            }

            // Parse the 'message' field as JSON.
            val voteJSON = try {
                JSONObject(it.transaction["message"].toString())
            } catch (e: JSONException) {
                // Assume a malicious vote if it claims to be a vote but does not contain
                // proper JSON.
                handleInvalidVote("Block was a voting block but did not contain " +
                    "proper JSON in its message field: ${it.transaction["message"].toString()}."
                )
                continue
            }

            // Assume a malicious vote if it does not have a VOTE_SUBJECT.
            if (!voteJSON.has("VOTE_SUBJECT")) {
                handleInvalidVote("Block type was a voting block but did not have a VOTE_SUBJECT.")
                continue
            }

            // A block with another VOTE_SUBJECT belongs to another vote.
            if (voteJSON.get("VOTE_SUBJECT") != voteName) {
                // Block belongs to another vote.
                continue
            }

            // A block with the same subject but no reply is the original vote proposal.
            if (!voteJSON.has("VOTE_REPLY")) {
                // Block is the initial vote proposal because it does not have a VOTE_REPLY field.
                continue
            }

            // Add the votes, or assume a malicious vote if it is not YES or NO.
            when (voteJSON.get("VOTE_REPLY")) {
                "YES" -> {
                    yesCount++
                    voters.add(it.publicKey.contentToString())
                }
                "NO" -> {
                    noCount++
                    voters.add(it.publicKey.contentToString())
                }
                else -> handleInvalidVote("Vote was not 'YES' or 'NO' but: '${voteJSON.get("VOTE_REPLY")}'.")
            }
        }

        return Pair(yesCount, noCount)

        /*var yesCount = 0
        var noCount = 0

        // Count votes
        trustchain.getChainByUser(proposerKey).forEach {
            val payload =
                it.transaction["message"].toString().removePrefix("{").removeSuffix("}").split(",")
            Log.e("vote_debug", "payload: $payload")

            if (payload.size > 1) {
                val subject = payload[0].split(":")[1]
                val reply = payload[1].split(":")[1]

                if (it.type == "voting_block" && subject == voteName) {
                    when (reply) {
                        "\"YES\"" -> yesCount++
                        "\"NO\"" -> noCount++
                        else -> Log.e("vote_debug", "Different option encountered: $reply")
                    }
                }
            }
        }
        Log.e("vote_debug", "$yesCount,$noCount")
        return Pair(yesCount, noCount)*/
    }

    fun handleInvalidVote(errorType: String) {
        Log.e("vote_debug", errorType)
    }

    class Factory : Overlay.Factory<VotingCommunity>(VotingCommunity::class.java)

}
