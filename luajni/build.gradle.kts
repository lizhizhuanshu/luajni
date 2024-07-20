plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
}

android {
  namespace = "top.lizhistudio.luajni"
  compileSdk = 34

  defaultConfig {
    minSdk = 21

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")

    externalNativeBuild {
      cmake {
        arguments ("-DANDROID_STL=c++_shared")
        cppFlags ("-frtti"," -fexceptions","-std=c++17")
        cFlags ("-fPIE"," -fexceptions")
        abiFilters ("armeabi-v7a", "arm64-v8a","x86","x86_64")
      }
    }
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

  externalNativeBuild {
    cmake {
      path("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  ndkVersion ="25.2.9519653"
}

afterEvaluate {
  tasks.withType<Task>().configureEach {
    if (name.startsWith("configureCMake") ) {
      dependsOn("kaptDebugKotlin", "kaptReleaseKotlin")
    }
  }
}


dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)

  implementation(project(":annotation"))
  kapt(project(":annotation_processor"))
  kaptTest(project(":annotation_processor"))
}