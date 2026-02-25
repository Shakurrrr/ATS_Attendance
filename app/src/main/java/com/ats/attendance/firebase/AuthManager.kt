package com.ats.attendance.firebase

import com.google.firebase.auth.FirebaseAuth

object AuthManager {
    private val auth by lazy { FirebaseAuth.getInstance() }
    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun signOut() = auth.signOut()
}