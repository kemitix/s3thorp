package net.kemitix.thorp.core

import java.io.File

import net.kemitix.thorp.console._
import net.kemitix.thorp.domain._
import net.kemitix.thorp.storage.api.Storage
import zio.{RIO, UIO}

case class DummyStorageService(remoteObjects: RemoteObjects,
                               uploadFiles: Map[File, (RemoteKey, MD5Hash)])
    extends Storage.Service {

  override def shutdown: UIO[StorageQueueEvent] =
    UIO(StorageQueueEvent.ShutdownQueueEvent())

  override def listObjects(
      bucket: Bucket,
      prefix: RemoteKey
  ): RIO[Console, RemoteObjects] =
    RIO(remoteObjects)

  override def upload(
      localFile: LocalFile,
      bucket: Bucket,
      uploadEventListener: UploadEventListener.Settings,
  ): UIO[StorageQueueEvent] = {
    val (remoteKey, md5Hash) = uploadFiles(localFile.file)
    UIO(StorageQueueEvent.UploadQueueEvent(remoteKey, md5Hash))
  }

  override def copy(bucket: Bucket,
                    sourceKey: RemoteKey,
                    hash: MD5Hash,
                    targetKey: RemoteKey): UIO[StorageQueueEvent] =
    UIO(StorageQueueEvent.CopyQueueEvent(sourceKey, targetKey))

  override def delete(bucket: Bucket,
                      remoteKey: RemoteKey): UIO[StorageQueueEvent] =
    UIO(StorageQueueEvent.DeleteQueueEvent(remoteKey))

}
