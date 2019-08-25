package net.kemitix.thorp.lib

import net.kemitix.thorp.config.Config
import net.kemitix.thorp.console._
import net.kemitix.thorp.domain.StorageQueueEvent.DoNothingQueueEvent
import net.kemitix.thorp.domain._
import net.kemitix.thorp.lib.Action.{DoNothing, ToCopy, ToDelete, ToUpload}
import net.kemitix.thorp.storage.Storage
import zio.{RIO, Task}

trait UnversionedMirrorArchive extends ThorpArchive {

  override def update(
      sequencedAction: SequencedAction,
      totalBytesSoFar: Long
  ): RIO[Storage with Console with Config, StorageQueueEvent] =
    sequencedAction match {
      case SequencedAction(ToUpload(bucket, localFile, _), index) =>
        doUpload(index, totalBytesSoFar, bucket, localFile) >>= logEvent
      case SequencedAction(ToCopy(bucket, sourceKey, hash, targetKey, _), _) =>
        Storage.copy(bucket, sourceKey, hash, targetKey) >>= logEvent
      case SequencedAction(ToDelete(bucket, remoteKey, _), _) =>
        Storage.delete(bucket, remoteKey) >>= logEvent
      case SequencedAction(DoNothing(_, remoteKey, _), _) =>
        Task(DoNothingQueueEvent(remoteKey))
    }

  private def doUpload(
      index: Int,
      totalBytesSoFar: Long,
      bucket: Bucket,
      localFile: LocalFile
  ) =
    for {
      batchMode <- Config.batchMode
      upload <- Storage.upload(
        localFile,
        bucket,
        UploadEventListener.Settings(
          localFile,
          index,
          totalBytesSoFar,
          batchMode
        )
      )
    } yield upload
}

object UnversionedMirrorArchive extends UnversionedMirrorArchive