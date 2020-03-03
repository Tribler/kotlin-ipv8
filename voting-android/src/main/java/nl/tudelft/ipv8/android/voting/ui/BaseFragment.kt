package nl.tudelft.ipv8.android.voting.ui

import androidx.fragment.app.Fragment
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.voting.VotingCommunity

import nl.tudelft.ipv8.android.voting.TrustChainHelper
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity

abstract class BaseFragment : Fragment() {
    protected val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    protected fun getTrustChainCommunity(): TrustChainCommunity {
        return getIpv8().getOverlay() ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected fun getVotingCommunity(): VotingCommunity {
        return getIpv8().getOverlay() ?: throw IllegalStateException("VotingCommunity is not configured")
    }
}
