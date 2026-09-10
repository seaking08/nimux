package de.muenchen.appcenter.nimux.datasources

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import dagger.internal.Provider
import de.muenchen.appcenter.nimux.util.collection_warehouse
import jakarta.inject.Inject

class WarehouseDataSource @Inject constructor(
    private val tenantRefProvider: Provider<DocumentReference?>
) {
    val collectionWarehouseRef: CollectionReference?
        get() = tenantRefProvider.get()?.collection(collection_warehouse)
}