package nl.tudelft.ipv8.android.voting

import android.util.Log
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.EMPTY_SIG
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.attestation.trustchain.store.UserInfo

/**
 * A helper class for interacting with TrustChain.
 */
class TrustChainHelper(
    private val trustChainCommunity: TrustChainCommunity
) {
    /**
     * Returns a list of users and their chain lengths.
     */
    fun getUsers(): List<UserInfo> {
        return trustChainCommunity.database.getUsers()
    }

    /**
     * Returns the number of blocks stored for the given public key.
     */
    fun getStoredBlockCountForUser(publicKeyBin: ByteArray): Long {
        return trustChainCommunity.database.getStoredBlockCountForUser(publicKeyBin)
    }

    /**
     * Returns a peer by its public key if found.
     */
    fun getPeerByPublicKeyBin(publicKeyBin: ByteArray): Peer? {
        return trustChainCommunity.network.getVerifiedByPublicKeyBin(publicKeyBin)
    }

    /**
     * Crawls the chain of the specified peer.
     */
    suspend fun crawlChain(peer: Peer) {
        trustChainCommunity.crawlChain(peer)
    }

    /**
     * Creates a new proposal block, using a text message as the transaction content.
     */
    fun createProposalBlock(message: String, publicKey: ByteArray, blockType: String = "voting_block") {
        val transaction = mapOf("message" to message)
        trustChainCommunity.createProposalBlock(blockType, transaction, publicKey)
    }

    fun createVoteProposalBlock(publicKey: ByteArray, transaction: Map<String, String>, blockType: String = "voting_block") {
        trustChainCommunity.createProposalBlock(blockType, transaction, publicKey)
    }

    /**
     * Creates an agreement block to a specified proposal block, using a custom transaction.
     */
    fun createAgreementBlock(link: TrustChainBlock, transaction: TrustChainTransaction) {
        trustChainCommunity.createAgreementBlock(link, transaction)
    }

//    /**
//     * Creates an agreement block to a specified voting porposal block, using a custom transaction.
//     */
//    fun createVoteAgreementBlock(link: TrustChainBlock, transaction: TrustChainTransaction) {
//        Log.e("vote_debug", link.)
//        Log.e("vote_debug", EMPTY_SIG.toString(Charsets.UTF_8))
//        trustChainCommunity.createAgreementBlock(link, transaction)
//    }


    /**
     * Returns a list of blocks in which the specified user is participating as a sender or
     * a receiver.
     */
    fun getChainByUser(publicKeyBin: ByteArray): List<TrustChainBlock> {
        return trustChainCommunity.database.getMutualBlocks(publicKeyBin, 1000)
    }
}
