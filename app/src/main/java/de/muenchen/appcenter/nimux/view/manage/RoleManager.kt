package de.muenchen.appcenter.nimux.view.manage

import android.R
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import de.muenchen.appcenter.nimux.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class RoleManager(
    private val fragment: Fragment,
    private val roleAutoComplete: AutoCompleteTextView,
    private val dataSource: UserSuggestionDataSource
) {
    private val rolesList = mutableListOf<Role>()
    private val roleNamesList = mutableListOf<String>()

    private val roleAdapter: ArrayAdapter<String> by lazy {
        object : ArrayAdapter<String>(
            fragment.requireContext(),
            R.layout.simple_dropdown_item_1line,
            roleNamesList
        ) {
            //Deactivates filter to show all roles
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        results.values = roleNamesList
                        results.count = roleNamesList.size
                        return results
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        if (results != null && results.count > 0) {
                            notifyDataSetChanged()
                        } else {
                            notifyDataSetInvalidated()
                        }
                    }
                }
            }
        }
    }

    private var activeRolesContainer: LinearLayout? = null

    fun initialize() {
        roleAutoComplete.setAdapter(roleAdapter)
        observeRoles()
    }

    private fun observeRoles() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            dataSource.getRolesFlow().collect { roles ->
                rolesList.clear()
                rolesList.addAll(roles)

                roleNamesList.clear()
                roleNamesList.addAll(roles.map { it.name })

                roleAdapter.notifyDataSetChanged()

                val currentSelection = roleAutoComplete.text.toString()
                if (currentSelection.isNotEmpty() && !roleNamesList.contains(currentSelection)) {
                    roleAutoComplete.setText("", false)
                }

                activeRolesContainer?.let { updateRolesListUI(it) }
            }
        }
    }

    fun showRoleConfigurationDialog() {
        val dialogView = fragment.layoutInflater.inflate(de.muenchen.appcenter.nimux.R.layout.dialog_manage_roles, null)
        val newRoleEditText = dialogView.findViewById<TextInputEditText>(de.muenchen.appcenter.nimux.R.id.new_role_edit_text)
        val addButton = dialogView.findViewById<MaterialButton>(de.muenchen.appcenter.nimux.R.id.button_add_role_dialog)

        activeRolesContainer = dialogView.findViewById(de.muenchen.appcenter.nimux.R.id.roles_list_container)

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Rollen konfigurieren")
            .setView(dialogView)
            .setPositiveButton("Fertig") { dialogInterface, _ ->
                activeRolesContainer = null
                dialogInterface.dismiss()
            }
            .setOnDismissListener {
                activeRolesContainer = null
            }
            .create()

        addButton.setOnClickListener {
            val newRoleName = newRoleEditText.text.toString().trim()

            if (newRoleName.isNotEmpty() && !roleNamesList.contains(newRoleName)) {
                val newRoleObject = Role(name = newRoleName, superRole = false)

                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        dataSource.addRole(newRoleObject)

                        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            newRoleEditText.text?.clear()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Fehler beim Speichern der Rolle")
                    }
                }
            }
        }

        activeRolesContainer?.let { updateRolesListUI(it) }
        dialog.show()
    }

    private fun updateRolesListUI(rolesContainer: LinearLayout) {
        if (!fragment.isAdded) return

        rolesContainer.removeAllViews()
        for (role in rolesList) {
            val itemView = fragment.layoutInflater.inflate(de.muenchen.appcenter.nimux.R.layout.item_role, rolesContainer, false)
            val roleNameText = itemView.findViewById<TextView>(de.muenchen.appcenter.nimux.R.id.text_role_name)
            val deleteButton = itemView.findViewById<MaterialButton>(de.muenchen.appcenter.nimux.R.id.button_delete_role)

            roleNameText.text = role.name

            deleteButton.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        dataSource.deleteRole(role)
                    } catch (e: Exception) {
                        Timber.e(e, "Fehler beim Löschen der Rolle")
                    }
                }
            }
            rolesContainer.addView(itemView)
        }
    }
}