plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":backend:shared-kernel"))
    implementation(project(":backend:identity-access"))
    implementation(project(":backend:ledger"))
    implementation(project(":backend:assets"))
    implementation(project(":backend:inventory"))
    implementation(project(":backend:audit"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
}
