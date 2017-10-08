package example;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinMessageListener;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.V2StorageComponent;
import zipkin.server.ZipkinHttpCollector;
import zipkin.server.ZipkinQueryApiV1;
import zipkin.storage.StorageComponent;
import zipkin2.storage.InMemoryStorage;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ZipkinStreamTestsConfig.class,
		webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.initialize=true" })
@ActiveProfiles("test")
public class ZipkinServerApplicationTests {

	@Autowired
	private StorageComponent storage;

	@Test
	public void contextLoads() {
		int count = this.storage.spanStore().getServiceNames().size();
		assertEquals(0, count);
	}

}


// TODO: Zipkin Server is not Boot 2.0 compatible
@Configuration
@SpringBootApplication
@EnableBoot2CompatibleZipkinServer
class ZipkinStreamTestsConfig {

}

//@EnableZipkinStreamServer
@EnableBinding(SleuthSink.class)
@Import({ZipkinMessageListener.class,
		Boot2ZipkinCompatibleConfig.class,
		ZipkinQueryApiV1.class,
		ZipkinHttpCollector.class})
@interface EnableBoot2CompatibleZipkinServer {
}

// CollectorMetrics bean definition from `ZipkinServerConfiguration`
// is not Boot 2.0 compatible
@Configuration
class Boot2ZipkinCompatibleConfig {

	@Bean CollectorMetrics collectorMetrics() {
		CollectorMetrics mock = Mockito.mock(CollectorMetrics.class);
		Mockito.when(mock.forTransport(Mockito.anyString())).thenReturn(mock);
		return mock;
	}

	@Bean
	@ConditionalOnMissingBean(CollectorSampler.class)
	CollectorSampler traceIdSampler(@Value("${zipkin.collector.sample-rate:1.0}") float rate) {
		return CollectorSampler.create(rate);
	}

	/**
	 * This is a special-case configuration if there's no StorageComponent of any kind. In-Mem can
	 * supply both read apis, so we add two beans here.
	 */
	@Configuration
	// "matchIfMissing = true" ensures this is used when there's no configured storage type
	@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "mem", matchIfMissing = true)
	@ConditionalOnMissingBean(StorageComponent.class)
	static class InMemoryConfiguration {
		@Bean StorageComponent storage(
				@Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
				@Value("${zipkin.storage.mem.max-spans:500000}") int maxSpans) {
			return V2StorageComponent.create(InMemoryStorage.newBuilder()
					.strictTraceId(strictTraceId)
					.maxSpanCount(maxSpans)
					.build());
		}

		@Bean InMemoryStorage v2Storage(V2StorageComponent component) {
			return (InMemoryStorage) component.delegate();
		}
	}
}
