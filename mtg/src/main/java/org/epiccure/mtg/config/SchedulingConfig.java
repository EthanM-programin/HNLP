package org.epiccure.mtg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables QueueService.tick(): expires abandoned carts and promotes the queue every minute. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
