/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.core.internal.lifecyclemapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.LifecycleMappingMetadataSource;
import org.eclipse.m2e.core.internal.lifecyclemapping.model.PluginExecutionMetadata;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.junit.Test;


/**
 * Tests the workspace mapping writer for the "run on full builds only" quick fix.
 */
public class WorkspaceRunOnIncrementalTest {

  @Test
  public void testWritesRunOnIncrementalOnce() {
    LifecycleMappingMetadataSource mapping = new LifecycleMappingMetadataSource();
    LifecycleMappingFactory.addLifecyclePluginExecution(mapping, "g", "a", "1.0", List.of("run"), true);
    LifecycleMappingFactory.addLifecyclePluginExecution(mapping, "g", "a", "1.0", List.of("run"), false);

    assertEquals(1, mapping.getPluginExecutions().size());
    PluginExecutionMetadata execution = mapping.getPluginExecutions().get(0);
    assertEquals(PluginExecutionAction.execute, execution.getAction());
    assertEquals(Set.of("run"), execution.getFilter().getGoals());
    Xpp3Dom configuration = execution.getConfiguration();
    assertEquals(1, configuration.getChildren(LifecycleMappingFactory.ELEMENT_RUN_ON_INCREMENTAL).length);
    assertEquals("false", configuration.getChild(LifecycleMappingFactory.ELEMENT_RUN_ON_INCREMENTAL).getValue());

    // m2e reads the value back: the mojo execution does not run on incremental builds
    MojoExecution mojoExecution = new MojoExecution(new MojoDescriptor());
    assertFalse(LifecycleMappingFactory.createMojoExecutionBuildParicipant(mojoExecution, execution)
        .appliesToBuildKind(IncrementalProjectBuilder.INCREMENTAL_BUILD));
  }
}
