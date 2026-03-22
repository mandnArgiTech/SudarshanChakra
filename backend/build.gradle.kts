// SudarshanChakra — Backend Root Build Configuration
// All microservices share this parent configuration (jwt-support is a plain library)

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

allprojects {
    group = "com.sudarshanchakra"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    extensions.getByType<DependencyManagementExtension>().apply {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    if (name == "jwt-support") {
        plugins.apply("java-library")
    } else {
        apply(plugin = "org.springframework.boot")

        dependencies {
            implementation("org.springframework.boot:spring-boot-starter-web")
            implementation("org.springframework.boot:spring-boot-starter-actuator")
            implementation("org.springframework.boot:spring-boot-starter-validation")
            implementation("org.projectlombok:lombok")
            annotationProcessor("org.projectlombok:lombok")
            implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

            testImplementation("org.springframework.boot:spring-boot-starter-test")
        }
    }

    tasks.test {
        useJUnitPlatform {
            excludeTags("integration")
        }
    }

    if (name != "jwt-support") {
        tasks.register<Test>("integrationTest") {
            group = "verification"
            description = "Testcontainers tests; requires Docker"
            testClassesDirs = sourceSets["test"].output.classesDirs
            classpath = sourceSets["test"].runtimeClasspath
            useJUnitPlatform {
                includeTags("integration")
            }
            shouldRunAfter(tasks.test)
        }
    }
}
