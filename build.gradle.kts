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

    // Apache PDFBox for PDF processing
    implementation("org.apache.pdfbox:pdfbox:2.0.30")

    // Lucene for vector search
    implementation("org.apache.lucene:lucene-core:9.9.2")
    implementation("org.apache.lucene:lucene-analysis-common:9.9.2")

    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
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
}