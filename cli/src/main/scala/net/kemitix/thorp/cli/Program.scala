package net.kemitix.thorp.cli

import net.kemitix.thorp.core._
import net.kemitix.thorp.domain.{StorageQueueEvent, SyncTotals}
import net.kemitix.thorp.storage.aws.S3HashService.defaultHashService
import net.kemitix.thorp.storage.aws.S3StorageServiceBuilder.defaultStorageService
import zio.console._
import zio.{Task, TaskR, ZIO}

trait Program extends PlanBuilder {

  lazy val version = s"Thorp v${thorp.BuildInfo.version}"

  def run(cliOptions: ConfigOptions): ZIO[Console, Nothing, Unit] = {
    val showVersion = ConfigQuery.showVersion(cliOptions)
    for {
      _ <- ZIO.when(showVersion)(putStrLn(version))
      _ <- ZIO.when(!showVersion)(doProgram(cliOptions).catchAll(handleErrors))
    } yield ()
  }

  def doProgram(cliOptions: ConfigOptions): ZIO[Console, Throwable, Unit] = {
    for {
      plan    <- createPlan(defaultStorageService, defaultHashService, cliOptions)
      archive <- thorpArchive(cliOptions, plan.syncTotals)
      events  <- handleActions(archive, plan)
      _       <- defaultStorageService.shutdown
      _       <- SyncLogging.logRunFinished(events)
    } yield ()
  }

  def handleErrors(throwable: Throwable): ZIO[Console, Nothing, Unit] =
    for {
      _ <- putStrLn("There were errors:")
      _ <- throwable match {
        case ConfigValidationException(errors) =>
          ZIO.foreach(errors)(error => putStrLn(s"- $error"))
        case x => throw x
      }
    } yield ()

  def thorpArchive(
      cliOptions: ConfigOptions,
      syncTotals: SyncTotals
  ): Task[ThorpArchive] = Task {
    UnversionedMirrorArchive.default(
      defaultStorageService,
      ConfigQuery.batchMode(cliOptions),
      syncTotals
    )
  }

  private def handleActions(
      archive: ThorpArchive,
      syncPlan: SyncPlan
  ): TaskR[Console, Stream[StorageQueueEvent]] = {
    type Accumulator = (Stream[StorageQueueEvent], Long)
    val zero: Accumulator = (Stream(), syncPlan.syncTotals.totalSizeBytes)
    TaskR
      .foldLeft(syncPlan.actions.reverse.zipWithIndex)(zero)(
        (acc, indexedAction) => {
          val (action, index)     = indexedAction
          val (stream, bytesToDo) = acc
          val remainingBytes      = bytesToDo - action.size
          (for {
            event <- archive.update(index, action, remainingBytes)
            events = stream ++ Stream(event)
          } yield events)
            .map((_, remainingBytes))
        })
      .map {
        case (events, _) => events
      }
  }

}

object Program extends Program
