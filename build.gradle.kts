plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.148"
}

fun prop(name: String) = property(name).toString()

val modId = prop("mod_id")

version = "${prop("mod_version")}+${prop("minecraft_version")}-neoforge"
group = prop("mod_group_id")

base {
    archivesName = modId
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

repositories {
    // Create, Ponder and Flywheel.
    maven("https://maven.createmod.net")
    // Registrate, which Create is built with.
    maven("https://maven.ithundxr.dev/snapshots")
    // Sable, the physics engine Create Aeronautics flies on, and its public API.
    maven("https://maven.ryanhcode.dev/releases") {
        content { includeGroupByRegex("dev\\.ryanhcode.*") }
    }
    // Veil, the rendering library Sable ships inside its jar, and the Molang library Veil ships inside its own.
    maven("https://maven.blamejared.com") {
        content {
            includeGroup("foundry.veil")
            includeGroup("gg.moonflower")
        }
    }
}

// Blockpilot (github.com/sparten11740/blockpilot-mcp), the client mod an agent play-tests the mod with: Aerolarge gives
// it commands of its own (see compat/blockpilot), compiled against its API from the local Maven repository, where
// `./gradlew publishToMavenLocal` in Blockpilot's repository puts it. Without it there, those commands are left out of
// the build, and nothing else changes.
val mavenLocalRepository = System.getProperty("maven.repo.local") ?: "${System.getProperty("user.home")}/.m2/repository"
val blockpilotApi = file("$mavenLocalRepository/dev/blockpilot/blockpilot-api/2.0.0")
val withBlockpilot = blockpilotApi.isDirectory
if (withBlockpilot) {
    repositories {
        mavenLocal {
            content { includeGroup("dev.blockpilot") }
        }
    }
    dependencies {
        compileOnly("dev.blockpilot:blockpilot-api:2.0.0")
    }
} else {
    sourceSets.main {
        java.exclude("dev/aerolarge/compat/blockpilot/**")
    }
}

// Create is required: its Super Glue holds the airliner together for the Physics Assembler. The autopilot reads the
// assembled airliner's position, attitude and speed from Sable; it only runs when Sable is installed. The tail camera
// display draws the world from the fin through Veil, which comes inside Sable's jar. All are only compiled against;
// players bring the mods. Create Propulsion and Create Aeronautics (Simulated, Offroad) are only named by registry id.
// To run them in a dev client, drop their jars (and Create's) into run/mods.
dependencies {
    // Create's published POM drags in JEI, FTB and more; only its classes are needed.
    compileOnly("com.simibubi.create:create-1.21.1:6.0.10-281:slim") { isTransitive = false }
    compileOnly("net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6")
    compileOnly("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")
    compileOnly("dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.5") { isTransitive = false }
    compileOnly("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0") { isTransitive = false }
    // The version Sable 2.0.5 bundles.
    compileOnly("foundry.veil:veil-neoforge-1.21.1:4.3.2") { isTransitive = false }
    // The one Veil 4.3.2 bundles: Veil sizes its framebuffers with Molang expressions.
    compileOnly("gg.moonflower:molang-compiler:3.1.1.19") { isTransitive = false }
}

neoForge {
    version = prop("neo_version")

    parchment {
        minecraftVersion = prop("parchment_minecraft_version")
        mappingsVersion = prop("parchment_mappings_version")
    }

    runs {
        register("client") {
            client()
            // ./gradlew runClient -PquickPlayWorld=<save> loads straight into run/saves/<save>.
            findProperty("quickPlayWorld")?.let { programArguments.addAll("--quickPlaySingleplayer", it.toString()) }
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val properties = mapOf(
        "mod_id" to modId,
        "mod_name" to prop("mod_name"),
        "mod_version" to prop("mod_version"),
        "mod_license" to prop("mod_license"),
        "loader_version_range" to prop("loader_version_range"),
        "neo_version" to prop("neo_version"),
        "minecraft_version_range" to prop("minecraft_version_range"),
    )
    inputs.properties(properties)
    expand(properties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
