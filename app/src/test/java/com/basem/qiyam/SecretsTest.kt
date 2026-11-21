package com.basem.qiyam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SecretsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun secretsLiveOutsideOrdinarySettings() {
        Prefs(ctx).putStr("t212Key", "abc")
        assertEquals("abc", Prefs(ctx).getStr("t212Key"))
        assertNull(ctx.getSharedPreferences("qiyam", Context.MODE_PRIVATE).getString("t212Key", null))
        Prefs(ctx).putStr("t212Key", "")
        assertEquals("", Prefs(ctx).getStr("t212Key"))
    }

    @Test fun oldKeysMoveIntoTheSecretStore() {
        ctx.getSharedPreferences("qiyam", Context.MODE_PRIVATE).edit().putString("lyftaKey", "old").commit()
        Secrets.migrate(ctx)
        assertEquals("old", Prefs(ctx).lyftaKey)
        assertNull(ctx.getSharedPreferences("qiyam", Context.MODE_PRIVATE).getString("lyftaKey", null))
    }
}
