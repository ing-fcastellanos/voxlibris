import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.fcastellanos.voxlibris"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fcastellanos.voxlibris"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Los dos teléfonos de prueba (POCO X3 Pro, Galaxy S25) son arm64.
        // sherpa-onnx trae 4 ABIs; empaquetamos solo esta para achicar el APK.
        ndk.abiFilters.add("arm64-v8a")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Readium 3.x lo exige (declarado en los metadatos de sus AAR).
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes.add("META-INF/{AL2.0,LGPL2.1}")
        resources.excludes.add("META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// ---------------------------------------------------------------------------
// sherpa-onnx (runtime de TTS on-device). El AAR pesa ~37 MB, así que no va al
// repositorio: se descarga de GitHub Releases la primera vez y se verifica su hash.
// ---------------------------------------------------------------------------
val sherpaOnnxVersion = "2.0.1"
val sherpaOnnxAarName = "sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar"
val sherpaOnnxAarSha256 = "07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575"
val sherpaOnnxAarFile = layout.projectDirectory.file("libs/$sherpaOnnxAarName").asFile

val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    description = "Descarga el AAR de sherpa-onnx a app/libs si todavía no está."
    outputs.file(sherpaOnnxAarFile)
    onlyIf { !sherpaOnnxAarFile.exists() }
    doLast {
        sherpaOnnxAarFile.parentFile.mkdirs()
        val url = URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/$sherpaOnnxAarName").toURL()
        val partial = File(sherpaOnnxAarFile.path + ".part")
        logger.lifecycle("Descargando $sherpaOnnxAarName ...")
        url.openStream().use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) digest.update(buffer, 0, read)
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != sherpaOnnxAarSha256) {
            partial.delete()
            throw GradleException("SHA-256 inesperado para $sherpaOnnxAarName: $actual")
        }
        if (!partial.renameTo(sherpaOnnxAarFile)) throw GradleException("No se pudo mover ${partial.name}")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Runtime de TTS on-device (se usa a partir de la fase de voz; se integra ya para validar el empaquetado).
    implementation(files(sherpaOnnxAarFile).builtBy(downloadSherpaOnnx))

    // Readium: parseo de EPUB (shared + streamer) y navegador (fase de lector).
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
