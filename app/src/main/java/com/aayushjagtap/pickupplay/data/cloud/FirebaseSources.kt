package com.aayushjagtap.pickupplay.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation. Every call is a suspend function built on the SDK's Task API, so
 * nothing blocks the main thread; the SDK does its network I/O on its own threads over TLS.
 */
class FirestoreSessionSource(private val db: FirebaseFirestore) : SessionRemoteSource {

    private val collection get() = db.collection(RemoteSession.COLLECTION)

    override suspend fun upload(session: RemoteSession) {
        val fields = session.toFields() + (RemoteSession.F_SERVER_CREATED_AT to FieldValue.serverTimestamp())
        collection.document(session.remoteId).set(fields).await()
    }

    override suspend fun fetchFromServer(): List<RemoteSession> {
        // Source.SERVER: fail instead of silently answering from Firestore's offline cache, so a
        // successful result proves the data came from the backend in this app session.
        val snapshot = collection
            .orderBy(RemoteSession.F_CREATED_AT, Query.Direction.DESCENDING)
            .limit(FETCH_LIMIT)
            .get(Source.SERVER)
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.data?.let { RemoteSession.fromFields(doc.id, it) }
        }
    }

    override suspend fun probeServerRead() {
        collection.limit(1).get(Source.SERVER).await()
    }

    private companion object {
        const val FETCH_LIMIT = 200L
    }
}

class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {

    override val currentUser: AuthUser?
        get() = auth.currentUser?.let { AuthUser(it.uid, it.email, it.displayName) }

    override fun addListener(listener: (AuthUser?) -> Unit) {
        auth.addAuthStateListener { listener(currentUser) }
    }

    override suspend fun signIn(email: String, password: String): AuthUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Sign-in returned no user")
        return AuthUser(user.uid, user.email, user.displayName)
    }

    override fun signOut() = auth.signOut()
}
