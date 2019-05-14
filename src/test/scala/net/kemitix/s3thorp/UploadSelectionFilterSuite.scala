package net.kemitix.s3thorp

import java.io.File
import java.time.Instant

import org.scalatest.FunSpec

class UploadSelectionFilterSuite extends FunSpec {

  new UploadSelectionFilter {
    describe("uploadRequiredFilter") {
      val localFile = Resource(this, "test-file-for-hash.txt")
      val localHash = "0cbfe978783bd7950d5da4ff85e4af37"
      val config = Config("bucket", "prefix", localFile.getParentFile)
      def invokeSubject(input: Either[File, S3MetaData]) =
        uploadRequiredFilter(config)(input).compile.toList.unsafeRunSync()
      describe("when supplied a file") {
        val input = Left(localFile)
        it("should be marked for upload") {
          assertResult(List(localFile))(invokeSubject(input))
        }
      }
      describe("when supplied S3MetaData") {
        describe("when hash is different") {
          val input = Right(S3MetaData(localFile, "", "doesn't match any hash", Instant.now))
          it("should be marked for upload") {
            assertResult(List(localFile))(invokeSubject(input))
          }
        }
        describe("when hash is the same") {
          val input = Right(S3MetaData(localFile, "", localHash, Instant.now))
          it("should not be marked for upload") {
            assertResult(List())(invokeSubject(input))
          }
        }
      }
    }
  }
}