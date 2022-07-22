/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.channel.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.BridgeTo;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import io.micrometer.tracing.test.simple.TracerAssert;

/**
 * @author Artem Bilan
 *
 * @since 6.0
 */
@SpringJUnitConfig
public class ObservationPropagationChannelInterceptorTests {

	@Autowired
	ObservationRegistry observationRegistry;

	@Autowired
	SimpleTracer simpleTracer;

	@Autowired
	SubscribableChannel directChannel;

	@Autowired
	SubscribableChannel executorChannel;

	@Autowired
	PollableChannel queueChannel;

	@Autowired
	DirectChannel testConsumer;

	@Autowired
	@Qualifier("testPropagationConsumer")
	DirectChannel testPropagationConsumer;

	@BeforeEach
	void setup() {
		this.simpleTracer.getSpans().clear();
	}

	@Test
	void observationPropagatedOverDirectChannel() throws InterruptedException {
		AtomicReference<Observation.Scope> scopeReference = new AtomicReference<>();
		CountDownLatch handleLatch = new CountDownLatch(1);
		this.directChannel.subscribe(m -> {
			scopeReference.set(this.observationRegistry.getCurrentObservationScope());
			handleLatch.countDown();
		});

		AtomicReference<Observation.Scope> originalScope = new AtomicReference<>();

		Observation.createNotStarted("test1", this.observationRegistry)
				.observe(() -> {
					originalScope.set(this.observationRegistry.getCurrentObservationScope());
					this.directChannel.send(new GenericMessage<>("test"));
				});

		assertThat(handleLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(scopeReference.get())
				.isNotNull()
				.isSameAs(originalScope.get());

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation();

		TracerAssert.assertThat(this.simpleTracer)
				.onlySpan()
				.hasNameEqualTo("test1");
	}

	@Test
	void observationPropagatedOverExecutorChannel() throws InterruptedException {
		AtomicReference<Observation.Scope> scopeReference = new AtomicReference<>();
		CountDownLatch handleLatch = new CountDownLatch(1);
		this.executorChannel.subscribe(m -> {
			scopeReference.set(this.observationRegistry.getCurrentObservationScope());
			handleLatch.countDown();
		});

		AtomicReference<Observation.Scope> originalScope = new AtomicReference<>();

		Observation.createNotStarted("test2", this.observationRegistry)
				.observe(() -> {
					originalScope.set(this.observationRegistry.getCurrentObservationScope());
					this.executorChannel.send(new GenericMessage<>("test"));
				});

		assertThat(handleLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(scopeReference.get())
				.isNotNull()
				.isNotSameAs(originalScope.get());

		assertThat(scopeReference.get().getCurrentObservation())
				.isSameAs(originalScope.get().getCurrentObservation());

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation();

		TracerAssert.assertThat(this.simpleTracer)
				.onlySpan()
				.hasNameEqualTo("test2");
	}

	@Test
	void observationPropagatedOverQueueChannel() throws InterruptedException {
		AtomicReference<Observation.Scope> scopeReference = new AtomicReference<>();
		CountDownLatch handleLatch = new CountDownLatch(1);
		this.testConsumer.subscribe(m -> {
			scopeReference.set(this.observationRegistry.getCurrentObservationScope());
			handleLatch.countDown();
		});

		AtomicReference<Observation.Scope> originalScope = new AtomicReference<>();

		Observation.createNotStarted("test3", this.observationRegistry)
				.observe(() -> {
					originalScope.set(this.observationRegistry.getCurrentObservationScope());
					this.queueChannel.send(new GenericMessage<>("test"));
				});

		assertThat(handleLatch.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(scopeReference.get())
				.isNotNull()
				.isNotSameAs(originalScope.get());

		assertThat(scopeReference.get().getCurrentObservation())
				.isSameAs(originalScope.get().getCurrentObservation());

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation();

		TracerAssert.assertThat(this.simpleTracer)
				.onlySpan()
				.hasNameEqualTo("test3");
	}

	@Test
	void observationContextPropagatedOverDirectChannel() throws InterruptedException {
		CountDownLatch handleLatch = new CountDownLatch(1);
		this.testPropagationConsumer.subscribe(m -> {
			// This would be the instrumentation code on the receiver side
			// We would need to check if Zipkin wouldn't require us to create the receiving span and then an additional one for the user code...
			ReceiverContext<Message<?>> receiverContext = new ReceiverContext<>((carrier, key) -> carrier.getHeaders().get(key, String.class));
			receiverContext.setCarrier(m);
			// ...if that's the case, then this would be the single 'receiving' span...
			Observation receiving = Observation.createNotStarted("receiving", receiverContext, this.observationRegistry).start();
			receiving.stop();
			// ...and this would be the user's code
			Observation.createNotStarted("user.code", receiverContext, this.observationRegistry)
					.parentObservation(receiving)
					.observe(() -> {
						// Let's assume that this is the user code
						handleLatch.countDown();
					});
		});

		// This would be the instrumentation code on the sender side (user's code would call e.g. MessageTemplate and this code
		// would lay in MessageTemplate)
		// We need to mutate the carrier so we need to use the builder not the message since messageheaders are immutable
		SenderContext<MessageBuilder<String>> senderContext = new SenderContext<>((carrier, key, value) -> Objects.requireNonNull(carrier).setHeader(key, value));
		MessageBuilder<String> builder = MessageBuilder.withPayload("test");
		senderContext.setCarrier(builder);
		Observation sending = Observation.createNotStarted("sending", senderContext, this.observationRegistry)
				.start();
		try {
			this.testPropagationConsumer.send(builder.build());
		} catch (Exception e) {
			sending.error(e);
		} finally {
			sending.stop();
		}

		assertThat(handleLatch.await(10, TimeUnit.SECONDS)).isTrue();

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
				.doesNotHaveAnyRemainingCurrentObservation();

		TracerAssert.assertThat(this.simpleTracer)
				.reportedSpans()
				.hasSize(3)
				// TODO: There must be a better way to do it without casting
				.satisfies(simpleSpans -> SpansAssert.assertThat(simpleSpans.stream().map(simpleSpan -> (FinishedSpan) simpleSpan).collect(Collectors.toList()))
						.hasASpanWithName("sending")
						.assertThatASpanWithNameEqualTo("receiving")
						.hasTag("foo", "some foo value")
						.hasTag("bar", "some bar value")
						.backToSpans()
						.hasASpanWithName("user.code"));
	}

	@Configuration
	@EnableIntegration
	public static class ContextConfiguration {

		@Bean
		SimpleTracer simpleTracer() {
			return new SimpleTracer();
		}

		@Bean
		ObservationRegistry observationRegistry(Tracer tracer, Propagator propagator) {
			TestObservationRegistry observationRegistry = TestObservationRegistry.create();
			observationRegistry.observationConfig().observationHandler(
					// Composite will pick the first matching handler
					new ObservationHandler.FirstMatchingCompositeObservationHandler(
					// This is responsible for creating a child span on the sender side
					new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
					// This is responsible for creating a span on the receiver side
					new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
					// This is responsible for creating a default span
					new DefaultTracingObservationHandler(tracer)));
			return observationRegistry;
		}

		@Bean
		@GlobalChannelInterceptor(patterns = "*Channel")
		public ChannelInterceptor observationPropagationInterceptor(ObservationRegistry observationRegistry) {
			return new ObservationPropagationChannelInterceptor(observationRegistry);
		}

		@Bean
		@BridgeTo(value = "testConsumer", poller = @Poller(fixedDelay = "100"))
		public PollableChannel queueChannel() {
			return new QueueChannel();
		}

		@Bean
		public SubscribableChannel executorChannel() {
			return new ExecutorChannel(Executors.newSingleThreadExecutor());
		}

		@Bean
		public SubscribableChannel directChannel() {
			return new DirectChannel();
		}

		@Bean
		public DirectChannel testConsumer() {
			return new DirectChannel();
		}

		@Bean
		public DirectChannel testPropagationConsumer() {
			return new DirectChannel();
		}

		@Bean
		public Propagator propagator(Tracer tracer) {
			return new Propagator() {
				// List of headers required for tracing propagation
				@Override
				public List<String> fields() {
					return Arrays.asList("foo", "bar");
				}

				// This is called on the producer side when the message is being sent
				// Normally we would pass information from tracing context - for tests we don't need to
				@Override
				public <C> void inject(TraceContext context, @Nullable C carrier, Setter<C> setter) {
					setter.set(carrier, "foo", "some foo value");
					setter.set(carrier, "bar", "some bar value");
				}


				// This is called on the consumer side when the message is consumed
				// Normally we would use tools like Extractor from tracing but for tests we are just manually creating a span
				@Override
				public <C> Span.Builder extract(C carrier, Getter<C> getter) {
					String foo = getter.get(carrier, "foo");
					String bar = getter.get(carrier, "bar");
					return tracer.spanBuilder().kind(Span.Kind.CONSUMER).tag("foo", foo).tag("bar", bar);
				}
			};
		}

	}

}
