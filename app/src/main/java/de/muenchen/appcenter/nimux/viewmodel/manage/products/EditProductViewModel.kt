package de.muenchen.appcenter.nimux.viewmodel.manage.products

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.util.round
import de.muenchen.appcenter.nimux.util.useMoneyPrefKey
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val productsRepository: ProductsRepository,
    private val savedStateHandle: SavedStateHandle,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    var product: Product = savedStateHandle.get<Product>("product")
        ?: error("Product missing in SavedStateHandle")
    val productIconId = MutableLiveData(product.productIcon)

    val productRefillSize = MutableLiveData(product.refillSize.toString())
    val productStock = MutableLiveData(product.currentStock.toString())

    private val _refillable = MutableLiveData(product.refillSize > 0)
    val refillable: LiveData<Boolean> get() = _refillable

    val priceInput = MutableLiveData(String.format("%.2f", product.price))

    private val _selectedRoles = MutableLiveData<Set<String>>(product.roles?.toSet() ?: emptySet())
    val selectedRoles: LiveData<Set<String>> get() = _selectedRoles

    val selectedRolesText: LiveData<String> = _selectedRoles.map { roles ->
        if (roles.isEmpty()) "" else roles.joinToString(", ")
    }

    private val _useMoney = MutableLiveData(
        sharedPreferences.getBoolean(useMoneyPrefKey, false)
    )
    val useMoney: LiveData<Boolean> get() = _useMoney

    fun checkMoneySetting(){
        _useMoney.value = sharedPreferences.getBoolean(useMoneyPrefKey, false)
    }

    fun updateSelectedRoles(newRoles: Set<String>) {
        _selectedRoles.value = newRoles
    }

    fun changeRefill() {
        doHideKeyboard()
        _refillable.value = !(refillable.value ?: false)
    }

    private val _priceInputWrong = MutableLiveData<Boolean>()
    val priceInputWrong: LiveData<Boolean> get() = _priceInputWrong

    private val _stockInputWrong = MutableLiveData<Boolean>()
    val stockInputWrong: LiveData<Boolean> get() = _stockInputWrong

    private val _refillInputWrong = MutableLiveData<Boolean>()
    val refillInputWrong: LiveData<Boolean> get() = _refillInputWrong

    private val _canceled = MutableLiveData<Boolean>()
    val canceled: LiveData<Boolean> get() = _canceled

    private val _updated = MutableLiveData<Boolean>()
    val updated: LiveData<Boolean> get() = _updated

    private val _networkHint = MutableLiveData<Boolean>()
    val networkHint: LiveData<Boolean> get() = _networkHint

    private val _showProgressBar = MutableLiveData<Boolean>()
    val showProgressBar: LiveData<Boolean> get() = _showProgressBar

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

        val finalRoles: List<String> = _selectedRoles.value?.toList() ?: emptyList()

        viewModelScope.launch {
            _showProgressBar.value = true
            if (productsRepository.connectedOnline()) {
                _networkHint.value = false

                val finalPrice = (parsedPrice ?: 0.0).round(2)
                val finalIconId = productIconId.value ?: 0
                val finalStock = if (isRefillable) (parsedStock ?: 0) else 0
                val finalRefill = if (isRefillable) (parsedRefill ?: 0) else 0

                productsRepository.updateProduct(
                    product.stringSortID,
                    finalPrice,
                    finalIconId,
                    finalStock,
                    finalRefill,
                    finalRoles
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
    val hideKeyboard: LiveData<Boolean> get() = _hideKeyboard

    private fun doHideKeyboard() {
        _hideKeyboard.value = true
    }

    fun doneHideKeyboard() {
        _hideKeyboard.value = false
    }
}