package cc.mewcraft.resourcepakku.util

import cc.mewcraft.resourcepakku.plugin
import java.io.File
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

/**
 * 复制模式:
 *
 * - OVERWRITE: 总是覆盖目标文件/文件夹中的内容, 无论目标是否存在.
 * - SKIP     : 如果目标文件/文件夹已存在, 则跳过, 不进行复制.
 * - MERGE    : 文件夹复制时, 仅复制目标文件夹下不存在的文件, 已存在的文件跳过; 文件复制行为同 SKIP (目标文件已存在则跳过).
 */
enum class CopyMode { OVERWRITE, SKIP, MERGE }

/**
 * 从插件 JAR 中复制资源 (文件或文件夹) 到本地.
 *
 * @param src JAR 根内路径 (不以 '/' 开头)
 * @param dst 目标路径 (文件或目录). 当 src 是目录时, 内容会复制到 dst 下新增同名目录
 * @param mode 模式参见 [CopyMode]
 * @return true 表示发生了至少一个复制写入; false 表示未写任何内容 (如 SKIP 或 MERGE 全部已存在)
 */
fun copyResource(src: String, dst: Path, mode: CopyMode = CopyMode.SKIP): Boolean {
    val jarUrl = plugin::class.java.protectionDomain.codeSource.location
    JarFile(File(jarUrl.toURI())).use { jar ->
        val allEntries = jar.entries().asSequence().toList()
        val directEntry = jar.getJarEntry(src) // 可能为 null (例如目录未显式存储)
        val hasDirChildren = allEntries.any { it.name.startsWith("$src/") }
        if (directEntry == null && !hasDirChildren) {
            throw IllegalArgumentException("Resource '$src' not found in JAR.")
        }
        val isSrcDir = (directEntry?.isDirectory == true) || hasDirChildren

        var wrote = false

        if (isSrcDir) {
            val baseDst = dst.resolve(File(src).name)
            if (mode == CopyMode.SKIP && baseDst.toFile().exists()) return false

            val entriesUnderSrc = allEntries.filter { e -> e.name == src || e.name.startsWith("$src/") }
            entriesUnderSrc.forEach { entry ->
                val relative = entry.name.removePrefix(src).removePrefix("/")
                if (relative.isEmpty() && !entry.isDirectory) return@forEach
                val outPath = if (relative.isEmpty()) baseDst else baseDst.resolve(relative)
                if (entry.isDirectory) {
                    if (!outPath.toFile().exists()) outPath.createDirectories()
                } else {
                    val targetFile = outPath.toFile()
                    if (shouldCopyFile(mode, targetFile.exists())) {
                        copyFileFromJar(jar, entry, outPath)
                        wrote = true
                    }
                }
            }
            return wrote
        }

        val targetPath = if (dst.toFile().isDirectory) dst.resolve(File(src).name) else dst
        val targetFile = targetPath.toFile()
        if (shouldCopyFile(mode, targetFile.exists())) {
            copyFileFromJar(jar, directEntry ?: error("Missing entry for file '$src'"), targetPath)
            wrote = true
        }
        return wrote
    }
}

private fun shouldCopyFile(mode: CopyMode, targetExists: Boolean): Boolean = when (mode) {
    CopyMode.OVERWRITE -> true
    CopyMode.MERGE -> !targetExists
    CopyMode.SKIP -> !targetExists
}

private fun copyFileFromJar(jar: JarFile, entry: JarEntry, outPath: Path) {
    outPath.createParentDirectories()
    jar.getInputStream(entry).use { input ->
        outPath.outputStream().use { output -> input.copyTo(output) }
    }
}
