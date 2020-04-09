@Suppress("UNCHECKED_CAST")
val projects = project(":kotlin-jps-plugin").extra["compilerComponents"] as List<String>

publishProjectJars(projects)