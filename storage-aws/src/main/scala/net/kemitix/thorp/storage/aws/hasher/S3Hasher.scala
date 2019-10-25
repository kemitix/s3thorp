package net.kemitix.thorp.storage.aws.hasher

import java.nio.file.Path

import net.kemitix.thorp.domain.{Hashes, MD5Hash}
import net.kemitix.thorp.filesystem.Hasher.Live.{hasher => CoreHasher}
import net.kemitix.thorp.filesystem.Hasher.Service
import net.kemitix.thorp.filesystem.{FileSystem, Hasher}
import net.kemitix.thorp.storage.aws.ETag
import zio.RIO

object S3Hasher {

  trait Live extends Hasher {
    val hasher: Service = new Service {

      /**
        * Generates an MD5 Hash and an multi-part ETag
        *
        * @param path the local path to scan
        * @return a set of hash values
        */
      override def hashObject(path: Path): RIO[Hasher with FileSystem, Hashes] =
        for {
          base <- CoreHasher.hashObject(path)
          etag <- ETagGenerator.eTag(path).map(MD5Hash(_))
        } yield base + (ETag -> etag)

      override def hashObjectChunk(
          path: Path,
          chunkNumber: Long,
          chunkSize: Long): RIO[Hasher with FileSystem, Hashes] =
        CoreHasher.hashObjectChunk(path, chunkNumber, chunkSize)

      override def hex(in: Array[Byte]): RIO[Hasher, String] =
        CoreHasher.hex(in)

      override def digest(in: String): RIO[Hasher, Array[Byte]] =
        CoreHasher.digest(in)
    }

  }
}
