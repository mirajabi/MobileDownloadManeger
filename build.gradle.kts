buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// JitPack invokes this name on the root project. The library module owns the
// publication; this task only forwards to it.
tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the downloader module to the local Maven repository."
    dependsOn(":downloader:publishToMavenLocal")
}