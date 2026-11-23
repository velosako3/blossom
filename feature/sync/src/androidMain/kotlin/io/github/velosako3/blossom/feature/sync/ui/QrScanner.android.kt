// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.BarcodeFormat
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.feature.sync.resources.Res
import io.github.velosako3.blossom.feature.sync.resources.pair_allow_camera
import io.github.velosako3.blossom.feature.sync.resources.pair_camera_needed
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.Executors

actual val hasQrScanner: Boolean = true

/** CameraX preview with ZXing decoding each frame's luminance; asks for the camera first. */
@Composable
actual fun QrScanner(onCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.pair_camera_needed))
            GlassButton({ ask.launch(Manifest.permission.CAMERA) }, prominent = true) { Text(stringResource(Res.string.pair_allow_camera)) }
        }
        return
    }
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    val reader = remember { MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) } }
    // What's bound, so leaving the screen turns the camera off (it's bound to the activity, which outlives the screen).
    val bound = remember { arrayOfNulls<Any>(3) } // provider, preview, analysis
    DisposableEffect(Unit) {
        onDispose {
            val provider = bound[0] as? ProcessCameraProvider
            runCatching { provider?.unbind(bound[1] as? Preview, bound[2] as? ImageAnalysis) }
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            if (done) return@setAnalyzer
                            val plane = image.planes[0].buffer
                            val bytes = ByteArray(plane.remaining()).also { plane.get(it) }
                            val source = PlanarYUVLuminanceSource(bytes, image.planes[0].rowStride, image.height, 0, 0, image.width, image.height, false)
                            val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                            reader.reset()
                            if (text != null) { done = true; ContextCompat.getMainExecutor(ctx).execute { onCode(text) } }
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    bound[0] = provider; bound[1] = preview; bound[2] = analysis
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier,
    )
}
