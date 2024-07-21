plugins {
  id("java-library")
  alias(libs.plugins.jetbrains.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.auto.service)
  implementation(libs.androidx.tools.apigenerator)
  kapt (libs.auto.service)
  implementation(project(":annotation"))
}