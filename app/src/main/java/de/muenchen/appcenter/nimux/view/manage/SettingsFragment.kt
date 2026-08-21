package de.muenchen.appcenter.nimux.view.manage

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.BuildConfig
import de.muenchen.appcenter.nimux.MainActivity
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentSettingsBinding
import de.muenchen.appcenter.nimux.repositories.OtherRepository
import de.muenchen.appcenter.nimux.repositories.UsersRepository
import de.muenchen.appcenter.nimux.util.LogInLogOutLog
import de.muenchen.appcenter.nimux.util.UserSessionManager
import de.muenchen.appcenter.nimux.util.faceRecognitionPrefKey
import de.muenchen.appcenter.nimux.util.standbyBoolPrefKey
import de.muenchen.appcenter.nimux.util.systemColorPrefKey
import javax.inject.Inject
import androidx.core.content.edit
import de.muenchen.appcenter.nimux.util.systemThemePrefKey
import de.muenchen.appcenter.nimux.util.useMoneyPrefKey

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var userSessionManager: UserSessionManager

    @Inject
    lateinit var usersRepository: UsersRepository

    @Inject
    lateinit var otherRepository: OtherRepository

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(layoutInflater, container, false)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsBuildNumber.text = "App Version " + BuildConfig.VERSION_NAME
        binding.settingsAccountLoggedIn.text =
            "Eingeloggt als " + userSessionManager.getUserEMail() + " mit Rolle " + userSessionManager.getRole() + " und Daten aus Tenant: " + userSessionManager.getTenantId()

        setClickListeners()
        setUpThemeListener()
        getSavedData()
        setupColorSelection()
    }

    private fun setClickListeners() {
        binding.settingsLogoutCard.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sign_out)
                .setMessage(getString(R.string.sign_out_alert_message))
                .setPositiveButton(R.string.yes) { _, _ ->
                    otherRepository.addLoginLogoutEntry(LogInLogOutLog(false))
                        .addOnSuccessListener {
                            performLogOut()
                        }
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
        binding.settingsChangePw.setOnClickListener {
            lifecycleScope.launchWhenStarted {
                if (userSessionManager.hasAdminRole()) {
                    findNavController().navigate(SettingsFragmentDirections.actionNavSettingsToChangeMainPwFragment())
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.no_admin_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setUpThemeListener(){
        val currentTheme = sharedPrefs.getInt(
            systemThemePrefKey,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.settingsThemeToggleGroup.check(R.id.theme_button_light)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.settingsThemeToggleGroup.check(R.id.theme_button_dark)
            else -> binding.settingsThemeToggleGroup.check(R.id.theme_button_system)
        }

        binding.settingsThemeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedMode = when (checkedId) {
                    R.id.theme_button_light -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.theme_button_dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                with(sharedPrefs.edit()) {
                    putInt(systemThemePrefKey, selectedMode)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(selectedMode)
            }
        }
    }

    private fun performLogOut() {
        userSessionManager.clearSession()
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(requireContext(), MainActivity::class.java)
        requireActivity().finish()
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600

        if (isTablet) {
            (activity as AppCompatActivity)
                .supportActionBar
                ?.setDisplayHomeAsUpEnabled(false)
        }
    }

    private fun getSavedData() {
        binding.settingsStandbySwitch.isChecked =
            sharedPrefs.getBoolean(standbyBoolPrefKey, false)
        binding.settingsScanFaceSwitch.isChecked =
            sharedPrefs.getBoolean(faceRecognitionPrefKey, false)
        binding.settngsUseMoneySwitch.isChecked =
            sharedPrefs.getBoolean(useMoneyPrefKey, false)

        binding.settingsStandbySwitch.setOnCheckedChangeListener { _, b ->
            with(sharedPrefs.edit()) {
                putBoolean(standbyBoolPrefKey, b)
                apply()
            }
        }
        binding.settingsScanFaceSwitch.setOnCheckedChangeListener { _, b ->
            with(sharedPrefs.edit()) {
                putBoolean(faceRecognitionPrefKey, b)
                apply()
            }
        }
        binding.settngsUseMoneySwitch.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                var confirmed = false

                if (isChecked) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.use_money_feature_title_activate))
                        .setMessage(getString(R.string.use_money_feature_text_activate))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            confirmed = true
                            with(sharedPrefs.edit()) {
                                putBoolean(useMoneyPrefKey, true)
                                apply()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener {
                            if (!confirmed) {
                                buttonView.setOnCheckedChangeListener(null)
                                buttonView.isChecked = false
                                buttonView.setOnCheckedChangeListener(this)
                            }
                        }
                        .show()

                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.use_money_feature_title_deactivate))
                        .setMessage(getString(R.string.use_money_feature_text_deactivate))
                        .setPositiveButton(R.string.yes) { _, _ ->
                            confirmed = true
                            with(sharedPrefs.edit()) {
                                putBoolean(useMoneyPrefKey, false)
                                apply()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener {
                            if (!confirmed) {
                                buttonView.setOnCheckedChangeListener(null)
                                buttonView.isChecked = true
                                buttonView.setOnCheckedChangeListener(this)
                            }
                        }
                        .show()
                }
            }
        })
    }

    private fun setupColorSelection() {
        val savedColor = sharedPrefs.getString(systemColorPrefKey, "blue") ?: "blue"
        updateColorSelectionUI(savedColor)

        binding.colorOptionBlue.setOnClickListener { selectColor("blue") }
        binding.colorOptionRed.setOnClickListener { selectColor("red") }
        binding.colorOptionGreen.setOnClickListener { selectColor("green") }
        binding.colorOptionOrange.setOnClickListener { selectColor("orange") }
        binding.colorOptionPurple.setOnClickListener { selectColor("purple") }
    }

    private fun selectColor(colorName: String) {
        sharedPrefs.edit {
            putString(systemColorPrefKey, colorName)
        }

        requireActivity().recreate()
    }

    private fun updateColorSelectionUI(selectedColor: String) {
        //calculate radius
        val strokeWidthActive = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            resources.displayMetrics
        ).toInt()
        val strokeWidthInactive = 0

        binding.colorOptionBlue.strokeWidth = strokeWidthInactive
        binding.colorOptionRed.strokeWidth = strokeWidthInactive
        binding.colorOptionGreen.strokeWidth = strokeWidthInactive
        binding.colorOptionOrange.strokeWidth = strokeWidthInactive
        binding.colorOptionPurple.strokeWidth = strokeWidthInactive

        when (selectedColor) {
            "blue" -> binding.colorOptionBlue.strokeWidth = strokeWidthActive
            "red" -> binding.colorOptionRed.strokeWidth = strokeWidthActive
            "green" -> binding.colorOptionGreen.strokeWidth = strokeWidthActive
            "orange" -> binding.colorOptionOrange.strokeWidth = strokeWidthActive
            "purple" -> binding.colorOptionPurple.strokeWidth = strokeWidthActive
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}