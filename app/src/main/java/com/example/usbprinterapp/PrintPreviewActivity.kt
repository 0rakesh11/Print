package com.example.usbprinterapp

import android.app.AlertDialog
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.print.PrintHelper

class PrintPreviewActivity : AppCompatActivity() {

    private lateinit var ivPageCanvas: ImageView
    private lateinit var tvPageSummary: TextView
    private lateinit var tvSelectedPrinterPreview: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var pdfNavLayout: LinearLayout
    private lateinit var usbHelper: UsbPrinterHelper

    private val sourceBitmaps = mutableListOf<Bitmap>()
    private var renderedPageBitmap: Bitmap? = null

    private var isPdf = false
    private var pdfUri: Uri? = null
    private var currentPageIndex = 0
    private var totalPages = 1

    // Exact Default Settings from User Screenshots
    private var pictureSize = "4\"×6\" (10×15 cm)"
    private var pictureScaling = "Crop"
    private var pictureAlign = "Center"
    private var pageLayout = "Single picture"
    private var pageMargins = "Normal"
    private var pageOrientation = "Auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print_preview)

        ivPageCanvas = findViewById(R.id.ivPageCanvas)
        tvPageSummary = findViewById(R.id.tvPageSummary)
        tvSelectedPrinterPreview = findViewById(R.id.tvSelectedPrinterPreview)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        pdfNavLayout = findViewById(R.id.pdfNavLayout)
        usbHelper = UsbPrinterHelper(this)

        tvSelectedPrinterPreview.text = "🖨️ ${UsbPrinterHelper.selectedPrinterName}"

        isPdf = intent.getBooleanExtra("IS_PDF", false)
        val uriList = intent.getStringArrayListExtra("FILE_URIS")
        val textContent = intent.getStringExtra("TEXT_CONTENT")

        if (WebPrintActivity.capturedWebBitmap != null && intent.getBooleanExtra("FROM_WEB", false)) {
            sourceBitmaps.add(WebPrintActivity.capturedWebBitmap!!)
            pictureSize = "Fit to page"
            pictureScaling = "Inside"
        } else if (textContent != null) {
            sourceBitmaps.add(createBitmapFromText(textContent))
            pictureSize = "Fit to page"
            pictureScaling = "Inside"
        } else if (!uriList.isNullOrEmpty()) {
            if (isPdf) {
                pdfUri = Uri.parse(uriList[0])
                pictureSize = "Fit to page"
                pictureScaling = "Inside"
                loadPdfPage(0)
            } else {
                for (u in uriList) {
                    loadBitmapFromUri(Uri.parse(u))?.let { sourceBitmaps.add(it) }
                }
            }
        }

        findViewById<Button>(R.id.btnPrevPage).setOnClickListener {
            if (isPdf && currentPageIndex > 0) {
                currentPageIndex--
                loadPdfPage(currentPageIndex)
                renderPageCanvas()
            }
        }

        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            if (isPdf && currentPageIndex < totalPages - 1) {
                currentPageIndex++
                loadPdfPage(currentPageIndex)
                renderPageCanvas()
            }
        }

        findViewById<Button>(R.id.btnOptions).setOnClickListener {
            showPrintOptionsMenu()
        }

        findViewById<Button>(R.id.btnPrint).setOnClickListener {
            showFinalPrintMethodDialog()
        }

        renderPageCanvas()
    }

    // Screenshot 4: Print Options Menu
    private fun showPrintOptionsMenu() {
        val menuItems = arrayOf(
            "Picture Size : $pictureSize",
            "Picture Scaling : $pictureScaling",
            "Picture Align : $pictureAlign",
            "Page Layout : $pageLayout",
            "Page Margins : $pageMargins",
            "Page Orientation : $pageOrientation",
            "Select Printer"
        )

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Print Options")
            .setItems(menuItems) { _, which ->
                when (which) {
                    // Screenshot 5: Picture Size
                    0 -> showRadioDialog("Picture Size", arrayOf(
                        "Original size",
                        "Fit to page",
                        "3½\"×5\" (9×13 cm)",
                        "4\"×6\" (10×15 cm)",
                        "5\"×7\" (13×18 cm)",
                        "8\"×10\" (20×25 cm)",
                        "8\"×12\" (20×30 cm)",
                        "11\"×14\" (28×36 cm)"
                    ), pictureSize) { pictureSize = it; renderPageCanvas() }

                    // Screenshot 6: Picture Scaling
                    1 -> showRadioDialog("Picture Scaling", arrayOf(
                        "Crop",
                        "Inside"
                    ), pictureScaling) { pictureScaling = it; renderPageCanvas() }

                    // Screenshot 7: Picture Align
                    2 -> showRadioDialog("Picture Align", arrayOf(
                        "Center",
                        "Top Left",
                        "Top Right",
                        "Bottom Left",
                        "Bottom Right",
                        "Middle Left",
                        "Middle Right",
                        "Top Middle",
                        "Bottom Middle"
                    ), pictureAlign) { pictureAlign = it; renderPageCanvas() }

                    // Page Layout (Single vs Grid)
                    3 -> showRadioDialog("Page Layout", arrayOf(
                        "Single picture",
                        "Grid 2x1",
                        "Grid 2x2",
                        "Grid 3x2",
                        "Grid 3x3"
                    ), pageLayout) { pageLayout = it; renderPageCanvas() }

                    // Screenshot 8: Page Margins
                    4 -> showRadioDialog("Page Margins", arrayOf(
                        "No (minimum allowable)",
                        "Small",
                        "Normal",
                        "Large"
                    ), pageMargins) { pageMargins = it; renderPageCanvas() }

                    // Screenshot 9: Page Orientation
                    5 -> showRadioDialog("Page Orientation", arrayOf(
                        "Auto",
                        "Portrait",
                        "Landscape"
                    ), pageOrientation) { pageOrientation = it; renderPageCanvas() }

                    6 -> {
                        val dev = usbHelper.findUsbPrinter()
                        if (dev != null) {
                            usbHelper.requestPermission(dev)
                            tvSelectedPrinterPreview.text = "🖨️ ${UsbPrinterHelper.selectedPrinterName}"
                        } else {
                            Toast.makeText(this, "No Direct USB Printer detected on OTG", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun showRadioDialog(title: String, options: Array<String>, currentVal: String, onConfirmed: (String) -> Unit) {
        var selectedIdx = options.indexOf(currentVal).coerceAtLeast(0)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(options, selectedIdx) { _, which ->
                selectedIdx = which
            }
            .setPositiveButton("OK") { _, _ ->
                onConfirmed(options[selectedIdx])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderPageCanvas() {
        if (sourceBitmaps.isEmpty()) return
        val firstBmp = sourceBitmaps[0]

        val isLandscape = when (pageOrientation) {
            "Landscape" -> true
            "Portrait" -> false
            else -> firstBmp.width > firstBmp.height
        }

        // Standard Letter/A4 @ 150 DPI (1275 x 1650 px)
        val pageW = if (isLandscape) 1650 else 1275
        val pageH = if (isLandscape) 1275 else 1650

        val sheetBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheetBmp)
        canvas.drawColor(Color.WHITE)

        val marginPx = when (pageMargins) {
            "No (minimum allowable)" -> 0
            "Small" -> 36
            "Normal" -> 75
            "Large" -> 135
            else -> 75
        }

        val availW = pageW - (marginPx * 2)
        val availH = pageH - (marginPx * 2)

        if (pageLayout.startsWith("Grid")) {
            val (rows, cols) = when (pageLayout) {
                "Grid 2x1" -> Pair(2, 1)
                "Grid 2x2" -> Pair(2, 2)
                "Grid 3x2" -> Pair(3, 2)
                "Grid 3x3" -> Pair(3, 3)
                else -> Pair(1, 1)
            }
            val cellW = availW / cols
            val cellH = availH / rows
            val gap = 12
            var idx = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val bmp = sourceBitmaps[idx % sourceBitmaps.size]
                    val cellRect = Rect(
                        marginPx + c * cellW + gap,
                        marginPx + r * cellH + gap,
                        marginPx + (c + 1) * cellW - gap,
                        marginPx + (r + 1) * cellH - gap
                    )
                    drawBitmapInBox(canvas, bmp, cellRect)
                    idx++
                }
            }
        } else {
            // Single picture with exact physical size @ 150 DPI
            var (boxW, boxH) = when (pictureSize) {
                "3½\"×5\" (9×13 cm)" -> Pair(525, 750)
                "4\"×6\" (10×15 cm)" -> Pair(600, 900)
                "5\"×7\" (13×18 cm)" -> Pair(750, 1050)
                "8\"×10\" (20×25 cm)" -> Pair(1200, 1500)
                "8\"×12\" (20×30 cm)" -> Pair(1200, 1650)
                "11\"×14\" (28×36 cm)" -> Pair(1650, 2100)
                "Original size" -> Pair(firstBmp.width.coerceAtMost(availW), firstBmp.height.coerceAtMost(availH))
                else -> Pair(availW, availH) // Fit to page
            }

            if (isLandscape && boxH > boxW) {
                val tmp = boxW; boxW = boxH; boxH = tmp
            }
            boxW = boxW.coerceAtMost(availW)
            boxH = boxH.coerceAtMost(availH)

            val left = when {
                pictureAlign.contains("Left") -> marginPx
                pictureAlign.contains("Right") -> pageW - marginPx - boxW
                else -> marginPx + (availW - boxW) / 2
            }

            val top = when {
                pictureAlign.contains("Top") -> marginPx
                pictureAlign.contains("Bottom") -> pageH - marginPx - boxH
                else -> marginPx + (availH - boxH) / 2
            }

            val targetRect = Rect(left, top, left + boxW, top + boxH)
            drawBitmapInBox(canvas, firstBmp, targetRect)
        }

        renderedPageBitmap = sheetBmp
        ivPageCanvas.setImageBitmap(sheetBmp)
        tvPageIndicator.text = "${currentPageIndex + 1} / $totalPages"
        tvPageSummary.text = "Letter • $pageMargins • $pageOrientation\n$pictureSize ($pictureScaling, $pictureAlign)"
    }

    private fun drawBitmapInBox(canvas: Canvas, bmp: Bitmap, targetRect: Rect) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val boxW = targetRect.width()
        val boxH = targetRect.height()

        if (pictureScaling == "Crop") {
            val scale = maxOf(boxW.toFloat() / bmp.width, boxH.toFloat() / bmp.height)
            val cropW = (boxW / scale).toInt()
            val cropH = (boxH / scale).toInt()
            val cropX = (bmp.width - cropW) / 2
            val cropY = (bmp.height - cropH) / 2
            val srcRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
            canvas.drawBitmap(bmp, srcRect, targetRect, paint)
        } else {
            val scale = minOf(boxW.toFloat() / bmp.width, boxH.toFloat() / bmp.height)
            val drawW = (bmp.width * scale).toInt()
            val drawH = (bmp.height * scale).toInt()
            val drawLeft = targetRect.left + (boxW - drawW) / 2
            val drawTop = targetRect.top + (boxH - drawH) / 2
            canvas.drawBitmap(bmp, null, Rect(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH), paint)
        }
    }

    private fun showFinalPrintMethodDialog() {
        val finalBmp = renderedPageBitmap ?: return
        val methods = arrayOf(
            "🎨 Full Color / Photo Print (HP OTG System Spooler / PDF)",
            "⚡ Direct Raw USB Bulk Transfer (PCL3 Monochrome)"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Send Print Job to HP 319")
            .setItems(methods) { _, which ->
                if (which == 0) {
                    val printHelper = PrintHelper(this)
                    printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
                    printHelper.printBitmap("HP319_Page_${currentPageIndex + 1}", finalBmp)
                } else {
                    usbHelper.sendRawUsbPrintJob(finalBmp) { _, message ->
                        runOnUiThread {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadPdfPage(pageIndex: Int) {
        val uri = pdfUri ?: return
        try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return
            val renderer = PdfRenderer(fd)
            totalPages = renderer.pageCount
            val page = renderer.openPage(pageIndex.coerceIn(0, totalPages - 1))
            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            renderer.close()
            fd.close()
            sourceBitmaps.clear()
            sourceBitmaps.add(bmp)
        } catch (e: Exception) {
            Toast.makeText(this, "PDF Read Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createBitmapFromText(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
        }
        var y = 100f
        for (line in text.split("\n")) {
            canvas.drawText(line, 60f, y, paint)
            y += 52f
        }
        return bmp
    }
}
