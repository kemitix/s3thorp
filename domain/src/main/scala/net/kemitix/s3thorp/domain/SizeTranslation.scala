package net.kemitix.s3thorp.domain

object SizeTranslation {

  val kbLimit = 10240L
  val mbLimit = kbLimit * 1024
  val gbLimit = mbLimit * 1024

  def sizeInEnglish(length: Long): String =
    length.toDouble match {
      case bytes if bytes > gbLimit => f"${bytes / 1024 / 1024 /1024}%.3fGb"
      case bytes if bytes > mbLimit => f"${bytes / 1024 / 1024}%.2fMb"
      case bytes if bytes > kbLimit => f"${bytes / 1024}%.0fKb"
      case bytes => s"${length}b"
    }


}
