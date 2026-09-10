package de.muenchen.appcenter.nimux.view.main.warehouse

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.databinding.FragmentWarehouseBinding
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.warehouse.Pillar
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.view.main.store.ShelfBottomSheetFragment
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class WarehouseFragment : Fragment() {

    private var _binding: FragmentWarehouseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WarehouseViewModel by viewModels()

    @Inject
    lateinit var productsRepository: ProductsRepository

    private var allProductsList: List<Product> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWarehouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            allProductsList = withContext(Dispatchers.IO) {
                productsRepository.getAllProducts()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.warehouses.collect { warehouses ->
                        if (warehouses.isNotEmpty()) {
                            updateActiveWarehouseView()
                        }
                    }
                }

                launch {
                    viewModel.currentIndex.collect { _ ->
                        updateActiveWarehouseView()
                    }
                }
            }
        }

        binding.switchEditMode.setOnCheckedChangeListener { _, isChecked ->
            binding.warehouseMapView.isEditMode = isChecked
            binding.isEditMode = isChecked

            if (!isChecked) {
                val currentWarehouse = viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
                if (currentWarehouse != null) {
                    viewModel.saveWarehouseToFirestore(currentWarehouse)
                    Toast.makeText(requireContext(), "Lager '${currentWarehouse.name}' erfolgreich gespeichert", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.warehouseMapView.setOnShelfClickListener { clickedShelf ->
            val bottomSheet = ShelfBottomSheetFragment().apply {
                selectedShelf = clickedShelf
                allProducts = allProductsList
            }
            bottomSheet.show(childFragmentManager, "ShelfBottomSheet")
        }

        binding.btnWarehouseSelector.setOnClickListener { view ->
            showWarehouseDropdownMenu(view)
        }

        binding.btnSaveWarehouse.setOnClickListener {
            val currentWarehouse = viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
            if (currentWarehouse != null) {
                viewModel.saveWarehouseToFirestore(currentWarehouse)
                Toast.makeText(requireContext(), "Lager '${currentWarehouse.name}' gespeichert", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteWarehouse.setOnClickListener {
            val currentWarehouse = viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Lager löschen")
                .setMessage("Möchtest du das Lager '${currentWarehouse.name}' wirklich unwiderruflich löschen?")
                .setPositiveButton("Löschen") { _, _ ->
                    viewModel.deleteCurrentWarehouse()
                    Toast.makeText(requireContext(), "Lager '${currentWarehouse.name}' gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        binding.fabEditWarehouse.setOnClickListener { showAddElementChoiceDialog() }
    }

    private fun updateActiveWarehouseView() {
        val warehouses = viewModel.warehouses.value
        val index = viewModel.currentIndex.value

        if (warehouses.isNotEmpty() && index in warehouses.indices) {
            val warehouse = warehouses[index]
            binding.btnWarehouseSelector.text = warehouse.name
            binding.warehouseMapView.setWarehouseData(warehouse)
        }
    }

    private fun showWarehouseDropdownMenu(anchorView: View) {
        val warehouses = viewModel.warehouses.value
        if (warehouses.isEmpty()) return

        val popupMenu = PopupMenu(requireContext(), anchorView)

        warehouses.forEachIndexed { index, warehouse ->
            popupMenu.menu.add(0, index, index, warehouse.name)
        }

        val addNewId = warehouses.size
        popupMenu.menu.add(0, addNewId, addNewId, "+ Neues Lager erstellen...")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedId = menuItem.itemId
            if (selectedId == addNewId) {
                showCreateNewWarehouseDialog()
            } else {
                viewModel.selectWarehouse(selectedId)
            }
            true
        }
        popupMenu.show()
    }

    private fun showCreateNewWarehouseDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Lagername"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Neues Lager anlegen")
            .setView(input)
            .setPositiveButton("Erstellen") { _, _ ->
                val name = input.text.toString().ifBlank { "Neues Lager" }
                val newWarehouse = Warehouse(
                    name = name,
                    length = 20,
                    width = 20,
                    shelves = mutableListOf()
                )
                viewModel.addNewWarehouse(newWarehouse)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAddElementChoiceDialog() {
        val options = arrayOf("Regal hinzufügen", "Stütze (Infrastruktur) hinzufügen")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Element hinzufügen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddShelfTypeDialog()
                    1 -> addDefaultPillar()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    //tmp, more types of shelf
    private fun showAddShelfTypeDialog() {
        val currentWarehouse = viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return

        val shelfTypes = arrayOf(
            "Standard-Regal (5 Spalten x 4 Reihen)",
            "Kompakt-Regal (2 Spalten x 2 Reihen)",
            "Großes Lagerregal (6 Spalten x 6 Reihen)"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Regal-Typ auswählen")
            .setItems(shelfTypes) { _, which ->
                val (cols, rows) = when (which) {
                    0 -> Pair(5, 4)
                    1 -> Pair(2, 2)
                    2 -> Pair(6, 6)
                    else -> Pair(4, 4)
                }

                val newShelfName = "Regal ${currentWarehouse.shelves.size + 1}"
                val newShelf = Shelf(
                    name = newShelfName,
                    topLeft = PointF(100f, 100f),
                    topRight = PointF(200f, 100f),
                    bottomRight = PointF(200f, 200f),
                    bottomLeft = PointF(100f, 200f),
                    columnSize = cols,
                    rowSize = rows,
                    items = mutableListOf()
                )

                currentWarehouse.shelves.add(newShelf)
                binding.warehouseMapView.setWarehouseData(currentWarehouse)
                viewModel.saveWarehouseToFirestore(currentWarehouse)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    //tmp, form?
    private fun addDefaultPillar() {
        val currentWarehouse = viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return

        val centerX = binding.warehouseMapView.width / 2f
        val centerY = binding.warehouseMapView.height / 2f

        val newPillar = Pillar(PointF(centerX, centerY))
        currentWarehouse.pillars.add(newPillar)
        viewModel.saveWarehouseToFirestore(currentWarehouse)

        binding.warehouseMapView.setWarehouseData(currentWarehouse)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}