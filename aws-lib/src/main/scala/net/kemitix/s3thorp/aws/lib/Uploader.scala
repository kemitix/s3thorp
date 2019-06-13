package net.kemitix.s3thorp.aws.lib

import cats.effect.IO
import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.amazonaws.services.s3.transfer.{TransferManager => AmazonTransferManager}
import net.kemitix.s3thorp.aws.api.S3Action.{ErroredS3Action, UploadS3Action}
import net.kemitix.s3thorp.aws.api.UploadEvent.{ByteTransferEvent, RequestEvent, TransferEvent}
import net.kemitix.s3thorp.aws.api.{S3Action, UploadProgressListener}
import net.kemitix.s3thorp.aws.lib.UploaderLogging.{logMultiPartUploadFinished, logMultiPartUploadStart}
import net.kemitix.s3thorp.domain.{Bucket, LocalFile, MD5Hash, RemoteKey}

import scala.util.Try

class Uploader(transferManager: => AmazonTransferManager) {

  def accepts(localFile: LocalFile)
             (implicit multiPartThreshold: Long): Boolean =
    localFile.file.length >= multiPartThreshold

  def upload(localFile: LocalFile,
             bucket: Bucket,
             uploadProgressListener: UploadProgressListener[IO],
             multiPartThreshold: Long,
             tryCount: Int,
             maxRetries: Int)
            (implicit info: Int => String => IO[Unit],
             warn: String => IO[Unit]): IO[S3Action] =
    for {
      _ <- logMultiPartUploadStart(localFile, tryCount)
      upload <- upload(localFile, bucket, uploadProgressListener)
      _ <- logMultiPartUploadFinished(localFile)
    } yield upload match {
      case Right(r) => UploadS3Action(RemoteKey(r.getKey), MD5Hash(r.getETag))
      case Left(e) => ErroredS3Action(localFile.remoteKey, e)
    }

  private def upload(localFile: LocalFile,
                     bucket: Bucket,
                     uploadProgressListener: UploadProgressListener[IO],
                    ): IO[Either[Throwable, UploadResult]] = {
    val listener = progressListener(uploadProgressListener)
    val putObjectRequest = request(localFile, bucket, listener)
    IO {
      Try(transferManager.upload(putObjectRequest))
        .map(_.waitForUploadResult)
        .toEither
    }
  }

  private def request(localFile: LocalFile, bucket: Bucket, listener: ProgressListener): PutObjectRequest =
    new PutObjectRequest(bucket.name, localFile.remoteKey.key, localFile.file)
      .withGeneralProgressListener(listener)

  private def progressListener(uploadProgressListener: UploadProgressListener[IO]) =
    new ProgressListener {
      override def progressChanged(progressEvent: ProgressEvent): Unit = {
        uploadProgressListener.listener(
          progressEvent match {
            case e: ProgressEvent if isTransfer(e) =>
              TransferEvent(e.getEventType.name)
            case e: ProgressEvent if isByteTransfer(e) =>
              ByteTransferEvent(e.getEventType.name)
            case e: ProgressEvent =>
              RequestEvent(e.getEventType.name, e.getBytes, e.getBytesTransferred)
          })
          .unsafeRunSync // the listener doesn't execute otherwise as it is never returned
      }
    }

  private def isTransfer(e: ProgressEvent) =
    e.getEventType.isTransferEvent

  private def isByteTransfer(e: ProgressEvent) =
    e.getEventType equals ProgressEventType.RESPONSE_BYTE_TRANSFER_EVENT

}
