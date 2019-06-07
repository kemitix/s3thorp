package net.kemitix.s3thorp.aws.lib

import cats.effect.IO
import com.amazonaws.services.s3.model.{AmazonS3Exception, InitiateMultipartUploadResult, UploadPartRequest, UploadPartResult}
import net.kemitix.s3thorp.domain.{LocalFile, MD5Hash}

object S3ClientTransferManagerLogging {

  private val prefix = "transfer-manager"

  def logMultiPartUploadStart(localFile: LocalFile,
                              tryCount: Int)
                             (implicit info: Int => String => Unit): IO[Unit] =
    IO{info(1)(s"$prefix:upload:try $tryCount: ${localFile.remoteKey.key}")}

  def logMultiPartUploadFinished(localFile: LocalFile)
                                (implicit info: Int => String => Unit): IO[Unit] =
    IO{info(4)(s"$prefix:upload:finished: ${localFile.remoteKey.key}")}

  def logMultiPartUploadInitiate(localFile: LocalFile)
                                (implicit info: Int => String => Unit): Unit =
    info(5)(s"$prefix:initiating: ${localFile.remoteKey.key}")

  def logMultiPartUploadPartsDetails(localFile: LocalFile,
                                     nParts: Int,
                                     partSize: Long)
                                    (implicit info: Int => String => Unit): Unit =
    info(5)(s"$prefix:parts $nParts:each $partSize: ${localFile.remoteKey.key}")

  def logMultiPartUploadPartDetails(localFile: LocalFile,
                                    partNumber: Int,
                                    partHash: MD5Hash)
                                   (implicit info: Int => String => Unit): Unit =
    info(5)(s"$prefix:part $partNumber:hash ${partHash.hash}: ${localFile.remoteKey.key}")

  def logMultiPartUploadPart(localFile: LocalFile,
                             partRequest: UploadPartRequest)
                            (implicit info: Int => String => Unit): Unit =
    info(5)(s"$prefix:sending:part ${partRequest.getPartNumber}: ${partRequest.getMd5Digest}: ${localFile.remoteKey.key}")

  def logMultiPartUploadPartDone(localFile: LocalFile,
                                 partRequest: UploadPartRequest,
                                 result: UploadPartResult)
                                (implicit info: Int => String => Unit): Unit =
    info(5)(s"$prefix:sent:part ${partRequest.getPartNumber}: ${result.getPartETag}: ${localFile.remoteKey.key}")

  def logMultiPartUploadPartError(localFile: LocalFile,
                                  partRequest: UploadPartRequest,
                                  error: AmazonS3Exception)
                                 (implicit warn: String => Unit): Unit = {
    val returnedMD5Hash = error.getAdditionalDetails.get("Content-MD5")
    warn(s"$prefix:error:part ${partRequest.getPartNumber}:ret-hash $returnedMD5Hash: ${localFile.remoteKey.key}")
  }

  def logMultiPartUploadCompleted(createUploadResponse: InitiateMultipartUploadResult,
                                  uploadPartResponses: Stream[UploadPartResult],
                                  localFile: LocalFile)
                                 (implicit info: Int => String => Unit): Unit =
    info(1)(s"$prefix:completed:parts ${uploadPartResponses.size}: ${localFile.remoteKey.key}")

  def logMultiPartUploadCancelling(localFile: LocalFile)
                                  (implicit warn: String => Unit): Unit =
    warn(s"$prefix:cancelling: ${localFile.remoteKey.key}")

  def logErrorRetrying(e: Throwable, localFile: LocalFile, tryCount: Int)
                      (implicit warn: String => Unit): Unit =
    warn(s"$prefix:retry:error ${e.getMessage}: ${localFile.remoteKey.key}")

  def logErrorCancelling(e: Throwable, localFile: LocalFile)
                        (implicit error: String => Unit) : Unit =
    error(s"$prefix:cancelling:error ${e.getMessage}: ${localFile.remoteKey.key}")

  def logErrorUnknown(e: Throwable, localFile: LocalFile)
                     (implicit error: String => Unit): Unit =
    error(s"$prefix:unknown:error $e: ${localFile.remoteKey.key}")

}
