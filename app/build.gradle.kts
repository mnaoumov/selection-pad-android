import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

plugins {
  id("com.android.application")
}

/*
 * The upload key, and why none of it is in this repo.
 *
 * Play App Signing holds the key that signs what users install; this build only signs with the
 * UPLOAD key, which Play uses to check that a bundle came from us. An upload key can be reset
 * through Play if it is lost or leaked, so it is a credential rather than an identity — but it is
 * still a credential, and the repo is public. So the keystore and its passwords live outside the
 * checkout, and the build reads four values, each from a Gradle property (normally set in
 * `~/.gradle/gradle.properties`) or else from an environment variable:
 *
 *   ascUploadStoreFile      ASC_UPLOAD_STORE_FILE      path to the .jks
 *   ascUploadStorePassword  ASC_UPLOAD_STORE_PASSWORD
 *   ascUploadKeyAlias       ASC_UPLOAD_KEY_ALIAS
 *   ascUploadKeyPassword    ASC_UPLOAD_KEY_PASSWORD
 *
 * With none of them set a release still builds, unsigned — which is what a clone on another
 * machine gets, and Play refuses such a bundle outright rather than accepting the wrong one.
 * Setting only some of them is a mistake worth stopping on, so that fails the build.
 */
fun uploadSetting(property: String, variable: String): String? =
  providers.gradleProperty(property).orElse(providers.environmentVariable(variable)).orNull
    ?.takeIf { it.isNotBlank() }

val uploadSettings = mapOf(
  "storeFile" to uploadSetting("ascUploadStoreFile", "ASC_UPLOAD_STORE_FILE"),
  "storePassword" to uploadSetting("ascUploadStorePassword", "ASC_UPLOAD_STORE_PASSWORD"),
  "keyAlias" to uploadSetting("ascUploadKeyAlias", "ASC_UPLOAD_KEY_ALIAS"),
  "keyPassword" to uploadSetting("ascUploadKeyPassword", "ASC_UPLOAD_KEY_PASSWORD"),
)

val uploadKeyConfigured = uploadSettings.values.all { it != null }

if (!uploadKeyConfigured && uploadSettings.values.any { it != null }) {
  val missing = uploadSettings.filterValues { it == null }.keys.joinToString()
  throw GradleException("The upload key is only partly configured; missing: $missing")
}

android {
  namespace = "dev.mnaoumov.selectionpad"
  // 36.1, not 36: AccessibilityNodeInfo.getSelection is a 36.1 API, and Chrome announces a page
  // selection through it once its extended-selection feature is on (SelectionObserver).
  compileSdk {
    version = release(36) { minorApiLevel = 1 }
  }

  defaultConfig {
    applicationId = "dev.mnaoumov.selectionpad"

    // TYPE_ACCESSIBILITY_OVERLAY is API 22 and dispatchGesture is API 24; the floor here is set by
    // nothing more exotic than wanting a modern baseline. The pad build briefly raised a spike to 34 for
    // attachAccessibilityOverlayToDisplay, which turned out not to work from an ordinary app.
    minSdk = 33
    targetSdk = 36

    // Play refuses a second upload with a versionCode it has seen, so this goes up by one for every
    // bundle that leaves the machine — including one that only ever reaches the internal track.
    versionCode = 1
    versionName = "0.1"
  }

  signingConfigs {
    if (uploadKeyConfigured) {
      create("upload") {
        storeFile = file(uploadSettings.getValue("storeFile")!!)
        storePassword = uploadSettings.getValue("storePassword")
        keyAlias = uploadSettings.getValue("keyAlias")
        keyPassword = uploadSettings.getValue("keyPassword")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (uploadKeyConfigured) signingConfig = signingConfigs.getByName("upload")
    }
  }
}

/*
 * `.\gradlew phone`: install the debug build on the phone and turn the pad on, in one command.
 *
 * It does what `scripts\pad.ps1 on` does, without PowerShell. Two choices are deliberate:
 *
 * - It installs with `adb -s <serial> install -r` rather than depending on `installDebug`, because
 *   `installDebug` installs on EVERY attached device, and the emulators on this machine belong to
 *   other projects' suites (AGENTS.md, "Never touch"). So the serial is resolved first: `-Pserial=`
 *   or `ANDROID_SERIAL` when given, else the one attached device that is not an emulator.
 * - When the service is already enabled it is taken off the list and put back, because a reinstall
 *   can leave it listed with no accessibility connection, and only a rewrite rebinds it. Every
 *   other service on the list (a password manager, say) is kept, and the write is read back.
 */
abstract class PhoneTask : DefaultTask() {
  @get:InputFile
  abstract val apk: RegularFileProperty

  @get:Internal
  abstract val adb: RegularFileProperty

  @get:Input
  @get:Optional
  abstract val serial: Property<String>

  @get:Input
  abstract val component: Property<String>

  @get:Inject
  abstract val execOperations: ExecOperations

  /*
   * The adb on PATH when there is one, the SDK's otherwise. Two adb versions on one machine each
   * restart the other's server, and a restart drops the phone until it reconnects: measured
   * 2026-10-30, the SDK's 37.0.0 after the PATH's 37.0.1 left `adb devices` empty for minutes.
   * The scripts under `scripts\` call the PATH's adb, so this one does too.
   */
  private val adbPath: String by lazy {
    val names = if (System.getProperty("os.name").startsWith("Windows")) listOf("adb.exe", "adb.cmd", "adb.bat") else listOf("adb")
    (System.getenv("PATH") ?: "").split(File.pathSeparator)
      .flatMap { dir -> names.map { File(dir, it) } }
      .firstOrNull { it.isFile }?.absolutePath
      ?: adb.get().asFile.absolutePath
  }

  private fun run(vararg args: String): String {
    val out = ByteArrayOutputStream()
    execOperations.exec {
      commandLine(listOf(adbPath) + args)
      standardOutput = out
    }
    return out.toString().trim()
  }

  private fun resolveSerial(): String {
    serial.orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val phones = run("devices").lines()
      .mapNotNull { Regex("""^(\S+)\s+device$""").find(it.trim())?.groupValues?.get(1) }
      .filterNot { it.startsWith("emulator-") }
    return when (phones.size) {
      1 -> phones.single()
      0 -> throw GradleException("No physical device attached (emulators are ignored on purpose). Plug the phone in, or pass -Pserial=.")
      else -> throw GradleException("More than one physical device attached: ${phones.joinToString()}. Pass -Pserial= to choose.")
    }
  }

  @TaskAction
  fun phone() {
    val device = resolveSerial()
    fun shell(vararg args: String) = run("-s", device, "shell", *args)
    fun enabled(): List<String> {
      val value = shell("settings", "get", "secure", "enabled_accessibility_services")
      return if (value.isEmpty() || value == "null") emptyList() else value.split(':').filter { it.isNotEmpty() }
    }
    fun write(services: List<String>) {
      shell("settings", "put", "secure", "enabled_accessibility_services", services.joinToString(":").ifEmpty { "\"\"" })
      if (services.isNotEmpty()) shell("settings", "put", "secure", "accessibility_enabled", "1")
      Thread.sleep(400)
    }

    logger.lifecycle("Installing ${apk.get().asFile.name} on $device")
    run("-s", device, "install", "-r", apk.get().asFile.absolutePath)

    val ours = component.get()
    val others = enabled().filter { it != ours }
    if (enabled().contains(ours)) write(others)
    write(others + ours)

    val actual = enabled()
    val missing = (others + ours).filterNot { it in actual }
    if (missing.isNotEmpty()) {
      throw GradleException("The device did not keep what was written. Missing: ${missing.joinToString()}. Now enabled: ${actual.joinToString()}")
    }
    logger.lifecycle("Selection Pad enabled on $device. The pad appears as soon as the service connects.")
  }
}

tasks.register<PhoneTask>("phone") {
  group = "install"
  description = "Installs the debug build on the attached phone (never an emulator) and turns the pad on."
  dependsOn("assembleDebug")
  apk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  adb.set(androidComponents.sdkComponents.adb)
  serial.set(providers.gradleProperty("serial").orElse(providers.environmentVariable("ANDROID_SERIAL")))
  component.set("dev.mnaoumov.selectionpad/dev.mnaoumov.selectionpad.AscAccessibilityService")
}

// Test-only: nothing here reaches the APK, so the permission list and no-third-party-code
// properties of the shipped app are untouched.
dependencies {
  testImplementation("junit:junit:4.13.2")
}
