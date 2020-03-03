package nl.tudelft.ipv8.android.voting

import android.util.Log
import nl.tudelft.ipv8.Address
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
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
        return getIpv8().getOverlay() ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected fun getVotingCommunity(): VotingCommunity {
        return getIpv8().getOverlay() ?: throw IllegalStateException("VotingCommunity is not configured")
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    fun startVote(voteSubject: String) {
        // Loop through all peers in the voting community and send a proposal.
        for (peer in getVotingCommunity().getPeers()) {
            val transaction = mapOf("VOTE_SUBJECT" to voteSubject)
            trustchain.createVoteProposalBlock(peer.publicKey.keyToBin(), transaction, "voting_block")

            Log.e("vote_debug", peer.publicKey.toString())
        }
    }

    fun respondToVote(voteName: String, vote: Boolean, proposalBlock: TrustChainBlock) {
        // Reply to the vote with YES or NO.
        val transaction = mapOf("VOTE_SUBJECT" to voteName, "VOTE_REPLY" to if (vote) "YES" else "NO")
//        val transaction = mapOf("vote" to if (vote) "YES" else "NO")

        trustchain.createAgreementBlock(proposalBlock, transaction)
        Log.e("vote_debug", transaction.toString())
    }

    fun countVotes(voteName: String, proposerKey: ByteArray): Int {
        // Crawl through chain of proposer and find all votes with voteName.
        Log.e("vote_debug", "Hit it")

        var yesCount = 0
        var noCount = 0

        for (block in trustchain.getChainByUser(proposerKey)) {
            Log.e("vote_debug", block.transaction.toString())

            if (block.type === "voting_block" &&
                block.transaction["VOTE_SUBJECT"] === voteName) {
                if (block.transaction["VOTE_REPLY"] === "YES") {
                    yesCount++
                } else if (block.transaction["VOTE_REPLY"] === "NO") {
                    noCount++
                } else {
                    Log.e("vote_debug", block.transaction["VOTE_REPLY"].toString())
                }
            }
        }

        Log.e("vote_debug", "Yes: $yesCount")
        Log.e("vote_debug", "No: $noCount")

        return if (yesCount > noCount) yesCount else noCount
//        trustchain.crawlChain()
    }
}
