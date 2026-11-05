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

    generatedSourceDirectory.set(project.project(":app").layout.buildDirectory.dir("generated/sources/fit"))
    var manualDir = project.project(":app").file("src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit/")
    inputs.dir(project.file("src"))
    inputs.dir(manualDir)
    outputs.dir(generatedSourceDirectory)

    mainClass = application.mainClass
    args(project.file("src/main/resources/fit_profile.json").absolutePath)
    args(generatedSourceDirectory.get().file("nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit/").asFile.absolutePath)
    args(project.project(":app").file("src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/garmin/fit/").absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}

tasks.register<JavaExec>("formatFitProfileJson") {
    description = "Reformat the JSON specification"
    var outputFile = project.file("src/main/resources/fit_profile.json")
    inputs.dir(project.file("src"))
    outputs.file(outputFile)

    mainClass = "nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.codegen.FormatFitProfileJson"
    args(outputFile.absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}
