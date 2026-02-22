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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
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

    // Views (must match activity_main.xml)
    private lateinit var pdfView: ImageView
    private lateinit var fetchReportButton: MaterialButton
    private lateinit var downloadReportButton: MaterialButton
    private lateinit var bottomNav: BottomNavigationView
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
        applyStatusBarPadding()
    }

    private fun bindViews() {
        pdfView = findViewById(R.id.pdfView)
        fetchReportButton = findViewById(R.id.fetchReportButton)
        downloadReportButton = findViewById(R.id.downloadReportButton)
        bottomNav = findViewById(R.id.bottomNav)
        tvDate = findViewById(R.id.tvDate)
        emptyState = findViewById(R.id.emptyState)
    }

    private fun setupUi() {
        downloadReportButton.visibility = View.GONE
        downloadReportButton.isEnabled = false
        emptyState.visibility = View.VISIBLE
        tvDate.text = currentDate
    }

    private fun setupClicks() {

        // Fetch button
        fetchReportButton.setOnClickListener {
            fetchReport(currentDate)
        }

        // Download button
        downloadReportButton.setOnClickListener {
            currentFile?.let { downloadFile(it) }
                ?: Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
        }

        // Bottom navigation
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

        // Long press on date to open picker
        tvDate.setOnClickListener {
            openDatePicker()
        }
    }

    private fun applyStatusBarPadding() {
        val scroll = findViewById<View>(R.id.contentScroll)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // add status bar height on top of your existing padding
            v.setPadding(v.paddingLeft, topInset + 16, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun openDatePicker() {
        val cal = Calendar.getInstance()

        val parts = currentDate.split("-")
        if (parts.size == 3) {
            cal.set(Calendar.YEAR, parts[0].toInt())
            cal.set(Calendar.MONTH, parts[1].toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                val selected = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                currentDate = selected
                tvDate.text = selected
                fetchReport(selected)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setLoading(isLoading: Boolean) {
        fetchReportButton.isEnabled = !isLoading
        fetchReportButton.text = if (isLoading) "Fetching..." else "Fetch"
        downloadReportButton.isEnabled = !isLoading && currentFile != null
    }

    private fun fetchReport(date: String) {
        setLoading(true)

        ApiClient.service.getDailyReport(date)
            .enqueue(object : Callback<ResponseBody> {

                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    setLoading(false)

                    if (!response.isSuccessful) {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to fetch the report",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val body = response.body() ?: return

                    try {
                        val file = File(filesDir, "attendance_report_$date.pdf")

                        body.byteStream().use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }

                        openPdfFile(file)
                        showPage(0)

                        currentFile = file
                        showDownloadButton()

                        Toast.makeText(
                            this@MainActivity,
                            "Report loaded for $date",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    setLoading(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun showDownloadButton() {
        downloadReportButton.visibility = View.VISIBLE
        downloadReportButton.isEnabled = true
        downloadReportButton.alpha = 0f
        downloadReportButton.animate().alpha(1f).setDuration(200).start()
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

        val bitmap = Bitmap.createBitmap(
            page.width,
            page.height,
            Bitmap.Config.ARGB_8888
        )

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pdfView.setImageBitmap(bitmap)

        emptyState.visibility = View.GONE
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
            Toast.makeText(
                this,
                "Error downloading file: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
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