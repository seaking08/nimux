package de.muenchen.appcenter.nimux.view.manage.users

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentAddUserBinding
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import de.muenchen.appcenter.nimux.util.hideKeyboard
import de.muenchen.appcenter.nimux.util.showNetworkHint
import de.muenchen.appcenter.nimux.view.manage.RoleManager
import de.muenchen.appcenter.nimux.viewmodel.manage.users.AddUserViewModel
import javax.inject.Inject

@AndroidEntryPoint
class AddUserFragment : Fragment() {

    private var _binding: FragmentAddUserBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddUserViewModel by viewModels()
    private var roleManager: RoleManager? = null

    @Inject
    lateinit var userSuggestionDataSource: UserSuggestionDataSource

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_add_user, container, false)

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        setupObservers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        roleManager = RoleManager(
            fragment = this,
            dataSource = userSuggestionDataSource
        ).also { it.initialize() }

        roleManager = RoleManager(this, userSuggestionDataSource).also { it.initialize() }

        binding.roleInputEditText.setOnClickListener {
            roleManager?.showMultiSelectRoleDialog(viewModel.selectedRoles.value ?: emptySet()) { updatedRoles ->
                viewModel.updateSelectedRoles(updatedRoles)
            }
        }
    }

    private fun setupObservers() {
        viewModel.nameEntered.observe(viewLifecycleOwner) { nameEntered ->
            if (!nameEntered)
                binding.addUserInputName.error = getString(R.string.add_user_name_error)
            else
                binding.addUserInputName.error = null
        }

        viewModel.userAdded.observe(viewLifecycleOwner) { userAdded ->
            if (userAdded) {
                hideKeyboard(activity as Activity)
                findNavController().navigate(AddUserFragmentDirections.actionAddUserFragmentToManageUsersFragment())
            }
        }

        viewModel.canceled.observe(viewLifecycleOwner) { canceled ->
            if (canceled) {
                hideKeyboard(requireActivity())
                findNavController().popBackStack()
            }
        }

        viewModel.pinEntered.observe(viewLifecycleOwner) {
            if (!it) binding.addUserPinInput.error = getString(R.string.add_user_pin_error)
            else binding.addUserPinInput.error = null
        }

        viewModel.pinConfirmed.observe(viewLifecycleOwner) {
            if (!it && binding.addUserPinInput.error == null)
                binding.addUserPinConfirmation.error =
                    getString(R.string.add_user_confirm_pin_error)
            else
                binding.addUserPinConfirmation.error = null
        }

        viewModel.userExists.observe(viewLifecycleOwner) { exists ->
            if (exists) binding.addUserInputName.error = getString(R.string.user_exists_hint)
            else binding.addUserInputName.error = null
        }

        viewModel.networkHint.observe(viewLifecycleOwner) {
            if (it) {
                showNetworkHint(requireContext(),
                    { _, _ -> viewModel.addUser() },
                    { viewModel.networkHintShown() })
            }
        }
        viewModel.showProgressBar.observe(viewLifecycleOwner) {
            if (it) {
                binding.progressBar.show()
                binding.addUserButton.isEnabled = false
            } else {
                binding.progressBar.hide()
                binding.addUserButton.isEnabled = true
            }
        }

        viewModel.perfHapticFeedback.observe(viewLifecycleOwner) {
            if (it) requireView().performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        viewModel.roleEmpty.observe(viewLifecycleOwner) { isRoleEmpty ->
            if (isRoleEmpty) {
                binding.roleInputLayout.error = getString(R.string.field_cant_be_empty)
            } else {
                binding.roleInputLayout.error = null
            }
        }

        viewModel.requirePin.observe(viewLifecycleOwner) {
            val pinLayout =
                requireView().findViewById<ConstraintLayout>(R.id.pin_confirmation_layout)
            if (it) {
                pinLayout.apply {
                    alpha = 0f
                    visibility = View.VISIBLE

                    animate()
                        .alpha(1f)
                        .setDuration(resources.getInteger(R.integer.motion_short)
                            .toLong())
                        .setListener(null)
                }
            } else {
                if (pinLayout.visibility != View.GONE) {
                    pinLayout.animate()
                        .alpha(0f)
                        .setDuration(resources.getInteger(R.integer.motion_short)
                            .toLong())
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                pinLayout.visibility = View.GONE
                            }
                        })
                }
            }
        }

        viewModel.hideKeyboard.observe(viewLifecycleOwner) { hideKeyboard ->
            if (hideKeyboard) {
                hideKeyboard(requireActivity())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        roleManager = null
        _binding = null
    }
}