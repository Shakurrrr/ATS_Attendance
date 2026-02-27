package com.ats.attendance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ats.attendance.firebase.AutoAuth
import com.ats.attendance.firebase.ReportMode
import com.ats.attendance.firebase.ReportPathBuilder
import com.ats.attendance.firebase.StorageRepository
import com.ats.attendance.util.DownloadUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: ImageView
    private lateinit var fetchReportButton: MaterialButton
    private lateinit var downloadReportButton: MaterialButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvDate: MaterialTextView
    private lateinit var tvReportTitle: MaterialTextView
    private lateinit var toggleReportMode: MaterialButtonToggleGroup
    private lateinit var btnDaily: MaterialButton
    private lateinit var btnWeekly: MaterialButton
    private lateinit var emptyState: View
    private lateinit var repo: StorageRepository

    private var currentFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private var currentDate: LocalDate = LocalDate.now()
    private var currentMode: ReportMode = ReportMode.DAILY

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private var downloadUrl: String? = null  // Variable to hold the download URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = StorageRepository(this)

        bindViews() // Bind views before setting up the UI or clicks
        setupUi()  // Setting up initial UI configurations
        setupClicks()  // Setting up click listeners

        lifecycleScope.launch {
            try {
                setLoading(true)
                AutoAuth.ensureSignedIn()
                fetchReport()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@MainActivity, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Hide the download button initially
        downloadReportButton.visibility = View.GONE

        // Set up click listener for the PDF preview
        pdfView.setOnClickListener {
            // Show the download button when the user taps on the PDF preview
            downloadReportButton.visibility = View.VISIBLE
        }

        // Set up the click listener for the download button
        downloadReportButton.setOnClickListener {
            downloadUrl?.let {
                lifecycleScope.launch {
                    try {
                        val sourceFile = repo.downloadToCache(it)  // Download the PDF file to cache
                        DownloadUtils.savePdfToDownloads(this@MainActivity, sourceFile)  // Save it to Downloads folder
                        Toast.makeText(this@MainActivity, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    } catch (e: IOException) {
                        Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
        }
    }

    // Ensure this is called first in onCreate to bind the views before using them
    private fun bindViews() {
        pdfView = findViewById(R.id.pdfView)
        fetchReportButton = findViewById(R.id.fetchReportButton)
        downloadReportButton = findViewById(R.id.downloadReportButton)
        bottomNav = findViewById(R.id.bottomNav)
        tvDate = findViewById(R.id.tvDate)
        tvReportTitle = findViewById(R.id.tvReportTitle)
        emptyState = findViewById(R.id.emptyState)
        toggleReportMode = findViewById(R.id.toggleReportMode)
        btnDaily = findViewById(R.id.btnDaily)
        btnWeekly = findViewById(R.id.btnWeekly)
    }

    private fun setupUi() {
        // Ensure that the buttons are set up and ready
        downloadReportButton.visibility = View.GONE
        downloadReportButton.isEnabled = false
        emptyState.visibility = View.VISIBLE

        // This line was causing the issue due to toggleReportMode not being initialized properly
        // So, we ensure that the views are correctly bound before this call
        toggleReportMode.check(R.id.btnDaily)
        updateHeader()
    }

    private fun setupClicks() {
        // Ensure the buttons are not accessed before initialization
        fetchReportButton.setOnClickListener { fetchReport() }

        downloadReportButton.setOnClickListener {
            downloadUrl?.let {
                lifecycleScope.launch {
                    try {
                        val sourceFile = repo.downloadToCache(it)  // Download the PDF file to cache
                        DownloadUtils.savePdfToDownloads(this@MainActivity, sourceFile)  // Save it to Downloads folder
                        Toast.makeText(this@MainActivity, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    } catch (e: IOException) {
                        Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
        }

        toggleReportMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentMode = when (checkedId) {
                R.id.btnWeekly -> ReportMode.WEEKLY
                else -> ReportMode.DAILY
            }
            updateHeader()
            fetchReport()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_attendance -> {
                    startActivity(Intent(this, AttendanceActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, ReportsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        tvDate.setOnClickListener { openDatePicker() }
    }

    private fun updateHeader() {
        tvReportTitle.text = if (currentMode == ReportMode.DAILY) "Daily Report" else "Weekly Report"
        tvDate.text = when (currentMode) {
            ReportMode.DAILY -> currentDate.format(dateFmt)
            ReportMode.WEEKLY -> ReportPathBuilder.displayLabel(ReportMode.WEEKLY, currentDate)
        }
    }

    private fun openDatePicker() {
        val y = currentDate.year
        val m = currentDate.monthValue - 1
        val d = currentDate.dayOfMonth

        DatePickerDialog(
            this,
            { _, year, month, day ->
                currentDate = LocalDate.of(year, month + 1, day)
                updateHeader()
                fetchReport()
            },
            y, m, d
        ).show()
    }

    private fun setLoading(isLoading: Boolean) {
        fetchReportButton.isEnabled = !isLoading
        fetchReportButton.text = if (isLoading) "Loading..." else "Fetch"
        downloadReportButton.isEnabled = !isLoading && downloadUrl != null
    }

    private fun fetchReport() {
        val storagePath = ReportPathBuilder.storagePath(currentMode, currentDate)
        val label = ReportPathBuilder.displayLabel(currentMode, currentDate)

        lifecycleScope.launch {
            try {
                setLoading(true)
                AutoAuth.ensureSignedIn()

                // Fetch the download URL from the repository (Firebase backend will provide this URL)
                val url = repo.getDownloadUrl(storagePath) // Using the suspending function to get the URL

                downloadUrl = url // Store the download URL here

                // Now fetch the file itself for rendering in the PDF view
                val file = repo.downloadToCache(storagePath)
                currentFile = file

                openPdfFile(file)
                showPage(0)
                showDownloadButton()

                Toast.makeText(this@MainActivity, "Loaded: $label", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                currentFile = null
                downloadReportButton.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Report not found: $label", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showDownloadButton() {
        Log.d("Download", "Download Button is being shown!")

        // Make the button visible and ensure it's fully visible
        downloadReportButton.visibility = View.VISIBLE
        downloadReportButton.isEnabled = true
        downloadReportButton.alpha = 1f  // Make sure it's fully visible

        // Hide the empty state view
        emptyState.visibility = View.GONE
    }

    private fun openPdfFile(file: File) {
        closePdf()
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)

        // Show the first page
        showPage(0)
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index !in 0 until renderer.pageCount) return

        // Close the previous page if it's already open
        currentPage?.close()

        // Open the page at the specified index
        currentPage = renderer.openPage(index)

        // Now render the page to a bitmap
        val page = currentPage ?: return
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

        // Render the page into the bitmap
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Set the bitmap to the ImageView (pdfView)
        pdfView.setImageBitmap(bitmap)
    }

    private fun closePdf() {
        try { currentPage?.close() } catch (_: Exception) {}
        currentPage = null
        try { pdfRenderer?.close() } catch (_: Exception) {}
        pdfRenderer = null
        try { fileDescriptor?.close() } catch (_: Exception) {}
        fileDescriptor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdf()
    }
}