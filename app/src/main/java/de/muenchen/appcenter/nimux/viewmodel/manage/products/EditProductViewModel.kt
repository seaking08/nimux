package de.muenchen.appcenter.nimux.viewmodel.manage.products

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.Role
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.util.round
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val productsRepository: ProductsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    var product: Product = savedStateHandle.get<Product>("product")
        ?: error("Product missing in SavedStateHandle")

    val productIcon = MutableLiveData(product.productIcon)
    val productRefillSize = MutableLiveData(product.refillSize.toString())
    val productStock = MutableLiveData(product.currentStock.toString())

    private val _refillable = MutableLiveData(product.refillSize > 0)
    val refillable: LiveData<Boolean> get() = _refillable

    val priceInput = MutableLiveData(String.format("%.2f", product.price))

    val roleNameText = MutableLiveData(product.role ?: "")

    var selectedRole: Role? = product.role?.let { Role(name = it) }
        private set

    fun changeRefill() {
        doHideKeyboard()
        _refillable.value = !(refillable.value ?: false)
    }

    private val _priceInputWrong = MutableLiveData<Boolean>()
    val priceInputWrong: LiveData<Boolean>
        get() = _priceInputWrong

    private val _stockInputWrong = MutableLiveData<Boolean>()
    val stockInputWrong: LiveData<Boolean>
        get() = _stockInputWrong

    private val _refillInputWrong = MutableLiveData<Boolean>()
    val refillInputWrong: LiveData<Boolean>
        get() = _refillInputWrong

    private val _canceled = MutableLiveData<Boolean>()
    val canceled: LiveData<Boolean>
        get() = _canceled

    private val _updated = MutableLiveData<Boolean>()
    val updated: LiveData<Boolean>
        get() = _updated

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean>
        get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean>
        get() = _showProgressBar

    fun setSelectedRole(roleName: String, isSuperRole: Boolean = false) {
        this.selectedRole = Role(name = roleName, superRole = isSuperRole)
        this.roleNameText.value = roleName
    }

    fun updateProduct() {
        val isRefillable = _refillable.value ?: false
        val parsedPrice = priceInput.value?.replace(',', '.')?.toDoubleOrNull()
        val parsedStock = productStock.value?.toIntOrNull()
        val parsedRefill = productRefillSize.value?.toIntOrNull()

        _priceInputWrong.value = parsedPrice == null
        _stockInputWrong.value = parsedStock == null && isRefillable
        _refillInputWrong.value = (parsedRefill == null || parsedRefill <= 0) && isRefillable

        val isPriceValid = _priceInputWrong.value == false
        val isStockValid = _stockInputWrong.value == false
        val isRefillValid = _refillInputWrong.value == false

        val isValid = if (isRefillable) {
            isPriceValid && isStockValid && isRefillValid
        } else {
            isPriceValid
        }

        if (!isValid) return

        val nameText = roleNameText.value?.trim() ?: ""
        val finalRoleName: String? = nameText.ifBlank { null }

        viewModelScope.launch {
            _showProgressBar.value = true
            if (productsRepository.connectedOnline()) {
                _networkHint.value = false

                val finalPrice = (parsedPrice ?: 0.0).round(2)
                val finalIcon = productIcon.value ?: 0
                val finalStock = if (isRefillable) (parsedStock ?: 0) else 0
                val finalRefill = if (isRefillable) (parsedRefill ?: 0) else 0

                productsRepository.updateProduct(
                    product.stringSortID,
                    finalPrice,
                    finalIcon,
                    finalStock,
                    finalRefill,
                    finalRoleName
                )
            } else {
                _networkHint.value = true
            }

            product = productsRepository.getProduct(product.stringSortID)
            _showProgressBar.value = false
            _updated.value = true
        }
    }

    fun cancel() {
        _canceled.value = true
    }

    fun networkHintShown() {
        _networkHint.value = false
    }

    private val _hideKeyboard = MutableLiveData<Boolean>()
    val hideKeyboard: LiveData<Boolean>
        get() = _hideKeyboard

    private fun doHideKeyboard() {
        _hideKeyboard.value = true
    }

    fun doneHideKeyboard() {
        _hideKeyboard.value = false
    }
}