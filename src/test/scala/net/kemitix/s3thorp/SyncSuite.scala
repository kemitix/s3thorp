package net.kemitix.s3thorp

import java.io.File
import java.time.Instant

import cats.effect.IO
import net.kemitix.s3thorp.Sync.{Bucket, LocalFile, MD5Hash, RemoteKey}
import net.kemitix.s3thorp.awssdk.S3Client
import org.scalatest.FunSpec

class SyncSuite extends FunSpec {

  describe("s3client thunk") {
    val testBucket = "bucket"
    val testRemoteKey = "prefix/file"
    describe("objectHead") {
      val md5Hash = "md5Hash"
      val lastModified = Instant.now()
      val sync = new Sync(new S3Client with DummyS3Client {
        override def objectHead(bucket: String, key: String) = {
          assert(bucket == testBucket)
          assert(key == testRemoteKey)
          IO(Some((md5Hash, lastModified)))
        }
      })
      it("delegates unmodified to the S3Client") {
        assertResult(Some((md5Hash, lastModified)))(
          sync.objectHead(testBucket, testRemoteKey).
            unsafeRunSync())
      }
    }
    describe("upload") {
      val md5Hash = "the-hash"
      val testLocalFile = new File("file")
      val sync = new Sync(new S3Client with DummyS3Client {
        override def upload(localFile: LocalFile, bucket: Bucket, remoteKey: RemoteKey): IO[Either[Throwable, MD5Hash]] = {
          assert(localFile == testLocalFile)
          assert(bucket == testBucket)
          assert(remoteKey == testRemoteKey)
          IO(Right(md5Hash))
        }
      })
      it("delegates unmodified to the S3Client") {
        assertResult(Right(md5Hash))(
          sync.upload(testLocalFile, testBucket, testRemoteKey).
            unsafeRunSync())
      }
    }
  }
  describe("run") {
    val testBucket = "bucket"
    val source = Resource(this, "upload")
    // source contains the files root-file and subdir/leaf-file
    val config = Config("bucket", "prefix", source)
    describe("when all files should be uploaded") {
      var uploadsRecord: Map[String, RemoteKey] = Map()
      val sync = new Sync(new S3Client {
        override def objectHead(bucket: Bucket, remoteKey: RemoteKey) =
          IO(None)
        override def upload(localFile: LocalFile, bucket: Bucket, remoteKey: RemoteKey) = {
          if (bucket == testBucket)
            uploadsRecord += (source.toPath.relativize(localFile.toPath).toString -> remoteKey)
          IO(Right("some hash value"))
        }
      })
      it("uploads all files") {
        sync.run(config).unsafeRunSync
        val expected = Map(
          "subdir/leaf-file" -> "prefix/subdir/leaf-file",
          "root-file" -> "prefix/root-file"
        )
        assertResult(expected)(uploadsRecord)
      }
    }
    describe("when no files should be uploaded") {
      val rootHash = "a3a6ac11a0eb577b81b3bb5c95cc8a6e"
      val leafHash = "208386a650bdec61cfcd7bd8dcb6b542"
      var uploadsRecord: Map[String, RemoteKey] = Map()
      val sync = new Sync(new S3Client {
        override def objectHead(bucket: Bucket, remoteKey: RemoteKey) = IO(
          remoteKey match {
            case "prefix/root-file" => Some((rootHash, Instant.now))
            case "prefix/subdir/leaf-file" => Some((leafHash, Instant.now))
            case _ => None
          })
        override def upload(localFile: LocalFile, bucket: Bucket, remoteKey: RemoteKey) = {
          if (bucket == testBucket)
            uploadsRecord += (source.toPath.relativize(localFile.toPath).toString -> remoteKey)
          IO(Right("some hash value"))
        }
      })
      it("uploads nothing") {
        sync.run(config).unsafeRunSync
        val expected = Map()
        assertResult(expected)(uploadsRecord)
      }
    }
  }
}