package com.example.payments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** A "upi://pay?..." request read from a merchant or personal UPI QR code. */
data class UpiPayRequest(
  val payeeVpa: String,
  val payeeName: String,
  val amount: Double?,
  val note: String?,
  /** All original parameters, so merchant codes, refs and signatures are passed on unchanged. */
  val params: Map<String, String>
) {
  /** Signed merchant QRs must not be changed, so the amount can only be filled in when the QR has none. */
  val amountLocked: Boolean get() = amount != null && amount > 0 || params.containsKey("sign")

  fun toUri(amount: Double?, note: String?): Uri {
    val p = LinkedHashMap(params)
    if (!amountLocked && amount != null && amount > 0) p["am"] = String.format(Locale.US, "%.2f", amount)
    if (!note.isNullOrBlank() && !params.containsKey("sign")) p["tn"] = note
    p.putIfAbsent("cu", "INR")
    val query = p.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    return Uri.parse("upi://pay?$query")
  }
}

data class UpiApp(val packageName: String, val label: String, val icon: Drawable?)

sealed class UpiPayResult {
  data class Success(val reference: String?) : UpiPayResult()
  data class Failed(val reason: String) : UpiPayResult()
  /** The app returned without a clear status (common with some apps): the user has to say. */
  data object Unknown : UpiPayResult()
}

object UpiPay {

  fun parse(raw: String): UpiPayRequest? {
    val text = raw.trim()
    if (!text.lowercase(Locale.ROOT).startsWith("upi://pay")) return null
    val query = text.substringAfter('?', "")
    val params = LinkedHashMap<String, String>()
    for (pair in query.split('&')) {
      if (pair.isBlank()) continue
      val key = pair.substringBefore('=').trim()
      val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrDefault(pair.substringAfter('='))
      if (key.isNotEmpty()) params[key.lowercase(Locale.ROOT)] = value
    }
    val vpa = params["pa"]?.takeIf { it.contains('@') } ?: return null
    return UpiPayRequest(
      payeeVpa = vpa,
      payeeName = params["pn"]?.takeIf { it.isNotBlank() } ?: vpa,
      amount = params["am"]?.toDoubleOrNull(),
      note = params["tn"],
      params = params
    )
  }

  /** Every installed app that can pay a UPI link (needs the <queries> entry in the manifest on Android 11+). */
  fun installedApps(context: Context): List<UpiApp> {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=test@upi&pn=test&am=1&cu=INR"))
    val infos = if (Build.VERSION.SDK_INT >= 33) {
      pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
      @Suppress("DEPRECATION") pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return infos.map { it.activityInfo.packageName }.distinct()
      .filter { it != context.packageName }
      .map { pkg ->
        val info = infos.first { it.activityInfo.packageName == pkg }
        UpiApp(pkg, info.loadLabel(pm).toString(), runCatching { info.loadIcon(pm) }.getOrNull())
      }
      .sortedBy { it.label.lowercase(Locale.ROOT) }
  }

  fun payIntent(uri: Uri, packageName: String): Intent = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)

  /** Reads the NPCI response string, e.g. "txnId=..&responseCode=00&Status=SUCCESS&txnRef=..&ApprovalRefNo=..". */
  fun parseResult(data: Intent?): UpiPayResult {
    val response = data?.getStringExtra("response") ?: data?.getStringExtra("Status")?.let { "Status=$it" }
      ?: return UpiPayResult.Unknown
    val fields = response.split('&').associate {
      it.substringBefore('=').lowercase(Locale.ROOT) to runCatching { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
    }
    val status = fields["status"]?.uppercase(Locale.ROOT)
    val ref = fields["approvalrefno"]?.takeIf { it.isNotBlank() } ?: fields["txnref"]?.takeIf { it.isNotBlank() }
      ?: fields["txnid"]?.takeIf { it.isNotBlank() }
    return when (status) {
      "SUCCESS" -> UpiPayResult.Success(ref)
      "FAILURE", "FAILED" -> UpiPayResult.Failed(fields["responsecode"]?.let { "Payment failed (code $it)" } ?: "Payment failed")
      "SUBMITTED", "PENDING" -> UpiPayResult.Unknown
      else -> UpiPayResult.Unknown
    }
  }

  /** Decodes a QR code from a photo or screenshot of one. */
  fun decodeQr(bitmap: Bitmap): String? {
    val scaled = if (maxOf(bitmap.width, bitmap.height) > 1600) {
      val f = 1600f / maxOf(bitmap.width, bitmap.height)
      Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true)
    } else bitmap
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    return runCatching {
      MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, px))), hints).text
    }.getOrNull()
  }
}
