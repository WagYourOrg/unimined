package xyz.wagyourtail.unimined.internal.minecraft.patch.access.transformer

import org.apache.commons.io.output.NullOutputStream
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessConvert
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessTransformerPatcher
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerApplier
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.access.AccessConvertImpl
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3.mcpconfig.SubprocessExecutor.shouldShowVerboseStderr
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3.mcpconfig.SubprocessExecutor.shouldShowVerboseStdout
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader
import xyz.wagyourtail.unimined.mapping.formats.at.ATWriter
import xyz.wagyourtail.unimined.mapping.formats.at.LegacyATReader
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.getShortSha1
import xyz.wagyourtail.unimined.util.openZipFileSystem
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

open class AccessTransformerMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String = "accessTransformer",
) : AbstractMinecraftTransformer(
    project,
    provider,
    providerName
), AccessTransformerPatcher, AccessConvert by AccessConvertImpl(project, provider) {

    override var legacyATFormat: Boolean by FinalizeOnRead(false)

    override var accessTransformer: File? by FinalizeOnRead(null)

    override var accessTransformerPaths: List<String> by FinalizeOnRead(listOf())

    override var dependency: Dependency by FinalizeOnRead(if (provider.minecraftData.metadata.javaVersion >= JavaVersion.VERSION_21) project.dependencies.create("net.neoforged.accesstransformers:at-cli:11.0.0") else project.dependencies.create("net.neoforged:accesstransformers:9.0.3"))

    override var atMainClass: String by FinalizeOnRead(if (provider.minecraftData.metadata.javaVersion >= JavaVersion.VERSION_21) "net.neoforged.accesstransformer.cli.TransformerProcessor" else "net.neoforged.accesstransformer.TransformerProcessor")

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        baseMinecraft.path.openZipFileSystem().use { fs ->
            val paths = mutableListOf<Path>()
            for (path in accessTransformerPaths) {
                val p = fs.getPath(path)
                if (p.exists()) {
                    paths.add(p)
                }
            }
            if (accessTransformer != null && accessTransformer!!.exists()) {
                paths.add(accessTransformer!!.toPath())
            }
            return applyATs(baseMinecraft, paths)
        }
    }

    private fun applyATs(baseMinecraft: MinecraftJar, ats: List<Path>): MinecraftJar {
        project.logger.lifecycle("[Unimined/ForgeTransformer] Applying ATs $ats")
        return if (accessTransformer != null) {
            project.logger.lifecycle("[Unimined/ForgeTransformer] Using user access transformer $accessTransformer")
            val output = MinecraftJar(
                baseMinecraft,
                awOrAt = "at+${accessTransformer!!.toPath().getShortSha1()}"
            )
            if (!output.path.exists() || project.unimined.forceReload) {
                transform(
                    project,
                    ats + listOf(accessTransformer!!.toPath()),
                    baseMinecraft.path,
                    output.path
                )
            }
            output
        } else {
            if (ats.isEmpty()) {
                return baseMinecraft
            }
            val output = MinecraftJar(baseMinecraft, awOrAt = "at")
            if (!output.path.exists() || project.unimined.forceReload) {
                transform(project, ats, baseMinecraft.path, output.path)
            }
            output
        }
    }

    private fun transform(project: Project, accessTransformers: List<Path>, baseMinecraft: Path, output: Path) {
        if (accessTransformers.isEmpty()) return
        if (output.exists()) output.deleteIfExists()
        output.parent.createDirectories()
        val temp = output.resolveSibling(baseMinecraft.nameWithoutExtension + "-mergedATs.cfg")
        temp.parent.createDirectories()
        temp.deleteIfExists()

        val list = mutableListOf<ATReader.ATData>()
        for (at in accessTransformers) {
            // ensure at's are in modern format, and on disk not a virtual fs, so that processor can read them
            if (legacyATFormat) {
                list.addAll(LegacyATReader.readData(CharReader(at.readText())))
            } else {
                list.addAll(ATReader.readData(CharReader(at.readText())))
            }
        }

        // merge at's
        temp.bufferedWriter(
            StandardCharsets.UTF_8,
            1024,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        ).use {
            ATWriter.writeData(list, it::append)
        }
        try {
            project.javaexec { spec ->
                val toolchain = project.extensions.getByType(JavaToolchainService::class.java)
                spec.executable = toolchain.launcherFor {
                    it.languageVersion.set(JavaLanguageVersion.of(provider.minecraftData.metadata.javaVersion.majorVersion))
                }.get().executablePath.asFile.absolutePath

                spec.classpath = project.configurations.detachedConfiguration(dependency)
                spec.mainClass.set(atMainClass)
                spec.args = listOf(
                    "--inJar",
                    baseMinecraft.absolutePathString(),
                    "--outJar",
                    output.absolutePathString(),
                    "--atFile",
                    temp.absolutePathString()
                )
                if (shouldShowVerboseStdout(project)) {
                    spec.standardOutput = System.out
                } else {
                    spec.standardOutput = NullOutputStream.NULL_OUTPUT_STREAM
                }
                if (shouldShowVerboseStderr(project)) {
                    spec.errorOutput = System.err
                } else {
                    spec.errorOutput = NullOutputStream.NULL_OUTPUT_STREAM
                }
            }.assertNormalExitValue().rethrowFailure()
        } catch (e: Exception) {
            output.deleteIfExists()
            throw e
        }
    }

}
