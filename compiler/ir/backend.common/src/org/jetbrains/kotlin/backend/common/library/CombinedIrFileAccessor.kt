/**
 * Copyright 2010-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.common.library

import com.intellij.util.io.ByteBufferWrapper
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

data class DeclarationId(val id: Long, val isLocal: Boolean)

class CombinedIrFileReader(file: File) {
    private val bufferWrapper = ByteBufferWrapper.readOnly(file, 0)
    private val declarationToOffsetSize = mutableMapOf<DeclarationId, Pair<Int, Int>>()

    init {
        val declarationsCount = bufferWrapper.buffer.getInt()
        for (i in 0 until declarationsCount) {
            val id = bufferWrapper.buffer.long
            val isLocal = bufferWrapper.buffer.int != 0
            val offset = bufferWrapper.buffer.int
            val size = bufferWrapper.buffer.int
            declarationToOffsetSize[DeclarationId(id, isLocal)] = offset to size
        }
    }

    fun declarationBytes(id: DeclarationId): ByteArray {
        val offsetSize = declarationToOffsetSize[id] ?: throw Error("No declaration with $id here")
        val result = ByteArray(offsetSize.second)
        bufferWrapper.buffer.position(offsetSize.first)
        bufferWrapper.buffer.get(result, 0, offsetSize.second)
        return result
    }

    fun dispose() = bufferWrapper.dispose()
}

private const val SINGLE_INDEX_RECORD_SIZE = 20  // sizeof(Long) + 3 * sizeof(Int).
private const val INDEX_HEADER_SIZE = 4  // sizeof(Int).

class CombinedIrFileWriter(val declarationCount: Int) {
    private var currentDeclaration = 0
    private var currentPosition = 0
    private val file = Files.createTempFile("ir", "").toFile()
    private val randomAccessFile = RandomAccessFile(file.path, "rw")

    init {
        randomAccessFile.writeInt(declarationCount)
        assert(randomAccessFile.filePointer.toInt() == INDEX_HEADER_SIZE)
        for (i in 0 until declarationCount) {
            randomAccessFile.writeLong(-1) // id
            randomAccessFile.writeInt(-1)  // isLocal
            randomAccessFile.writeInt(-1)  // offset
            randomAccessFile.writeInt(-1)  // size
        }
        currentPosition = randomAccessFile.filePointer.toInt()
        assert(currentPosition == INDEX_HEADER_SIZE + SINGLE_INDEX_RECORD_SIZE * declarationCount)
    }

    fun skipDeclaration() {
        currentDeclaration++
    }

    fun addDeclaration(id: DeclarationId, bytes: ByteArray) {
        randomAccessFile.seek((currentDeclaration * SINGLE_INDEX_RECORD_SIZE + INDEX_HEADER_SIZE).toLong())
        randomAccessFile.writeLong(id.id)
        randomAccessFile.writeInt(if (id.isLocal) 1 else 0)
        randomAccessFile.writeInt(currentPosition)
        randomAccessFile.writeInt(bytes.size)
        randomAccessFile.seek(currentPosition.toLong())
        randomAccessFile.write(bytes)
        assert(randomAccessFile.filePointer < Int.MAX_VALUE.toLong())
        currentPosition = randomAccessFile.filePointer.toInt()
        currentDeclaration++
    }

    fun finishWriting(): File {
        assert(currentDeclaration == declarationCount)
        randomAccessFile.close()
        return file
    }
}

