package xyz.wagyourtail.unimined.internal.minecraft.patch.rift

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessTransformerPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.rift.RiftPatcher
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.access.transformer.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.util.withSourceSet

class RiftMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    private val accessTransformerMinecraftTransformer: AccessTransformerMinecraftTransformer = AccessTransformerMinecraftTransformer(project, provider, "Rift")
) : AbstractMinecraftTransformer(project, provider, "Rift"), RiftPatcher, AccessTransformerPatcher by accessTransformerMinecraftTransformer {

    private val rift: Configuration = project.configurations.maybeCreate(providerName.withSourceSet(provider.sourceSet)).also {
        project.configurations.getByName("modImplementation").extendsFrom(it)
    }
    override fun loader(dep: Any, action: Dependency.() -> Unit) {
        rift.dependencies.add(
            (if (dep is String && !dep.contains(":")) {
                project.dependencies.create("org.dimdev:rift:$dep")
            } else project.dependencies.create(dep)).apply(action)
        )
    }

    init {
        project.unimined.wagYourMaven("releases")
        project.unimined.minecraftForgeMaven()
        project.unimined.spongeMaven()
    }

    override fun apply() {
        createRiftDependency("org.spongepowered:mixin:0.7.11-SNAPSHOT")
        createRiftDependency("net.minecraft:launchwrapper:1.12")
        createRiftDependency("org.ow2.asm:asm:6.2", true)
        createRiftDependency("org.ow2.asm:asm-commons:6.2", true)
        createRiftDependency("org.ow2.asm:asm-tree:6.2", true)

        if (rift.dependencies.isEmpty()) rift.dependencies.add(project.dependencies.create("org.dimdev:rift:${provider.version}"))

        super.apply()
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        // TODO: Apply Rift AccessTransformer probably
        return super.afterRemap(baseMinecraft)
    }

    override fun applyClientRunTransform(config: RunConfig) {
        config.mainClass = "net.minecraft.launchwrapper.Launch"

        config.args.addAll(arrayOf("--tweakClass", "org.dimdev.riftloader.launch.RiftLoaderClientTweaker").toList())
    }

    override fun applyServerRunTransform(config: RunConfig) {
        config.mainClass = "net.minecraft.launchwrapper.Launch"

        config.args.addAll(arrayOf("--tweakClass", "org.dimdev.riftloader.launch.RiftLoaderServerTweaker").toList())
    }

    private fun createRiftDependency(name: String, transitive: Boolean = false) {
        val dep = project.dependencies.create(name) as ModuleDependency

        dep.setTransitive(transitive)

        provider.minecraftLibraries.dependencies.add(dep)
    }

}