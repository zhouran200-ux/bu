import java.nio.charset.Charset
import java.security.MessageDigest
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

val nativeCharset = Charset.forName(
    System.getProperty("native.encoding", System.getProperty("file.encoding"))
)

// Windows Java 启动器按本地编码读取参数文件，Gradle 9.4.1 则按 daemon 编码写入。
if (System.getProperty("os.name").startsWith("Windows") && Charset.defaultCharset() != nativeCharset) {
    val cacheCandidates = listOf(gradle.gradleUserHomeDir, file(System.getProperty("java.io.tmpdir")))
    val projectKey = MessageDigest.getInstance("SHA-256")
        .digest(projectDir.absolutePath.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val classpathDirectory = layout.dir(providers.provider {
        val cacheRoot = cacheCandidates.firstOrNull { directory -> directory.absolutePath.all { it.code < 128 } }
            ?: throw GradleException("Windows unit tests require an ASCII GRADLE_USER_HOME or java.io.tmpdir path.")
        cacheRoot.resolve("lucent-test-classpath/$projectKey")
    })

    afterEvaluate {
        tasks.withType<Test>().toList().forEach { testTask ->
            val originalClasspath = testTask.classpath
            val classpathJar = tasks.register<Jar>("${testTask.name}ClasspathJar") {
                archiveFileName.set("${testTask.name}-classpath.jar")
                destinationDirectory.set(classpathDirectory)
                dependsOn(originalClasspath)
                inputs.files(originalClasspath)
                    .withPropertyName("testRuntimeClasspath")
                    .withNormalizer(ClasspathNormalizer::class.java)
                inputs.property("testRuntimeClasspathLocations", originalClasspath.elements.map { entries ->
                    entries.map { it.asFile.absolutePath }
                })
                doFirst {
                    (this as Jar).manifest.attributes(
                        "Class-Path" to originalClasspath.files
                            .filter { it.exists() }
                            .joinToString(" ") { it.toURI().toASCIIString() }
                    )
                }
            }
            // 原始条目仍供 Gradle 扫描测试基类、跟踪内容变化和推导编译依赖。
            testTask.classpath = files(classpathJar, originalClasspath)
        }
    }
}
