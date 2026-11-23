package com.aayushjagtap.pickupplay.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aayushjagtap.pickupplay.R
import com.aayushjagtap.pickupplay.data.cloud.AccountViewModel
import com.aayushjagtap.pickupplay.data.cloud.CloudError
import com.aayushjagtap.pickupplay.data.cloud.CloudSyncManager
import com.aayushjagtap.pickupplay.data.cloud.CloudSyncManager.Busy
import com.aayushjagtap.pickupplay.data.cloud.CloudSyncManager.Outcome
import com.aayushjagtap.pickupplay.databinding.FragmentAccountBinding
import java.util.Date

/**
 * A4 Phase 2: email/password sign-in for cloud sync, sync status, and a signed-out server read
 * that demonstrates the Firestore security rules. The rest of the app works without signing in.
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AccountViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }
        binding.passwordInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { signIn(); true } else false
        }
        binding.signInButton.setOnClickListener { signIn() }
        binding.signOutButton.setOnClickListener { viewModel.signOut() }
        binding.syncButton.setOnClickListener { viewModel.syncNow() }
        binding.probeButton.setOnClickListener { viewModel.probeUnauthenticatedRead() }

        var unsynced = 0
        viewModel.unsyncedCount.observe(viewLifecycleOwner) { count ->
            unsynced = count
            viewModel.state.value?.let { render(it, unsynced) }
        }
        viewModel.state.observe(viewLifecycleOwner) { render(it, unsynced) }
    }

    private fun signIn() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        var valid = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_email_invalid); valid = false
        }
        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.error_password_required); valid = false
        }
        if (valid) viewModel.signIn(email, password)
    }

    private fun render(state: CloudSyncManager.State, unsynced: Int) {
        if (_binding == null) return
        val busy = state.busy
        val signedIn = state.user != null

        binding.configBanner.isVisible = !state.configured || viewModel.usingEmulator
        binding.configBanner.setText(
            if (!state.configured) R.string.cloud_not_configured else R.string.cloud_using_emulator
        )
        binding.signedOutGroup.isVisible = state.configured && !signedIn
        binding.signedInGroup.isVisible = state.configured && signedIn
        binding.cloudProgress.isVisible = busy != null

        binding.signInButton.isEnabled = busy == null
        binding.probeButton.isEnabled = busy == null
        binding.syncButton.isEnabled = busy == null
        binding.signOutButton.isEnabled = busy != Busy.SIGNING_IN
        binding.emailInput.isEnabled = busy != Busy.SIGNING_IN
        binding.passwordInput.isEnabled = busy != Busy.SIGNING_IN

        state.user?.let {
            binding.signedInAs.text = getString(R.string.signed_in_as, it.email ?: it.uid)
            binding.passwordInput.text = null // never keep the password around once signed in
        }
        binding.passwordLayout.error = state.signInError?.takeIf { !signedIn }?.let { getString(it.messageRes) }

        val status = when (busy) {
            Busy.SIGNING_IN -> getString(R.string.cloud_busy_signing_in)
            Busy.SYNCING -> getString(R.string.cloud_busy_syncing)
            Busy.UPLOADING -> getString(R.string.cloud_busy_uploading)
            Busy.PROBING -> getString(R.string.cloud_busy_probing)
            null -> state.last?.let { describe(it) }
        }
        binding.cloudStatus.text = status
        binding.cloudStatus.isVisible = state.configured && status != null

        binding.unsyncedText.isVisible = state.configured && unsynced > 0
        binding.unsyncedText.text = resources.getQuantityString(
            if (signedIn) R.plurals.unsynced_signed_in else R.plurals.unsynced_signed_out, unsynced, unsynced,
        )
    }

    private fun describe(outcome: Outcome): String {
        val time = DateFormat.getTimeFormat(requireContext()).format(Date(outcome.at))
        return when (outcome) {
            is Outcome.Synced -> buildString {
                append(getString(R.string.cloud_last_sync_ok, time, outcome.fetchedFromServer, outcome.addedLocally))
                if (outcome.uploaded > 0) append('\n').append(getString(R.string.cloud_last_uploaded, outcome.uploaded))
                if (outcome.notUploaded > 0) {
                    append('\n').append(
                        getString(
                            R.string.cloud_last_not_uploaded, outcome.notUploaded,
                            getString((outcome.uploadError ?: CloudError.UNKNOWN).messageRes),
                        )
                    )
                }
            }
            is Outcome.SyncFailed ->
                getString(R.string.cloud_last_sync_failed, time, getString(outcome.error.messageRes))
            is Outcome.Uploaded -> if (outcome.notUploaded == 0) {
                getString(R.string.cloud_last_upload_ok, time, outcome.uploaded)
            } else {
                getString(
                    R.string.cloud_last_upload_failed, time,
                    getString((outcome.error ?: CloudError.UNKNOWN).messageRes),
                )
            }
            is Outcome.Probe -> when {
                outcome.denied -> getString(R.string.probe_denied, time)
                outcome.error == null -> getString(R.string.probe_allowed, time)
                else -> getString(R.string.probe_inconclusive, time, getString(outcome.error.messageRes))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
