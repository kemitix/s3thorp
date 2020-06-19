package net.kemitix.thorp

import net.kemitix.eip.zio.MessageChannel.UChannel
import net.kemitix.eip.zio.{Message, MessageChannel}
import net.kemitix.thorp.cli.CliArgs
import net.kemitix.thorp.config._
import net.kemitix.thorp.console._
import net.kemitix.thorp.domain.{Counters, StorageEvent}
import net.kemitix.thorp.domain.StorageEvent.{
  CopyEvent,
  DeleteEvent,
  ErrorEvent,
  UploadEvent
}
import net.kemitix.thorp.lib._
import net.kemitix.thorp.storage.Storage
import net.kemitix.thorp.uishell.{UIEvent, UIShell}
import zio.clock.Clock
import zio.{RIO, UIO, ZIO}
import scala.io.AnsiColor.{WHITE, RESET}
import scala.jdk.CollectionConverters._

trait Program {

  val version           = "0.11.0"
  lazy val versionLabel = s"${WHITE}Thorp v${version}$RESET"

  def run(args: List[String])
    : ZIO[Storage with Console with Config with Clock with FileScanner,
          Throwable,
          Unit] = {
    for {
      cli    <- CliArgs.parse(args)
      config <- ConfigurationBuilder.buildConfig(cli)
      _      <- Config.set(config)
      _      <- Console.putStrLn(versionLabel)
      _      <- ZIO.when(!showVersion(cli))(executeWithUI.catchAll(handleErrors))
    } yield ()
  }

  private def showVersion: ConfigOptions => Boolean =
    cli => ConfigQuery.showVersion(cli)

  private def executeWithUI =
    for {
      uiEventSender   <- execute
      uiEventReceiver <- UIShell.receiver
      _               <- MessageChannel.pointToPoint(uiEventSender)(uiEventReceiver).runDrain
    } yield ()

  type UIChannel = UChannel[Any, UIEvent]

  private def execute
    : ZIO[Any,
          Nothing,
          MessageChannel.ESender[
            Storage with Config with Clock with FileScanner with Console,
            Throwable,
            UIEvent]] = UIO { uiChannel =>
    (for {
      _          <- showValidConfig(uiChannel)
      remoteData <- fetchRemoteData(uiChannel)
      archive    <- UIO(UnversionedMirrorArchive)
      copyUploadEvents <- LocalFileSystem.scanCopyUpload(uiChannel,
                                                         remoteData,
                                                         archive)
      deleteEvents <- LocalFileSystem.scanDelete(uiChannel, remoteData, archive)
      _            <- showSummary(uiChannel)(copyUploadEvents ++ deleteEvents)
    } yield ()) <* MessageChannel.endChannel(uiChannel)
  }

  private def showValidConfig(uiChannel: UIChannel) =
    Message.create(UIEvent.ShowValidConfig) >>= MessageChannel.send(uiChannel)

  private def fetchRemoteData(uiChannel: UIChannel) =
    for {
      bucket  <- Config.bucket
      prefix  <- Config.prefix
      objects <- Storage.list(bucket, prefix)
      _ <- Message.create(UIEvent.RemoteDataFetched(objects.byKey.size)) >>= MessageChannel
        .send(uiChannel)
    } yield objects

  private def handleErrors(throwable: Throwable) =
    Console.putStrLn("There were errors:") *> logValidationErrors(throwable)

  private def logValidationErrors(throwable: Throwable) =
    throwable match {
      case validateError: ConfigValidationException =>
        ZIO.foreach_(validateError.getErrors.asScala)(error =>
          Console.putStrLn(s"- $error"))
    }

  private def showSummary(uiChannel: UIChannel)(
      events: Seq[StorageEvent]): RIO[Clock, Unit] = {
    val counters = events.foldLeft(Counters.empty)(countActivities)
    Message.create(UIEvent.ShowSummary(counters)) >>=
      MessageChannel.send(uiChannel)
  }

  private def countActivities: (Counters, StorageEvent) => Counters =
    (counters: Counters, s3Action: StorageEvent) => {
      s3Action match {
        case _: UploadEvent => counters.incrementUploaded()
        case _: CopyEvent   => counters.incrementCopied()
        case _: DeleteEvent => counters.incrementDeleted()
        case _: ErrorEvent  => counters.incrementErrors()
        case _              => counters
      }
    }

}

object Program extends Program
