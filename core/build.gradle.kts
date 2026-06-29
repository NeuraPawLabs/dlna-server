plugins {
    id("org.jetbrains.kotlin.jvm")
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

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
