plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("7.4.2").apply(false)
    id("com.android.library").version("7.4.2").apply(false)
    kotlin("android").version("2.0.21-KBA-010").apply(false)
    kotlin("multiplatform").version("2.0.21-KBA-010").apply(false)
    // commonMain 全部是 Compose 代码,ohos 世界必须独立应用 Compose 编译器插件;
    // 版本跟随 ohos 世界的 Kotlin(主构建在根 build.gradle.kts 里用的是 2.1.21)
    kotlin("plugin.compose").version("2.0.21-KBA-010").apply(false)
    id("com.google.devtools.ksp").version("2.0.21-1.0.27").apply(false)

}
