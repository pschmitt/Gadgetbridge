plugins {
    id("java-library")
    alias(libs.plugins.protobuf)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.protobuf.lite)
}

protobuf {
    protoc {
        artifact = "${libs.protoc.get()}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                findByName("java")?.option("lite")
            }
        }
    }
}
