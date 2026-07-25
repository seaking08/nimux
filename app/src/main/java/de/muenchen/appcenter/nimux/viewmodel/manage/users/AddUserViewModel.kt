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
class AddUserViewModel @Inject constructor(private val usersRepository: UsersRepository) :
    ViewModel() {

    var nameText: String = ""
    var roleNameText: String = ""
    var role: Role = Role(name = "")
    var pinText: String = ""
    var confirmPinText: String = ""
    private lateinit var newUser: User

    private val _nameEntered = MutableLiveData<Boolean>()
    val nameEntered: LiveData<Boolean>
        get() = _nameEntered

    private val _pinEntered = MutableLiveData<Boolean>()
    val pinEntered: LiveData<Boolean>
        get() = _pinEntered

    private val _pinConfirmed = MutableLiveData<Boolean>()
    val pinConfirmed: LiveData<Boolean>
        get() = _pinConfirmed

    private val _userAdded = MutableLiveData<Boolean>()
    val userAdded: LiveData<Boolean>
        get() = _userAdded

    private val _canceled = MutableLiveData<Boolean>()
    val canceled: LiveData<Boolean>
        get() = _canceled

    private val _userExists = MutableLiveData<Boolean>()
    val userExists: LiveData<Boolean>
        get() = _userExists

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean>
        get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean>
        get() = _showProgressBar

    private val _perfHapticFeedback = MutableLiveData<Boolean>()
    val perfHapticFeedback: LiveData<Boolean>
        get() = _perfHapticFeedback

    fun addUserClick() {
        _nameEntered.value = nameText.isNotBlank()

        val selectedName = roleNameText.ifBlank { role.name }
        val finalRole = if (selectedName.isNotBlank()) Role(name = selectedName) else null

        if (requirePin.value == true) {
            _pinEntered.value = pinText.length == 4 && pinText.toIntOrNull() != null
            _pinConfirmed.value = pinText == confirmPinText
            if (nameEntered.value == true && pinEntered.value == true && pinConfirmed.value == true) {
                newUser = User(
                    name = nameText,
                    role = finalRole!!.name,
                    showCredit = showCredit.value ?: true,
                    collectData = processData.value ?: true,
                    pin = md5(pinText)
                )
                addUser()
            }
        } else {
            _pinEntered.value = true
            if (nameEntered.value == true) {
                newUser = User(
                    name = nameText,
                    role = finalRole!!.name,
                    showCredit = showCredit.value ?: true,
                    collectData = processData.value ?: true
                )
                addUser()
            }
        }
    }

    private val _showCredit = MutableLiveData(true)
    val showCredit: LiveData<Boolean>
        get() = _showCredit

    private val _processData = MutableLiveData(true)
    val processData: LiveData<Boolean>
        get() = _processData

    private val _requirePin = MutableLiveData(false)
    val requirePin: LiveData<Boolean>
        get() = _requirePin

    fun addUser() {
        viewModelScope.launch {
            _showProgressBar.value = true
            if (usersRepository.connectedOnline()) {
                if (usersRepository.userExistsCheck(nameText))
                    _userExists.value = true
                else {
                    usersRepository.addUser(newUser)
                    _userAdded.value = true
                }
            } else _networkHint.value = true
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

    private val _hideKeyboard = MutableLiveData<Boolean>()
    val hideKeyboard: LiveData<Boolean>
        get() = _hideKeyboard

    private fun doHideKeyboard() {
        _hideKeyboard.value = true
    }
}