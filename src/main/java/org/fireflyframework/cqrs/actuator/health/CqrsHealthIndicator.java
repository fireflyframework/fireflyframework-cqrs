package org.fireflyframework.cqrs.actuator.health;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.command.CommandHandlerRegistry;
import org.fireflyframework.cqrs.command.CommandMetricsService;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot Actuator health indicator for the CQRS framework.
 * <p>
 * Extends {@link FireflyHealthIndicator} for consistent health reporting.
 * Monitors the overall health and performance of the CQRS framework,
 * including command and query processing capabilities, handler registrations, and metrics collection.
 *
 * @author Firefly Common Domain Library
 * @since 1.0.0
 */
@Component
public class CqrsHealthIndicator extends FireflyHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(CqrsHealthIndicator.class);

    // Health thresholds
    private static final double ERROR_RATE_THRESHOLD = 10.0; // 10% error rate threshold
    private static final double SLOW_PROCESSING_THRESHOLD = 5000.0; // 5 seconds threshold

    private final MeterRegistry meterRegistry;
    private final CommandMetricsService commandMetricsService;
    private final CommandHandlerRegistry commandHandlerRegistry;
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final Instant startupTime;

    public CqrsHealthIndicator(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) CommandMetricsService commandMetricsService,
            @Autowired(required = false) CommandHandlerRegistry commandHandlerRegistry,
            @Autowired(required = false) CommandBus commandBus,
            @Autowired(required = false) QueryBus queryBus) {
        super("cqrs");
        this.meterRegistry = meterRegistry;
        this.commandMetricsService = commandMetricsService;
        this.commandHandlerRegistry = commandHandlerRegistry;
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.startupTime = Instant.now();

        log.debug("CQRS Health Indicator initialized");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            performHealthCheck(builder);
        } catch (Exception e) {
            log.error("Error performing CQRS health check", e);
            builder.down()
                .withDetail("error", "Health check failed: " + e.getMessage())
                .withDetail("error.type", e.getClass().getSimpleName());
        }
    }

    private void performHealthCheck(Health.Builder builder) {
        // Check core components
        if (commandBus == null) {
            builder.down().withDetail("command.bus", "NOT_AVAILABLE");
        } else {
            builder.withDetail("command.bus", "UP");
        }

        if (queryBus == null) {
            builder.down().withDetail("query.bus", "NOT_AVAILABLE");
        } else {
            builder.withDetail("query.bus", "UP");
        }

        // Add handler registry information
        addHandlerRegistryDetails(builder);

        // Add metrics information
        addMetricsDetails(builder);

        // Add performance indicators
        addPerformanceIndicators(builder);

        // Add framework information
        builder.withDetail("framework.version", "2025-08");
        builder.withDetail("uptime", Duration.between(startupTime, Instant.now()).toString());
    }

    private void addHandlerRegistryDetails(Health.Builder builder) {
        if (commandHandlerRegistry != null) {
            try {
                java.lang.reflect.Field handlersField = commandHandlerRegistry.getClass().getDeclaredField("handlers");
                handlersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<Class<?>, Object> handlers = (java.util.Map<Class<?>, Object>) handlersField.get(commandHandlerRegistry);

                builder.withDetail("command.handlers", handlers.size());

                if (handlers.isEmpty()) {
                    builder.withDetail("command.handlers.warning", "No command handlers registered");
                }

            } catch (Exception e) {
                log.debug("Could not access command handler registry: {}", e.getMessage());
                builder.withDetail("command.handlers", "UNKNOWN");
            }
        } else {
            builder.withDetail("command.handlers", "NOT_AVAILABLE");
        }

        // Query handlers (if DefaultQueryBus)
        if (queryBus instanceof org.fireflyframework.cqrs.query.DefaultQueryBus) {
            try {
                java.lang.reflect.Field handlersField = queryBus.getClass().getDeclaredField("handlers");
                handlersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<Class<?>, Object> handlers = (java.util.Map<Class<?>, Object>) handlersField.get(queryBus);

                builder.withDetail("query.handlers", handlers.size());

                if (handlers.isEmpty()) {
                    builder.withDetail("query.handlers.warning", "No query handlers registered");
                }

            } catch (Exception e) {
                log.debug("Could not access query handler registry: {}", e.getMessage());
                builder.withDetail("query.handlers", "UNKNOWN");
            }
        } else {
            builder.withDetail("query.handlers", "NOT_AVAILABLE");
        }
    }

    private void addMetricsDetails(Health.Builder builder) {
        if (meterRegistry == null) {
            builder.withDetail("metrics.enabled", false);
            builder.withDetail("metrics.status", "MeterRegistry not available");
            return;
        }

        builder.withDetail("metrics.enabled", true);

        if (commandMetricsService != null) {
            builder.withDetail("command.metrics.enabled", commandMetricsService.isMetricsEnabled());
        } else {
            builder.withDetail("command.metrics.enabled", false);
        }
    }

    private void addPerformanceIndicators(Health.Builder builder) {
        if (meterRegistry == null) {
            return;
        }

        addCommandPerformanceIndicators(builder);
        addQueryPerformanceIndicators(builder);
    }

    private void addCommandPerformanceIndicators(Health.Builder builder) {
        Counter processedCounter = meterRegistry.find("firefly.cqrs.command.processed").counter();
        Counter failedCounter = meterRegistry.find("firefly.cqrs.command.failed").counter();
        Timer processingTimer = meterRegistry.find("firefly.cqrs.command.processing.time").timer();

        if (processedCounter != null && failedCounter != null) {
            double totalProcessed = processedCounter.count();
            double totalFailed = failedCounter.count();
            double totalRequests = totalProcessed + totalFailed;

            if (totalRequests > 0) {
                double errorRate = (totalFailed / totalRequests) * 100;
                builder.withDetail("command.success.rate", String.format("%.2f%%", (totalProcessed / totalRequests) * 100));
                builder.withDetail("command.error.rate", String.format("%.2f%%", errorRate));

                if (errorRate > ERROR_RATE_THRESHOLD) {
                    builder.withDetail("command.error.rate.warning",
                        String.format("High error rate: %.2f%% (threshold: %.1f%%)", errorRate, ERROR_RATE_THRESHOLD));
                }
            }

            builder.withDetail("total.commands.processed", (long) totalProcessed);
            builder.withDetail("total.commands.failed", (long) totalFailed);
        }

        if (processingTimer != null) {
            double avgProcessingTime = processingTimer.mean(TimeUnit.MILLISECONDS);
            double maxProcessingTime = processingTimer.max(TimeUnit.MILLISECONDS);

            builder.withDetail("avg.command.processing.time.ms", String.format("%.2f", avgProcessingTime));
            builder.withDetail("max.command.processing.time.ms", String.format("%.2f", maxProcessingTime));

            if (avgProcessingTime > SLOW_PROCESSING_THRESHOLD) {
                builder.withDetail("command.processing.warning",
                    String.format("Slow average processing time: %.2fms (threshold: %.0fms)",
                        avgProcessingTime, SLOW_PROCESSING_THRESHOLD));
            }
        }
    }

    private void addQueryPerformanceIndicators(Health.Builder builder) {
        Counter processedCounter = meterRegistry.find("firefly.cqrs.query.processed").counter();
        Timer processingTimer = meterRegistry.find("firefly.cqrs.query.processing.time").timer();

        if (processedCounter != null) {
            builder.withDetail("total.queries.processed", (long) processedCounter.count());
        }

        if (processingTimer != null) {
            double avgProcessingTime = processingTimer.mean(TimeUnit.MILLISECONDS);
            double maxProcessingTime = processingTimer.max(TimeUnit.MILLISECONDS);

            builder.withDetail("avg.query.processing.time.ms", String.format("%.2f", avgProcessingTime));
            builder.withDetail("max.query.processing.time.ms", String.format("%.2f", maxProcessingTime));

            if (avgProcessingTime > SLOW_PROCESSING_THRESHOLD) {
                builder.withDetail("query.processing.warning",
                    String.format("Slow average processing time: %.2fms (threshold: %.0fms)",
                        avgProcessingTime, SLOW_PROCESSING_THRESHOLD));
            }
        }
    }
}
