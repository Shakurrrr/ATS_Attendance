package com.ats.attendance

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ats.attendance.firebase.AutoAuth
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class AttendanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        // ✅ Silent auth so the app “just works” even if user lands here first
        lifecycleScope.launch {
            try {
                AutoAuth.ensureSignedIn()
            } catch (e: Exception) {
                Toast.makeText(
                    this@AttendanceActivity,
                    "Auth failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_attendance

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_attendance -> true
                R.id.nav_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}