package com.ats.attendance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ContextMenu
import android.view.MenuItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MENU_DOWNLOAD_PDF = 2001
    }

    private lateinit var pdfView: ImageView
    private lateinit var fetchReportButton: MaterialButton
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = StorageRepository(this)

        bindViews()
        setupUi()
        setupClicks()

        registerForContextMenu(pdfView)

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
    }

    private fun bindViews() {
        pdfView = findViewById(R.id.pdfView)
        fetchReportButton = findViewById(R.id.fetchReportButton)
        bottomNav = findViewById(R.id.bottomNav)
        tvDate = findViewById(R.id.tvDate)
        tvReportTitle = findViewById(R.id.tvReportTitle)
        emptyState = findViewById(R.id.emptyState)
        toggleReportMode = findViewById(R.id.toggleReportMode)
        btnDaily = findViewById(R.id.btnDaily)
        btnWeekly = findViewById(R.id.btnWeekly)
    }

    private fun setupUi() {
        emptyState.visibility = View.VISIBLE
        pdfView.setImageDrawable(null)
        toggleReportMode.check(R.id.btnDaily)
        updateHeader()
    }

    private fun setupClicks() {
        fetchReportButton.setOnClickListener { fetchReport() }

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
        tvReportTitle.text =
            if (currentMode == ReportMode.DAILY) "Daily Report"
            else "Weekly Report"

        tvDate.text = when (currentMode) {
            ReportMode.DAILY -> currentDate.format(dateFmt)
            ReportMode.WEEKLY -> ReportPathBuilder.displayLabel(ReportMode.WEEKLY, currentDate)
        }
    }

    private fun openDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                currentDate = LocalDate.of(year, month + 1, day)
                updateHeader()
                fetchReport()
            },
            currentDate.year,
            currentDate.monthValue - 1,
            currentDate.dayOfMonth
        ).show()
    }

    private fun setLoading(isLoading: Boolean) {
        fetchReportButton.isEnabled = !isLoading
        fetchReportButton.text = if (isLoading) "Loading..." else "Fetch"
    }

    private fun fetchReport() {
        val storagePath = ReportPathBuilder.storagePath(currentMode, currentDate)
        val label = ReportPathBuilder.displayLabel(currentMode, currentDate)

        lifecycleScope.launch {
            try {
                setLoading(true)
                AutoAuth.ensureSignedIn()

                val file = repo.downloadToCache(storagePath)
                currentFile = file

                openPdfFile(file)
                showPage(0)

                emptyState.visibility = View.GONE

                Toast.makeText(this@MainActivity, "Loaded: $label", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                currentFile = null
                pdfView.setImageDrawable(null)
                emptyState.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Report not found: $label", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
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
        currentPage = renderer.openPage(index)

        val page = currentPage ?: return

        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE) // important visibility fix
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        pdfView.setImageBitmap(bitmap)
    }

    private fun downloadToPublicDownloads(file: File) {
        lifecycleScope.launch {
            try {
                val label = ReportPathBuilder.displayLabel(currentMode, currentDate)
                val niceName = when (currentMode) {
                    ReportMode.DAILY -> "ATS_Daily_$label.pdf"
                    ReportMode.WEEKLY -> "ATS_Weekly_$label.pdf"
                }

                withContext(Dispatchers.IO) {
                    DownloadUtils.savePdfToDownloads(this@MainActivity, file, niceName)
                }

                Toast.makeText(this@MainActivity, "Saved to Downloads", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v == pdfView) {
            val hasFile = currentFile?.exists() == true
            if (hasFile) {
                menu?.add(0, MENU_DOWNLOAD_PDF, 0, "Download PDF")
            } else {
                menu?.add(0, MENU_DOWNLOAD_PDF, 0, "Download PDF")?.isEnabled = false
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_DOWNLOAD_PDF -> {
                val file = currentFile
                if (file == null || !file.exists()) {
                    Toast.makeText(this, "No report to download", Toast.LENGTH_SHORT).show()
                } else {
                    downloadToPublicDownloads(file)
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }
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