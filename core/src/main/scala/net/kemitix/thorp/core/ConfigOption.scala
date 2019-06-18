package net.kemitix.thorp.core

import java.nio.file.Path

import net.kemitix.thorp.domain
import net.kemitix.thorp.domain.{Config, RemoteKey}

sealed trait ConfigOption {
  def update(config: Config): Config
}

object ConfigOption {
  case class Source(path: Path) extends ConfigOption {
    override def update(config: Config): Config = config.copy(source = path.toFile)
  }
  case class Bucket(name: String) extends ConfigOption {
    override def update(config: Config): Config = config.copy(bucket = domain.Bucket(name))
  }
  case class Prefix(path: String) extends ConfigOption {
    override def update(config: Config): Config = config.copy(prefix = RemoteKey(path))
  }
  case class Include(pattern: String) extends ConfigOption {
    override def update(config: Config): Config = config.copy(filters = domain.Filter.Include(pattern) :: config.filters)
  }
  case class Exclude(pattern: String) extends ConfigOption {
    override def update(config: Config): Config = config.copy(filters = domain.Filter.Exclude(pattern) :: config.filters)
  }
  case class Debug() extends ConfigOption {
    override def update(config: Config): Config = config.copy(debug = true)
  }
}