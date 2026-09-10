package de.muenchen.appcenter.nimux.view.main.store

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.content.ClipData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import de.muenchen.appcenter.nimux.R
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.warehouse.Box
import de.muenchen.appcenter.nimux.model.warehouse.LooseProduct
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.ShelfItem
import de.muenchen.appcenter.nimux.view.manage.products.drawIcon
import de.muenchen.appcenter.nimux.view.manage.products.iconMap

class ShelfBottomSheetFragment() : BottomSheetDialogFragment() {

    var selectedShelf: Shelf? = null
    var allProducts: List<Product> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var infoView: TextView
    private var shelfColumns = 0
    private var shelfRows = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_shelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findViewById<TextView>(R.id.bs_shelf_title)
        infoView = view.findViewById<TextView>(R.id.bs_shelf_boxes_info)
        recyclerView = view.findViewById<RecyclerView>(R.id.bs_boxes_recycler)

        val btnEditShelf = view.findViewById<View>(R.id.btn_edit_shelf)
        btnEditShelf.setOnClickListener {
            showEditShelfDialog()
        }

        selectedShelf?.let { shelf ->
            titleView.text = shelf.name

            shelfColumns = shelf.columnSize
            shelfRows = shelf.rowSize

            recyclerView.layoutManager = GridLayoutManager(requireContext(), shelfColumns)

            refreshGrid()
        }
    }

    private fun refreshGrid() {
        val shelf = selectedShelf ?: return
        val gridData = Array<ShelfItem?>(shelfColumns * shelfRows) { null }

        for (item in shelf.items) {
            if (item.row < shelfRows && item.column < shelfColumns) {
                val index = (item.row * shelfColumns) + item.column
                gridData[index] = item
            }
        }

        val adapter = ShelfItemGridAdapter(
            gridData = gridData,
            columns = shelfColumns,
            onSingleClick = { clickedItem, row, col ->
                if (clickedItem == null) {
                    showCreateItemDialog(row, col)
                } else {
                    showItemInfoDialog(clickedItem)
                }
            },
            onDoubleClick = { clickedItem ->
                when (clickedItem) {
                    is Box -> {
                        showEditBoxDialog(clickedItem)
                    }
                    is LooseProduct -> {
                        showProductSelectionDialog(
                            title = "Loses Produkt ändern",
                            allProducts = allProducts
                        ) { selectedProduct ->
                            clickedItem.product = selectedProduct
                            refreshGrid()
                        }
                    }
                }
            },
            onDropItem = { fromIndex, toIndex ->
                val movedItem = gridData[fromIndex]
                val targetItem = gridData[toIndex]

                if (movedItem != null) {
                    movedItem.row = toIndex / shelfColumns
                    movedItem.column = toIndex % shelfColumns
                }

                if (targetItem != null) {
                    targetItem.row = fromIndex / shelfColumns
                    targetItem.column = fromIndex % shelfColumns
                }

                refreshGrid()
            }
        )
        recyclerView.adapter = adapter
        infoView.text = "Belegte Fächer: ${shelf.items.size}"
    }

    private fun showEditShelfDialog() {
        val shelf = selectedShelf ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_shelf, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_shelf_name)
        val etColumns = dialogView.findViewById<TextInputEditText>(R.id.et_shelf_columns)
        val etRows = dialogView.findViewById<TextInputEditText>(R.id.et_shelf_rows)

        etName.setText(shelf.name)
        etColumns.setText(shelf.columnSize.toString())
        etRows.setText(shelf.rowSize.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Regal bearbeiten")
            .setView(dialogView)
            .setPositiveButton("Speichern") { _, _ ->
                val newName = etName.text.toString()
                val newCols = etColumns.text.toString().toIntOrNull() ?: shelf.columnSize
                val newRows = etRows.text.toString().toIntOrNull() ?: shelf.rowSize

                shelf.name = newName
                shelf.columnSize = newCols
                shelf.rowSize = newRows


                shelfColumns = newCols
                shelfRows = newRows

                recyclerView.layoutManager = GridLayoutManager(requireContext(), shelfColumns)
                requireView().findViewById<TextView>(R.id.bs_shelf_title).text = newName

                refreshGrid()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showEditBoxDialog(box: Box) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_box, null)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_product)
        val recyclerViewProducts = dialogView.findViewById<RecyclerView>(R.id.rv_box_products)

        recyclerViewProducts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        //function to update intern dialog list
        fun updateDialogList(dialog: androidx.appcompat.app.AlertDialog) {
            val adapter = object : RecyclerView.Adapter<BoxProductViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoxProductViewHolder {
                    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_product, parent, false)
                    return BoxProductViewHolder(v)
                }

                override fun onBindViewHolder(holder: BoxProductViewHolder, position: Int) {
                    val product = box.products[position]
                    holder.tvName.text = product.name

                    holder.itemView.setOnClickListener {
                        (box.products).removeAt(position)
                        refreshGrid()
                        updateDialogList(dialog)
                    }
                }

                override fun getItemCount(): Int = box.products.size
            }
            recyclerViewProducts.adapter = adapter
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Box bearbeiten (R${box.row} | S${box.column})")
            .setView(dialogView)
            .setPositiveButton("Fertig", null)
            .setNegativeButton("Box löschen") { _, _ ->
                (selectedShelf?.items as? MutableList)?.remove(box)
                refreshGrid()
            }
            .create()

        updateDialogList(dialog)

        btnAdd.setOnClickListener {
            showProductSelectionDialog(
                title = "Produkt zur Box hinzufügen",
                allProducts = allProducts
            ) { selectedProduct ->
                (box.products).add(selectedProduct)
                refreshGrid()
                updateDialogList(dialog)
            }
        }

        dialog.show()
    }

    private inner class BoxProductViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_product_name)
    }

    private fun showItemInfoDialog(item: ShelfItem) {
        val title = "Fach (R${item.row} | S${item.column})"
        val message = when (item) {
            is Box -> if (item.products.isNotEmpty()) {
                "Inhalt:\n" + item.products.joinToString("\n") { "• ${it.name}" }
            } else {
                "Diese Box ist leer."
            }
            is LooseProduct -> "Loses Produkt:\n• ${item.product.name}"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun showCreateItemDialog(row: Int, col: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Neues Fach belegen (R$row | S$col)")
            .setMessage("Möchtest du hier eine neue Box oder ein loses Produkt platzieren?")
            .setPositiveButton("Neue Box platzieren") { _, _ ->
                val newBox = Box(row = row, column = col, products = mutableListOf())
                (selectedShelf?.items as? MutableList)?.add(newBox)
                refreshGrid()
            }
            .setNegativeButton("Loses Produkt platzieren") { _, _ ->
                showProductSelectionDialog(
                    title = "Loses Produkt auswählen",
                    allProducts = allProducts
                ) { selectedProduct ->
                    val newLooseProduct = LooseProduct(row = row, column = col, product = selectedProduct)
                    (selectedShelf?.items as? MutableList)?.add(newLooseProduct)
                    refreshGrid()
                }
            }
            .setNeutralButton("Abbrechen", null)
            .show()
    }

    private fun showProductSelectionDialog(
        title: String,
        allProducts: List<Product>,
        onProductSelected: (Product) -> Unit
    ) {
        if (allProducts.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keine Produkte verfügbar")
                .setMessage("Es wurden keine Produkte in der Datenbank gefunden.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val productNames = allProducts.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(productNames) { _, which ->
                val selectedProduct = allProducts[which]
                onProductSelected(selectedProduct)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}

class ShelfItemGridAdapter(
    val gridData: Array<ShelfItem?>,
    private val columns: Int,
    private val onSingleClick: (ShelfItem?, Int, Int) -> Unit,
    private val onDoubleClick: (ShelfItem) -> Unit,
    private val onDropItem: (fromIndex: Int, toIndex: Int) -> Unit
) : RecyclerView.Adapter<ShelfItemGridAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.box_card)
        val tvPosition: TextView = view.findViewById(R.id.tv_box_position)
        val tvContents: TextView = view.findViewById(R.id.tv_box_contents)
        val ivIcon: ImageView = view.findViewById(R.id.iv_box_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_box_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = position / columns
        val col = position % columns
        val item = gridData[position]

        holder.tvPosition.text = "R$row | S$col"
        val context = holder.card.context

        if (item != null) {
            holder.ivIcon.visibility = View.VISIBLE

            //start drag drop
            holder.card.setOnLongClickListener {
                val clipData = ClipData.newPlainText("", "")
                val shadow = View.DragShadowBuilder(holder.card)
                it.startDragAndDrop(clipData, shadow, holder.adapterPosition, 0)
                true
            }

            val boxColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE)
            val looseProductColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryContainer, Color.GREEN)

            when (item) {
                is Box -> {
                    holder.card.setCardBackgroundColor(boxColor)
                    holder.ivIcon.setImageDrawable(drawIcon(context, "cmd_package_variant_closed", 24))
                    holder.tvContents.text = if (item.products.isNotEmpty()) item.products.joinToString(", ") { it.name } else "Leere Box"
                }
                is LooseProduct -> {
                    holder.card.setCardBackgroundColor(looseProductColor)
                    var iconName = iconMap[item.product.productIcon]
                    if (iconName == "keins") iconName = "cmd_star"
                    holder.ivIcon.setImageDrawable(drawIcon(context, iconName!!, 24))
                    holder.tvContents.text = item.product.name
                }
            }
        } else {
            val emptyColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)
            holder.card.setCardBackgroundColor(emptyColor)
            holder.ivIcon.visibility = View.GONE
            holder.tvContents.text = ""

            holder.card.setOnLongClickListener(null)
        }

        //double click listener
        var clickCount = 0
        val handler = android.os.Handler(context.mainLooper)

        holder.card.setOnClickListener {
            clickCount++
            if (clickCount == 1) {
                handler.postDelayed({
                    if (clickCount == 1) {
                        onSingleClick(item, row, col)
                    }
                    clickCount = 0
                }, 250)
            } else if (clickCount == 2) {
                clickCount = 0
                if (item != null) {
                    onDoubleClick(item)
                }
            }
        }

        holder.card.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    holder.card.alpha = 0.5f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    holder.card.alpha = 1.0f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    holder.card.alpha = 1.0f
                    val fromPosition = event.localState as? Int
                    val toPosition = holder.adapterPosition

                    if (fromPosition != null && fromPosition != toPosition) {
                        onDropItem(fromPosition, toPosition)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    holder.card.alpha = 1.0f
                    true
                }
                else -> false
            }
        }
    }

    override fun getItemCount(): Int = gridData.size
}