package net.kemitix.thorp.core

import java.nio.file.Paths

import net.kemitix.thorp.domain.{Config, LocalFile, Logger, MD5HashData, Sources}
import net.kemitix.thorp.storage.api.HashService
import org.scalatest.FunSpec

class LocalFileStreamSuite extends FunSpec {

  private val source = Resource(this, "upload")
  private val sourcePath = source.toPath
  private val hashService: HashService = DummyHashService(Map(
    file("root-file") -> Map("md5" -> MD5HashData.Root.hash),
    file("subdir/leaf-file") -> Map("md5" -> MD5HashData.Leaf.hash)
  ))

  private def file(filename: String) =
    sourcePath.resolve(Paths.get(filename))

  implicit private val config: Config = Config(sources = Sources(List(sourcePath)))
  implicit private val logger: Logger = new DummyLogger

  describe("findFiles") {
    it("should find all files") {
      val result: Set[String] =
        invoke.localFiles.toSet
          .map { x: LocalFile => x.relative.toString }
      assertResult(Set("subdir/leaf-file", "root-file"))(result)
    }
    it("should count all files") {
      val result = invoke.count
      assertResult(2)(result)
    }
    it("should sum the size of all files") {
      val result = invoke.totalSizeBytes
      assertResult(113)(result)
    }
  }

  private def invoke =
    LocalFileStream.findFiles(sourcePath, hashService).unsafeRunSync

}
