package de.muenchen.appcenter.nimux.datasources

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import dagger.internal.Provider
import de.muenchen.appcenter.nimux.model.Role
import de.muenchen.appcenter.nimux.model.User
import de.muenchen.appcenter.nimux.util.await
import de.muenchen.appcenter.nimux.util.collection_suggest_users
import de.muenchen.appcenter.nimux.util.collection_suggest_users_role
import de.muenchen.appcenter.nimux.util.stringToStringSortID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class UserSuggestionDataSource @Inject constructor(
    private val tenantRefProvider: Provider<DocumentReference>
) {
    @Inject
    lateinit var userDataSource: UserDataSource
    @Inject
    lateinit var productDataSoure: ProductDataSource

    private val userSuggestionsRef
        get() = tenantRefProvider.get()
            ?.collection(collection_suggest_users)
            ?: throw IllegalStateException("Tenant missing – user is not logged in")

    private val userRolesRef
        get() = tenantRefProvider.get()
            ?.collection(collection_suggest_users_role)
            ?: throw IllegalStateException("Tenant missing – user is not logged in")

    fun addUserSuggestion(user: User) {
        userSuggestionsRef.document(user.stringSortID).set(user)
    }

    suspend fun getUserSuggestionCount(): Int {
        val res = CompletableDeferred<Int>()
        var count = 0
        userSuggestionsRef.get().addOnSuccessListener {
            it.forEach { _ ->
                count++
            }
            res.complete(count)
        }.addOnFailureListener { res.complete(count) }
        return res.await()
    }

    suspend fun userSuggestionExistsCheck(userName: String): Boolean {
        val res = CompletableDeferred<Boolean>()
        var userExists = false
        userSuggestionsRef.get().addOnSuccessListener { result ->
            for (document in result) {
                if (document.id == stringToStringSortID(userName))
                    userExists = true
            }
            res.complete(userExists)
        }.addOnFailureListener {
            res.complete(false)
        }
        return res.await()
    }

    fun getUserSuggestionsQuery(): Query =
        userSuggestionsRef.orderBy("stringSortID", Query.Direction.ASCENDING)

    fun deleteUserSuggestion(id: String) {
        userSuggestionsRef.document(id).delete()
    }

    suspend fun userSuggestionApproved(user: User): Boolean {
        val res = CompletableDeferred<Boolean>()
        if (userDataSource.userExistsCheck(user.name)) res.complete(false)
        else {
            userDataSource.addUser(user)
            deleteUserSuggestion(user.stringSortID)
            res.complete(true)
        }
        return res.await()
    }

    fun getRolesFlow(): Flow<List<Role>> = callbackFlow {
        val listenerRegistration = userRolesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val roles = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Role::class.java)
            } ?: emptyList()

            trySend(roles)
        }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addRole(role: Role) {
        val roleData = mapOf(
            "name" to role.name,
            "superRole" to role.superRole
        )
        userRolesRef.document(role.name).set(roleData).await()
    }

    suspend fun deleteRole(role: Role) {
        val roleName = role.name

        userRolesRef.document(roleName).delete().await()
        userDataSource.deleteRoleFromUser(role.name)
        productDataSoure.deleteRoleFromProduct(role.name)
    }
}