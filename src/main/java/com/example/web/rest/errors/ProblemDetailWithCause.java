package com.example.web.rest.errors;

import java.io.Serial;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ProblemDetail;

/** RFC 7807 {@link ProblemDetail} that can optionally carry the problem detail of its cause. */
public class ProblemDetailWithCause extends ProblemDetail {

  @Serial private static final long serialVersionUID = 1L;

  private ProblemDetailWithCause cause;

  ProblemDetailWithCause() {
    super();
  }

  ProblemDetailWithCause(int rawStatus) {
    super(rawStatus);
  }

  public ProblemDetailWithCause getCause() {
    return cause;
  }

  public void setCause(ProblemDetailWithCause cause) {
    this.cause = cause;
  }

  public static final class Builder {

    private static final URI BLANK_TYPE = URI.create("about:blank");

    private URI type = BLANK_TYPE;
    private String title;
    private int status;
    private String detail;
    private URI instance;
    private final Map<String, Object> properties = new HashMap<>();
    private ProblemDetailWithCause cause;

    private Builder() {}

    public static Builder instance() {
      return new Builder();
    }

    public Builder withType(URI type) {
      this.type = type;
      return this;
    }

    public Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder withStatus(int status) {
      this.status = status;
      return this;
    }

    public Builder withDetail(String detail) {
      this.detail = detail;
      return this;
    }

    public Builder withInstance(URI instance) {
      this.instance = instance;
      return this;
    }

    public Builder withCause(ProblemDetailWithCause cause) {
      this.cause = cause;
      return this;
    }

    public Builder withProperty(String key, Object value) {
      this.properties.put(key, value);
      return this;
    }

    public ProblemDetailWithCause build() {
      ProblemDetailWithCause problem = new ProblemDetailWithCause(status);
      problem.setType(type);
      problem.setTitle(title);
      problem.setDetail(detail);
      problem.setInstance(instance);
      problem.setCause(cause);
      properties.forEach(problem::setProperty);
      return problem;
    }
  }
}
