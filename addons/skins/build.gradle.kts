bukkitPluginYaml {
    main = "xyz.jpenilla.squaremap.addon.skins.SquaremapSkins"
    authors.add("BillyGalbreath")
    depend.add("SkinsRestorer")
}

dependencies {
    implementation("org.imgscalr:imgscalr-lib:4.2")
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.2.0")
}
