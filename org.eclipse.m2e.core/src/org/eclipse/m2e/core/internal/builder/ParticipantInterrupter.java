/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.core.internal.builder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;


/**
 * Interrupts a thread while a build participant runs, if the build must stop. The interrupt comes after a short grace
 * period, so that a participant that is about to end can still finish. A stopped build runs again later, so an
 * interrupt does not lose work. A participant stops only if it reacts to the interrupt, for example in a blocking
 * call.
 * <p>
 * Experimental: an interrupt can stop a participant while it writes a file.
 */
final class ParticipantInterrupter {

  static final long GRACE_MILLIS = 250;

  private static final long CHECK_MILLIS = 50;

  private static final ScheduledExecutorService WATCHER = Executors.newSingleThreadScheduledExecutor(runnable -> {
    Thread thread = new Thread(runnable, "m2e build participant interrupter"); //$NON-NLS-1$
    thread.setDaemon(true);
    return thread;
  });

  private final Thread thread;

  private final BooleanSupplier stopRequested;

  private final long graceMillis;

  private final ScheduledFuture<?> task;

  // the fields below are guarded by this
  private long stopRequestedSince = -1;

  private boolean interruptSent;

  private boolean finished;

  private ParticipantInterrupter(Thread thread, BooleanSupplier stopRequested, long graceMillis) {
    this.thread = thread;
    this.stopRequested = stopRequested;
    this.graceMillis = graceMillis;
    this.task = WATCHER.scheduleWithFixedDelay(this::check, CHECK_MILLIS, CHECK_MILLIS, TimeUnit.MILLISECONDS);
  }

  /**
   * Starts to watch for a stop request. Call {@link #finish()} in the same thread when the participant ends.
   */
  static ParticipantInterrupter start(BooleanSupplier stopRequested) {
    return start(stopRequested, GRACE_MILLIS);
  }

  static ParticipantInterrupter start(BooleanSupplier stopRequested, long graceMillis) {
    return new ParticipantInterrupter(Thread.currentThread(), stopRequested, graceMillis);
  }

  private synchronized void check() {
    if(finished || interruptSent) {
      return;
    }
    if(!stopRequested.getAsBoolean()) {
      stopRequestedSince = -1;
      return;
    }
    long now = System.currentTimeMillis();
    if(stopRequestedSince < 0) {
      stopRequestedSince = now;
    }
    if(now - stopRequestedSince >= graceMillis) {
      interruptSent = true;
      thread.interrupt();
    }
  }

  /**
   * Stops to watch. After this method, no interrupt comes from this instance. It clears an interrupt that this
   * instance sent, so that the rest of the build does not see it.
   *
   * @return <code>true</code> if this instance interrupted the thread
   */
  synchronized boolean finish() {
    finished = true;
    task.cancel(false);
    if(interruptSent) {
      Thread.interrupted();
    }
    return interruptSent;
  }
}
