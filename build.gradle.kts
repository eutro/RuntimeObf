plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.0")
    implementation("org.ow2.asm:asm-tree:9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
}

tasks.test {
    useJUnitPlatform()
}
