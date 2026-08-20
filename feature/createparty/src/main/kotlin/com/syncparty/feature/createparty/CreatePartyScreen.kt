package com.syncparty.feature.createparty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap

/**
 * Host's party screen header per Section 6 ASCII mock: party code, QR code,
 * ready for the connected-devices list + transport controls (feature:party
 * hosts the actual playback surface; this composable is embedded there).
 */
@Composable
fun CreatePartyCodeCard(
    partyId: String,
    hostAddress: String?,
    port: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Text(
            "LOCAL PARTY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            partyId,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.secondary
        )

        if (hostAddress != null && port != null) {
            val qrPayload = """{"partyId":"$partyId","hostAddress":"$hostAddress","port":$port}"""
            val bitmap = remember(qrPayload) { generateQrBitmap(qrPayload) }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = "Party QR code",
                    modifier = Modifier.size(180.dp)
                )
            }
        } else {
            Spacer(Modifier.height(180.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }

        Text(
            "Friends scan this or enter the code to join — no internet needed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun remember(key: Any?, calculation: () -> ImageBitmap?): ImageBitmap? {
    return androidx.compose.runtime.remember(key) { calculation() }
}

private fun generateQrBitmap(payload: String, sizePx: Int = 512): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                )
            }
        }
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
