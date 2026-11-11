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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.junit.Test;


public class IncrementalMojoTimesTest {

  private final IncrementalMojoTimes times = new IncrementalMojoTimes();

  private final IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("times");

  private final MojoExecutionKey key = new MojoExecutionKey("g", "a", "1", "run", "validate", "slow");

  @Test
  public void testNotSlowBeforeMinimumRuns() {
    for(int i = 1; i < IncrementalMojoTimes.MIN_RUNS; i++ ) {
      times.record(project, key, 5_000);
    }
    assertNull(times.getSlowMojo(project, key));
    times.record(project, key, 5_000);
    assertNotNull(times.getSlowMojo(project, key));
  }

  @Test
  public void testAverageOfLastRuns() {
    // old fast runs leave the window
    for(int i = 0; i < IncrementalMojoTimes.WINDOW; i++ ) {
      times.record(project, key, 10);
    }
    assertNull(times.getSlowMojo(project, key));
    for(int i = 0; i < IncrementalMojoTimes.WINDOW; i++ ) {
      times.record(project, key, 2_000);
    }
    IncrementalMojoTimes.SlowMojo slow = times.getSlowMojo(project, key);
    assertEquals(2_000, slow.averageMillis());
    assertEquals(IncrementalMojoTimes.WINDOW, slow.runs());
  }

  @Test
  public void testFastMojoIsNotSlow() {
    for(int i = 0; i < IncrementalMojoTimes.WINDOW; i++ ) {
      times.record(project, key, IncrementalMojoTimes.SLOW_MILLIS - 1);
    }
    assertNull(times.getSlowMojo(project, key));
  }

  @Test
  public void testForget() {
    for(int i = 0; i < IncrementalMojoTimes.WINDOW; i++ ) {
      times.record(project, key, 5_000);
    }
    times.forget(project, key);
    assertNull(times.getSlowMojo(project, key));
    for(int i = 0; i < IncrementalMojoTimes.WINDOW; i++ ) {
      times.record(project, key, 5_000);
    }
    times.forget(project);
    assertNull(times.getSlowMojo(project, key));
  }
}
