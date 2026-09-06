plugins { `java-library` }
dependencies {
    api(project(":minecraft-common"))
    implementation("com.github.cryptomorin:XSeries:9.3.1") { isTransitive = false }
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.google.guava:guava:33.3.1-jre")
}
