package de.muenchen.appcenter.nimux.view.manage

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.datasources.UserSuggestionDataSource
import de.muenchen.appcenter.nimux.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.coroutines.withContext

class RoleManager(
    private val fragment: Fragment,
    private val dataSource: UserSuggestionDataSource
) {
    private val rolesList = mutableListOf<Role>()
    private val roleNamesList = mutableListOf<String>()


    private val _availableRolesFlow = MutableStateFlow<List<String>>(emptyList())
    val availableRolesFlow: StateFlow<List<String>> = _availableRolesFlow.asStateFlow()

    private var activeRolesContainer: LinearLayout? = null

    private var activeToast: Toast? = null

    fun initialize() {
        observeRoles()
    }

    private fun observeRoles() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            dataSource.getRolesFlow().collect { roles ->
                rolesList.clear()
                rolesList.addAll(roles)

                roleNamesList.clear()
                roleNamesList.addAll(roles.map { it.name })

                _availableRolesFlow.value = roleNamesList.toList()

                activeRolesContainer?.let { updateRolesListUI(it) }
            }
        }
    }

    fun showRoleConfigurationDialog() {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_manage_roles, null)

        activeRolesContainer = dialogView.findViewById(R.id.roles_list_container)

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(fragment.getString(R.string.action_role))
            .setView(dialogView)
            .setPositiveButton(fragment.getString(R.string.save)) { dialogInterface, _ ->
                activeRolesContainer = null
                dialogInterface.dismiss()
            }
            .setNegativeButton(fragment.getString(R.string.add_new_role), null)
            .setOnDismissListener {
                activeRolesContainer = null
            }
            .create()

        activeRolesContainer?.let { updateRolesListUI(it) }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            addRole(dialogView)
        }
    }

    private fun addRole(dialogView: View) {
        val newRoleEditText = dialogView.findViewById<TextInputEditText>(R.id.new_role_edit_text)
        val isSuperRoleCheckBox =
            dialogView.findViewById<MaterialCheckBox>(R.id.checkbox_is_super_role)
        val newRoleName = newRoleEditText.text.toString().trim()
        val isSuperRole = isSuperRoleCheckBox.isChecked

        if (newRoleName.isNotEmpty() && !roleNamesList.contains(newRoleName)) {
            val newRoleObject = Role(name = newRoleName, superRole = isSuperRole)

            fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    dataSource.addRole(newRoleObject)

                    fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        newRoleEditText.text?.clear()
                        isSuperRoleCheckBox.isChecked = false
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Fehler beim Speichern der Rolle")
                }
            }
        }
    }

    private fun updateRolesListUI(rolesContainer: LinearLayout) {
        if (!fragment.isAdded) return

        rolesContainer.removeAllViews()
        for (role in rolesList) {
            val itemView =
                fragment.layoutInflater.inflate(R.layout.item_role, rolesContainer, false)
            val roleNameText = itemView.findViewById<TextView>(R.id.text_role_name)
            val superRoleCheckBox =
                itemView.findViewById<MaterialCheckBox>(R.id.checkbox_item_super_role)
            val deleteButton = itemView.findViewById<MaterialButton>(R.id.button_delete_role)

            roleNameText.text = role.name

            superRoleCheckBox.setOnCheckedChangeListener(null)
            superRoleCheckBox.isChecked = role.superRole

            superRoleCheckBox.setOnCheckedChangeListener { _, isChecked ->
                val updatedRole = role.copy(superRole = isChecked)
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        dataSource.addRole(updatedRole)
                    } catch (e: Exception) {
                        Timber.e(e, "Fehler beim Aktualisieren der Rolle")
                    }
                }
            }

            deleteButton.setOnClickListener {
                deleteRole(role)
            }
            rolesContainer.addView(itemView)
        }
    }

    private fun deleteRole(role: Role) {
        val context = fragment.context ?: return

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(fragment.getString(R.string.action_role))
            .setMessage(fragment.getString(R.string.delete_role))
            .setPositiveButton(fragment.getString(R.string.yes), null)
            .setNegativeButton(fragment.getString(R.string.cancel), null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    dataSource.deleteRole(role)

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Fehler beim Löschen der Rolle")
                }
            }
        }
    }

    private fun showLimitedToast(context: android.content.Context, message: String) {
        activeToast?.cancel()
        activeToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        activeToast?.show()
    }

    fun showMultiSelectRoleDialog(
        currentSelectedRoles: Set<String>,
        onRolesSelected: (Set<String>) -> Unit
    ) {
        val availableRolesList = availableRolesFlow.value

        if (availableRolesList.isEmpty()) {
            return
        }

        val availableRolesArray = availableRolesList.toTypedArray()
        val checkedItems = availableRolesArray.map { role ->
            currentSelectedRoles.contains(role)
        }.toBooleanArray()

        val tempSelectedRoles = currentSelectedRoles.toMutableSet()

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(fragment.getString(R.string.action_role))
            .setMultiChoiceItems(availableRolesArray, checkedItems) { _, which, isChecked ->
                val clickedRole = availableRolesArray[which]
                if (isChecked) {
                    tempSelectedRoles.add(clickedRole)
                } else {
                    tempSelectedRoles.remove(clickedRole)
                }
            }
            .setPositiveButton(fragment.getString(R.string.save), null)
            .setNegativeButton(fragment.getString(R.string.cancel), null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (tempSelectedRoles.size > 3) {
                showLimitedToast(
                    fragment.requireContext(),
                    fragment.getString(R.string.max_roles)
                )
            } else {
                onRolesSelected(tempSelectedRoles)
                dialog.dismiss()
            }
        }
    }
}