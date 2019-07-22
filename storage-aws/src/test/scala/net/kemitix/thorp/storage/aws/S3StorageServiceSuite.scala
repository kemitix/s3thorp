package net.kemitix.thorp.storage.aws

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import com.amazonaws.services.s3.model.{ListObjectsV2Result, S3ObjectSummary}
import net.kemitix.thorp.console.MyConsole
import net.kemitix.thorp.core.Resource
import net.kemitix.thorp.domain._
import org.scalamock.scalatest.MockFactory
import org.scalatest.FreeSpec
import zio.Runtime
import zio.internal.PlatformLive

class S3StorageServiceSuite extends FreeSpec with MockFactory {

  private val runtime = Runtime(MyConsole.Live, PlatformLive.Default)

  trait S3ClientTest {
    val amazonS3Client: AmazonS3.Client = stub[AmazonS3.Client]
    val amazonS3TransferManager: AmazonTransferManager =
      stub[AmazonTransferManager]
    val storageService: S3StorageService =
      new S3StorageService(amazonS3Client, amazonS3TransferManager)
  }

  "listObjects" - {
    def objectSummary(hash: MD5Hash,
                      remoteKey: RemoteKey,
                      lastModified: LastModified) = {
      val summary = new S3ObjectSummary()
      summary.setETag(hash.hash)
      summary.setKey(remoteKey.key)
      summary.setLastModified(Date.from(lastModified.when))
      summary
    }
    val source     = Resource(this, "upload")
    val sourcePath = source.toPath
    val prefix     = RemoteKey("prefix")
    implicit val config: Config =
      Config(Bucket("bucket"), prefix, sources = Sources(List(sourcePath)))
    val lm             = LastModified(Instant.now.truncatedTo(ChronoUnit.MILLIS))
    val h1             = MD5Hash("hash1")
    val k1a            = RemoteKey("key1a")
    val o1a            = objectSummary(h1, k1a, lm)
    val k1b            = RemoteKey("key1b")
    val o1b            = objectSummary(h1, k1b, lm)
    val h2             = MD5Hash("hash2")
    val k2             = RemoteKey("key2")
    val o2             = objectSummary(h2, k2, lm)
    val myFakeResponse = new ListObjectsV2Result()
    val summaries      = myFakeResponse.getObjectSummaries
    summaries.add(o1a)
    summaries.add(o1b)
    summaries.add(o2)

    "should build list of hash lookups, with duplicate objects grouped by hash" in {
      val expected = Right(
        S3ObjectsData(
          byHash = Map(h1 -> Set(KeyModified(k1a, lm), KeyModified(k1b, lm)),
                       h2 -> Set(KeyModified(k2, lm))),
          byKey = Map(k1a -> HashModified(h1, lm),
                      k1b -> HashModified(h1, lm),
                      k2  -> HashModified(h2, lm))
        ))
      new S3ClientTest {
        (amazonS3Client.listObjectsV2 _)
          .when()
          .returns(_ => myFakeResponse)
        val result = invoke(storageService)
        assertResult(expected)(result)
      }
    }
  }

  private def invoke(storageService: S3StorageService) =
    runtime.unsafeRunSync {
      storageService
        .listObjects(Bucket("bucket"), RemoteKey("prefix"))
    }.toEither

}
