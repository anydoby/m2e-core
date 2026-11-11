/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.editor.internal.lifecycle;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.performOnDOMDocument;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.util.NLS;

import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.lifecyclemapping.LifecycleMappingFactory;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.LifecycleMappingMetadataSource;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob;
import org.eclipse.m2e.core.ui.internal.editing.LifecycleMappingOperation;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.CompoundOperation;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.Operation;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.OperationTuple;
import org.eclipse.m2e.editor.internal.Messages;


/**
 * Maps a slow mojo execution to the execute action with runOnIncremental=false, in pom.xml or in the workspace
 * lifecycle mapping. The mojo execution then runs on full builds only.
 */
public class SkipOnIncrementalBuildResolution extends AbstractLifecycleMappingResolution {

  private static final Logger LOG = LoggerFactory.getLogger(SkipOnIncrementalBuildResolution.class);

  private final boolean workspace;

  /**
   * @param workspace <code>true</code> to change the workspace lifecycle mapping, <code>false</code> to change pom.xml
   */
  public SkipOnIncrementalBuildResolution(IMarker marker, boolean workspace) {
    super(marker, PluginExecutionAction.execute);
    this.workspace = workspace;
  }

  @Override
  public int getOrder() {
    return workspace ? 60 : 50;
  }

  @Override
  public String getLabel() {
    String goal = getMarker().getAttribute(IMavenConstants.MARKER_ATTR_GOAL, ""); //$NON-NLS-1$
    return NLS.bind(workspace ? Messages.LifecycleMappingProposal_workspaceSkipOnIncremental_label
        : Messages.LifecycleMappingProposal_skipOnIncremental_label, goal);
  }

  @Override
  public boolean canFix(IMarker marker) throws CoreException {
    // other build markers have the same type: compare the hint too
    return super.canFix(marker) && IMavenConstants.EDITOR_HINT_SLOW_INCREMENTAL_MOJO_EXECUTION
        .equals(marker.getAttribute(IMavenConstants.MARKER_ATTR_EDITOR_HINT, null))
        && (!workspace || !marker.getAttribute(IMavenConstants.MARKER_ATTR_MAPPING_IN_POM, false));
  }

  @Override
  protected void fix(IDocument document, List<IMarker> markers, IProgressMonitor monitor) {
    if(workspace) {
      fixWorkspace(markers);
      return;
    }
    try {
      performOnDOMDocument(new OperationTuple(document, createOperation(markers)));
    } catch(IOException | CoreException e) {
      LOG.error("Error changing the lifecycle mapping in pom.xml", e); //$NON-NLS-1$
    }
  }

  @Override
  protected void fix(IResource resource, List<IMarker> markers, IProgressMonitor monitor) {
    if(workspace) {
      fixWorkspace(markers);
      return;
    }
    try {
      performOnDOMDocument(new OperationTuple((IFile) resource, createOperation(markers)));
    } catch(IOException | CoreException e) {
      LOG.error("Error changing the lifecycle mapping in pom.xml", e); //$NON-NLS-1$
    }
  }

  private static Operation createOperation(List<IMarker> markers) {
    return new CompoundOperation(markers.stream().map(m -> {
      String groupId = m.getAttribute(IMavenConstants.MARKER_ATTR_GROUP_ID, ""); //$NON-NLS-1$
      String artifactId = m.getAttribute(IMavenConstants.MARKER_ATTR_ARTIFACT_ID, ""); //$NON-NLS-1$
      String version = m.getAttribute(IMavenConstants.MARKER_ATTR_VERSION, ""); //$NON-NLS-1$
      String goal = m.getAttribute(IMavenConstants.MARKER_ATTR_GOAL, ""); //$NON-NLS-1$
      return new LifecycleMappingOperation(groupId, artifactId, version, PluginExecutionAction.execute,
          new String[] {goal}, false, Boolean.FALSE);
    }).toArray(Operation[]::new));
  }

  private void fixWorkspace(List<IMarker> markers) {
    // reload from disk in case another process changed the mapping file
    LifecycleMappingMetadataSource mapping = LifecycleMappingFactory.getWorkspaceMetadata(true);
    for(IMarker marker : markers) {
      LifecycleMappingFactory.addLifecyclePluginExecution(mapping,
          marker.getAttribute(IMavenConstants.MARKER_ATTR_GROUP_ID, ""), //$NON-NLS-1$
          marker.getAttribute(IMavenConstants.MARKER_ATTR_ARTIFACT_ID, ""), //$NON-NLS-1$
          marker.getAttribute(IMavenConstants.MARKER_ATTR_VERSION, ""), //$NON-NLS-1$
          List.of(marker.getAttribute(IMavenConstants.MARKER_ATTR_GOAL, "")), false); //$NON-NLS-1$
    }
    LifecycleMappingFactory.writeWorkspaceMetadata(mapping);
    // the pom.xml does not change, so update the projects here
    new UpdateMavenProjectJob(getProjects(markers.stream())).schedule();
  }
}
