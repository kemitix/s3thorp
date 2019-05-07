package net.kemitix.s3thorp

import java.nio.file.Path
import java.time.Instant
import fs2.Stream
import cats.effect.IO
import net.kemitix.s3thorp.Sync.S3MetaData
import Main.putStrLn

trait S3MetaDataEnricher {

  def enrichWithS3MetaData: Path => Stream[IO, S3MetaData] =
    path => Stream.eval(for {
      _ <- putStrLn(s"enrich: $path")
      // HEAD(bucket, prefix, relative(path))
      // create blank S3MetaData records (sealed trait?)
    } yield S3MetaData(path, "", "", Instant.now()))

}
