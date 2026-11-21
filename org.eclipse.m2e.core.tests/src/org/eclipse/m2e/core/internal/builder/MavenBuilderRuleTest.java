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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests that the Maven builder asks for the rule of its project, and for the workspace root only when it must refresh
 * the project registry.
 */
public class MavenBuilderRuleTest extends AbstractMavenProjectTestCase {

  private IProject project;

  private final AtomicReference<ISchedulingRule> ruleInBuild = new AtomicReference<>();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    StopTestProjectConfigurator.reset();
    project = importProject("resources/projects/buildStopParticipants/pom.xml");
    waitForJobsToComplete();
    // only the Maven builder, so that the rule of the build is the rule of the Maven builder
    IProjectDescription description = project.getDescription();
    description.setBuildSpec(Arrays.stream(description.getBuildSpec())
        .filter(c -> IMavenConstants.BUILDER_ID.equals(c.getBuilderName())).toArray(ICommand[]::new));
    assertEquals(1, description.getBuildSpec().length);
    project.setDescription(description, monitor);
    project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
    waitForJobsToComplete();
    WorkspaceHelpers.assertNoErrors(project);
    StopTestProjectConfigurator.reset();
    StopTestProjectConfigurator.action = (id, m) -> {
      if("first".equals(id)) {
        ruleInBuild.set(Job.getJobManager().currentRule());
      }
    };
  }

  @After
  public void resetConfigurator() {
    StopTestProjectConfigurator.reset();
  }

  @Test
  public void testSourceChangeBuildsWithProjectRule() throws Exception {
    setContents(project.getFile("src/changed.txt"), "changed " + System.nanoTime());
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());

    assertEquals(List.of("first:changed", "second:changed"), StopTestProjectConfigurator.calls);
    ISchedulingRule rule = ruleInBuild.get();
    assertNotNull("the build must hold a rule", rule);
    assertTrue("the rule must cover the project: " + rule, rule.contains(project));
    assertFalse("the rule must not be the workspace root: " + rule,
        rule.contains(ResourcesPlugin.getWorkspace().getRoot()));
  }

  @Test
  public void testPomChangeBuildsWithWorkspaceRule() throws Exception {
    IFile pom = project.getFile("pom.xml");
    String content = new String(pom.getContents().readAllBytes());
    setContents(pom, content + "\n<!-- " + System.nanoTime() + " -->\n");
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());

    ISchedulingRule rule = ruleInBuild.get();
    assertNotNull("the build must hold a rule", rule);
    assertTrue("a pom change must build with the workspace root: " + rule,
        rule.contains(ResourcesPlugin.getWorkspace().getRoot()));
  }

  private void setContents(IFile file, String text) throws Exception {
    file.setContents(new ByteArrayInputStream(text.getBytes()), IResource.FORCE, monitor);
  }
}
