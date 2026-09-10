# AGENTS.md

This project is a **Kuikly** app (Kotlin Multiplatform, single codebase → Android / iOS / HarmonyOS / H5 / Mini Program). It uses **Kuikly Compose DSL** for pages (pages extend `ComposeContainer`.

## Kuikly Compose rules (follow these)

@rules/kuiklyComposeDSL.mdc

The core rule: only `androidx.compose.runtime.*` uses the official package; **everything else** uses `com.tencent.kuikly.compose.*` (e.g. `com.tencent.kuikly.compose.ui.Modifier`, `com.tencent.kuikly.compose.foundation.layout.Column`, `com.tencent.kuikly.compose.material3.Text`).

## Kuikly DSL rules (only if you switch to the self-developed DSL builder)

@rules/kuiklyDSL.mdc

## Skills

The Kuikly AI skills are installed under `.claude/skills/` and `.agents/skills/` (13 skills, e.g. `kuikly-compose-ui-framework`, `kuikly-animation`, `kuikly-network-and-json`); versions are pinned in `skills-lock.json`. Invoke the relevant one when working on Kuikly UI components, modules, layouts, or routing.

## Build

- Gradle wrapper 8.5 (Tencent mirror), JDK 17 (set via `org.gradle.java.home` in `gradle.properties`).
- `./gradlew :shared:compileDebugKotlinAndroid` compiles the shared module for Android.
- Kuikly artifacts resolve from `https://mirrors.tencent.com/nexus/repository/maven-tencent/` and Aliyun mirrors (see `settings.gradle.kts`).
