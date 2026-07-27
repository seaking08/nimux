package de.muenchen.appcenter.nimux.view.manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.databinding.FragmentManageOverviewBinding
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import javax.inject.Inject

@AndroidEntryPoint
class ManageOverviewFragment : Fragment() {

    private var _binding: FragmentManageOverviewBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var userSuggestionDataSource: UserSuggestionDataSource

    private lateinit var roleManager: RoleManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentManageOverviewBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity)
            .supportActionBar
            ?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dummyAutoComplete = AutoCompleteTextView(requireContext())

        roleManager = RoleManager(
            fragment = this,
            roleAutoComplete = dummyAutoComplete,
            dataSource = userSuggestionDataSource
        )

        roleManager.initialize()

        setupListeners()
    }

    private fun setupListeners() {
        binding.manageOverviewSuggestedUserCard.setOnClickListener {
            findNavController().navigate(ManageOverviewFragmentDirections.actionManageOverviewFragmentToSuggestedUsersFragment())
        }

        binding.manageOverviewUserCard.setOnClickListener {
            findNavController().navigate(ManageOverviewFragmentDirections.actionManageOverviewFragmentToManageUsersFragment())
        }

        binding.manageOverviewProductCard.setOnClickListener {
            findNavController().navigate(ManageOverviewFragmentDirections.actionManageOverviewFragmentToManageProductsFragment())
        }

        binding.manageOverviewHistoryCard.setOnClickListener {
            findNavController().navigate(ManageOverviewFragmentDirections.actionManageOverviewFragmentToHistoryListFragment())
        }

        binding.manageOverviewRoleCard.setOnClickListener {
            roleManager.showRoleConfigurationDialog()
        }
    }
}