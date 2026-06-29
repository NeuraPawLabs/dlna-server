plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDirs("src/main/java")
        }
        test {
            kotlin.srcDirs("src/test/java")
        }
    }
}

application {
    mainClass.set("labs.newrapaw.dlna.probe.desktop.DesktopCliKt")
}

dependencies {
    implementation(project(":core"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
