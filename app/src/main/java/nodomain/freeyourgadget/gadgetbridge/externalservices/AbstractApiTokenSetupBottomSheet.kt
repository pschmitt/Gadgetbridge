/*  Copyright (C) 2026 Arjan Schrijver, José Rebelo, Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.externalservices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.GB

/**
 * Setup wizard for a service the user authenticates to with a token they paste in, optionally
 * alongside the address of their own instance.
 *
 * The token is checked against the server before anything is stored, so a typo, a revoked token, a
 * wrong address or a server that is not there is reported here rather than at the first upload,
 * when the user is no longer looking.
 */
abstract class AbstractApiTokenSetupBottomSheet(
    @param:LayoutRes private val layoutRes: Int,
    @param:IdRes private val apiTokenViewId: Int,
    /** Field holding the address of the user's instance, absent for a hosted service. */
    @param:IdRes private val serverViewId: Int? = null,
) : BottomSheetDialogFragment() {
    /** The service being set up. */
    protected abstract val service: ExternalService

    private var serverInput: EditText? = null

    /** Address the user typed, empty for a service hosted at one address. */
    protected val serverName: String get() = serverInput?.text?.toString()?.trim().orEmpty()

    /** Looks up and fills in whatever fields the service owns beyond the token and the server. */
    protected open fun onViewsBound(view: View) {}

    /**
     * Checks [apiToken] and any field the subclass owns before going to the network. Answers a
     * message to show the user, or null when the input is worth trying. Runs on the main thread.
     */
    protected abstract fun validate(apiToken: String): String?

    /**
     * Asks the server whether it is there and whether it accepts [apiToken]. Answers
     * (accepted, reason), where reason is a message to show when it is not. Runs off the main
     * thread, so it must not touch a view.
     */
    protected abstract fun checkToken(apiToken: String): Pair<Boolean, String?>

    /** Stores [apiToken] and anything else the subclass owns, once the server has accepted it. */
    protected abstract fun onTokenAccepted(apiToken: String)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(layoutRes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.title)?.text =
            getString(R.string.service_setup_title, getString(service.nameRes))
        serverViewId?.let { id ->
            serverInput = view.findViewById<EditText>(id).apply {
                setText(service.serverUrl().orEmpty())
            }
        }
        onViewsBound(view)

        val apiTokenInput = view.findViewById<EditText>(apiTokenViewId)
        val saveButton = view.findViewById<Button>(R.id.save_button)

        saveButton.setOnClickListener {
            val apiToken = apiTokenInput.text.toString().trim()

            val validationError = validate(apiToken)
            if (validationError != null) {
                GB.toast(validationError, Toast.LENGTH_SHORT, GB.WARN)
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val (accepted, reason) = withContext(Dispatchers.IO) { checkToken(apiToken) }
                saveButton.isEnabled = true
                if (!accepted) {
                    GB.toast(
                        reason ?: getString(R.string.unknown_error),
                        Toast.LENGTH_LONG,
                        GB.ERROR
                    )
                    return@launch
                }
                service.serverPrefKey?.let { key ->
                    GBApplication.getPrefs().preferences.edit { putString(key, serverName) }
                }
                onTokenAccepted(apiToken)
                parentFragmentManager.setFragmentResult(
                    externalServiceLoginResultKey(service.id),
                    Bundle().apply { putBoolean("success", true) }
                )
                dismiss()
            }
        }
    }
}
