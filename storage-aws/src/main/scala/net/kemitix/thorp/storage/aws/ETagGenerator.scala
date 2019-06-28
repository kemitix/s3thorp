package net.kemitix.thorp.storage.aws

import java.io.File

import cats._
import cats.data._
import cats.implicits._
import cats.effect.IO
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.transfer.TransferManagerConfiguration
import com.amazonaws.services.s3.transfer.internal.TransferManagerUtils
import net.kemitix.thorp.core.MD5HashGenerator
import net.kemitix.thorp.domain.{Logger, MD5Hash}

trait ETagGenerator {

  def request: File => PutObjectRequest = file => new PutObjectRequest("", "", file)

  def configuration: TransferManagerConfiguration = new TransferManagerConfiguration

  def eTag(file: File)(implicit l: Logger): IO[String]= {
    val optimumPartSize = TransferManagerUtils.calculateOptimalPartSize(request(file), configuration)
    val numParts = offsets(file.length, optimumPartSize).length
    offsets(file.length, optimumPartSize)
      .zipWithIndex
      .map(chunk => {
        val (_, index) = chunk
        hashChunk(file, index, optimumPartSize)
          .map(_.digest)
      }).sequence
      .map(all => {
        MD5HashGenerator.hex(all.foldLeft(Array[Byte]())((acc, ab) => acc ++ ab)) + "-" + numParts
      })
  }

  def offsets(totalFileSizeBytes: Long, optimalPartSize: Long): List[Long] =
    Range.Long(0, totalFileSizeBytes, optimalPartSize).toList

  def hashChunk(file: File, chunkNumber: Long, chunkSize: Long)(implicit l: Logger): IO[MD5Hash] =
    MD5HashGenerator.md5FileChunk(file, chunkNumber * chunkSize, chunkSize)
}

object ETagGenerator extends ETagGenerator
