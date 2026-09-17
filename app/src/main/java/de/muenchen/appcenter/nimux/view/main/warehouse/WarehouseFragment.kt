package de.muenchen.appcenter.nimux.view.main.warehouse

import android.app.AlertDialog
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
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.databinding.FragmentWarehouseBinding
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.warehouse.Pillar
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.Wall
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import de.muenchen.appcenter.nimux.repositories.ProductsRepository
import de.muenchen.appcenter.nimux.view.main.store.ShelfBottomSheetFragment
import de.muenchen.appcenter.nimux.viewmodel.manage.WarehouseViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.any
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                val currentWarehouse =
                    viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
                if (currentWarehouse != null) {
                    viewModel.saveWarehouseToFirestore(currentWarehouse)
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.warehouse)} '${currentWarehouse.name}' ${
                            getString(
                                R.string.successfully_saved
                            )
                        }",
                        Toast.LENGTH_LONG
                    ).show()
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
            val currentWarehouse =
                viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
            if (currentWarehouse != null) {
                viewModel.saveWarehouseToFirestore(currentWarehouse)
                Toast.makeText(
                    requireContext(),
                    "${getString(R.string.warehouse)} '${currentWarehouse.name}' ${getString(
                        R.string.successfully_saved
                    )}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnDeleteWarehouse.setOnClickListener {
            val currentWarehouse =
                viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
                    ?: return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_warehouse))
                .setMessage("${getString(R.string.delete_warehouse_dialog_1)} '${currentWarehouse.name}' ${getString(
                    R.string.delete_warehouse_dialog_2)}")
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.deleteCurrentWarehouse()
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.warehouse)} '${currentWarehouse.name}' ${getString(
                            R.string.deleted)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.fabEditWarehouse.setOnClickListener { showAddElementChoiceDialog() }

        binding.btnUndo.setOnClickListener {
            viewModel.undo()
        }

        binding.btnRedo.setOnClickListener {
            viewModel.redo()
        }

        binding.warehouseMapView.setOnStateChangeStartedListener {
            val currentWarehouse =
                viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value)
            if (currentWarehouse != null) {
                viewModel.saveStateForUndo(currentWarehouse)
            }
        }
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
        popupMenu.menu.add(0, addNewId, addNewId, getString(R.string.add_warehouse))

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
            hint = getString(R.string.name)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_warehouse))
            .setView(input)
            .setPositiveButton(getString(R.string.add), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

            val name = input.text.toString().ifBlank {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date())
                "${getString(R.string.new_warehouse)} $formattedDate"
            }

            val nameExists = viewModel.warehouses.value.any { it.name.equals(name, ignoreCase = true) }

            if (nameExists) {
                Toast.makeText(requireContext(), getString(R.string.warehouse_exists_dialog), Toast.LENGTH_SHORT).show()

                input.requestFocus()
                input.selectAll()
            } else {
                val newWarehouse = Warehouse(
                    name = name,
                    length = 20,
                    width = 20,
                    shelves = mutableListOf()
                )
                viewModel.addNewWarehouse(newWarehouse)

                dialog.dismiss()
            }
        }
    }

    private fun showAddElementChoiceDialog() {
        val options =
            arrayOf(getString(R.string.add_shelf), getString(R.string.add_pillar), getString(R.string.add_wall))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_element_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddShelfTypeDialog()
                    1 -> addDefaultPillar()
                    2 -> addDefaultWall()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun addDefaultWall() {
        val currentWarehouse =
            viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return

        viewModel.saveStateForUndo(currentWarehouse)

        val centerX = binding.warehouseMapView.width / 2f
        val centerY = binding.warehouseMapView.height / 2f

        val startPoint = PointF(centerX - 150f, centerY)
        val endPoint = PointF(centerX + 150f, centerY)

        val newWall = Wall(startPoint, endPoint)

        currentWarehouse.walls.add(newWall)

        binding.warehouseMapView.setWarehouseData(currentWarehouse)
        viewModel.saveWarehouseToFirestore(currentWarehouse)
    }

    //tmp, more types of shelf
    private fun showAddShelfTypeDialog() {
        val currentWarehouse =
            viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return

        val shelfTypes = arrayOf(
            getString(R.string.std_shelf),
            getString(R.string.small_shelf),
            getString(R.string.big_shelf)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.choose_shelf_typ))
            .setItems(shelfTypes) { _, which ->
                val (cols, rows) = when (which) {
                    0 -> Pair(5, 4)
                    1 -> Pair(2, 2)
                    2 -> Pair(6, 6)
                    else -> Pair(4, 4)
                }

                val newShelfName = "${getString(R.string.shelf)} ${currentWarehouse.shelves.size + 1}"
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
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    //tmp, form?
    private fun addDefaultPillar() {
        val currentWarehouse =
            viewModel.warehouses.value.getOrNull(viewModel.currentIndex.value) ?: return

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