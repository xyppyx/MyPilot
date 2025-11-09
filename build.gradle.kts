plugins {
    id("java")
    //id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.javaee"
version = "1.0"

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral() // 作为备用仓库，确保所有依赖都能下载

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
    // 注意：直接使用 IntelliJ Platform 自带的 Lucene，不单独引入依赖
    // 这样可以避免版本冲突问题（如 Codec 加载错误）
    // 平台提供的 Lucene API 会在编译和运行时自动可用
    // 参考：https://plugins.jetbrains.com/docs/intellij/using-platform-apis.html

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
            <ul>
              <li>初始发布：集成工具窗口、RAG 与 Agent 双模式</li>
              <li>新增编辑器/项目视图右键菜单与 Alt+M 快捷键</li>
              <li>支持代码引用卡片、会话管理与设置页入口</li>
            </ul>
        """.trimIndent()

        // 可选：在这里统一声明 vendor 信息（将自动补丁到 plugin.xml）
        vendor {
            name = "徐云鹏、陈艺龙、李雪菲"
            email = "2354093@tongji.edu.cn"
            url = "https://github.com/xyppyx/MyPilot"
        }
    }

    // JetBrains Marketplace 发布配置（本地/CI 通过环境变量注入）
    publishing {
        // 在本地/CI 设置：PUBLISH_TOKEN 或 JETBRAINS_TOKEN
        token.set(
            providers.environmentVariable("PUBLISH_TOKEN")
                .orElse(providers.environmentVariable("JETBRAINS_TOKEN"))
        )
        // 可选渠道：default、EAP 等；也可通过环境变量覆写
        channels.set(
            listOf(
                providers.environmentVariable("PUBLISH_CHANNEL").orElse("default").get()
            )
        )
    }

    //（可选）插件签名，用于 Marketplace 分发校验（建议在 CI 配置）
    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
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