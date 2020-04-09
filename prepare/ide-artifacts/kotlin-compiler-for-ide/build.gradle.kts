val compilerModules: Array<String> by rootProject.extra

val projects = compilerModules.asList() + listOf(
    ":kotlin-compiler-runner",
    ":kotlin-preloader",
    ":daemon-common",
    ":daemon-common-new",
    ":kotlin-daemon",
    ":kotlin-daemon-client",
    ":kotlin-daemon-client-new"
)

publishProjectJars(
    projects = projects,
    libraryDependencies = listOf(protobufFull())
)