package de.muenchen.appcenter.nimux.view.manage.users

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentManageUsersBinding
import de.muenchen.appcenter.nimux.model.User
import de.muenchen.appcenter.nimux.repositories.UsersRepository
import de.muenchen.appcenter.nimux.viewmodel.manage.users.ManageUsersViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ManageUsersFragment : Fragment(), ManageUserAdapter.OnItemClickListener {

    @Inject
    lateinit var usersRepository: UsersRepository

    private lateinit var adapter: ManageUserAdapter
    private var _binding: FragmentManageUsersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ManageUsersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_manage_users,
            container,
            false
        )
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_manage_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_check_network_action -> {
                lifecycleScope.launch {
                    if (usersRepository.connectedOnline())
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.network_check_successful),
                            Toast.LENGTH_SHORT
                        ).show()
                    else
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.network_check_failed))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClick(user: User, position: Int) {
        binding.userListRv.isVerticalScrollBarEnabled = false
        val action =
            ManageUsersFragmentDirections.actionManageUsersFragmentToManageUserItem(user)
        findNavController().navigate(action)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        doOther()
    }

    private fun doOther() {
        binding.userListRv.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                var fabVis = binding.addUserFab.isVisible
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0 && fabVis) {
                        fabVis = false
                        binding.addUserFab.hide()
                    } else if (dy < 0 && !fabVis) {
                        fabVis = true
                        binding.addUserFab.show()
                    }
                }
            })
        }

        adapter = ManageUserAdapter(emptyList(), this)
        binding.userListRv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val querySnapshot = usersRepository.getUserRVQuery().get().await()
                val usersList = querySnapshot.toObjects(User::class.java)

                adapter.updateData(usersList)
            } catch (e: Exception) {
                Timber.e(e, "Fehler beim Laden der Benutzer")
            }
        }

        viewModel.navToAddUser.observe(viewLifecycleOwner) { navToAddUser ->
            if (navToAddUser) {
                findNavController().navigate(R.id.action_manageUsersFragment_to_addUserFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ManageUserAdapter(
    private var users: List<User>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<ManageUserAdapter.ManageUserViewHolder>() {

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_manage_user_view, parent, false)
        return ManageUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: ManageUserViewHolder, position: Int) {
        holder.setAttrs(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class ManageUserViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun setAttrs(user: User) {
            view.findViewById<TextView>(R.id.list_user_name).text = user.name
            view.findViewById<TextView>(R.id.list_user_role).text = user.roles.joinToString(", ")
            view.findViewById<TextView>(R.id.list_user_pay).text =
                (view.resources.getString(R.string.credit) + " " + String.format(
                    "%.2f",
                    user.toPay
                ) + "€")

            view.findViewById<MaterialCardView>(R.id.manage_user_list_card).setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(users[position], position)
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(user: User, position: Int)
    }
}