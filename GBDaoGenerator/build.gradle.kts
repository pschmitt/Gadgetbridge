plugins {
    application
    java
}

java {
    toolchain {
        // Setting sourceCompatibility and targetCompatibility isn't required as this module ships
        // no complied code.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "nodomain.freeyourgadget.gadgetbridge.daogen.GBDaoGenerator"
}

dependencies {
    // https://codeberg.org/Freeyourgadget/greenDao
    // As of 2025-06-19, this is bundled directly in the repository due to jitpack build issues.
    //implementation 'com.github.Freeyourgadget:greendao:1998d7cd2d21f662c6044f6ccf3b3a251bbad341'

    implementation(libs.freemarker)
}

sourceSets {
    main {
        java {
            srcDir("src")
        }
        resources {
            srcDir("src-template")
        }
    }
}

abstract class JavaExecDao : JavaExec() {
    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty
}

tasks.register<JavaExecDao>("genSources") {
    generatedSourceDirectory.set(project.project(":app").layout.buildDirectory.dir("generated/sources/gbdao"))
    inputs.dir("src")
    inputs.dir("src-template")
    outputs.dir(generatedSourceDirectory)

    mainClass = application.mainClass
    args(generatedSourceDirectory.get().asFile.absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = file("../")
    if (gradle.startParameter.logLevel <= LogLevel.INFO) {
        jvmArgs("-Dverbose=true")
    }
}
