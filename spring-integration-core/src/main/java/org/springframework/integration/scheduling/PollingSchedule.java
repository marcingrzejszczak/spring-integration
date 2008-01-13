/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.scheduling;

import java.util.concurrent.TimeUnit;

import org.springframework.util.Assert;

/**
 * Scheduling metadata for a polling task.
 * 
 * @author Mark Fisher
 */
public class PollingSchedule implements Schedule {

	private long period;

	private long initialDelay = 0;

	private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

	private boolean fixedRate = false;


	/**
	 * Create a fixed-delay schedule with no initial delay.
	 * 
	 * @param period the polling period in milliseconds
	 */
	public PollingSchedule(long period) {
		this.period = period;
	}


	public long getPeriod() {
		return this.period;
	}

	/**
	 * Set the polling period. A period of 0 indicates a repeating task.
	 */
	public void setPeriod(long period) {
		this.period = period;
	}

	public long getInitialDelay() {
		return this.initialDelay;
	}

	public void setInitialDelay(long initialDelay) {
		Assert.isTrue(initialDelay >= 0, "'initialDelay' must not be negative");
		this.initialDelay = initialDelay;
	}

	public TimeUnit getTimeUnit() {
		return this.timeUnit;
	}

	public void setTimeUnit(TimeUnit timeUnit) {
		Assert.notNull(timeUnit, "'timeUnit' must not be null");
		this.timeUnit = timeUnit;
	}

	public boolean getFixedRate() {
		return this.fixedRate;
	}

	public void setFixedRate(boolean fixedRate) {
		this.fixedRate = fixedRate;
	}

}
