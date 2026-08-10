package com.blackglass.filemanager.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Thin wrapper around java.io.File operations used by the file manager.
 * Kept free of Android framework types so it stays easily testable.
 */
object FileRepository {

    fun listChildren(dir: File): List<FileItem> {
        val children = dir.listFiles() ?: return emptyList()
        return children.map { FileItem(it) }
    }

    fun createFolder(parent: File, name: String): Result<File> = runCatching {
        val target = File(parent, name)
        if (target.exists()) error("A file or folder named \"$name\" already exists")
        if (!target.mkdirs()) error("Could not create folder")
        target
    }

    fun createFile(parent: File, name: String): Result<File> = runCatching {
        val target = File(parent, name)
        if (target.exists()) error("A file named \"$name\" already exists")
        if (!target.createNewFile()) error("Could not create file")
        target
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        val target = File(file.parentFile, newName)
        if (target.exists()) error("A file or folder named \"$newName\" already exists")
        if (!file.renameTo(target)) error("Rename failed")
        target
    }

    fun delete(file: File): Result<Unit> = runCatching {
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!ok) error("Failed to delete ${file.name}")
    }

    fun copy(source: File, destinationDir: File): Result<File> = runCatching {
        val target = uniqueTarget(destinationDir, source.name)
        if (source.isDirectory) {
            copyDirectory(source, target)
        } else {
            copyFileBytes(source, target)
        }
        target
    }

    fun move(source: File, destinationDir: File): Result<File> = runCatching {
        val target = uniqueTarget(destinationDir, source.name)
        val moved = source.renameTo(target)
        if (!moved) {
            // Fallback for cross-volume moves: copy then delete
            if (source.isDirectory) copyDirectory(source, target) else copyFileBytes(source, target)
            val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
            if (!deleted) error("Copied but could not remove original")
        }
        target
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun copyFileBytes(source: File, target: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { child ->
            val childTarget = File(target, child.name)
            if (child.isDirectory) copyDirectory(child, childTarget) else copyFileBytes(child, childTarget)
        }
    }

    fun totalSize(file: File): Long {
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += totalSize(it) }
        return total
    }
}
