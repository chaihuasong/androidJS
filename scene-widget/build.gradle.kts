plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.androidjs.widget"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
}
