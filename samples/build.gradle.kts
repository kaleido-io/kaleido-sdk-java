subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Forward SSL/TLS JVM args to forked app processes (bootRun, JavaExec)
    // without polluting the Gradle daemon. Set APP_JVM_ARGS env var with
    // space-separated -D flags (e.g. keyStore/trustStore for mTLS).
    tasks.withType<JavaExec> {
        val appArgs = System.getenv("APP_JVM_ARGS")
        if (!appArgs.isNullOrBlank()) {
            jvmArgs(appArgs.split(" "))
        }
    }
}
