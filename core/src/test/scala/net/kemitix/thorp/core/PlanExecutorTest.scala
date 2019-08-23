package net.kemitix.thorp.core

import net.kemitix.thorp.config.Config
import net.kemitix.thorp.console.Console
import net.kemitix.thorp.core.Action.DoNothing
import net.kemitix.thorp.domain.{
  Bucket,
  RemoteKey,
  StorageQueueEvent,
  SyncTotals
}
import net.kemitix.thorp.storage.api.Storage
import org.scalatest.FreeSpec
import zio.{DefaultRuntime, ZIO}

class PlanExecutorTest extends FreeSpec {

  private def subject(in: Stream[Int]): ZIO[Any, Throwable, Stream[Int]] =
    ZIO.foldLeft(in)(Stream.empty[Int])((s, i) => ZIO(i #:: s)).map(_.reverse)

  "zio foreach on a stream can be a stream" in {
    val input   = (1 to 1000000).toStream
    val program = subject(input)
    val result  = new DefaultRuntime {}.unsafeRunSync(program).toEither
    assertResult(Right(input))(result)
  }

  "build plan with 100,000 actions" in {
    val nActions  = 100000
    val bucket    = Bucket("bucket")
    val remoteKey = RemoteKey("remoteKey")
    val input     = (1 to nActions).toStream.map(DoNothing(bucket, remoteKey, _))

    val syncTotals  = SyncTotals.empty
    val archiveTask = UnversionedMirrorArchive.default(syncTotals)

    val syncPlan = SyncPlan(input, syncTotals)
    val program: ZIO[Storage with Config with Console,
                     Throwable,
                     Seq[StorageQueueEvent]] =
      archiveTask.flatMap(archive =>
        PlanExecutor.executePlan(archive, syncPlan))

    val result: Either[Throwable, Seq[StorageQueueEvent]] =
      new DefaultRuntime {}.unsafeRunSync(program.provide(TestEnv)).toEither

    val expected = Right(
      (1 to nActions).toStream
        .map(_ => StorageQueueEvent.DoNothingQueueEvent(remoteKey)))
    assertResult(expected)(result)
  }

  object TestEnv extends Storage.Test with Config.Live with Console.Test

}