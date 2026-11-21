package com.basem.qiyam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.basem.qiyam.ui.Night
import java.time.Duration
import java.util.Locale

// Small building blocks shared by every screen.

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    // iOS inset-grouped card: no border, soft corners, sits on the grey grouped background.
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Night.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text.uppercase(), color = Night.Muted, fontSize = 13.sp, letterSpacing = 0.2.sp)

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Night.Gold, unfocusedBorderColor = Night.CardEdge,
    focusedLabelColor = Night.Gold, unfocusedLabelColor = Night.Muted,
    focusedTextColor = Night.Text, unfocusedTextColor = Night.Text, cursorColor = Night.Gold,
)

/** Gets a fresh location (network first, then GPS), names the place, and saves it. */
@SuppressLint("MissingPermission")
internal fun fetchLocation(ctx: Context, prefs: Prefs, done: (String) -> Unit) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val lm = ctx.getSystemService(LocationManager::class.java)
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
        runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
    }
    fun save(loc: Location?) {
        val l = loc ?: providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        if (l == null) { done("Couldn't get a location. Turn on Location, or set coordinates manually."); return }
        prefs.lat = l.latitude; prefs.lng = l.longitude
        prefs.placeName = String.format(Locale.US, "%.3f, %.3f", l.latitude, l.longitude)
        done("Location updated")
        Thread {
            runCatching {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()
                val name = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                if (name != null) prefs.placeName = name
            }
        }.start()
    }
    val provider = providers.firstOrNull() ?: return save(null)
    if (Build.VERSION.SDK_INT >= 30) {
        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { save(it) }
    } else {
        save(null)
    }
}

fun human(d: Duration): String {
    val m = d.toMinutes().coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
