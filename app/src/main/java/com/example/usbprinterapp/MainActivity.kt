package com.example.usbprinterapp

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var usbHelper: UsbPrinterHelper
    private lateinit var tvPrinterStatus: TextView

    private val PICK_IMAGE = 201
    private val PICK_PDF = 202

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbPrinterHelper.ACTION_USB_PERMISSION == intent.action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                    UsbPrinterHelper.connectedUsbDevice = device
                    UsbPrinterHelper.selectedPrinterName = device.productName ?: "HP Ink Tank 319"
                    tvPrinterStatus.text = UsbPrinterHelper.selectedPrinterName
                    Toast.makeText(context, "Connected: ${UsbPrinterHelper.selectedPrinterName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbHelper = UsbPrinterHelper(this)
        tvPrinterStatus = findViewById(R.id.tvPrinterStatus)

        val filter = IntentFilter(UsbPrinterHelper.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        // Auto-check USB printer on launch
        usbHelper.findUsbPrinter()?.let {
            tvPrinterStatus.text = UsbPrinterHelper.selectedPrinterName
        }

        findViewById<Button>(R.id.btnSelectPrinter).setOnClickListener {
            showSelectPrinterDialog()
        }

        findViewById<Button>(R.id.btnPictures).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(Intent.createChooser(intent, "Select Picture(s)"), PICK_IMAGE)
        }

        findViewById<Button>(R.id.btnDocuments).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
            }
            startActivityForResult(Intent.createChooser(intent, "Select PDF Document"), PICK_PDF)
        }

        findViewById<Button>(R.id.btnWebPages).setOnClickListener {
            startActivity(Intent(this, WebPrintActivity::class.java))
        }

        val textPrintListener = { title: String, sample: String ->
            showQuickTextPrintDialog(title, sample)
        }

        findViewById<Button>(R.id.btnGmail).setOnClickListener {
            textPrintListener("Print Email / Text", "Subject: Important Document\n\nPaste or type your email content here to print...")
        }
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            textPrintListener("Print Contacts", "Name: Rakesh Kumar Singh\nPhone: +91-XXXXXXXXXX")
        }
        findViewById<Button>(R.id.btnCalendar).setOnClickListener {
            textPrintListener("Print Calendar Notes", "Event: Office Meeting\nDate: 25 Oct 2026")
        }
        findViewById<Button>(R.id.btnMessages).setOnClickListener {
            textPrintListener("Print SMS / Message", "Type or paste message to print on HP 319...")
        }
        findViewById<Button>(R.id.btnCallLog).setOnClickListener {
            textPrintListener("Print Call Log", "Incoming Call - 10:30 AM (5 mins)")
        }
    }

    private fun showQuickTextPrintDialog(title: String, defaultText: String) {
        val input = EditText(this).apply {
            setText(defaultText)
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Preview & Print") { _, _ ->
                val intent = Intent(this, PrintPreviewActivity::class.java).apply {
                    putExtra("TEXT_CONTENT", input.text.toString())
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Matches Screenshot 2: Select Printer Dialog
    private fun showSelectPrinterDialog() {
        val items = arrayOf(
            "📡  Nearby WiFi",
            "🦷  Nearby Bluetooth",
            "🔌  Direct USB Connected",
            "🖥️  PrinterShare Remote",
            "📄  Print to PDF"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Printer")
            .setItems(items) { _, which ->
                when (which) {
                    2 -> {
                        val dev = usbHelper.findUsbPrinter()
                        if (dev != null) {
                            usbHelper.requestPermission(dev)
                            tvPrinterStatus.text = dev.productName ?: "HP Ink Tank 319 (USB)"
                        } else {
                            UsbPrinterHelper.selectedPrinterName = "Direct USB (Waiting for OTG...)"
                            tvPrinterStatus.text = UsbPrinterHelper.selectedPrinterName
                            Toast.makeText(this, "USB Printer nahi dikha! Cable aur Phone OTG setting check karein.", Toast.LENGTH_LONG).show()
                        }
                    }
                    4 -> {
                        UsbPrinterHelper.selectedPrinterName = "Print to PDF / System Spooler"
                        tvPrinterStatus.text = UsbPrinterHelper.selectedPrinterName
                    }
                    else -> {
                        UsbPrinterHelper.selectedPrinterName = items[which].substring(3).trim()
                        tvPrinterStatus.text = UsbPrinterHelper.selectedPrinterName
                    }
                }
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uris = ArrayList<String>()
            if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    uris.add(data.clipData!!.getItemAt(i).uri.toString())
                }
            } else if (data.data != null) {
                uris.add(data.data!!.toString())
            }

            if (uris.isNotEmpty()) {
                val intent = Intent(this, PrintPreviewActivity::class.java).apply {
                    putStringArrayListExtra("FILE_URIS", uris)
                    putExtra("IS_PDF", requestCode == PICK_PDF)
                }
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }
}
