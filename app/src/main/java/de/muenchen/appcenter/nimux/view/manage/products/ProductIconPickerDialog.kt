package de.muenchen.appcenter.nimux.view.manage.products

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import de.muenchen.appcenter.nimux.R
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import de.muenchen.appcenter.nimux.repositories.ProductIconRepository

/*
import de.muenchen.appcenter.nimux.util.product_icon_bottle
import de.muenchen.appcenter.nimux.util.product_icon_can
import de.muenchen.appcenter.nimux.util.product_icon_cookie
import de.muenchen.appcenter.nimux.util.product_icon_cup
import de.muenchen.appcenter.nimux.util.product_icon_egg
import de.muenchen.appcenter.nimux.util.product_icon_fastfood
import de.muenchen.appcenter.nimux.util.product_icon_fridge
import de.muenchen.appcenter.nimux.util.product_icon_icecream
import de.muenchen.appcenter.nimux.util.product_icon_none
import de.muenchen.appcenter.nimux.util.product_icon_pizza
import de.muenchen.appcenter.nimux.util.product_icon_tea
import de.muenchen.appcenter.nimux.util.product_icon_water

enum class ProductIcon(
    val nameRes: Int,
    val drawableRes: Int?,
    val iconValue: Int // Hinweis: Falls 'product_icon_cup' etc. Strings sind, ändere 'Int' zu 'String'
) {
    NONE(R.string.none, null, product_icon_none),
    CUP(R.string.cup, R.drawable.ic_round_free_breakfast_24, product_icon_cup),
    BOTTLE(R.string.bottle, R.drawable.ic_bottle_wine, product_icon_bottle),
    WATER(R.string.water, R.drawable.ic_round_local_drink_24, product_icon_water),
    FASTFOOD(R.string.fastfood, R.drawable.ic_round_fastfood_24, product_icon_fastfood),
    FRIDGE(R.string.fridge, R.drawable.ic_round_kitchen_24, product_icon_fridge),
    COOKIE(R.string.cookie, R.drawable.ic_round_cookie_24, product_icon_cookie),
    EGG(R.string.egg_icon_name, R.drawable.ic_baseline_egg_24, product_icon_egg),
    TEA(R.string.tea_icon_name, R.drawable.ic_baseline_emoji_food_beverage_24, product_icon_tea),
    PIZZA(R.string.pizza_icon_name, R.drawable.ic_baseline_local_pizza_24, product_icon_pizza),
    ICECREAM(R.string.ice_cream_icon_name, R.drawable.ic_baseline_icecream_24, product_icon_icecream),
    CAN(R.string.product_icon_can_name, R.drawable.ic_coke_can, product_icon_can)
} */

val productIconRepository = ProductIconRepository()
val iconMap = productIconRepository.iconMap
val foldersList = productIconRepository.foldersList

fun drawIcon(context: Context,iconName: String, size: Int): Drawable{
    return if (iconName.startsWith("faw_")) {
        IconicsDrawable(context, FontAwesome.getIcon(iconName))
    } else {
        IconicsDrawable(context, CommunityMaterial.getIcon(iconName))
    }.apply {
        colorInt = Color.DKGRAY
        sizeDp = size
    }
}

class FolderAdapter(
    private val context: Context,
    private val folders: List<ProductIconRepository.FolderItem>,
    private val onFolderSelected: (ProductIconRepository.FolderItem) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFolderIcon: ImageView = view.findViewById(R.id.ivFolderIcon)
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.tvFolderName.text = folder.name

        try {
            val iconName = iconMap[folder.previewIconId] ?: "KEINS"

            val drawable = drawIcon(context, iconName, 28)

            holder.ivFolderIcon.setImageDrawable(drawable)
        } catch (e: Exception) {
            holder.ivFolderIcon.setImageDrawable(null)
        }

        holder.itemView.setOnClickListener {
            onFolderSelected(folder)
        }
    }

    override fun getItemCount() = folders.size
}

class IconAdapter(
    private val context: Context,
    private val iconIds: List<Int>,
    private val onIconSelected: (Int) -> Unit
) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

    inner class IconViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_icon, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val iconId = iconIds[position]
        val iconName = iconMap[iconId] ?: "KEINS"

        try {
            val drawable = drawIcon(context, iconName, 24)

            holder.imageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            holder.imageView.setImageDrawable(null)
        }

        holder.itemView.setOnClickListener {
            onIconSelected(iconId)
        }
    }

    override fun getItemCount() = iconIds.size
}

class IconPickerAlertDialog(
    private val onIconIdSelected: (Int) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_icon_picker, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewIcons)
        val layoutHeader = view.findViewById<LinearLayout>(R.id.layoutHeader)
        val btnBack = view.findViewById<Button>(R.id.btnBackToFolders)
        val tvFolderTitle = view.findViewById<TextView>(R.id.tvCurrentFolderTitle)

        fun showIconGridView(folder: ProductIconRepository.FolderItem) {
            layoutHeader.visibility = View.VISIBLE
            tvFolderTitle.text = folder.name

            recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)

            recyclerView.adapter = IconAdapter(requireContext(), folder.iconIds) { selectedIconId ->
                onIconIdSelected(selectedIconId)
                dismiss()
            }
        }

        fun showFolderView() {
            layoutHeader.visibility = View.GONE
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
            recyclerView.adapter = FolderAdapter(requireContext(), foldersList) { selectedFolder ->
                showIconGridView(selectedFolder)
            }
        }

        btnBack.setOnClickListener {
            showFolderView()
        }

        showFolderView()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Wähle einen Ordner")
            .setView(view)
            .setNegativeButton("Abbrechen") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}