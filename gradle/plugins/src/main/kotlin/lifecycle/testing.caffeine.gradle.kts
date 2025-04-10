import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
  `java-library`
  id("errorprone.caffeine")
}

val javaTestVersion: Provider<JavaLanguageVersion> = java.toolchain.languageVersion.map {
  val version = System.getenv("JAVA_TEST_VERSION")?.toIntOrNull()
  if (version == null) it else JavaLanguageVersion.of(version)
}
val mockitoAgent: Configuration by configurations.creating

dependencies {
  testImplementation(libs.truth)
  testImplementation(libs.testng)
  testImplementation(libs.bundles.junit)
  testImplementation(platform(libs.asm.bom))
  testImplementation(platform(libs.kotlin.bom))
  testImplementation(platform(libs.junit5.bom))

  testRuntimeOnly(libs.junit5.launcher)

  mockitoAgent(libs.mockito) {
    isTransitive = false
  }
}

tasks.withType<Test>().configureEach {
  inputs.property("javaVendor", java.toolchain.vendor.get().toString())
  inputs.property("javaDistribution", System.getenv("JDK_DISTRIBUTION")).optional(true)

  // Use --debug-jvm to remotely attach to the test task
  jvmArgs("-XX:SoftRefLRUPolicyMSPerMB=0", "-XX:+EnableDynamicAgentLoading", "-Xshare:off")
  jvmArgs("-javaagent:${mockitoAgent.asPath}")
  jvmArgs(defaultJvmArgs())
  if (isCI()) {
    reports.junitXml.includeSystemOutLog = false
    reports.junitXml.includeSystemErrLog = false
  }
  testLogging {
    events = setOf(SKIPPED, FAILED)
    exceptionFormat = FULL
    showStackTraces = true
    showExceptions = true
    showCauses = true
  }
  javaLauncher.set(
    javaToolchains.launcherFor {
      languageVersion.set(javaTestVersion)
    }
  )
}

tasks.named<JavaCompile>("compileTestJava").configure {
  options.errorprone.nullaway {
    customInitializerAnnotations.addAll(listOf(
      "org.testng.annotations.BeforeClass",
      "org.testng.annotations.BeforeMethod"))
    externalInitAnnotations.addAll(listOf(
      "org.mockito.testng.MockitoSettings",
      "picocli.CommandLine.Command"))
    excludedFieldAnnotations.addAll(listOf(
      "jakarta.inject.Inject",
      "org.mockito.Captor",
      "org.mockito.Mock"))
  }
}
