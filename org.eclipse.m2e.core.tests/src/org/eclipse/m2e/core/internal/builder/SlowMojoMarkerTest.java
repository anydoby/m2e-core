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
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;
import org.junit.After;
import org.junit.Test;


/**
 * Tests the warning for a mojo execution that runs on each incremental build and is slow.
 */
public class SlowMojoMarkerTest extends AbstractMavenProjectTestCase {

  private IProject project;

  @After
  public void forgetTimes() {
    if(project != null) {
      IncrementalMojoTimes.INSTANCE.forget(project);
    }
  }

  @Test
  public void testSlowMojoGetsWarningUntilItRunsOnFullBuildsOnly() throws Exception {
    project = importProject("resources/projects/slowMojo/pom.xml");
    waitForJobsToComplete();
    // the first build of a builder is a full build; full builds do not count
    project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
    WorkspaceHelpers.assertNoErrors(project);

    for(int i = 0; i < IncrementalMojoTimes.MIN_RUNS - 1; i++ ) {
      buildAfterChange();
    }
    assertEquals("no warning before the minimum number of runs", List.of(), slowMojoMarkers());

    buildAfterChange();
    List<IMarker> markers = slowMojoMarkers();
    assertEquals(1, markers.size());
    IMarker marker = markers.get(0);
    assertEquals(IMarker.SEVERITY_WARNING, marker.getAttribute(IMarker.SEVERITY, -1));
    assertTrue(marker.getAttribute(IMarker.MESSAGE, ""),
        marker.getAttribute(IMarker.MESSAGE, "").contains("maven-antrun-plugin:run (slow)"));
    assertEquals("org.apache.maven.plugins", marker.getAttribute(IMavenConstants.MARKER_ATTR_GROUP_ID, ""));
    assertEquals("maven-antrun-plugin", marker.getAttribute(IMavenConstants.MARKER_ATTR_ARTIFACT_ID, ""));
    assertEquals("run", marker.getAttribute(IMavenConstants.MARKER_ATTR_GOAL, ""));
    assertEquals("slow", marker.getAttribute(IMavenConstants.MARKER_ATTR_EXECUTION_ID, ""));
    assertTrue("the pom.xml maps the execution",
        marker.getAttribute(IMavenConstants.MARKER_ATTR_MAPPING_IN_POM, false));

    // a build without relevant changes keeps the warning
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
    assertEquals(1, slowMojoMarkers().size());

    // what the quick fix writes: the execution runs on full builds only, and the warning goes away
    copyContent(project, "pom_fullBuildOnly.xml", "pom.xml");
    MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(project, monitor);
    waitForJobsToComplete();
    buildAfterChange();
    assertEquals(List.of(), slowMojoMarkers());
  }

  private void buildAfterChange() throws Exception {
    byte[] content = ("changed " + System.nanoTime()).getBytes();
    project.getFile("src/changed.txt").setContents(new ByteArrayInputStream(content), IResource.FORCE, monitor);
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
  }

  private List<IMarker> slowMojoMarkers() throws Exception {
    IMarker[] markers = project.getFile(IMavenConstants.POM_FILE_NAME).findMarkers(IMavenConstants.MARKER_BUILD_ID,
        false, IResource.DEPTH_ZERO);
    return Arrays.stream(markers).filter(m -> IMavenConstants.EDITOR_HINT_SLOW_INCREMENTAL_MOJO_EXECUTION
        .equals(m.getAttribute(IMavenConstants.MARKER_ATTR_EDITOR_HINT, null))).toList();
  }
}
