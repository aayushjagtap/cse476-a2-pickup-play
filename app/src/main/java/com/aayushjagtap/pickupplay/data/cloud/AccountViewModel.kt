package com.aayushjagtap.pickupplay.data.cloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

/** Account screen state. The work itself lives in [CloudSyncManager], which outlives screens. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val cloud = CloudSyncManager.get(application)

    val state: LiveData<CloudSyncManager.State> = cloud.state.asLiveData()
    val unsyncedCount: LiveData<Int> = cloud.unsyncedCount.asLiveData()
    val usingEmulator: Boolean get() = cloud.usingEmulator

    fun signIn(email: String, password: String) = cloud.signIn(email, password)
    fun signOut() = cloud.signOut()
    fun syncNow() = cloud.syncNow()
    fun probeUnauthenticatedRead() = cloud.probeUnauthenticatedRead()
}
