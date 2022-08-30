package xyz.wagyourtail.unimined

import org.gradle.api.Project
import org.gradle.api.provider.Property
import xyz.wagyourtail.unimined.providers.mappings.MappingsProvider
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.mod.ModProvider
import java.nio.file.Path

@Suppress("LeakingThis")
abstract class UniminedExtension(val project: Project) {
    val events = GradleEvents(project)

    val minecraftProvider = project.extensions.create("minecraft", MinecraftProvider::class.java, project, this)
    val mappingsProvider = project.extensions.create("mappings", MappingsProvider::class.java, project, this)
    val modProvider = ModProvider(project, this)

    abstract val useGlobalCache: Property<Boolean>

    init {
        useGlobalCache.convention(true).finalizeValueOnRead()
    }

    fun getGlobalCache(): Path {
        if (useGlobalCache.get()) {
            return project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").maybeCreate()
        } else {
            return getLocalCache()
        }
    }

    fun getLocalCache(): Path {
        return project.buildDir.toPath().resolve("unimined").maybeCreate()
    }
}