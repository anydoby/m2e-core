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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;


public class ParticipantInterrupterTest {

  @After
  public void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  public void testInterruptsAfterGracePeriod() {
    long start = System.currentTimeMillis();
    ParticipantInterrupter interrupter = ParticipantInterrupter.start(() -> true, 300);
    try {
      Thread.sleep(10_000);
      fail("expected an interrupt");
    } catch(InterruptedException expected) {
      // the participant ends because of the interrupt
    }
    long elapsed = System.currentTimeMillis() - start;
    assertTrue("interrupt came too early: " + elapsed, elapsed >= 300);
    assertTrue("interrupt came too late: " + elapsed, elapsed < 5_000);
    assertTrue(interrupter.finish());
    assertFalse(Thread.currentThread().isInterrupted());
  }

  @Test
  public void testNoInterruptWithoutStopRequest() throws InterruptedException {
    ParticipantInterrupter interrupter = ParticipantInterrupter.start(() -> false, 0);
    Thread.sleep(500);
    assertFalse(interrupter.finish());
    assertFalse(Thread.currentThread().isInterrupted());
  }

  @Test
  public void testStopRequestMustStayForGracePeriod() throws InterruptedException {
    AtomicBoolean stop = new AtomicBoolean(true);
    ParticipantInterrupter interrupter = ParticipantInterrupter.start(stop::get, 1_000);
    Thread.sleep(300);
    stop.set(false);
    Thread.sleep(300);
    stop.set(true);
    Thread.sleep(500);
    // the stop request was present for 300 ms and then for 500 ms, never for 1000 ms
    assertFalse(interrupter.finish());
    assertFalse(Thread.currentThread().isInterrupted());
  }

  @Test
  public void testNoInterruptAfterFinish() throws InterruptedException {
    ParticipantInterrupter interrupter = ParticipantInterrupter.start(() -> true, 200);
    Thread.sleep(100);
    assertFalse(interrupter.finish());
    // the rest of the build must not get an interrupt from this participant
    Thread.sleep(800);
    assertFalse(Thread.currentThread().isInterrupted());
  }
}
