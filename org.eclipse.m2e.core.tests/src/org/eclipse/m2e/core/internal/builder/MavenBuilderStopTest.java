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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests that the Maven builder stops between build participants when the build must stop, and that the next build
 * gets the same changes again.
 */
public class MavenBuilderStopTest extends AbstractMavenProjectTestCase {

  private IProject project;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    StopTestProjectConfigurator.reset();
    project = importProject("resources/projects/buildStopParticipants/pom.xml");
    waitForJobsToComplete();
    project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
    waitForJobsToComplete();
    WorkspaceHelpers.assertNoErrors(project);
    assertEquals("the test participants must run", List.of("first", "second"), StopTestProjectConfigurator.calls);
    StopTestProjectConfigurator.reset();
  }

  @After
  public void resetConfigurator() {
    StopTestProjectConfigurator.reset();
  }

  @Test
  public void testCanceledBuildKeepsChangesForNextBuild() throws Exception {
    changeFile();
    StopTestProjectConfigurator.action = (id, m) -> {
      if("first".equals(id)) {
        m.setCanceled(true);
      }
    };
    try {
      project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
    } catch(OperationCanceledException expected) {
      // the platform can report the cancel
    }
    assertEquals(List.of("first:changed"), StopTestProjectConfigurator.calls);

    StopTestProjectConfigurator.reset();
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
    assertEquals(List.of("first:changed", "second:changed"), StopTestProjectConfigurator.calls);
  }

  @Test
  public void testAutoBuildStopsWhenOperationWaits() throws Exception {
    setAutoBuilding(true);
    waitForJobsToComplete();
    StopTestProjectConfigurator.reset();
    AtomicReference<Thread> operation = startOperationInFirstParticipant();

    changeFile();
    waitForAutoBuildAndOperation(operation);

    List<String> calls = List.copyOf(StopTestProjectConfigurator.calls);
    // the auto-build stops before "second"; the operation runs; the next auto-build gets the same change again
    assertEquals(calls.toString(), "first:changed", calls.get(0));
    assertEquals(calls.toString(), "operation done", calls.get(1));
    assertTrue(calls.toString(), calls.contains("second:changed"));
    assertEquals(calls.toString(), 1, calls.stream().filter(c -> c.startsWith("second")).count());
  }

  @Test
  public void testManualBuildDoesNotStopWhenOperationWaits() throws Exception {
    AtomicReference<Thread> operation = startOperationInFirstParticipant();

    changeFile();
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
    operation.get().join(30_000);

    assertEquals(List.of("first:changed", "second:changed", "operation done"), StopTestProjectConfigurator.calls);
  }

  private void changeFile() throws Exception {
    byte[] content = ("changed " + System.nanoTime()).getBytes();
    project.getFile("src/changed.txt").setContents(new ByteArrayInputStream(content), IResource.FORCE, monitor);
  }

  /**
   * The first run of the "first" participant starts an operation that waits for the build, and gives the operation
   * time to request a stop.
   */
  private AtomicReference<Thread> startOperationInFirstParticipant() {
    AtomicBoolean armed = new AtomicBoolean(true);
    AtomicReference<Thread> operation = new AtomicReference<>();
    StopTestProjectConfigurator.action = (id, m) -> {
      if("first".equals(id) && armed.compareAndSet(true, false)) {
        Thread thread = new Thread(() -> {
          try {
            ResourcesPlugin.getWorkspace().run(
                pm -> project.getFile("src/operation.txt").create(new ByteArrayInputStream(new byte[0]), true, pm),
                project, 0, new NullProgressMonitor());
            StopTestProjectConfigurator.calls.add("operation done");
          } catch(Exception e) {
            StopTestProjectConfigurator.calls.add("operation failed: " + e);
          }
        }, "test operation");
        operation.set(thread);
        thread.start();
        try {
          Thread.sleep(1_000);
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    return operation;
  }

  private void waitForAutoBuildAndOperation(AtomicReference<Thread> operation) throws Exception {
    long end = System.currentTimeMillis() + 60_000;
    while(operation.get() == null && System.currentTimeMillis() < end) {
      Thread.sleep(100);
    }
    operation.get().join(60_000);
    waitForJobsToComplete();
  }
}
