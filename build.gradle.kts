plugins {
    id("java")
    //id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.javaee"
version = "1.0-SNAPSHOT"

repositories {
    //mavenCentral()

    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }

    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("com.intellij.java")

    }

    // Additional dependencies for UI components
    implementation("org.jetbrains:annotations:24.0.1")

    // RAG dependencies
    // Apache POI for PPT processing
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5") // For DOC (97-2003) format support

    // Apache PDFBox for PDF processing
    implementation("org.apache.pdfbox:pdfbox:2.0.30")

    // Lucene for vector search
    implementation("org.apache.lucene:lucene-core:9.11.1")
    implementation("org.apache.lucene:lucene-analysis-common:9.11.1")

    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("junit:junit:4.13.2") // JUnit 4 for IntelliJ test framework compatibility
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // Configure test task to use JUnit 5
    test {
        useJUnitPlatform()

        // Add JVM arguments to fix Lucene MMapDirectory issues
        jvmArgs(
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
            "-Dorg.apache.lucene.store.MMapDirectory.enableMmapHack=false"
        )

        // Increase memory for tests
        minHeapSize = "512m"
        maxHeapSize = "2048m"
    }

    // 确保 resources 目录下的所有文件都被包含（包括 PPT、PDF 等二进制文件）
    processResources {
        // 默认情况下，Gradle 会复制所有 resources 文件
        // 但为了确保大文件和二进制文件也被包含，我们显式配置
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        // 包含所有文件类型
        include("**/*")

        // 特别说明：不要过滤二进制文件
        filteringCharset = "UTF-8"
    }

    // 构建插件时，确保资源文件被打包
    buildPlugin {
        dependsOn(processResources)
    }
}