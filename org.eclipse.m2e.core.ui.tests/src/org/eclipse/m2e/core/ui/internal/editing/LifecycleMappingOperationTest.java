/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.core.ui.internal.editing;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.performOnDOMDocument;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.m2e.core.lifecyclemapping.model.PluginExecutionAction;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.OperationTuple;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.junit.Test;


/**
 * Tests the pom.xml writer for the "run on full builds only" quick fix.
 */
public class LifecycleMappingOperationTest extends AbstractMavenProjectTestCase {

  private static final String RUN_ON_INCREMENTAL_FALSE = "<runOnIncremental>false</runOnIncremental>";

  @Test
  public void testAddsExecuteMappingWithRunOnIncrementalFalse() throws Exception {
    IFile pom = importPom();

    performOnDOMDocument(new OperationTuple(pom, new LifecycleMappingOperation("g", "a", "1.0",
        PluginExecutionAction.execute, new String[] {"run"}, false, Boolean.FALSE)));

    String text = read(pom);
    assertEquals(text, 1, count(text, "<pluginExecution>"));
    assertEquals(text, 1, count(text, RUN_ON_INCREMENTAL_FALSE));
    assertTrue(text, text.contains("<goal>run</goal>"));
    assertFalse("no hint comment when the value is written", text.contains("use <runOnIncremental>"));
  }

  @Test
  public void testChangesExistingExecuteMapping() throws Exception {
    IFile pom = importPom();
    performOnDOMDocument(new OperationTuple(pom,
        new LifecycleMappingOperation("g", "a", "1.0", PluginExecutionAction.execute, new String[] {"run"})));

    performOnDOMDocument(new OperationTuple(pom, new LifecycleMappingOperation("g", "a", "1.0",
        PluginExecutionAction.execute, new String[] {"run"}, false, Boolean.FALSE)));
    performOnDOMDocument(new OperationTuple(pom, new LifecycleMappingOperation("g", "a", "1.0",
        PluginExecutionAction.execute, new String[] {"run"}, false, Boolean.FALSE)));

    String text = read(pom);
    assertEquals(text, 1, count(text, "<pluginExecution>"));
    assertEquals(text, 1, count(text, "<runOnIncremental>"));
    assertEquals(text, 1, count(text, RUN_ON_INCREMENTAL_FALSE));
    assertEquals(text, 1, count(text, "<goal>run</goal>"));
  }

  private IFile importPom() throws Exception {
    IProject project = importProject("resources/projects/simplePomOK/pom.xml");
    waitForJobsToComplete();
    return project.getFile("pom.xml");
  }

  private static String read(IFile file) throws Exception {
    try (InputStream in = file.getContents(true)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static int count(String text, String part) {
    int count = 0;
    for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
      count++ ;
    }
    return count;
  }
}
