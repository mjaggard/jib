/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.SkaffoldFilesOutput;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link FilesTaskV2}. */
public class FilesTaskV2Test {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject multiTestProject = new TestProject("multi-service");

  /**
   * Verifies that the files task succeeded and returns the list of paths it prints out.
   *
   * @param project the project to run the task on
   * @param moduleName the name of the sub-project, or {@code null} if no sub-project
   * @return the JSON string printed by the task
   */
  private static String verifyTaskSuccess(TestProject project, @Nullable String moduleName) {
    String taskName =
        ":" + (moduleName == null ? "" : moduleName + ":") + JibPlugin.FILES_TASK_V2_NAME;
    BuildResult buildResult = project.build(taskName, "-q");
    BuildTask jibTask = buildResult.task(taskName);
    Assert.assertNotNull(jibTask);
    Assert.assertEquals(TaskOutcome.SUCCESS, jibTask.getOutcome());
    String output = buildResult.getOutput().trim();
    Assert.assertThat(output, CoreMatchers.startsWith("BEGIN JIB JSON"));
    Assert.assertThat(output, CoreMatchers.endsWith("END JIB JSON"));

    // Return task output with header/footer removed
    return output.replace("BEGIN JIB JSON", "").replace("END JIB JSON", "").trim();
  }

  /**
   * Asserts that two lists contain the same paths. Required to avoid Mac's /var/ vs. /private/var/
   * symlink issue.
   *
   * @param expected the expected list of paths
   * @param actual the actual list of paths
   * @throws IOException if checking if two files are the same fails
   */
  private static void assertPathListsAreEqual(List<Path> expected, List<String> actual)
      throws IOException {
    Assert.assertEquals(expected.size(), actual.size());
    for (int index = 0; index < expected.size(); index++) {
      Assert.assertEquals(
          expected.get(index).toRealPath(), Paths.get(actual.get(index)).toRealPath());
    }
  }

  @Test
  public void testFilesTask_singleProject() throws IOException {
    Path projectRoot = simpleTestProject.getProjectRoot();
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(simpleTestProject, null));
    assertPathListsAreEqual(
        ImmutableList.of(projectRoot.resolve("build.gradle")), result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("src/main/resources"),
            projectRoot.resolve("src/main/java"),
            projectRoot.resolve("src/main/custom-extra-dir")),
        result.getInputs());
    Assert.assertEquals(result.getIgnore().size(), 0);
  }

  @Test
  public void testFilesTask_multiProjectSimpleService() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path simpleServiceRoot = projectRoot.resolve("simple-service");
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(multiTestProject, "simple-service"));
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            simpleServiceRoot.resolve("build.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(simpleServiceRoot.resolve("src/main/java")), result.getInputs());
    Assert.assertEquals(result.getIgnore().size(), 0);
  }

  @Test
  public void testFilesTask_multiProjectComplexService() throws IOException {
    Path projectRoot = multiTestProject.getProjectRoot();
    Path complexServiceRoot = projectRoot.resolve("complex-service");
    Path libRoot = projectRoot.resolve("lib");
    SkaffoldFilesOutput result =
        new SkaffoldFilesOutput(verifyTaskSuccess(multiTestProject, "complex-service"));
    assertPathListsAreEqual(
        ImmutableList.of(
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("settings.gradle"),
            projectRoot.resolve("gradle.properties"),
            complexServiceRoot.resolve("build.gradle"),
            libRoot.resolve("build.gradle")),
        result.getBuild());
    assertPathListsAreEqual(
        ImmutableList.of(
            complexServiceRoot.resolve("src/main/extra-resources-1"),
            complexServiceRoot.resolve("src/main/extra-resources-2"),
            complexServiceRoot.resolve("src/main/java"),
            complexServiceRoot.resolve("src/main/other-jib"),
            libRoot.resolve("src/main/resources"),
            libRoot.resolve("src/main/java")),
        result.getInputs().subList(0, 6));
    // guava jar is in a temporary-looking directory, so we need to do some extra processing to
    // match this
    Assert.assertThat(
        result.getInputs().get(result.getInputs().size() - 1),
        CoreMatchers.endsWith("guava-HEAD-jre-SNAPSHOT.jar"));
    Assert.assertEquals(7, result.getInputs().size());
    Assert.assertEquals(result.getIgnore().size(), 0);
  }
}
