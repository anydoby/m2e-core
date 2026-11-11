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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;

import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;


/**
 * Keeps the last run times of mojo executions in incremental builds, for each project. The data is in memory only.
 */
final class IncrementalMojoTimes {

  /** Number of runs that are kept for each mojo execution. */
  static final int WINDOW = 5;

  /** A mojo execution is slow only after this number of runs. */
  static final int MIN_RUNS = 3;

  /** A mojo execution is slow if its average run time is this value or more. */
  static final long SLOW_MILLIS = 1000;

  static final IncrementalMojoTimes INSTANCE = new IncrementalMojoTimes();

  /**
   * The average run time of a slow mojo execution.
   *
   * @param averageMillis the average run time
   * @param runs the number of runs in the average
   */
  record SlowMojo(long averageMillis, int runs) {
  }

  private final Map<IProject, Map<MojoExecutionKey, Deque<Long>>> times = new ConcurrentHashMap<>();

  void record(IProject project, MojoExecutionKey key, long millis) {
    Deque<Long> runs = times.computeIfAbsent(project, p -> new ConcurrentHashMap<>()).computeIfAbsent(key,
        k -> new ArrayDeque<>());
    synchronized(runs) {
      runs.addLast(millis);
      while(runs.size() > WINDOW) {
        runs.removeFirst();
      }
    }
  }

  /**
   * @return the average run time if the mojo execution is slow, or <code>null</code>
   */
  SlowMojo getSlowMojo(IProject project, MojoExecutionKey key) {
    Map<MojoExecutionKey, Deque<Long>> projectTimes = times.get(project);
    Deque<Long> runs = projectTimes == null ? null : projectTimes.get(key);
    if(runs == null) {
      return null;
    }
    synchronized(runs) {
      if(runs.size() < MIN_RUNS) {
        return null;
      }
      long average = Math.round(runs.stream().mapToLong(Long::longValue).average().orElse(0));
      return average >= SLOW_MILLIS ? new SlowMojo(average, runs.size()) : null;
    }
  }

  void forget(IProject project, MojoExecutionKey key) {
    Map<MojoExecutionKey, Deque<Long>> projectTimes = times.get(project);
    if(projectTimes != null) {
      projectTimes.remove(key);
    }
  }

  void forget(IProject project) {
    times.remove(project);
  }
}
