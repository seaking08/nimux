package de.muenchen.appcenter.nimux.view.main.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import de.muenchen.appcenter.nimux.repositories.WarehouseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses: StateFlow<List<Warehouse>> = _warehouses.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()


    init {
        loadWarehousesFromFirestore()
    }

    fun loadWarehousesFromFirestore() {
        viewModelScope.launch {
            try {
                var list = warehouseRepository.loadWarehousesFromFirestore().toMutableList()

                //std warehouse
                if (list.isEmpty()) {
                    val defaultWarehouse = Warehouse(
                        name = "Hauptlager",
                        length = 20,
                        width = 20,
                        shelves = mutableListOf(),
                        pillars = mutableListOf()
                    )
                    warehouseRepository.saveWarehouseToFirestore(defaultWarehouse)
                    list.add(defaultWarehouse)
                }

                _warehouses.value = list
                if (list.isNotEmpty()) {
                    _currentIndex.value = 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveWarehouseToFirestore(warehouse: Warehouse) {
        viewModelScope.launch {
            try {
                warehouseRepository.saveWarehouseToFirestore(warehouse)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNewWarehouse(newWarehouse: Warehouse) {
        viewModelScope.launch {
            try {
                warehouseRepository.saveWarehouseToFirestore(newWarehouse)
                val updatedList = _warehouses.value.toMutableList().apply {
                    add(newWarehouse)
                }
                _warehouses.value = updatedList
                _currentIndex.value = updatedList.size - 1
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteCurrentWarehouse() {
        val currentList = _warehouses.value
        val currentIndex = _currentIndex.value

        if (currentList.isEmpty() || currentIndex !in currentList.indices) return

        val warehouseToDelete = currentList[currentIndex]

        viewModelScope.launch {
            try {
                warehouseRepository.deleteWarehouseFromFirestore(warehouseToDelete.name)

                val updatedList = currentList.toMutableList().apply {
                    removeAt(currentIndex)
                }

                //std warehouse
                if (updatedList.isEmpty()) {
                    val defaultWarehouse = Warehouse(
                        name = "Hauptlager",
                        length = 20,
                        width = 20,
                        shelves = mutableListOf(),
                        pillars = mutableListOf()
                    )
                    warehouseRepository.saveWarehouseToFirestore(defaultWarehouse)
                    updatedList.add(defaultWarehouse)
                }

                _warehouses.value = updatedList

                _currentIndex.value = (currentIndex - 1).coerceAtLeast(0)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectWarehouse(index: Int) {
        if (index in _warehouses.value.indices) {
            _currentIndex.value = index
        }
    }
}