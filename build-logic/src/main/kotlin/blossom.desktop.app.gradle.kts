// SPDX-License-Identifier: AGPL-3.0-or-later
// The desktop app: bundles libVLC (Spotube's vlcj-bundler, vendored in org/spotube) into every installer so users install nothing.
apply<org.spotube.VlcjBundlerGradlePlugin>()

extensions.configure<org.spotube.VlcjBundlerExtension> {
    packageName.set("io.github.velosako3.blossom.vlc")
    objectName.set("VlcLoader")
    bundleVlc.set(true) // also for .deb: blossom's desktop installs are self-contained
}

val addVlcPlugins = tasks.register<AddVlcPluginsTask>("addVlcPlugins") {
    nativesDirectory.set(project.extensions.getByType<org.spotube.VlcjBundlerExtension>().vlcNativesDirectory)
    dependsOn("downloadVlcNatives")
}
tasks.matching { it.name == "downloadVlcNatives" }.configureEach { finalizedBy(addVlcPlugins) }
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(addVlcPlugins) }
