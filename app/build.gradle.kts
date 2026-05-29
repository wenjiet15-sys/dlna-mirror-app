plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dlna"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dlna"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/beans.xml",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Jetty (Cling 所需，在 Android 10+ 需包含 servlet-api)
    implementation("org.eclipse.jetty:jetty-server:8.1.21.v20160908")
    implementation("org.eclipse.jetty:jetty-servlet:8.1.21.v20160908")
    implementation("org.eclipse.jetty:jetty-client:8.1.21.v20160908")
    implementation("javax.servlet:javax.servlet-api:3.1.0")
    implementation("org.slf4j:slf4j-jdk14:1.7.25")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // NanoHTTPD
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // Cling DLNA
    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")
}
