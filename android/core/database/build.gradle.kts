plugins {
    id("informedcitizen.android.library")
    id("informedcitizen.android.hilt")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.informedcitizen.database"
}

sqldelight {
    databases {
        create("BillSummaryDatabase") {
            packageName.set("com.informedcitizen.cache")
        }
    }
}

// SQLDelight 2.x generates Kotlin sources into build/generated/sqldelight/.
// AGP's regular kotlin compilation picks them up, but the KSP task doesn't,
// so Hilt fails to resolve BillSummaryDatabase when it processes
// SqlDelightDriverModule. Register the generated dir as Kotlin source for
// each variant, and tie the kspKotlin task to the generator so codegen
// runs first.
androidComponents {
    onVariants { variant ->
        val genDir = layout.buildDirectory
            .dir("generated/sqldelight/code/BillSummaryDatabase/${variant.name}")
            .get().asFile.absolutePath
        variant.sources.kotlin?.addStaticSourceDirectory(genDir)
    }
}
afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        val generateTask = tasks.findByName("generate${variant}BillSummaryDatabaseInterface") ?: return@forEach
        val kspTask = tasks.findByName("ksp${variant}Kotlin") ?: return@forEach
        kspTask.dependsOn(generateTask)
    }
}

dependencies {
    // The cache surface exposes BillSummary / BillTopic to consumers.
    api(project(":core:model"))

    // SQLDelight runtime + coroutines extensions are exposed since the
    // BillSummaryDatabase type is part of the api surface.
    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines.extensions)
}
