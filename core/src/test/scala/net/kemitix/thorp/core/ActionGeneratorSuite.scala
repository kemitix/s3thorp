package net.kemitix.thorp.core

import java.time.Instant

import net.kemitix.thorp.config._
import net.kemitix.thorp.core.Action.{DoNothing, ToCopy, ToUpload}
import net.kemitix.thorp.domain.HashType.MD5
import net.kemitix.thorp.domain._
import net.kemitix.thorp.filesystem.FileSystem
import org.scalatest.FunSpec
import zio.DefaultRuntime

class ActionGeneratorSuite extends FunSpec {
  val lastModified       = LastModified(Instant.now())
  private val source     = Resource(this, "upload")
  private val sourcePath = source.toPath
  private val sources    = Sources(List(sourcePath))
  private val prefix     = RemoteKey("prefix")
  private val bucket     = Bucket("bucket")
  private val configOptions = ConfigOptions(
    List(
      ConfigOption.Bucket("bucket"),
      ConfigOption.Prefix("prefix"),
      ConfigOption.Source(sourcePath),
      ConfigOption.IgnoreUserOptions,
      ConfigOption.IgnoreGlobalOptions
    ))
  private val fileToKey =
    KeyGenerator.generateKey(sources, prefix) _

  describe("create actions") {

    val previousActions = Stream.empty[Action]

    describe("#1 local exists, remote exists, remote matches - do nothing") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              fileToKey)
        theRemoteMetadata = RemoteMetaData(theFile.remoteKey,
                                           theHash,
                                           lastModified)
        input = S3MetaData(
          theFile, // local exists
          matchByHash = Set(theRemoteMetadata), // remote matches
          matchByKey = Some(theRemoteMetadata) // remote exists
        )
      } yield (theFile, input)
      it("do nothing") {
        env.map({
          case (theFile, input) => {
            val expected =
              Right(Stream(
                DoNothing(bucket, theFile.remoteKey, theFile.file.length + 1)))
            val result = invoke(input, previousActions)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe("#2 local exists, remote is missing, other matches - copy") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              fileToKey)
        theRemoteKey   = theFile.remoteKey
        otherRemoteKey = prefix.resolve("other-key")
        otherRemoteMetadata = RemoteMetaData(otherRemoteKey,
                                             theHash,
                                             lastModified)
        input = S3MetaData(
          theFile, // local exists
          matchByHash = Set(otherRemoteMetadata), // other matches
          matchByKey = None) // remote is missing
      } yield (theFile, theRemoteKey, input, otherRemoteKey)
      it("copy from other key") {
        env.map({
          case (theFile, theRemoteKey, input, otherRemoteKey) => {
            val expected = Right(
              Stream(
                ToCopy(bucket,
                       otherRemoteKey,
                       theHash,
                       theRemoteKey,
                       theFile.file.length))) // copy
            val result = invoke(input, previousActions)
            assertResult(expected)(result)
          }
        })
      }
      describe("#3 local exists, remote is missing, other no matches - upload") {
        val theHash = MD5Hash("the-hash")
        val env = for {
          theFile <- LocalFileValidator.resolve("the-file",
                                                md5HashMap(theHash),
                                                sourcePath,
                                                fileToKey)
          input = S3MetaData(theFile, // local exists
                             matchByHash = Set.empty, // other no matches
                             matchByKey = None) // remote is missing
        } yield (theFile, input)
        it("upload") {
          env.map({
            case (theFile, input) => {
              val expected = Right(Stream(
                ToUpload(bucket, theFile, theFile.file.length))) // upload
              val result = invoke(input, previousActions)
              assertResult(expected)(result)
            }
          })
        }
      }
    }
    describe(
      "#4 local exists, remote exists, remote no match, other matches - copy") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              fileToKey)
        theRemoteKey   = theFile.remoteKey
        oldHash        = MD5Hash("old-hash")
        otherRemoteKey = prefix.resolve("other-key")
        otherRemoteMetadata = RemoteMetaData(otherRemoteKey,
                                             theHash,
                                             lastModified)
        oldRemoteMetadata = RemoteMetaData(theRemoteKey,
                                           hash = oldHash, // remote no match
                                           lastModified = lastModified)
        input = S3MetaData(
          theFile, // local exists
          matchByHash = Set(otherRemoteMetadata), // other matches
          matchByKey = Some(oldRemoteMetadata)) // remote exists
      } yield (theFile, theRemoteKey, input, otherRemoteKey)
      it("copy from other key") {
        env.map({
          case (theFile, theRemoteKey, input, otherRemoteKey) => {
            val expected = Right(
              Stream(
                ToCopy(bucket,
                       otherRemoteKey,
                       theHash,
                       theRemoteKey,
                       theFile.file.length))) // copy
            val result = invoke(input, previousActions)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe(
      "#5 local exists, remote exists, remote no match, other no matches - upload") {
      val theHash = MD5Hash("the-hash")
      val env = for {
        theFile <- LocalFileValidator.resolve("the-file",
                                              md5HashMap(theHash),
                                              sourcePath,
                                              fileToKey)
        theRemoteKey      = theFile.remoteKey
        oldHash           = MD5Hash("old-hash")
        theRemoteMetadata = RemoteMetaData(theRemoteKey, oldHash, lastModified)
        input = S3MetaData(
          theFile, // local exists
          matchByHash = Set.empty, // remote no match, other no match
          matchByKey = Some(theRemoteMetadata) // remote exists
        )
      } yield (theFile, input)
      it("upload") {
        env.map({
          case (theFile, input) => {
            val expected = Right(
              Stream(ToUpload(bucket, theFile, theFile.file.length))) // upload
            val result = invoke(input, previousActions)
            assertResult(expected)(result)
          }
        })
      }
    }
    describe("#6 local missing, remote exists - delete") {
      it("TODO") {
        pending
      }
    }
  }

  private def md5HashMap(theHash: MD5Hash): Map[HashType, MD5Hash] = {
    Map(MD5 -> theHash)
  }

  private def invoke(
      input: S3MetaData,
      previousActions: Stream[Action]
  ) = {
    type TestEnv = Config with FileSystem
    val testEnv: TestEnv = new Config.Live with FileSystem.Live {}

    def testProgram =
      for {
        config  <- ConfigurationBuilder.buildConfig(configOptions)
        _       <- Config.set(config)
        actions <- ActionGenerator.createActions(input, previousActions)
      } yield actions

    new DefaultRuntime {}.unsafeRunSync {
      testProgram.provide(testEnv)
    }.toEither
  }
}
