package de.muenchen.appcenter.nimux.model.warehouse

import de.muenchen.appcenter.nimux.model.Product

sealed class ShelfItem {
    abstract var row: Int
    abstract var column: Int
}

data class Box(
    override var row: Int = 0,
    override var column: Int = 0,
    val products: MutableList<Product> = mutableListOf()
) : ShelfItem()

data class LooseProduct(
    override var row: Int = 0,
    override var column: Int = 0,
    var product: Product = Product()
) : ShelfItem()