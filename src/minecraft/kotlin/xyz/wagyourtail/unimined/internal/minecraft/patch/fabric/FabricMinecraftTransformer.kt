package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.ii.InterfaceInjectionMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.util.getShortSha1
import java.io.InputStreamReader
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

abstract class FabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricLikeMinecraftTransformer(
    project,
    provider,
    "fabric",
    "fabric.mod.json",
    "accessWidener"
) {

    override val ENVIRONMENT: String = "Lnet/fabricmc/api/Environment;"
    override val ENV_TYPE: String = "Lnet/fabricmc/api/EnvType;"

    override fun addMavens() {
        project.unimined.fabricMaven()
    }

    override fun addIncludeToModJson(json: JsonObject, dep: Dependency, path: String) {
        var jars = json.get("jars")?.asJsonArray
        if (jars == null) {
            jars = JsonArray()
            json.add("jars", jars)
        }
        jars.add(JsonObject().apply {
            addProperty("file", path)
        })
    }

    override fun applyExtraLaunches() {
        super.applyExtraLaunches()
        if (provider.side == EnvType.DATAGEN) {
            TODO("DATAGEN not supported yet")
        }
    }

    override fun applyClientRunTransform(config: RunConfig) {
        super.applyClientRunTransform(config)
        config.jvmArgs += listOf(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=${intermediaryClasspath}"
        )
    }

    override fun applyServerRunTransform(config: RunConfig) {
        super.applyServerRunTransform(config)
        config.jvmArgs += listOf(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=${intermediaryClasspath}"
        )
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        val baseMinecraft = super.afterRemap(baseMinecraft)

        val injections = hashMapOf<String, List<String>>()

        this.collectInterfaceInjections(baseMinecraft, injections)

        return if (injections.isNotEmpty()) {
            val oldSuffix = if (baseMinecraft.awOrAt != null) baseMinecraft.awOrAt + "+" else ""

            val output = MinecraftJar(
                baseMinecraft,
                parentPath = provider.localCache.resolve("fabric").createDirectories(),
                awOrAt = "${oldSuffix}ii+${injections.hashCode()}"
            );

            if (!output.path.exists() || project.unimined.forceReload) {
                if (InterfaceInjectionMinecraftTransformer.transform(
                        injections,
                        baseMinecraft.path,
                        output.path,
                        project.logger
                    )
                ) {
                    output
                } else baseMinecraft
            } else output
        } else baseMinecraft
    }

    private fun collectInterfaceInjections(baseMinecraft: MinecraftJar, injections: HashMap<String, List<String>>) {
        val modJsonPath = this.getModJsonPath()

        if (modJsonPath != null && modJsonPath.exists()) {
            val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(modJsonPath.toPath()))).asJsonObject

            val custom = json.getAsJsonObject("custom")

            if (custom != null) {
                val interfaces = custom.getAsJsonObject("loom:injected_interfaces")

                if (interfaces != null) {
                    injections.putAll(interfaces.entrySet()
                        .filterNotNull()
                        .filter { it.key != null && it.value != null && it.value.isJsonArray }
                        .map {
                            val element = it.value!!

                            Pair(it.key!!, if (element.isJsonArray) {
                                element.asJsonArray.mapNotNull { name -> name.asString }
                            } else arrayListOf())
                        }
                        .map {
                            var target = it.first

                            val clazz = provider.mappings.mappingTree.getClass(
                                target,
                                provider.mappings.mappingTree.getNamespaceId(prodNamespace.name)
                            )

                            if (clazz != null) {
                                var newTarget = clazz.getName(provider.mappings.mappingTree.getNamespaceId(baseMinecraft.mappingNamespace.name))

                                if (newTarget == null) {
                                    newTarget = clazz.getName(provider.mappings.mappingTree.getNamespaceId(baseMinecraft.fallbackNamespace.name))
                                }

                                if (newTarget != null) {
                                    target = newTarget
                                }
                            }

                            Pair(target, it.second)
                        }
                    )
                }
            }
        }
    }
}