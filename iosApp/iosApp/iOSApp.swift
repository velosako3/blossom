// SPDX-License-Identifier: AGPL-3.0-or-later
// blossom's iOS entry point: one full-screen Compose view (the whole app is Kotlin, see composeApp/src/iosMain).
import SwiftUI
import UIKit
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
