package com.example.usbprinterapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.usb.*
import android.os.Build
import java.io.ByteArrayOutputStream
import java.util.Arrays

class UsbPrinterHelper(private val context: Context) {

    val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.usbprinterapp.USB_PERMISSION"
        var selectedPrinterName: String = "Not selected"
        var connectedUsbDevice: UsbDevice? = null
    }

    fun findUsbPrinter(): UsbDevice? {
        for (device in usbManager.deviceList.values) {
            // Vendor ID 1008 is HP (0x03F0), Class 7 is Printer
            if (device.vendorId == 1008 || device.deviceClass == UsbConstants.USB_CLASS_PRINTER) {
                connectedUsbDevice = device
                selectedPrinterName = device.productName ?: "HP Ink Tank 319 (USB)"
                return device
            }
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                    connectedUsbDevice = device
                    selectedPrinterName = device.productName ?: "HP Ink Tank 319 (USB)"
                    return device
                }
            }
        }
        return null
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    fun sendRawUsbPrintJob(pageBitmap: Bitmap, onResult: (Boolean, String) -> Unit) {
        val device = connectedUsbDevice ?: findUsbPrinter()
        if (device == null) {
            onResult(false, "USB OTG Printer connect nahi hai! Settings > OTG Connection ON karein.")
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            onResult(false, "USB Permission popup ko Allow karein aur dobara Print dabayein.")
            return
        }

        Thread {
            var connection: UsbDeviceConnection? = null
            var targetInterface: UsbInterface? = null
            try {
                var endpointOut: UsbEndpoint? = null
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    for (j in 0 until intf.endpointCount) {
                        val ep = intf.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                            targetInterface = intf
                            endpointOut = ep
                            break
                        }
                    }
                    if (endpointOut != null) break
                }

                if (targetInterface == null || endpointOut == null) {
                    onResult(false, "Printer Bulk OUT Endpoint nahi mila!")
                    return@Thread
                }

                connection = usbManager.openDevice(device)
                connection.claimInterface(targetInterface, true)

                val pclData = encodeBitmapToHpPcl(pageBitmap)
                val chunkSize = 16384
                var offset = 0

                while (offset < pclData.size) {
                    val len = minOf(chunkSize, pclData.size - offset)
                    val chunk = pclData.copyOfRange(offset, offset + len)
                    val transferred = connection.bulkTransfer(endpointOut, chunk, chunk.size, 8000)
                    if (transferred < 0) {
                        throw Exception("USB Bulk Transfer Interrupted")
                    }
                    offset += len
                }

                onResult(true, "Print Job Sent to ${device.productName ?: "HP Ink Tank 319"}!")
            } catch (e: Exception) {
                onResult(false, "USB Print Error: ${e.message}")
            } finally {
                try {
                    if (targetInterface != null) connection?.releaseInterface(targetInterface)
                    connection?.close()
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun encodeBitmapToHpPcl(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("\u001B%-12345X@PJL JOB NAME=\"ANDROID_USB_PRINT\"\r\n".toByteArray())
        out.write("@PJL ENTER LANGUAGE = PCL\r\n".toByteArray())
        out.write("\u001BE".toByteArray())       // Printer Reset
        out.write("\u001B*t150R".toByteArray())  // 150 DPI Raster Resolution
        out.write("\u001B*r1A".toByteArray())    // Start Raster Graphics at current position

        val width = bitmap.width
        val height = bitmap.height
        val rowBytes = (width + 7) / 8
        val rowBuffer = ByteArray(rowBytes)

        for (y in 0 until height) {
            Arrays.fill(rowBuffer, 0.toByte())
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                if (lum < 165) {
                    rowBuffer[x / 8] = (rowBuffer[x / 8].toInt() or (1 shl (7 - (x % 8)))).toByte()
                }
            }
            out.write("\u001B*b0M".toByteArray()) // Unencoded raster mode
            out.write("\u001B*b${rowBytes}W".toByteArray())
            out.write(rowBuffer)
        }

        out.write("\u001B*rB".toByteArray()) // End Raster Graphics
        out.write("\u000C".toByteArray())    // Form Feed / Eject Page
        out.write("\u001BE".toByteArray())   // Reset
        out.write("\u001B%-12345X@PJL EOJ\r\n".toByteArray())
        return out.toByteArray()
    }
}
