package com.example.atsattendance

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.atsattendance.api.ApiClient
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textview.MaterialTextView
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    // Views (must match IDs in activity_main.xml)
    private lateinit var pdfView: ImageView
    private lateinit var fetchReportButton: MaterialButton
    private lateinit var downloadReportButton: MaterialButton
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var chipOpen: Chip
    private lateinit var chipFilter: Chip
    private lateinit var tvDate: MaterialTextView
    private lateinit var emptyState: View

    // PDF state
    private var currentFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    // Selected date (YYYY-MM-DD)
    private var currentDate: String = "2026-02-21"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupUi()
        setupClicks()
        setContentView(R.layout.activity_main)
        applyInsets()
    }

    private fun bindViews() {
        pdfView = findViewById(R.id.pdfView)
        fetchReportButton = findViewById(R.id.fetchReportButton)
        downloadReportButton = findViewById(R.id.downloadReportButton)
        topAppBar = findViewById(R.id.topAppBar)
        bottomNav = findViewById(R.id.bottomNav)
        chipOpen = findViewById(R.id.chipOpen)
        chipFilter = findViewById(R.id.chipFilter)
        tvDate = findViewById(R.id.tvDate)
        emptyState = findViewById(R.id.emptyState)
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // push everything below status bar; keep your existing padding if any
            v.setPadding(v.paddingLeft, top, v.paddingRight, bottom)
            insets
        }
    }

    private fun setupUi() {
        // Download hidden until a report is fetched
        downloadReportButton.visibility = View.GONE
        downloadReportButton.isEnabled = false

        // Empty placeholder visible until we render a PDF
        emptyState.visibility = View.VISIBLE

        // Show current date in UI
        tvDate.text = currentDate
    }

    private fun setupClicks() {
        // Sticky buttons
        fetchReportButton.setOnClickListener { fetchReport(currentDate) }
        downloadReportButton.setOnClickListener {
            val file = currentFile
            if (file != null) downloadFile(file)
            else Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
        }

        // Top bar
        topAppBar.setNavigationOnClickListener {
            bottomNav.selectedItemId = R.id.nav_home
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
        }


        // Chips
        chipOpen.setOnClickListener { openCurrentReportIfAvailable() }
        chipFilter.setOnClickListener { openDatePicker() }

        // Bottom nav
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_attendance -> {
                    Toast.makeText(this, "Attendance", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_reports -> {
                    Toast.makeText(this, "Reports", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

    }

    private fun openDatePicker() {
        val cal = Calendar.getInstance()

        // Prefill with currently selected date
        val parts = currentDate.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m - 1)
                cal.set(Calendar.DAY_OF_MONTH, d)
            }
        }

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            val selected = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            currentDate = selected
            tvDate.text = selected

            // Auto-fetch immediately for the selected date
            fetchReport(selected)
        }, year, month, day).show()
    }

    private fun setLoading(isLoading: Boolean) {
        fetchReportButton.isEnabled = !isLoading
        fetchReportButton.text = if (isLoading) "Fetching..." else "Fetch"

        // Keep download disabled while fetching
        downloadReportButton.isEnabled = !isLoading && currentFile != null
        chipFilter.isEnabled = !isLoading
        chipOpen.isEnabled = !isLoading
    }

    private fun showDownloadButton() {
        downloadReportButton.visibility = View.VISIBLE
        downloadReportButton.isEnabled = true
        downloadReportButton.alpha = 0f
        downloadReportButton.animate().alpha(1f).setDuration(200).start()
    }

    private fun fetchReport(date: String) {
        setLoading(true)

        ApiClient.service.getDailyReport(date).enqueue(object : Callback<ResponseBody> {

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                setLoading(false)

                if (!response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Failed to fetch the report", Toast.LENGTH_SHORT).show()
                    return
                }

                val body = response.body()
                if (body == null) {
                    Toast.makeText(this@MainActivity, "Empty response body", Toast.LENGTH_SHORT).show()
                    return
                }

                try {
                    // Save PDF to internal storage
                    val file = File(filesDir, "attendance_report_$date.pdf")
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Open + render first page
                    openPdfFile(file)
                    showPage(0)

                    // Store for download/open
                    currentFile = file

                    // Show download button
                    showDownloadButton()

                    Toast.makeText(this@MainActivity, "Report loaded for $date", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error saving/opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                setLoading(false)
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun openPdfFile(file: File) {
        closePdf()
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index !in 0 until renderer.pageCount) return

        currentPage?.close()
        val page = renderer.openPage(index)
        currentPage = page

        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pdfView.setImageBitmap(bitmap)

        // Hide empty placeholder when PDF renders
        emptyState.visibility = View.GONE
    }

    private fun openCurrentReportIfAvailable() {
        if (currentFile == null || pdfRenderer == null) {
            Toast.makeText(this, "No report yet. Fetch one first.", Toast.LENGTH_SHORT).show()
            return
        }
        showPage(0)
        Toast.makeText(this, "Opened current report", Toast.LENGTH_SHORT).show()
    }

    private fun downloadFile(file: File) {
        try {
            val downloadsDir = File(getExternalFilesDir(null), "Downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val downloadedFile = File(downloadsDir, file.name)
            file.copyTo(downloadedFile, overwrite = true)

            Toast.makeText(
                this,
                "Downloaded to: ${downloadedFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error downloading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closePdf() {
        currentPage?.close()
        currentPage = null

        pdfRenderer?.close()
        pdfRenderer = null

        fileDescriptor?.close()
        fileDescriptor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdf()
    }
}