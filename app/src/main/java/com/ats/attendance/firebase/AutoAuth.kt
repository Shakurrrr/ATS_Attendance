package com.ats.attendance.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object AutoAuth {
    private val auth by lazy { FirebaseAuth.getInstance() }

    suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().await()
    }
}