package de.muenchen.appcenter.nimux.viewmodel.manage.products

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.Role
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.repositories.UsersRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val productsRepository: ProductsRepository
) : ViewModel() {

    private val _addProductDone = MutableLiveData<Boolean>()
    val addProductDone: LiveData<Boolean>
        get() = _addProductDone

    private val _productAdded = MutableLiveData<Boolean>()
    val productAdded: LiveData<Boolean>
        get() = _productAdded

    private val _hideKeyboard = MutableLiveData<Boolean>()
    val hideKeyboard: LiveData<Boolean>
        get() = _hideKeyboard

    private val _refillable = MutableLiveData(false)
    val refillable: LiveData<Boolean>
        get() = _refillable

    val productName = MutableLiveData("")
    val productPrice = MutableLiveData("")
    val productIcon = MutableLiveData(0)
    val productStock = MutableLiveData("")
    val productRefillSize = MutableLiveData("")

    val roleNameText = MutableLiveData("")

    var selectedRole: Role? = null
        private set

    private val _performHapticFeedback = MutableLiveData<Boolean>()
    val performHapticFeedback: LiveData<Boolean>
        get() = _performHapticFeedback

    val productNameEmpty = MutableLiveData<Boolean>()
    val productPriceEmpty = MutableLiveData<Boolean>()
    val productStockEmpty = MutableLiveData<Boolean>()
    val productRefillSizeEmpty = MutableLiveData<Boolean>()
    val productRoleEmpty = MutableLiveData<Boolean>()

    val productPriceWrong = MutableLiveData<Boolean>()
    val productStockWrong = MutableLiveData<Boolean>()
    val productRefillSizeWrong = MutableLiveData<Boolean>()

    val productNameExists = MutableLiveData<Boolean>()

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean>
        get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean>
        get() = _showProgressBar

    fun addProduct() {
        val name = productName.value.orEmpty().trim()
        val priceStr = productPrice.value.orEmpty()
        val stockStr = productStock.value.orEmpty()
        val refillStr = productRefillSize.value.orEmpty()
        val roleText = roleNameText.value.orEmpty().trim()
        val isRefillable = refillable.value ?: false

        val nameIsEmpty = name.isEmpty()
        val priceIsEmpty = priceStr.isEmpty()
        val stockIsEmpty = isRefillable && stockStr.isEmpty()
        val refillIsEmpty = isRefillable && refillStr.isEmpty()
        val roleIsEmpty = roleText.isEmpty()

        productNameEmpty.value = nameIsEmpty
        productPriceEmpty.value = priceIsEmpty
        productStockEmpty.value = stockIsEmpty
        productRefillSizeEmpty.value = refillIsEmpty
        productRoleEmpty.value = roleIsEmpty

        if (nameIsEmpty || priceIsEmpty || stockIsEmpty || refillIsEmpty || roleIsEmpty) {
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

        val finalRole: Role = selectedRole?.copy(name = roleText) ?: Role(name = roleText)

        viewModelScope.launch {
            _showProgressBar.value = true
            if (usersRepository.connectedOnline()) {
                if (productsRepository.productExists(name)) {
                    productNameExists.value = true
                } else {
                    val price = parsedPrice ?: 0.0
                    val icon = productIcon.value ?: 0

                    if (isRefillable) {
                        productsRepository.addRefillableProduct(
                            name,
                            price,
                            icon,
                            parsedStock ?: 0,
                            parsedRefill ?: 0,
                            finalRole!!.name
                        )
                    } else {
                        productsRepository.addNonRefillableProduct(
                            name,
                            price,
                            icon,
                            finalRole!!
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