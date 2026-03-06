configurations {
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.1")
    }
}
