package de.muenchen.appcenter.nimux.viewmodel.manage.products

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.repositories.UsersRepository
import de.muenchen.appcenter.nimux.util.useMoneyPrefKey
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val productsRepository: ProductsRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _addProductDone = MutableLiveData<Boolean>()
    val addProductDone: LiveData<Boolean> get() = _addProductDone

    private val _productAdded = MutableLiveData<Boolean>()
    val productAdded: LiveData<Boolean> get() = _productAdded

    private val _hideKeyboard = MutableLiveData<Boolean>()
    val hideKeyboard: LiveData<Boolean> get() = _hideKeyboard

    private val _refillable = MutableLiveData(false)
    val refillable: LiveData<Boolean> get() = _refillable

    val productName = MutableLiveData("")
    val productPrice = MutableLiveData("")
    val productIcon = MutableLiveData(0)
    val productStock = MutableLiveData("")
    val productRefillSize = MutableLiveData("")

    private val _useMoney = MutableLiveData(
        sharedPreferences.getBoolean(useMoneyPrefKey, false)
    )
    val useMoney: LiveData<Boolean> get() = _useMoney

    private val _selectedRoles = MutableLiveData<Set<String>>(emptySet())
    val selectedRoles: LiveData<Set<String>> get() = _selectedRoles

    val selectedRolesText: LiveData<String> = _selectedRoles.map { roles ->
        if (roles.isEmpty()) "" else roles.joinToString(", ")
    }

    fun updateSelectedRoles(newRoles: Set<String>) {
        _selectedRoles.value = newRoles
    }

    private val _performHapticFeedback = MutableLiveData<Boolean>()
    val performHapticFeedback: LiveData<Boolean> get() = _performHapticFeedback

    val productNameEmpty = MutableLiveData<Boolean>()
    val productPriceEmpty = MutableLiveData<Boolean>()
    val productStockEmpty = MutableLiveData<Boolean>()
    val productRefillSizeEmpty = MutableLiveData<Boolean>()

    val productPriceWrong = MutableLiveData<Boolean>()
    val productStockWrong = MutableLiveData<Boolean>()
    val productRefillSizeWrong = MutableLiveData<Boolean>()

    val productNameExists = MutableLiveData<Boolean>()

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean> get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean> get() = _showProgressBar

    fun addProduct() {
        val name = productName.value.orEmpty().trim()
        val priceStr = productPrice.value.orEmpty().ifEmpty { "0,00" }
        val stockStr = productStock.value.orEmpty()
        val refillStr = productRefillSize.value.orEmpty()
        val isRefillable = refillable.value ?: false

        val nameIsEmpty = name.isEmpty()
        val priceIsEmpty = priceStr.isEmpty()
        val stockIsEmpty = isRefillable && stockStr.isEmpty()
        val refillIsEmpty = isRefillable && refillStr.isEmpty()

        productNameEmpty.value = nameIsEmpty
        productPriceEmpty.value = priceIsEmpty
        productStockEmpty.value = stockIsEmpty
        productRefillSizeEmpty.value = refillIsEmpty

        if (nameIsEmpty || priceIsEmpty || stockIsEmpty || refillIsEmpty) {
            return
        }

        val parsedPrice = priceStr.replace(',', '.').toDoubleOrNull()
        val parsedStock = stockStr.toIntOrNull()
        val parsedRefill = refillStr.toIntOrNull()

        val isPriceWrong = parsedPrice == null
        val isStockWrong = isRefillable && parsedStock == null
        val isRefillWrong = isRefillable && (parsedRefill == null || parsedRefill <= 0)

        productPriceWrong.value = isPriceWrong
        productStockWrong.value = isStockWrong
        productRefillSizeWrong.value = isRefillWrong

        if (isPriceWrong || isStockWrong || isRefillWrong) {
            return
        }

        val finalRoles: List<String> = _selectedRoles.value?.toList() ?: emptyList()

        viewModelScope.launch {
            _showProgressBar.value = true
            if (usersRepository.connectedOnline()) {
                if (productsRepository.productExists(name)) {
                    productNameExists.value = true
                } else {
                    val price = parsedPrice
                    val iconId = productIcon.value ?: 0

                    if (isRefillable) {
                        productsRepository.addRefillableProduct(
                            name,
                            price,
                            iconId,
                            parsedStock ?: 0,
                            parsedRefill ?: 0,
                            finalRoles
                        )
                    } else {
                        productsRepository.addNonRefillableProduct(
                            name,
                            price,
                            iconId,
                            finalRoles
                        )
                    }
                    _productAdded.value = true
                }
            } else {
                _networkHint.value = true
            }
            _showProgressBar.value = false
        }
    }

    fun cancelAdd() {
        _addProductDone.value = true
        doHideKeyboard()
    }

    fun changeRefill() {
        _refillable.value = !(refillable.value ?: false)
        productStockWrong.value = false
        productStockEmpty.value = false
        productRefillSizeEmpty.value = false
        productRefillSizeWrong.value = false
        doHideKeyboard()
    }

    private fun doHideKeyboard() {
        _hideKeyboard.value = true
    }

    fun doneHideKeyboard() {
        _hideKeyboard.value = false
    }

    fun networkHintShown() {
        _networkHint.value = false
    }
}