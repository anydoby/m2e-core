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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;


/**
 * Gives one build participant for each mojo execution. The participant records its execution id and whether its
 * delta contains <code>src/changed.txt</code>, then runs {@link #action}.
 */
public class StopTestProjectConfigurator extends AbstractProjectConfigurator {

  static final List<String> calls = Collections.synchronizedList(new ArrayList<>());

  /** Runs in each participant, with the execution id and the build monitor. */
  static volatile BiConsumer<String, IProgressMonitor> action = (id, monitor) -> {
  };

  static void reset() {
    calls.clear();
    action = (id, monitor) -> {
    };
  }

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) {
  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    String id = execution.getExecutionId();
    return new AbstractBuildParticipant() {
      @Override
      public Set<IProject> build(int kind, IProgressMonitor monitor) {
        IProject project = getMavenProjectFacade().getProject();
        IResourceDelta delta = getDelta(project);
        boolean changed = delta != null
            && delta.findMember(project.getFile("src/changed.txt").getProjectRelativePath()) != null;
        calls.add(id + (changed ? ":changed" : ""));
        action.accept(id, monitor);
        return null;
      }
    };
  }
}
