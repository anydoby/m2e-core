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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Deletes the output of a build participant that was interrupted. An interrupt can stop a participant while it writes
 * a file. Many plugins skip an output file that is newer than its input, so a half-written file could stay. While the
 * build runs, it holds the workspace lock, so no other builder writes to the build directory. A file there that changed
 * after the participant started comes from that participant. The next build makes the deleted files again.
 */
final class InterruptedOutputCleaner {

  private static final Logger log = LoggerFactory.getLogger(InterruptedOutputCleaner.class);

  private InterruptedOutputCleaner() {
  }

  /**
   * Deletes the regular files under the directory that changed at or after the given time. Does not follow symbolic
   * links.
   *
   * @return the number of deleted files
   */
  static int deleteFilesChangedSince(Path directory, long sinceMillis) {
    if(directory == null || !Files.isDirectory(directory)) {
      return 0;
    }
    int[] deleted = {0};
    try {
      Files.walkFileTree(directory, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if(attrs.isRegularFile() && attrs.lastModifiedTime().toMillis() >= sinceMillis) {
            try {
              Files.deleteIfExists(file);
              deleted[0]++ ;
            } catch(IOException e) {
              log.debug("Cannot delete {}", file, e); //$NON-NLS-1$
            }
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch(IOException e) {
      log.debug("Cannot read {}", directory, e); //$NON-NLS-1$
    }
    return deleted[0];
  }
}
