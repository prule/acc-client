package io.github.prule.acc.client.simulator

import java.io.InputStream

interface Source {
    fun inputStream(): InputStream
}

class ClasspathSource(
    private val path: String,
) : Source {
    override fun inputStream(): InputStream =
        javaClass.classLoader.getResourceAsStream(path) ?: throw Exception("Resource not found on classpath: $path")
}

class FileSource(
    private val path: String,
) : Source {
    override fun inputStream(): InputStream {
        val file = java.io.File(path)
        if (!file.exists()) throw Exception("File not found: $path")
        return file.inputStream()
    }
}
