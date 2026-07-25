package de.muenchen.appcenter.nimux.view.manage.suggestUser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentSuggestUserBinding
import de.muenchen.appcenter.nimux.datasources.UserDataSource
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import de.muenchen.appcenter.nimux.model.Role
import de.muenchen.appcenter.nimux.model.User
import de.muenchen.appcenter.nimux.view.manage.RoleManager
import de.muenchen.appcenter.nimux.util.md5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SuggestUserFragment : Fragment() {

    private var _binding: FragmentSuggestUserBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var userSuggestionDataSource: UserSuggestionDataSource

    @Inject
    lateinit var userDataSource: UserDataSource

    private lateinit var roleManager: RoleManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSuggestUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        roleManager = RoleManager(
            fragment = this,
            roleAutoComplete = binding.roleAutocomplete,
            dataSource = userSuggestionDataSource
        )

        roleManager.initialize()

        setupListeners()
    }

    private fun setupListeners() {
        binding.buttonConfigureRoles.setOnClickListener {
            roleManager.showRoleConfigurationDialog()
        }
        binding.switchRequirePin.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.constraintLayoutSelectPin.apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                    animate()
                        .alpha(1f)
                        .setDuration(
                            resources.getInteger(R.integer.motion_short)
                                .toLong()
                        )
                        .setListener(null)
                }
            } else {
                if (binding.constraintLayoutSelectPin.visibility != View.GONE) {
                    binding.constraintLayoutSelectPin.animate()
                        .alpha(0f)
                        .setDuration(
                            resources.getInteger(R.integer.motion_short)
                                .toLong()
                        )
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                binding.constraintLayoutSelectPin.visibility = View.GONE
                            }
                        })
                }
            }
        }
        binding.buttonSuggestUser.setOnClickListener {
            val name = binding.nameInputLayout.editText?.text.toString()
            val characterFilter = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]"
            val emotionless: String = name.replace(characterFilter, "")
            Timber.d("$name Without: $emotionless")
            val showCredit = binding.switchShowCredit.isChecked
            val processData = binding.switchProcessData.isChecked
            val requirePin = binding.switchRequirePin.isChecked

            val rawPin = binding.pinTextLayout.editText?.text?.toString() ?: ""
            val rawConfirmPin = binding.confirmPinTextLayout.editText?.text?.toString() ?: ""

            val pinInput = if (requirePin && rawPin.isNotBlank()) md5(rawPin) else null
            val confirmPinInput = if (requirePin && rawConfirmPin.isNotBlank()) md5(rawConfirmPin) else null

            val selectedRoleName = binding.roleAutocomplete.text.toString()

            if (allInputsCorrect(
                    name,
                    requirePin,
                    rawPin,
                    rawConfirmPin,
                    selectedRoleName
                )
            ) {
                val selectedRoleObj = Role(name = selectedRoleName)
                addUserSuggestion(name, showCredit, processData, pinInput, selectedRoleObj)
            }
        }
    }

    private fun addUserSuggestion(
        name: String,
        showCredit: Boolean,
        processData: Boolean,
        pinstring: String?,
        selectedRole: Role,
    ) {
        binding.progressBar.show()

        lifecycleScope.launch(Dispatchers.IO) {
            if (userSuggestionDataSource.getUserSuggestionCount() <= 10) {
                if (userDataSource.userExistsCheck(name) || userSuggestionDataSource.userSuggestionExistsCheck(
                        name
                    )
                ) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.hide()
                        binding.nameInputLayout.error =
                            getString(R.string.user_or_sugg_exists_error)
                    }
                } else {
                    userSuggestionDataSource.addUserSuggestion(
                        User(
                            name = name,
                            showCredit = showCredit,
                            collectData = processData,
                            pin = pinstring,
                            role = selectedRole.name
                        )
                    )
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.hide()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.suggestion_added_toast),
                            Toast.LENGTH_LONG
                        ).show()
                        binding.nameInputLayout.editText?.text?.clear()
                        binding.pinTextLayout.editText?.text?.clear()
                        binding.confirmPinTextLayout.editText?.text?.clear()
                        binding.roleAutocomplete.setText("", false) // Dropdown leeren
                        binding.switchRequirePin.isChecked = false
                        binding.switchShowCredit.isChecked = true
                        binding.switchProcessData.isChecked = true
                    }
                }
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.progressBar.hide()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.too_many_suggestions_toast_text),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun allInputsCorrect(
        name: String,
        requirePin: Boolean,
        rawPin: String,
        rawConfirmPin: String,
        selectedRole: String
    ): Boolean {
        var isValid = true

        if (selectedRole.isBlank()) {
            binding.roleInputLayout.error = "Bitte wähle eine Rolle aus"
            isValid = false
        } else {
            binding.roleInputLayout.error = null
        }

        if (name.isBlank()) {
            binding.nameInputLayout.error = getString(R.string.add_user_name_error)
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        if (requirePin) {
            if (rawPin.isBlank()) {
                binding.pinTextLayout.error = getString(R.string.add_user_pin_error)
                isValid = false
            } else {
                binding.pinTextLayout.error = null
            }

            if (rawPin != rawConfirmPin) {
                binding.confirmPinTextLayout.error = getString(R.string.add_user_confirm_pin_error)
                isValid = false
            } else {
                binding.confirmPinTextLayout.error = null
            }
        } else {
            binding.pinTextLayout.error = null
            binding.confirmPinTextLayout.error = null
        }
        return isValid
    }
}