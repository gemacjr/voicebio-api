package com.example;

import com.example.config.ApplicationProperties;
import com.example.config.CRLFLogConverter;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

@SpringBootApplication()
@EnableConfigurationProperties({ApplicationProperties.class})
@Slf4j
public class VoicebioApiApp {

  private final Environment env;

  public VoicebioApiApp(Environment env) {
    this.env = env;
  }

  /**
   * Initializes voicebioApi.
   *
   * <p>Spring profiles can be configured with a program argument
   * --spring.profiles.active=your-active-profile
   */
  @PostConstruct
  public void initApplication() {
    Collection<String> activeProfiles = List.of(env.getActiveProfiles());
    if (activeProfiles.contains(SpringProfiles.DEVELOPMENT)
        && activeProfiles.contains(SpringProfiles.PRODUCTION)) {
      log.error(
          "You have misconfigured your application! It should not run "
              + "with both the 'dev' and 'prod' profiles at the same time.");
    }
    if (activeProfiles.contains(SpringProfiles.DEVELOPMENT)
        && activeProfiles.contains(SpringProfiles.CLOUD)) {
      log.error(
          "You have misconfigured your application! It should not "
              + "run with both the 'dev' and 'cloud' profiles at the same time.");
    }
  }

  /**
   * Main method, used to run the application.
   *
   * @param args the command line arguments.
   */
  public static void main(String[] args) {
    var app = new SpringApplication(VoicebioApiApp.class);
    addDefaultProfile(app);
    Environment env = app.run(args).getEnvironment();
    logApplicationStartup(env);
  }

  /**
   * Use the "dev" profile when none is configured (via {@code spring.profiles.active} or the {@code
   * SPRING_PROFILES_ACTIVE} environment variable).
   *
   * @param app the Spring application.
   */
  public static void addDefaultProfile(SpringApplication app) {
    app.setDefaultProperties(Map.of("spring.profiles.default", SpringProfiles.DEVELOPMENT));
  }

  private static void logApplicationStartup(Environment env) {
    String protocol =
        Optional.ofNullable(env.getProperty("server.ssl.key-store"))
            .map(key -> "https")
            .orElse("http");
    String applicationName = env.getProperty("spring.application.name");
    String serverPort = env.getProperty("server.port");
    String contextPath =
        Optional.ofNullable(env.getProperty("server.servlet.context-path"))
            .filter(StringUtils::isNotBlank)
            .orElse("/");
    var hostAddress = "localhost";
    try {
      hostAddress = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      log.warn("The host name could not be determined, using `localhost` as fallback");
    }
    log.info(
        CRLFLogConverter.CRLF_SAFE_MARKER,
        """

            ----------------------------------------------------------
            \tApplication '{}' is running! Access URLs:
            \tLocal: \t\t{}://localhost:{}{}
            \tExternal: \t{}://{}:{}{}
            \tProfile(s): \t{}
            ----------------------------------------------------------""",
        applicationName,
        protocol,
        serverPort,
        contextPath,
        protocol,
        hostAddress,
        serverPort,
        contextPath,
        env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles());
  }
}
