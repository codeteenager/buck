/*
 * Copyright 2018-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java;

import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.NonHashableSourcePathContainer;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolver;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.toolchain.tool.impl.VersionedTool;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.ProcessExecutor.Result;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.types.Either;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Optional;

/** Provides utilities for creating/providing javac instances. */
public class ExternalJavacFactory {
  private final ProcessExecutor processExecutor;

  @VisibleForTesting
  ExternalJavacFactory(ProcessExecutor processExecutor) {
    this.processExecutor = processExecutor;
  }

  public ExternalJavacFactory() {
    this(new DefaultProcessExecutor(Console.createNullConsole()));
  }

  /** Creates an ExternalJavac. */
  public ExternalJavac create(final Either<PathSourcePath, SourcePath> pathToJavac) {
    if (pathToJavac.isRight() && pathToJavac.getRight() instanceof BuildTargetSourcePath) {
      BuildTargetSourcePath buildTargetPath = (BuildTargetSourcePath) pathToJavac.getRight();
      return new ExternalJavac(
          () -> createNonHashingSourcePathJavac(buildTargetPath),
          buildTargetPath.getTarget().toString());
    } else {
      PathSourcePath actualPath =
          pathToJavac.transform(path -> path, path -> (PathSourcePath) path);
      return new ExternalJavac(() -> createVersionedJavac(actualPath), actualPath.toString());
    }
  }

  private Tool createVersionedJavac(PathSourcePath actualPath) {
    ProcessExecutorParams params =
        ProcessExecutorParams.builder()
            .setCommand(ImmutableList.of(actualPath.toString(), "-version"))
            .build();
    Result result;
    try {
      result = processExecutor.launchAndExecute(params);
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
    Optional<String> stderr = result.getStderr();
    String output = stderr.orElse("").trim();
    String version;
    if (Strings.isNullOrEmpty(output)) {
      version = actualPath.toString();
    } else {
      version = JavacVersion.of(output).toString();
    }
    return VersionedTool.of(actualPath, "external_javac", version);
  }

  // TODO(cjhopman): It's unclear why this is using a non-hashing sourcepath.
  private static Tool createNonHashingSourcePathJavac(BuildTargetSourcePath buildTargetPath) {
    return new Tool() {
      @AddToRuleKey
      private final NonHashableSourcePathContainer container =
          new NonHashableSourcePathContainer(buildTargetPath);

      @Override
      public ImmutableList<String> getCommandPrefix(SourcePathResolver resolver) {
        return ImmutableList.of(resolver.getAbsolutePath(container.getSourcePath()).toString());
      }

      @Override
      public ImmutableMap<String, String> getEnvironment(SourcePathResolver resolver) {
        return ImmutableMap.of();
      }
    };
  }
}