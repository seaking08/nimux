package de.muenchen.appcenter.nimux.repositories

import android.graphics.PointF
import de.muenchen.appcenter.nimux.datasources.WarehouseDataSource
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.warehouse.Box
import de.muenchen.appcenter.nimux.model.warehouse.DockOrientation
import de.muenchen.appcenter.nimux.model.warehouse.LooseProduct
import de.muenchen.appcenter.nimux.model.warehouse.Pillar
import de.muenchen.appcenter.nimux.model.warehouse.Shelf
import de.muenchen.appcenter.nimux.model.warehouse.ShelfItem
import de.muenchen.appcenter.nimux.model.warehouse.Warehouse
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val warehouseDataSource: WarehouseDataSource
) {
    suspend fun deleteWarehouseFromFirestore(warehouseName: String) {
        val collectionRef = warehouseDataSource.collectionWarehouseRef ?: return
        collectionRef.document(warehouseName).delete().await()
    }

    suspend fun loadWarehousesFromFirestore(): List<Warehouse> {
        val collectionRef = warehouseDataSource.collectionWarehouseRef ?: return emptyList()
        val snapshot = collectionRef.get().await()

        return snapshot.documents.mapNotNull { doc -> parseWarehouse(doc) }
    }

    private fun parseWarehouse(doc: com.google.firebase.firestore.DocumentSnapshot): Warehouse? {
        val name = doc.getString("name") ?: return null
        val length = doc.getLong("length")?.toInt() ?: 20
        val width = doc.getLong("width")?.toInt() ?: 20

        val dockPosition = parsePoint(doc.get("dockPosition") as? Map<*, *>, defaultX = 150f)
        val dockOrientation = parseDockOrientation(doc.getString("dockOrientation"))

        val shelvesData = doc.get("shelves") as? List<Map<String, Any>> ?: emptyList()
        val shelvesList = parseShelves(shelvesData)

        val pillarsData = doc.get("pillars") as? List<Map<String, Any>> ?: emptyList()
        val pillarsList = parsePillars(pillarsData)

        return Warehouse(
            name = name,
            length = length,
            width = width,
            shelves = shelvesList,
            pillars = pillarsList,
            dockPosition = dockPosition,
            dockOrientation = dockOrientation
        )
    }

    private fun parsePoint(map: Map<*, *>?, defaultX: Float = 0f, defaultY: Float = 0f): PointF {
        val x = (map?.get("x") as? Number)?.toFloat() ?: defaultX
        val y = (map?.get("y") as? Number)?.toFloat() ?: defaultY
        return PointF(x, y)
    }

    private fun parseDockOrientation(orientationStr: String?): DockOrientation {
        return try {
            DockOrientation.valueOf(orientationStr ?: "HORIZONTAL")
        } catch (e: IllegalArgumentException) {
            DockOrientation.HORIZONTAL
        }
    }

    private fun parseShelves(shelvesData: List<Map<String, Any>>): MutableList<Shelf> {
        return shelvesData.map { shelfMap ->
            val shelfName = shelfMap["name"] as? String ?: ""
            val columnSize = (shelfMap["columnSize"] as? Long)?.toInt() ?: 4
            val rowSize = (shelfMap["rowSize"] as? Long)?.toInt() ?: 4

            val topLeft = parsePoint(shelfMap["topLeft"] as? Map<*, *>)
            val topRight = parsePoint(shelfMap["topRight"] as? Map<*, *>)
            val bottomRight = parsePoint(shelfMap["bottomRight"] as? Map<*, *>)
            val bottomLeft = parsePoint(shelfMap["bottomLeft"] as? Map<*, *>)

            val itemsData = shelfMap["items"] as? List<Map<String, Any>> ?: emptyList()
            val itemsList = parseShelfItems(itemsData)

            Shelf(
                name = shelfName,
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft,
                columnSize = columnSize,
                rowSize = rowSize,
                items = itemsList
            )
        }.toMutableList()
    }

    private fun parseShelfItems(itemsData: List<Map<String, Any>>): MutableList<ShelfItem> {
        return itemsData.mapNotNull { itemMap ->
            val type = itemMap["type"] as? String
            val row = (itemMap["row"] as? Long)?.toInt() ?: 0
            val column = (itemMap["column"] as? Long)?.toInt() ?: 0

            when (type) {
                "loose" -> {
                    val productName = itemMap["productName"] as? String ?: ""
                    LooseProduct(row = row, column = column, product = Product(name = productName))
                }
                "box" -> {
                    val productNames = itemMap["productNames"] as? List<String> ?: emptyList()
                    val products = productNames.map { Product(name = it) }.toMutableList()
                    Box(row = row, column = column, products = products)
                }
                else -> null
            }
        }.toMutableList()
    }

    private fun parsePillars(pillarsData: List<Map<String, Any>>): MutableList<Pillar> {
        return pillarsData.map { pillarMap ->
            val posMap = pillarMap["position"] as? Map<*, *>
            Pillar(position = parsePoint(posMap))
        }.toMutableList()
    }

    suspend fun saveWarehouseToFirestore(warehouse: Warehouse) {
        val collectionRef = warehouseDataSource.collectionWarehouseRef ?: return

        val firestoreMap = hashMapOf(
            "name" to warehouse.name,
            "length" to warehouse.length,
            "width" to warehouse.width,
            "dockPosition" to mapOf("x" to warehouse.dockPosition.x, "y" to warehouse.dockPosition.y),
            "dockOrientation" to warehouse.dockOrientation.name,
            "shelves" to warehouse.shelves.map { shelf ->
                mapOf(
                    "name" to shelf.name,
                    "columnSize" to shelf.columnSize,
                    "rowSize" to shelf.rowSize,
                    "topLeft" to mapOf("x" to shelf.topLeft.x, "y" to shelf.topLeft.y),
                    "topRight" to mapOf("x" to shelf.topRight.x, "y" to shelf.topRight.y),
                    "bottomRight" to mapOf("x" to shelf.bottomRight.x, "y" to shelf.bottomRight.y),
                    "bottomLeft" to mapOf("x" to shelf.bottomLeft.x, "y" to shelf.bottomLeft.y),
                    "items" to shelf.items.map { item ->
                        when (item) {
                            is LooseProduct -> mapOf(
                                "type" to "loose",
                                "row" to item.row,
                                "column" to item.column,
                                "productName" to item.product.name
                            )
                            is Box -> mapOf(
                                "type" to "box",
                                "row" to item.row,
                                "column" to item.column,
                                "productNames" to item.products.map { it.name }
                            )
                        }
                    }
                )
            },
            "pillars" to warehouse.pillars.map { pillar ->
                mapOf(
                    "position" to mapOf("x" to pillar.position.x, "y" to pillar.position.y)
                )
            }
        )

        collectionRef.document(warehouse.name).set(firestoreMap).await()
    }
}