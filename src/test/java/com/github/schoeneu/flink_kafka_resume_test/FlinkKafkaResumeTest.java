package com.github.schoeneu.flink_kafka_resume_test;

import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaHelper;
import com.github.charithe.kafka.KafkaJunitRule;
import com.github.knaufk.flinkjunit.FlinkJUnitRule;
import com.github.knaufk.flinkjunit.FlinkJUnitRuleBuilder;
import org.apache.flink.runtime.client.JobCancellationException;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.junit.Rule;
import org.junit.Test;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class FlinkKafkaResumeTest {

    @Rule
    public KafkaJunitRule kafka = new KafkaJunitRule(EphemeralKafkaBroker.create());
    @Rule
    public FlinkJUnitRule flink = new FlinkJUnitRuleBuilder().withWebUiEnabled(WEB_UI_PORT).build();

    private final AtomicInteger ctr = new AtomicInteger();
    private static final int WEB_UI_PORT = 8845;
    private final Timer timer = new Timer("eventSource");

    @Test
    public void testFlow() throws Exception {
        kafka.waitForStartup();
        final KafkaHelper helper = kafka.helper();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                helper.produceStrings("topic", String.valueOf(ctr.incrementAndGet()));
            }
        }, 0, 1000);
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:" + kafka.helper().kafkaPort());
        properties.setProperty("group.id", "test");

        try {
            System.out.println("running job. Let it run, then cancel via web UI on localhost:" + WEB_UI_PORT);
            runJob(properties);
        } catch (JobCancellationException e) {
            System.out.println("job cancelled");
        }
        System.out.println("sleeping 10s...");
        Thread.sleep(10000);
        System.out.println("...job restarting");

        try {
            runJob(properties);
        } catch (JobCancellationException e) {
            System.out.println("job cancelled a second time, exiting...");
        } finally {
            timer.cancel();
        }
    }

    private void runJob(Properties properties) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<String> stream = env
                .addSource(new FlinkKafkaConsumer010<>("topic", new SimpleStringSchema(), properties));
        stream.addSink(new PrintSinkFunction<>());
        env.execute();
    }
}
