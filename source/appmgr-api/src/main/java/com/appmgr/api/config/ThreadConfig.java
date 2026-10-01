package com.appmgr.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadConfig {
    @Value("${thread.pool.size:10}")
    private Integer threadPoolSize;

    @Value("${thread.pool.queue.size:100}")
    private Integer threadQueuePoolSize;


    @Bean(name = "threadPoolExecutor")
    public TaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(threadQueuePoolSize);
        executor.setThreadNamePrefix("ww-auth-invoke-");
        executor.initialize();
        return executor;
    }

    /**
     *
     * ThreadPoolExecutor and ThreadPoolTaskExecutor are also Executors but they have the following additional parameters:
     *
     * corePoolSize: Default number of Threads in the Pool
     * maxPoolSize: Maximum number of Threads in the Pool
     * queueCapacity: Maximum number of BlockingQueue
     * # Operating principles
     *
     * For example, with ThreadPoolExecutor there is:
     *
     * corePoolSize: 5
     * maxPoolSize: 15
     * queueCapacity: 100
     *
     * When there is a request, it will create a maximum of 5 threads in the Pool (corePoolSize).
     * When the number of threads exceeds 5 threads. It will be put into the queue.
     * When the number of queues is full 100 (queueCapacity). At this time, it will start creating new Threads.
     * The maximum number of newly created threads is 15 (maxPoolSize).
     * When Request exceeds 15 threads. Request will be rejected!
     *
     * */
}
