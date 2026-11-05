package qa02.investigation;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** External test-classpath-only fault injection; no production file or guard change. */
@Configuration(proxyBeanMethods = false)
public class ClockSkewConfiguration {
    @Bean @Primary
    Clock q70ControlledClock() {
        System.out.println("Q70_CONTROLLED_CLOCK_OFFSET_SECONDS=-2");
        return Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-2));
    }
}
