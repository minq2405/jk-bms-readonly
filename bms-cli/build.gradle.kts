plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":bms-core"))
}

application {
    mainClass.set("com.bmsjk.cli.MainKt")
}
