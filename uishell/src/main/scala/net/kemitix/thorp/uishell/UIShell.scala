package net.kemitix.thorp.uishell

import net.kemitix.eip.zio.MessageChannel
import net.kemitix.thorp.config.Config
import net.kemitix.thorp.console.ConsoleOut.{
  CopyComplete,
  DeleteComplete,
  UploadComplete
}
import net.kemitix.thorp.console.{Console, ConsoleOut}
import net.kemitix.thorp.domain.{
  Action,
  Counters,
  LocalFile,
  MD5Hash,
  RemoteKey,
  SizeTranslation,
  Terminal
}
import net.kemitix.thorp.domain.Action.ToUpload
import net.kemitix.thorp.domain.Terminal.{
  eraseLineForward,
  eraseToEndOfScreen,
  progressBar
}
import zio.{UIO, ZIO}

import scala.io.AnsiColor.{GREEN, RESET}

object UIShell {

  def receiver: UIO[MessageChannel.UReceiver[Console with Config, UIEvent]] =
    UIO { uiEventMessage =>
      uiEventMessage.body match {
        case UIEvent.ShowValidConfig         => showValidConfig
        case UIEvent.RemoteDataFetched(size) => remoteDataFetched(size)
        case UIEvent.ShowSummary(counters)   => showSummary(counters)
        case UIEvent.FileFound(localFile)    => fileFound(localFile)
        case UIEvent.ActionChosen(action)    => UIO(())
        case UIEvent.AwaitingAnotherUpload(remoteKey, hash) =>
          awaitingUpload(remoteKey, hash)
        case UIEvent.AnotherUploadWaitComplete(action) =>
          uploadWaitComplete(action)
        case UIEvent.ActionFinished(action, _, _) => actionFinished(action)
        case UIEvent.KeyFound(_)                  => UIO(())
        case UIEvent.RequestCycle(localFile,
                                  bytesTransferred,
                                  index,
                                  totalBytesSoFar) =>
          requestCycle(localFile, bytesTransferred, index, totalBytesSoFar)
      }
    }

  private def actionFinished(
      action: Action): ZIO[Console with Config, Nothing, Unit] =
    for {
      batchMode <- Config.batchMode
      _ <- action match {
        case _: Action.DoNothing => UIO(())
        case ToUpload(_, localFile, _) =>
          Console.putMessageLnB(UploadComplete(localFile.remoteKey), batchMode)
        case Action.ToCopy(_, sourceKey, _, targetKey, _) =>
          Console.putMessageLnB(CopyComplete(sourceKey, targetKey), batchMode)
        case Action.ToDelete(_, remoteKey, _) =>
          Console.putMessageLnB(DeleteComplete(remoteKey), batchMode)
      }
    } yield ()

  private def uploadWaitComplete(action: Action): ZIO[Console, Nothing, Unit] =
    Console.putStrLn(s"Finished waiting to other upload - now $action")

  private def awaitingUpload(remoteKey: RemoteKey,
                             hash: MD5Hash): ZIO[Console, Nothing, Unit] =
    Console.putStrLn(
      s"Awaiting another upload of $hash before copying it to $remoteKey")

  private def fileFound(
      localFile: LocalFile): ZIO[Console with Config, Nothing, Unit] =
    for {
      batchMode <- Config.batchMode
      _         <- ZIO.when(batchMode)(Console.putStrLn(s"Found: ${localFile.file}"))
    } yield ()

  private def showSummary(
      counters: Counters): ZIO[Console with Config, Nothing, Unit] =
    Console.putStrLn(eraseToEndOfScreen) *>
      Console.putStrLn(s"Uploaded ${counters.uploaded} files") *>
      Console.putStrLn(s"Copied   ${counters.copied} files") *>
      Console.putStrLn(s"Deleted  ${counters.deleted} files") *>
      Console.putStrLn(s"Errors   ${counters.errors}")

  private def remoteDataFetched(size: Int): ZIO[Console, Nothing, Unit] =
    Console.putStrLn(s"Found $size remote objects")

  private def showValidConfig: ZIO[Console with Config, Nothing, Unit] =
    for {
      bucket  <- Config.bucket
      prefix  <- Config.prefix
      sources <- Config.sources
      _       <- Console.putMessageLn(ConsoleOut.ValidConfig(bucket, prefix, sources))
    } yield ()

  private def requestCycle(
      localFile: LocalFile,
      bytesTransferred: Long,
      index: Int,
      totalBytesSoFar: Long): ZIO[Console with Config, Nothing, Unit] =
    UIO {
      val remoteKey    = localFile.remoteKey.key
      val fileLength   = localFile.file.length
      val statusHeight = 3
      if (bytesTransferred < fileLength)
        println(
          s"${GREEN}Uploading:$RESET $remoteKey$eraseToEndOfScreen\n" +
            statusWithBar(" File",
                          SizeTranslation.sizeInEnglish,
                          bytesTransferred,
                          fileLength) +
            s"${Terminal.cursorPrevLine(statusHeight)}")
    }

  private def statusWithBar(
      label: String,
      format: Long => String,
      current: Long,
      max: Long
  ): String = {
    val percent = f"${(current * 100) / max}%2d"
    s"$GREEN$label:$RESET ($percent%) ${format(current)} of ${format(max)}" +
      s"$eraseLineForward\n" +
      progressBar(current, max, Terminal.width)
  }
}
