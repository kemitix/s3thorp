package net.kemitix.thorp.cli

import net.kemitix.thorp.console.Console
import net.kemitix.thorp.storage.aws.S3Storage
import zio.{App, ZIO}

object Main extends App {

  object LiveThorpApp extends S3Storage.Live with Console.Live

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    Program
      .run(args)
      .provide(LiveThorpApp)
      .fold(_ => 1, _ => 0)

}
