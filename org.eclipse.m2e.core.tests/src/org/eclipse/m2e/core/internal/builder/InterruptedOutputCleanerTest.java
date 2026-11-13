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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class InterruptedOutputCleanerTest {

  private Path root;

  @Before
  public void createRoot() throws Exception {
    root = Files.createTempDirectory("cleaner");
  }

  @After
  public void deleteRoot() throws Exception {
    FileUtils.deleteDirectory(root.toFile());
  }

  @Test
  public void testDeletesOnlyFilesChangedSinceTheStart() throws Exception {
    Path target = Files.createDirectories(root.resolve("target"));
    long start = System.currentTimeMillis();
    Path old = write(target.resolve("classes/Old.class"));
    Files.setLastModifiedTime(old, FileTime.fromMillis(start - 10_000));
    Path halfWritten = write(target.resolve("generated/deep/HalfWritten.java"));
    Files.setLastModifiedTime(halfWritten, FileTime.fromMillis(start + 5));

    assertEquals(1, InterruptedOutputCleaner.deleteFilesChangedSince(target, start));

    assertTrue(Files.exists(old));
    assertFalse(Files.exists(halfWritten));
    assertTrue("folders stay", Files.isDirectory(halfWritten.getParent()));
  }

  @Test
  public void testDoesNotFollowSymbolicLinks() throws Exception {
    Path target = Files.createDirectories(root.resolve("target"));
    Path outside = write(root.resolve("outside.txt"));
    long start = System.currentTimeMillis() - 10_000;
    Files.createSymbolicLink(target.resolve("link.txt"), outside);

    assertEquals(0, InterruptedOutputCleaner.deleteFilesChangedSince(target, start));

    assertTrue(Files.exists(outside));
    assertTrue(Files.isSymbolicLink(target.resolve("link.txt")));
  }

  @Test
  public void testMissingDirectory() {
    Path missing = root.resolve("missing");
    assertEquals(0, InterruptedOutputCleaner.deleteFilesChangedSince(missing, 0));
    assertEquals(0, InterruptedOutputCleaner.deleteFilesChangedSince(null, 0));
  }

  private static Path write(Path file) throws Exception {
    Files.createDirectories(file.getParent());
    return Files.writeString(file, "content");
  }
}
