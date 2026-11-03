package com.example.config;

import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

/**
 * Cache key generator that prefixes every key with a build identifier (git commit, build time or version),
 * so entries cached by a previous deployment are never read back by a new one.
 */
public class PrefixedKeyGenerator implements KeyGenerator {

    private final String prefix;

    public PrefixedKeyGenerator(GitProperties gitProperties, BuildProperties buildProperties) {
        this.prefix = generatePrefix(gitProperties, buildProperties);
    }

    String getPrefix() {
        return prefix;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return new SimpleKey(prefix, method.getName(), SimpleKeyGenerator.generateKey(params));
    }

    private static String generatePrefix(GitProperties gitProperties, BuildProperties buildProperties) {
        if (gitProperties != null && gitProperties.getShortCommitId() != null) {
            return gitProperties.getShortCommitId();
        }
        if (buildProperties != null) {
            if (buildProperties.getTime() != null) {
                return DateTimeFormatter.ISO_INSTANT.format(buildProperties.getTime());
            }
            if (buildProperties.getVersion() != null) {
                return buildProperties.getVersion();
            }
        }
        return UUID.randomUUID().toString();
    }
}
