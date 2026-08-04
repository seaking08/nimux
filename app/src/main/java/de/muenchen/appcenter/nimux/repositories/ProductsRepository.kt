package de.muenchen.appcenter.nimux.repositories

import com.google.firebase.firestore.Query
import de.muenchen.appcenter.nimux.datasources.ProductDataSource
import de.muenchen.appcenter.nimux.datasources.UserDataSource
import de.muenchen.appcenter.nimux.model.Product
import de.muenchen.appcenter.nimux.model.Role
import java.util.Date
import javax.inject.Inject

class ProductsRepository @Inject constructor() {

    @Inject
    lateinit var productDataSource: ProductDataSource
    @Inject
    lateinit var userDataSource: UserDataSource

    suspend fun connectedOnline(): Boolean {
        return productDataSource.checkOnlineConnection()
    }

    suspend fun productExists(productName: String): Boolean {
        return productDataSource.productExistsCheck(productName)
    }

    fun addRefillableProduct(
        name: String,
        price: Double,
        productIcon: Int,
        currentStock: Int,
        refillSize: Int,
        role: String,
    ) {
        productDataSource.addProduct(
            Product(
                name,
                price,
                productIcon,
                currentStock,
                refillSize,
                role
            )
        )
    }

    fun addNonRefillableProduct(
        name: String,
        price: Double,
        productIcon: Int,
        role: Role
    ) {
        productDataSource.addProduct(Product(name, price, productIcon, role = role.name))
    }

    fun getProductQuery(): Query {
        return productDataSource.getProductQuery().orderBy("name")
    }

    fun getProductQueryByRole(userRole: String?, isSuperUser: Boolean): Query {
        val baseQuery = productDataSource.getProductQuery()

        if (isSuperUser) {
            return baseQuery
        }

        val targetRoles: List<Any?> = listOfNotNull(userRole) + listOf("", null)

        return baseQuery.whereIn("role", targetRoles.distinct())
    }

    //TMP JUST FOR ME
    suspend fun fixLegacyProductsWithoutRole() {
        productDataSource.fixLegacyProductsWithoutRole()
        productDataSource.fixLegacyProductIcons()
    }

    fun getUserStatQuery(userId: String): Query {
        return productDataSource.getUserStatQuery(userId)
    }

    fun getTotalStatQuery(): Query {
        return productDataSource.getTotalStatQuery()
    }

    fun deleteProduct(id: String) {
        productDataSource.deleteProduct(id)
    }

    fun getProductLogsQueryWithTimerangeAndOrderByTimestamp(
        timeRange: Date,
        order: Query.Direction
    ): Query {
        return productDataSource.getProductLogsQueryWithTimerangeAndOrderByTimestamp(
            timeRange,
            order
        )
    }

    suspend fun addStock(id: String, amount: Int) {
        productDataSource.addStock(id, amount)
    }

    suspend fun updateProduct(
        id: String,
        price: Double,
        productIcon: Int,
        currentStock: Int,
        refillSize: Int,
        role: String?
    ) {
        productDataSource.updateProduct(id, price, productIcon, currentStock, refillSize, role)
    }

    suspend fun productBought(
        userId: String,
        product: Product,
        amount: Int,
        faceDetected: Boolean,
        productDetected: Boolean,
    ) {
        val curUser = userDataSource.getUser(userId)
        productDataSource.productBought(
            userId,
            product,
            amount,
            faceDetected,
            productDetected,
            curUser
        )
    }

    suspend fun getProduct(id: String): Product {
        return productDataSource.getProduct(id)
    }

    suspend fun getAllProducts(): List<Product> = productDataSource.getAllProducts()

    suspend fun getAllProductsByRole(role: String, isSuperUser: Boolean): List<Product> {
        val allProducts = productDataSource.getAllProducts()
        if (isSuperUser) {
            return allProducts
        }
        return allProducts.filter { it.role == role }
    }
}