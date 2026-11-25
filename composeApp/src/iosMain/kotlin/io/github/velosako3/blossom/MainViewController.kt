// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.window.ComposeUIViewController
import io.github.velosako3.blossom.core.designsystem.components.installImageLoader
import platform.UIKit.UIViewController

private val graph by lazy { installImageLoader(); IosGraph() }

/** The iOS app's root view controller, created by iosApp's Swift entry point. */
@Suppress("unused", "FunctionName") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController { IosApp(graph) }
