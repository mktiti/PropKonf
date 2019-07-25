package com.mktiti.propkonf.core.general

import java.io.FileReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Path

fun load(reader: Reader): SourceStream = stringTraverser(reader.use(Reader::readText))

fun load(stream: InputStream): SourceStream = stream.use { load(InputStreamReader(stream)) }

fun loadFile(path: Path): SourceStream = load(FileReader(path.toFile()))