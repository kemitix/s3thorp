package net.kemitix.thorp.core.hasher

import java.io.{File, FileInputStream}
import java.nio.file.Path
import java.security.MessageDigest

import net.kemitix.thorp.domain.MD5Hash
import net.kemitix.thorp.filesystem.FileSystem
import zio.{Task, RIO}

import scala.collection.immutable.NumericRange

private object MD5HashGenerator {

  val maxBufferSize = 8048
  val defaultBuffer = new Array[Byte](maxBufferSize)

  def hex(in: Array[Byte]): String = {
    val md5 = MessageDigest getInstance "MD5"
    md5 update in
    (md5.digest map ("%02x" format _)).mkString
  }

  def digest(in: String): Array[Byte] = {
    val md5 = MessageDigest getInstance "MD5"
    md5 update in.getBytes
    md5.digest
  }

  def md5File(path: Path): RIO[FileSystem, MD5Hash] =
    md5FileChunk(path, 0, path.toFile.length)

  def md5FileChunk(
      path: Path,
      offset: Long,
      size: Long
  ): RIO[FileSystem, MD5Hash] = {
    val file      = path.toFile
    val endOffset = Math.min(offset + size, file.length)
    for {
      digest <- readFile(file, offset, endOffset)
      hash = MD5Hash.fromDigest(digest)
    } yield hash
  }

  private def readFile(
      file: File,
      offset: Long,
      endOffset: Long
  ) =
    FileSystem.open(file, offset) >>= { managedFileInputStream =>
      managedFileInputStream.use { fileInputStream =>
        digestFile(fileInputStream, offset, endOffset)
      }
    }

  private def digestFile(
      fis: FileInputStream,
      offset: Long,
      endOffset: Long
  ) =
    Task {
      val md5 = MessageDigest getInstance "MD5"
      NumericRange(offset, endOffset, maxBufferSize)
        .foreach(currentOffset =>
          md5 update readToBuffer(fis, currentOffset, endOffset))
      md5.digest
    }

  private def readToBuffer(
      fis: FileInputStream,
      currentOffset: Long,
      endOffset: Long
  ) = {
    val buffer =
      if (nextBufferSize(currentOffset, endOffset) < maxBufferSize)
        new Array[Byte](nextBufferSize(currentOffset, endOffset))
      else defaultBuffer
    fis read buffer
    buffer
  }

  private def nextBufferSize(
      currentOffset: Long,
      endOffset: Long
  ) = {
    val toRead = endOffset - currentOffset
    Math.min(maxBufferSize, toRead).toInt
  }

}
