import java.io.RandomAccessFile
import java.util.Properties

plugins {
    java
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "org.glavo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.compileJava {
    options.release.set(9)
    modularity.inferModulePath.set(true)
    options.isWarnings = false
    doLast {
        val tree = fileTree(destinationDirectory)
        tree.include("**/*.class")
        tree.exclude("module-info.class")
        tree.forEach {
            RandomAccessFile(it, "rw").use { rf ->
                rf.seek(7)   // major version
                rf.write(52)   // java 8
                rf.close()
            }
        }
    }
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        tags = listOf(
            "apiNote:a:API Note:",
            "implSpec:a:Implementation Requirements:",
            "implNote:a:Implementation Note:"
        )

        addStringOption("Xdoclint:none", "-quiet")
    }

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            version = project.version.toString()
            artifactId = project.name
            from(components["java"])

            pom {
                name.set(project.name)
                description.set("Public distribution of jdk.internal.jimage")
                url.set("https://github.com/Glavo/jimage")
                licenses {
                    license {
                        name.set("GPL v2 with Classpath Exception")
                        url.set("https://openjdk.java.net/legal/gplv2+ce.html")
                    }
                }
                developers {
                    developer {
                        id.set("glavo")
                        name.set("Glavo")
                        email.set("zjx001202@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/Glavo/jimage")
                }
            }
        }
    }
}

var secretPropsFile = project.rootProject.file("gradle/maven-central-publish.properties")
if (!secretPropsFile.exists()) {
    secretPropsFile = file(System.getProperty("user.home")).resolve(".gradle").resolve("maven-central-publish.properties")
}

if (secretPropsFile.exists()) {
    // Read local.properties file first if it exists
    val p = Properties()
    secretPropsFile.reader().use {
        p.load(it)
    }

    p.forEach { (name, value) ->
        rootProject.ext[name.toString()] = value
    }
}

listOf(
    "sonatypeUsername" to "OSSRH_USERNAME",
    "sonatypePassword" to "OSSRH_PASSWORD",
    "sonatypeStagingProfileId" to "SONATYPE_STAGING_PROFILE_ID",
    "signing.keyId" to "SIGNING_KEY_ID",
    "signing.password" to "SIGNING_PASSWORD",
    "signing.key" to "SIGNING_KEY"
).forEach { (p, e) ->
    if (!rootProject.ext.has(p)) {
        rootProject.ext[p] = System.getenv(e)
    }
}

signing {
    if (rootProject.ext.has("signing.key")) {
        useInMemoryPgpKeys(
            rootProject.ext["signing.keyId"].toString(),
            rootProject.ext["signing.key"].toString(),
            rootProject.ext["signing.password"].toString(),
        )
    }
    sign(publishing.publications["maven"])
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(rootProject.ext["sonatypeStagingProfileId"].toString())
            username.set(rootProject.ext["sonatypeUsername"].toString())
            password.set(rootProject.ext["sonatypePassword"].toString())
        }
    }
}
