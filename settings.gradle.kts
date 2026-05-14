plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "acc-client"

include("acc-client-core", "simulator-grpc-server", "simulator-grpc-client", "examples")

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
