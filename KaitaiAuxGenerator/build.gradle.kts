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
    mainClass = "org.gadgetbridge.kaitai_aux.main.KaitaiAuxGen"
}

dependencies {
    implementation(libs.snakeyaml)
}

abstract class JavaExecKaitai : JavaExec() {
    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty
}

tasks.register<JavaExecKaitai>("genKaitaiAux") {
    description = "Generate auxiliary source files from Kaitai structs"

    val appProject = project(":app")
    val ksyDir = appProject.file("src/main/ksy")

    generatedSourceDirectory.set(appProject.layout.buildDirectory.dir("generated/sources/kaitaiaux"))

    inputs.dir(ksyDir)
    outputs.dir(generatedSourceDirectory)

    mainClass = application.mainClass
    args(ksyDir, generatedSourceDirectory.get().asFile.absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}
