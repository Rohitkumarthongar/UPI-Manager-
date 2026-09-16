package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder

object QrGenerator {

  /**
   * Generates a standard NPCI UPI payload:
   * upi://pay?pa=...&pn=...&cu=INR&am=...
   */
  fun buildUpiPayload(
    upiId: String,
    payeeName: String,
    amount: Double? = null,
    note: String? = null
  ): String {
    val encodedName = URLEncoder.encode(payeeName, "UTF-8")
    val sb = StringBuilder("upi://pay?pa=$upiId&pn=$encodedName&cu=INR")
    if (amount != null && amount > 0) {
      sb.append("&am=").append(String.format(java.util.Locale.US, "%.2f", amount))
    }
    if (!note.isNullOrBlank()) {
      sb.append("&tn=").append(URLEncoder.encode(note, "UTF-8"))
    }
    return sb.toString()
  }

  /**
   * Generates a clean, high-contrast QR Bitmap using ZXing.
   */
  fun generateQrBitmap(
    content: String,
    sizePx: Int = 512,
    darkColor: Int = 0xFF0F172A.toInt(),
    lightColor: Int = 0xFFFFFFFF.toInt()
  ): Bitmap? {
    if (content.isBlank()) return null
    return try {
      val hints = hashMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q
      )
      val bitMatrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        hints
      )
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)

      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
        }
      }

      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
      bitmap
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Decodes a QR code directly from a Bitmap using ZXing.
   */
  fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
      val width = bitmap.width
      val height = bitmap.height
      val pixels = IntArray(width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
      val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
      val result = com.google.zxing.qrcode.QRCodeReader().decode(binaryBitmap)
      result.text
    } catch (e: Exception) {
      null
    }
  }
}
