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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.builder.MavenBuilderImpl.BuildResult;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests that {@link MavenBuilderImpl} stops between build participants, and optionally interrupts a running
 * participant, when the build must stop.
 */
public class MavenBuilderImplStopTest extends AbstractMavenProjectTestCase {

  private IProject project;

  private IMavenProjectFacade facade;

  private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    project = importProject("resources/projects/buildStop/pom.xml");
    waitForJobsToComplete();
    facade = MavenPlugin.getMavenProjectRegistry().getProject(project);
  }

  @Test
  public void testRunsAllParticipantsWithoutStopRequest() throws Exception {
    BuildResult result = build(() -> false, false, participant("first", () -> {
    }), participant("second", () -> {
    }));

    assertFalse(result.stopped());
    assertEquals(List.of("first", "second"), calls);
  }

  @Test
  public void testStopsBeforeNextParticipant() throws Exception {
    AtomicBoolean stop = new AtomicBoolean();
    BuildResult result = build(stop::get, false, participant("first", () -> stop.set(true)),
        participant("second", () -> {
        }));

    assertTrue(result.stopped());
    assertEquals(List.of("first"), calls);
  }

  @Test
  public void testDoesNotInterruptRunningParticipantByDefault() throws Exception {
    AtomicBoolean stop = new AtomicBoolean();
    AtomicBoolean interrupted = new AtomicBoolean();
    Path output = targetDirectory().resolve("complete.txt");
    BuildResult result = build(stop::get, false, participant("first", () -> {
      write(output);
      stop.set(true);
      try {
        Thread.sleep(ParticipantInterrupter.GRACE_MILLIS + 1_000);
      } catch(InterruptedException e) {
        interrupted.set(true);
      }
    }), participant("second", () -> {
    }));

    assertFalse("the participant must not get an interrupt", interrupted.get());
    assertTrue(result.stopped());
    assertEquals(List.of("first"), calls);
    assertTrue("a participant that was not interrupted keeps its output", Files.exists(output));
  }

  @Test
  public void testInterruptsRunningParticipantWhenEnabled() throws Exception {
    AtomicBoolean stop = new AtomicBoolean();
    Path older = targetDirectory().resolve("older.txt");
    write(older);
    Files.setLastModifiedTime(older, FileTime.fromMillis(System.currentTimeMillis() - 10_000));
    Path halfWritten = targetDirectory().resolve("generated/half-written.txt");
    long start = System.currentTimeMillis();
    BuildResult result = build(stop::get, true, participant("first", () -> {
      write(halfWritten);
      stop.set(true);
      try {
        Thread.sleep(60_000);
      } catch(InterruptedException e) {
        // a participant that reacts to the interrupt fails with an exception
        throw new IllegalStateException("interrupted", e);
      }
    }), participant("second", () -> {
    }));
    long elapsed = System.currentTimeMillis() - start;

    assertTrue("the build did not stop in time: " + elapsed, elapsed < 20_000);
    assertTrue(result.stopped());
    assertEquals(List.of("first"), calls);
    assertFalse("the interrupt must not stay on the build thread", Thread.currentThread().isInterrupted());
    // an interrupted participant is a stop, not a build error
    IMarker[] markers = project.findMarkers(IMavenConstants.MARKER_BUILD_ID, true, IResource.DEPTH_INFINITE);
    assertEquals(0, markers.length);
    // the output of the interrupted participant is deleted, older output stays
    assertFalse(Files.exists(halfWritten));
    assertTrue(Files.exists(older));
  }

  @Test
  public void testParticipantErrorIsStillReportedWhenInterruptIsEnabled() throws Exception {
    BuildResult result = build(() -> false, true, participant("first", () -> {
      throw new IllegalStateException("participant error");
    }), participant("second", () -> {
    }));

    assertFalse(result.stopped());
    assertEquals(List.of("first", "second"), calls);
    IMarker[] markers = project.findMarkers(IMavenConstants.MARKER_BUILD_ID, true, IResource.DEPTH_INFINITE);
    assertEquals(1, markers.length);
  }

  private Path targetDirectory() {
    return Path.of(facade.getMavenProject().getBuild().getDirectory());
  }

  private static void write(Path file) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, "content");
    } catch(IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private BuildResult build(BooleanSupplier stopRequested, boolean interruptParticipants,
      AbstractBuildParticipant... participants) throws Exception {
    Map<MojoExecutionKey, List<AbstractBuildParticipant>> map = new LinkedHashMap<>();
    int i = 0;
    for(AbstractBuildParticipant participant : participants) {
      map.put(new MojoExecutionKey("org.eclipse.m2e.tests", "test-plugin", "1.0", "goal", "validate", "exec" + i++),
          List.of(participant));
    }
    // no delta: the build runs as a full build
    MavenBuilderImpl builder = new MavenBuilderImpl(p -> null);
    return facade.createExecutionContext()
        .execute((context, m) -> builder.build(context.getSession(), facade, IncrementalProjectBuilder.FULL_BUILD,
            Map.of(), map, stopRequested, interruptParticipants, m), monitor);
  }

  private AbstractBuildParticipant participant(String name, Runnable body) {
    return new AbstractBuildParticipant() {
      @Override
      public Set<IProject> build(int kind, IProgressMonitor monitor) {
        calls.add(name);
        body.run();
        return null;
      }
    };
  }
}
