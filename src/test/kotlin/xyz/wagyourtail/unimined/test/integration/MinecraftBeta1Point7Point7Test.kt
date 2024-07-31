package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import xyz.wagyourtail.unimined.util.*

class MinecraftBeta1Point7Point7Test {
    @ParameterizedTest
    @MethodSource("xyz.wagyourtail.unimined.util.IntegrationTestUtils#versions")
    fun testMinecraftBeta1Point7Point3(gradleVersion: String) {
        try {
            val result = runTestProject("minecraft/beta-1.7.3", gradleVersion)

            try {
                result.task(":build")?.outcome?.let {
                    if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
                } ?: throw Exception("build failed")
            } catch (e: Exception) {
                println(result.output)
                throw Exception(e)
            }
        } catch (e: UnexpectedBuildFailure) {
            println(e)
            throw Exception("build failed", e)
        }
    }
}
