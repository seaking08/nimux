package de.muenchen.appcenter.nimux.viewmodel.manage.users

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.Role
import de.muenchen.appcenter.nimux.model.User
import de.muenchen.appcenter.nimux.repositories.UsersRepository
import de.muenchen.appcenter.nimux.util.md5
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddUserViewModel @Inject constructor(
    private val usersRepository: UsersRepository
) : ViewModel() {

    val nameText = MutableLiveData("")
    val roleNameText = MutableLiveData("")
    val pinText = MutableLiveData("")
    val confirmPinText = MutableLiveData("")

    var role: Role = Role(name = "")
    private lateinit var newUser: User

    private val _nameEntered = MutableLiveData<Boolean>()
    val nameEntered: LiveData<Boolean> get() = _nameEntered

    private val _roleEmpty = MutableLiveData<Boolean>()
    val roleEmpty: LiveData<Boolean> get() = _roleEmpty

    private val _pinEntered = MutableLiveData<Boolean>()
    val pinEntered: LiveData<Boolean> get() = _pinEntered

    private val _pinConfirmed = MutableLiveData<Boolean>()
    val pinConfirmed: LiveData<Boolean> get() = _pinConfirmed

    private val _userAdded = MutableLiveData<Boolean>()
    val userAdded: LiveData<Boolean> get() = _userAdded

    private val _canceled = MutableLiveData<Boolean>()
    val canceled: LiveData<Boolean> get() = _canceled

    private val _userExists = MutableLiveData<Boolean>()
    val userExists: LiveData<Boolean> get() = _userExists

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean> get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean> get() = _showProgressBar

    private val _perfHapticFeedback = MutableLiveData<Boolean>()
    val perfHapticFeedback: LiveData<Boolean> get() = _perfHapticFeedback

    private val _showCredit = MutableLiveData(true)
    val showCredit: LiveData<Boolean> get() = _showCredit

    private val _processData = MutableLiveData(true)
    val processData: LiveData<Boolean> get() = _processData

    private val _requirePin = MutableLiveData(false)
    val requirePin: LiveData<Boolean> get() = _requirePin

    private val _hideKeyboard = MutableLiveData<Boolean>()
    val hideKeyboard: LiveData<Boolean> get() = _hideKeyboard

    fun addUserClick() {
        val name = nameText.value.orEmpty().trim()
        val roleText = roleNameText.value.orEmpty().trim()
        val pin = pinText.value.orEmpty()
        val confirmPin = confirmPinText.value.orEmpty()

        val isNameEntered = name.isNotBlank()
        val isRoleEntered = roleText.isNotBlank()

        _nameEntered.value = isNameEntered
        _roleEmpty.value = !isRoleEntered

        if (!isNameEntered || !isRoleEntered) {
            return
        }

        val finalRoleName = roleText.ifBlank { role.name }

        if (requirePin.value == true) {
            val isPinValid = pin.length == 4 && pin.toIntOrNull() != null
            val isPinMatch = pin == confirmPin

            _pinEntered.value = isPinValid
            _pinConfirmed.value = isPinMatch

            if (isPinValid && isPinMatch) {
                newUser = User(
                    name = name,
                    role = finalRoleName,
                    showCredit = showCredit.value ?: true,
                    collectData = processData.value ?: true,
                    pin = md5(pin)
                )
                addUser()
            }
        } else {
            _pinEntered.value = true
            newUser = User(
                name = name,
                role = finalRoleName,
                showCredit = showCredit.value ?: true,
                collectData = processData.value ?: true
            )
            addUser()
        }
    }

    fun addUser() {
        viewModelScope.launch {
            _showProgressBar.value = true
            val currentName = nameText.value.orEmpty().trim()

            if (usersRepository.connectedOnline()) {
                if (usersRepository.userExistsCheck(currentName)) {
                    _userExists.value = true
                } else {
                    usersRepository.addUser(newUser)
                    _userAdded.value = true
                }
            } else {
                _networkHint.value = true
            }
            _showProgressBar.value = false
        }
    }

    fun cancelAdd() {
        _canceled.value = true
    }

    fun networkHintShown() {
        _networkHint.value = false
    }

    fun switchShowCredit() {
        _showCredit.value = !(showCredit.value ?: true)
    }

    fun switchRequirePin() {
        val current = requirePin.value ?: false
        val nextState = !current
        _requirePin.value = nextState
        if (!nextState) doHideKeyboard()
    }

    fun switchProcessData() {
        _processData.value = !(processData.value ?: true)
    }

    private fun doHideKeyboard() {
        _hideKeyboard.value = true
    }
}