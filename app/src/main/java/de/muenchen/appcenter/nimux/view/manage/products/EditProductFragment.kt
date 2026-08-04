package de.muenchen.appcenter.nimux.view.manage.products

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentEditProductBinding
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import de.muenchen.appcenter.nimux.util.hideKeyboard
import de.muenchen.appcenter.nimux.util.showNetworkHint
import de.muenchen.appcenter.nimux.view.manage.RoleManager
import de.muenchen.appcenter.nimux.viewmodel.manage.products.EditProductViewModel
import javax.inject.Inject

@AndroidEntryPoint
class EditProductFragment : Fragment() {

    private lateinit var binding: FragmentEditProductBinding
    private var roleManager: RoleManager? = null
    @Inject
    lateinit var userSuggestionDataSource: UserSuggestionDataSource

    private val viewModel: EditProductViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_edit_product,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        roleManager = RoleManager(
            fragment = this,
            roleAutoComplete = binding.roleAutocomplete,
            dataSource = userSuggestionDataSource
        ).also { it.initialize() }

        binding.buttonConfigureRoles.setOnClickListener {
            roleManager?.showRoleConfigurationDialog()
        }

        binding.roleAutocomplete.setOnItemClickListener { parent, _, position, _ ->
            val selectedRoleName = parent.getItemAtPosition(position) as String
            viewModel.setSelectedRole(selectedRoleName)
        }

        binding.editProductTitle.text = getString(R.string.edit_title, viewModel.product.name)
        binding.editProductChooseIconButton.setOnClickListener {
            showIconSelect(it)
        }

        val initialIconId = viewModel.product.productIcon
        val initialIconName = iconMap[initialIconId] ?: ""

        if (initialIconName.isNotEmpty()) {
            val drawable = IconicsDrawable(requireContext(), initialIconName).apply {
                colorInt = Color.DKGRAY
                sizeDp = 24
            }
            setButtonHelper(binding.editProductChooseIconButton, initialIconName, drawable)
        } else {
            setButtonHelper(binding.editProductChooseIconButton, getString(R.string.none), null)
        }

        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        roleManager = null
    }

    private fun setupObservers() {
        viewModel.priceInputWrong.observe(viewLifecycleOwner) {
            if (it) binding.editProductPriceLayout.error = getString(R.string.value_wrong_error)
            else binding.editProductPriceLayout.error = null
        }
        viewModel.stockInputWrong.observe(viewLifecycleOwner) {
            if (it) binding.currentStockInputLayout.error = getString(R.string.value_wrong_error)
            else binding.currentStockInputLayout.error = null
        }
        viewModel.refillInputWrong.observe(viewLifecycleOwner) {
            if (it) binding.refillSizeLayout.error = getString(R.string.value_wrong_error)
            else binding.refillSizeLayout.error = null
        }

        viewModel.canceled.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().popBackStack()
            }
        }
        viewModel.updated.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(
                    EditProductFragmentDirections.actionEditProductFragmentToProductItemFragment(
                        viewModel.product
                    )
                )
            }
        }
        viewModel.networkHint.observe(viewLifecycleOwner) {
            if (it) {
                showNetworkHint(
                    requireContext(),
                    { _, _ -> viewModel.updateProduct() },
                    { viewModel.networkHintShown() })
            }
        }
        viewModel.showProgressBar.observe(viewLifecycleOwner) {
            if (it) {
                binding.progressBar.show()
                binding.saveChangesButton.isEnabled = false
            } else {
                binding.progressBar.hide()
                binding.saveChangesButton.isEnabled = true
            }
        }

        viewModel.hideKeyboard.observe(viewLifecycleOwner) { hideKeyboard ->
            if (hideKeyboard) {
                hideKeyboard(requireActivity())
                viewModel.doneHideKeyboard()
            }
        }
    }

    private fun setButtonHelper(btnView: MaterialButton, title: String, iconLeft: Drawable?) {
        btnView.text = title
        btnView.setCompoundDrawablesWithIntrinsicBounds(
            iconLeft,
            null,
            AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.ic_round_arrow_drop_down_24
            ),
            null
        )
    }

    private fun showIconSelect(view: View) {
        hideKeyboard(requireActivity())

        val btnView = view.findViewById<MaterialButton>(R.id.edit_product_choose_icon_button)

        val dialog = IconPickerAlertDialog { selectedIconId ->
            val iconName = iconMap[selectedIconId] ?: ""

            val newIconDrawable = IconicsDrawable(requireContext(), iconName).apply {
                colorInt = Color.DKGRAY
                sizeDp = 24
            }

            setButtonHelper(btnView, iconName, newIconDrawable)

            viewModel.productIconId.value = selectedIconId
        }

        dialog.show(childFragmentManager, "IconPickerAlert")
    }
}