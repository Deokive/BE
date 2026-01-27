package com.depth.deokive.system.ratelimit.config;

import com.depth.deokive.system.security.util.PropertiesParserUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private Redis redis = new Redis();
    private Expiration expiration = new Expiration();
    private Failure failure = new Failure();
    private Key key = new Key();
    private Logging logging = new Logging();
    private Proxy proxy = new Proxy();

    @Getter @Setter
    public static class Redis {
        private Duration timeoutFailOpen = Duration.ofMillis(200);
        private Duration timeoutFailClosed = Duration.ofMillis(500);
    }

    @Getter @Setter
    public static class Expiration {
        private Duration refillToMaxJitter = Duration.ofSeconds(10);
    }

    public enum FailureMode { FAIL_OPEN, FAIL_CLOSED }

    @Getter @Setter
    public static class Failure {
        private FailureMode mode = FailureMode.FAIL_OPEN;
    }

    @Getter @Setter
    public static class Key {
        private String namespace = "ratelimit";
        private String userPrefix = "user";
        private String ipPrefix = "ip";
        private String emailPrefix = "email";
    }

    @Getter @Setter
    public static class Logging {
        private long backendErrorMinIntervalMs = 1000;
    }

    @Getter @Setter
    public static class Proxy {
        /**
         * env/TRUSTED_CIDRS를 그대로 문자열로 바인딩한다.
         */
        private String trustedCidrs = "";

        /**
         * 사용 시점에만 안전하게 파싱해서 List로 제공한다.
         */
        public List<String> trustedCidrsAsList() {
            if (trustedCidrs == null || trustedCidrs.isBlank()) {
                return List.of();
            }
            return PropertiesParserUtils.propertiesParser(trustedCidrs);
        }
    }
}
