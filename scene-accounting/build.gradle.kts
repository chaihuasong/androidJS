plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.androidjs.accounting"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
}
