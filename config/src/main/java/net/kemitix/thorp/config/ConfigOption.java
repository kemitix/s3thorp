package net.kemitix.thorp.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.kemitix.mon.TypeAlias;
import net.kemitix.thorp.domain.Filter;
import net.kemitix.thorp.domain.RemoteKey;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public interface ConfigOption {
    Configuration update(Configuration config);

    static ConfigOption source(Path path) {
        return new Source(path);
    }
    class Source extends TypeAlias<Path> implements ConfigOption {
        private Source(Path value) {
            super(value);
        }
        @Override
        public Configuration update(Configuration config) {
            return config.withSources(config.sources.append(getValue()));
        }
        public Path path() {
            return getValue();
        }
    }

    static ConfigOption bucket(String name) {
        return new Bucket(name);
    }
    class Bucket extends TypeAlias<String> implements ConfigOption {
        private Bucket(String value) {
            super(value);
        }
        @Override
        public Configuration update(Configuration config) {
            return config.withBucket(
                    net.kemitix.thorp.domain.Bucket.named(getValue()));
        }
    }

    static ConfigOption prefix(String path) {
        return new Prefix(path);
    }
    class Prefix extends TypeAlias<String> implements ConfigOption {
        private Prefix(String value) {
            super(value);
        }
        @Override
        public Configuration update(Configuration config) {
            return config.withPrefix(RemoteKey.create(getValue()));
        }
    }

    static ConfigOption include(String pattern) {
        return new Include(pattern);
    }
    class Include extends TypeAlias<String> implements ConfigOption {
        private Include(String value) {
            super(value);
        }
        @Override
        public Configuration update(Configuration config) {
            List<Filter> filters = new ArrayList<>(config.filters);
            filters.add(net.kemitix.thorp.domain.Filter.include(getValue()));
            return config.withFilters(filters);
        }
    }

    static ConfigOption exclude(String pattern) {
        return new Exclude(pattern);
    }
    class Exclude extends TypeAlias<String> implements ConfigOption {
        private Exclude(String value) {
            super(value);
        }
        @Override
        public Configuration update(Configuration config) {
            List<Filter> filters = new ArrayList<>(config.filters);
            filters.add(net.kemitix.thorp.domain.Filter.exclude(getValue()));
            return config.withFilters(filters);
        }
    }

    static ConfigOption debug() {
        return new Debug();
    }
    class Debug implements ConfigOption {
        @Override
        public Configuration update(Configuration config) {
            return config.withDebug(true);
        }
        @Override
        public String toString() {
            return "Debug";
        }
    }

    static ConfigOption batchMode() {
        return new BatchMode();
    }
    class BatchMode implements ConfigOption {
        @Override
        public Configuration update(Configuration config) {
            return config.withDebug(true);
        }

        @Override
        public String toString() {
            return "BatchMode";
        }
    }

    static ConfigOption version() {
        return new Version();
    }
    class Version implements ConfigOption {
        @Override
        public Configuration update(Configuration config) {
            return config;
        }
        @Override
        public String toString() {
            return "Version";
        }
    }

    static ConfigOption ignoreUserOptions() {
        return new IgnoreUserOptions();
    }
    class IgnoreUserOptions implements ConfigOption {
        @Override
        public Configuration update(Configuration config) {
            return config;
        }
        @Override
        public String toString() {
            return "Ignore User Options";
        }
    }

    static ConfigOption ignoreGlobalOptions() {
        return new IgnoreGlobalOptions();
    }
    class IgnoreGlobalOptions implements ConfigOption {
        @Override
        public Configuration update(Configuration config) {
            return config;
        }

        @Override
        public String toString() {
            return "Ignore Global Options";
        }
    }

    static ConfigOption parallel(int factor) {
        return new Parallel(factor);
    }
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class Parallel implements ConfigOption {
        public final int factor;
        @Override
        public Configuration update(Configuration config) {
            return config.withParallel(factor);
        }
        @Override
        public String toString() {
            return "Parallel: " + factor;
        }
    }
}
