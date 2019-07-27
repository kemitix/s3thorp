package net.kemitix.thorp.storage.aws

import net.kemitix.thorp.console.Console
import net.kemitix.thorp.domain.StorageQueueEvent.ShutdownQueueEvent
import net.kemitix.thorp.domain.{
  Bucket,
  LocalFile,
  MD5Hash,
  RemoteKey,
  S3ObjectsData,
  StorageQueueEvent,
  UploadEventListener
}
import net.kemitix.thorp.storage.api.Storage
import org.scalamock.scalatest.MockFactory
import zio.{TaskR, UIO}

trait AmazonS3ClientTestFixture extends MockFactory {

  val fixture: Fixture =
    Fixture(stub[AmazonS3.Client], stub[AmazonTransferManager])

  case class Fixture(
      amazonS3Client: AmazonS3.Client,
      amazonS3TransferManager: AmazonTransferManager,
  ) {
    lazy val storageService: Storage.Service =
      new Storage.Service {

        private val client          = amazonS3Client
        private val transferManager = amazonS3TransferManager

        override def listObjects(
            bucket: Bucket,
            prefix: RemoteKey): TaskR[Console, S3ObjectsData] =
          Lister.listObjects(client)(bucket, prefix)

        override def upload(localFile: LocalFile,
                            bucket: Bucket,
                            batchMode: Boolean,
                            uploadEventListener: UploadEventListener,
                            tryCount: Int): UIO[StorageQueueEvent] =
          Uploader.upload(transferManager)(localFile,
                                           bucket,
                                           batchMode,
                                           uploadEventListener,
                                           1)

        override def copy(bucket: Bucket,
                          sourceKey: RemoteKey,
                          hash: MD5Hash,
                          targetKey: RemoteKey): UIO[StorageQueueEvent] =
          Copier.copy(client)(bucket, sourceKey, hash, targetKey)

        override def delete(bucket: Bucket,
                            remoteKey: RemoteKey): UIO[StorageQueueEvent] =
          Deleter.delete(client)(bucket, remoteKey)

        override def shutdown: UIO[StorageQueueEvent] = {
          transferManager.shutdownNow(true)
          client.shutdown().map(_ => ShutdownQueueEvent())
        }
      }
  }

}
