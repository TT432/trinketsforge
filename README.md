# Trinkets Forge

forge port for [trinkets](https://github.com/emilyploszaj/trinkets)

---

The mod has been uploaded to modrinth and you can simply use modrinth maven to depend on this project,
here is a simple example to use directly:
```gradle
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation("maven.modrinth:trinketsforge:{mc_version}-{mod_version}")
}
```