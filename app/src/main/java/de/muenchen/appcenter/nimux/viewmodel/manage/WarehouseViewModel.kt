package de.muenchen.appcenter.nimux.viewmodel.manage

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.muenchen.appcenter.nimux.model.warehouse.Box
import de.muenchen.appcenter.nimux.model.warehouse.LooseProduct
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

    private val undoStack = mutableListOf<Warehouse>()
    private val redoStack = mutableListOf<Warehouse>()

    private val defaultWarehouse: Warehouse = Warehouse(
        name = "Hauptlager",
        length = 20,
        width = 20,
        shelves = mutableListOf(),
        pillars = mutableListOf()
    )

    init {
        loadWarehousesFromFirestore()
    }

    fun loadWarehousesFromFirestore() {
        viewModelScope.launch {
            try {
                val list = warehouseRepository.loadWarehousesFromFirestore().toMutableList()

                if (list.isEmpty()) {
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

                if (updatedList.isEmpty()) {
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

    fun saveStateForUndo(warehouse: Warehouse) {
        val warehouseCopy = Warehouse(
            name = warehouse.name,
            length = warehouse.length,
            width = warehouse.width,
            dockPosition = PointF(warehouse.dockPosition.x, warehouse.dockPosition.y),
            dockOrientation = warehouse.dockOrientation,
            shelves = warehouse.shelves.map { shelf ->
                shelf.copy(
                    topLeft = PointF(shelf.topLeft.x, shelf.topLeft.y),
                    topRight = PointF(shelf.topRight.x, shelf.topRight.y),
                    bottomRight = PointF(shelf.bottomRight.x, shelf.bottomRight.y),
                    bottomLeft = PointF(shelf.bottomLeft.x, shelf.bottomLeft.y),
                    items = shelf.items.map { item ->
                        when (item) {
                            is LooseProduct -> item.copy(product = item.product.copy())
                            is Box -> item.copy(products = item.products.map { it.copy() }.toMutableList())
                        }
                    }.toMutableList()
                )
            }.toMutableList(),
            pillars = warehouse.pillars.map { pillar ->
                pillar.copy(position = PointF(pillar.position.x, pillar.position.y))
            }.toMutableList()
        )

        undoStack.add(warehouseCopy)
        redoStack.clear()
    }

    fun undo() {
        val currentWarehouse = _warehouses.value.getOrNull(_currentIndex.value) ?: return
        if (undoStack.isNotEmpty()) {
            val currentCopy = createDeepCopy(currentWarehouse)
            redoStack.add(currentCopy)

            val previousState = undoStack.removeAt(undoStack.size - 1)
            updateWarehouseState(previousState)
        }
    }

    fun redo() {
        val currentWarehouse = _warehouses.value.getOrNull(_currentIndex.value) ?: return
        if (redoStack.isNotEmpty()) {
            val currentCopy = createDeepCopy(currentWarehouse)
            undoStack.add(currentCopy)

            val nextState = redoStack.removeAt(redoStack.size - 1)
            updateWarehouseState(nextState)
        }
    }

    private fun createDeepCopy(warehouse: Warehouse): Warehouse {
        return Warehouse(
            name = warehouse.name,
            length = warehouse.length,
            width = warehouse.width,
            dockPosition = PointF(warehouse.dockPosition.x, warehouse.dockPosition.y),
            dockOrientation = warehouse.dockOrientation,
            shelves = warehouse.shelves.map { shelf ->
                shelf.copy(
                    topLeft = PointF(shelf.topLeft.x, shelf.topLeft.y),
                    topRight = PointF(shelf.topRight.x, shelf.topRight.y),
                    bottomRight = PointF(shelf.bottomRight.x, shelf.bottomRight.y),
                    bottomLeft = PointF(shelf.bottomLeft.x, shelf.bottomLeft.y),
                    items = shelf.items.map { item ->
                        when (item) {
                            is LooseProduct -> item.copy(product = item.product.copy())
                            is Box -> item.copy(products = item.products.map { it.copy() }.toMutableList())
                        }
                    }.toMutableList()
                )
            }.toMutableList(),
            pillars = warehouse.pillars.map { pillar ->
                pillar.copy(position = PointF(pillar.position.x, pillar.position.y))
            }.toMutableList()
        )
    }

    private fun updateWarehouseState(newState: Warehouse) {
        val list = _warehouses.value.toMutableList()
        val index = _currentIndex.value
        if (index in list.indices) {
            list[index] = newState
            _warehouses.value = emptyList()
            _warehouses.value = list
        }
    }
}