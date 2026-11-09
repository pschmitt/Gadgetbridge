plugins {
    application
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.codegen.FitCodeGen"
}

dependencies {
    implementation(libs.json)
    implementation(libs.gson)
}

abstract class JavaExecFit : JavaExec() {
    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty
}

tasks.register<JavaExecFit>("genFit") {
    description = "Generate FitMessage source files from JSON"

    val appProject = project(":app")
    val fitProfileJson = file("src/main/resources/fit_profile.json")
    val manualDir = appProject.file("src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit/")

    generatedSourceDirectory.set(appProject.layout.buildDirectory.dir("generated/sources/fit"))

    inputs.file(fitProfileJson)
    inputs.dir(manualDir)
    outputs.dir(generatedSourceDirectory)

    mainClass = application.mainClass
    args(
        fitProfileJson.absolutePath,
        generatedSourceDirectory.dir("nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit/").get().asFile.absolutePath,
        manualDir.absolutePath
    )
    classpath = sourceSets.main.get().runtimeClasspath
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}

tasks.register<JavaExec>("formatFitProfileJson") {
    description = "Reformat the JSON specification"

    val outputFile = file("src/main/resources/fit_profile.json")

    inputs.file(outputFile)
    outputs.file(outputFile)

    mainClass = "nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.codegen.FormatFitProfileJson"
    args(outputFile.absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}
