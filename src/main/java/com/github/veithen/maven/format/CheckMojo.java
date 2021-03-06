/*-
 * #%L
 * java-format-maven-plugin
 * %%
 * Copyright (C) 2021 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.maven.format;

import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractFormatMojo {
    @Override
    protected boolean isCheckOnly() {
        return true;
    }

    @Override
    protected void processNonConformingFiles(List<String> files)
            throws MojoExecutionException, MojoFailureException {
        throw new MojoFailureException(
                String.format(
                        "Found %d non-conforming files; run mvn java-format:fix to fix them",
                        files.size()));
    }
}
