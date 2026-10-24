package com.example.usbprinterapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class WebPrintActivity : AppCompatActivity() {

    companion object {
        var capturedWebBitmap: Bitmap? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_print)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val btnGo = findViewById<Button>(R.id.btnGo)
        val webView = findViewById<WebView>(R.id.webView)
        val btnCapture = findViewById<Button>(R.id.btnCaptureWeb)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(etUrl.text.toString())

        btnGo.setOnClickListener {
            var url = etUrl.text.toString().trim()
            if (!url.startsWith("http")) url = "https://$url"
            webView.loadUrl(url)
        }

        btnCapture.setOnClickListener {
            val bmp = Bitmap.createBitmap(
                webView.width.coerceAtLeast(100),
                webView.height.coerceAtLeast(100),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            webView.draw(canvas)
            capturedWebBitmap = bmp

            val intent = Intent(this, PrintPreviewActivity::class.java).apply {
                putExtra("FROM_WEB", true)
            }
            startActivity(intent)
        }
    }
}
