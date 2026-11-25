package io.github.carsonfiechtner.keepup.ui.stage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders every stage drawable and launcher icon, so broken vector XML fails here and the art can be reviewed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageArtRenderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val art = listOf(R.drawable.stage_1, R.drawable.stage_2, R.drawable.stage_3, R.drawable.stage_4, R.drawable.stage_5, R.drawable.stage_6)
    private val icons = listOf(
        R.mipmap.ic_launcher_stage_1, R.mipmap.ic_launcher_stage_2, R.mipmap.ic_launcher_stage_3,
        R.mipmap.ic_launcher_stage_4, R.mipmap.ic_launcher_stage_5, R.mipmap.ic_launcher_stage_6,
    )

    @Test
    fun `stage art and launcher icons render`() {
        val size = 240
        val sheet = Bitmap.createBitmap(size * 6 + 70, size * 2 + 30, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        art.forEachIndexed { i, res ->
            val d = ContextCompat.getDrawable(context, res)!!
            d.setBounds(10 + i * (size + 10), 10, 10 + i * (size + 10) + size, 10 + size)
            d.draw(canvas)
        }
        icons.forEachIndexed { i, res ->
            val d = ContextCompat.getDrawable(context, res)!!
            val left = 10 + i * (size + 10)
            val top = 20 + size
            // Launcher-style mask like the Pixel's: a circle showing the middle 72 of the 108dp canvas.
            val inset = size / 4
            canvas.save()
            val mask = Path().apply {
                addCircle(left + size / 2f, top + size / 2f, size / 2f, Path.Direction.CW)
            }
            canvas.clipPath(mask)
            d.setBounds(left - inset, top - inset, left + size + inset, top + size + inset)
            d.draw(canvas)
            canvas.restore()
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "stages.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The top-left pixel of stage 1 is its light background, of stage 6 its dark sky.
        assertThat(Color.green(sheet.getPixel(12, 12))).isGreaterThan(200)
        assertThat(Color.red(sheet.getPixel(12 + 5 * (size + 10), 12))).isLessThan(80)
    }
}
