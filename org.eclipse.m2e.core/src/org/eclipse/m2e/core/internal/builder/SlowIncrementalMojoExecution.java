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

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;

import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.Messages;
import org.eclipse.m2e.core.internal.lifecyclemapping.MojoExecutionProblemInfo;
import org.eclipse.m2e.core.internal.markers.SourceLocation;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;


/**
 * A warning for a mojo execution that runs on each incremental build and takes much time.
 */
public class SlowIncrementalMojoExecution extends MojoExecutionProblemInfo {

  private final boolean mappingInPom;

  /**
   * @param mappingInPom <code>true</code> if a pom.xml maps the mojo execution. A workspace mapping then has no effect.
   */
  public SlowIncrementalMojoExecution(MojoExecutionKey mojoExecutionKey, long averageMillis, int runs,
      boolean mappingInPom, SourceLocation markerLocation) {
    super(NLS.bind(Messages.SlowIncrementalMojoExecution, new Object[] {getName(mojoExecutionKey),
        String.valueOf(averageMillis), String.valueOf(runs)}), IMarker.SEVERITY_WARNING, mojoExecutionKey,
        markerLocation);
    this.mappingInPom = mappingInPom;
  }

  private static String getName(MojoExecutionKey key) {
    return NLS.bind("{0}:{1} ({2})", new Object[] {key.artifactId(), key.goal(), key.executionId()}); //$NON-NLS-1$
  }

  @Override
  public void processMarker(IMarker marker) throws CoreException {
    super.processMarker(marker);
    marker.setAttribute(IMavenConstants.MARKER_ATTR_EDITOR_HINT,
        IMavenConstants.EDITOR_HINT_SLOW_INCREMENTAL_MOJO_EXECUTION);
    marker.setAttribute(IMavenConstants.MARKER_ATTR_MAPPING_IN_POM, mappingInPom);
  }
}
