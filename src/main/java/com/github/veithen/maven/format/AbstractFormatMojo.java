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

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.StringUtils;

import com.google.googlejavaformat.java.JavaFormatterOptions;

public abstract class AbstractFormatMojo extends AbstractMojo {
    @Component private ToolchainManager toolchainManager;

    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.testSourceDirectory}", required = true)
    private File testSourceDirectory;

    @Parameter(defaultValue = "${project.build.sourceEncoding}", required = true)
    private String sourceEncoding;

    @Parameter private File[] additionalSourceDirectories;

    @Parameter private String[] includes = {"**/*.java"};

    @Parameter private String[] excludes;

    @Parameter(defaultValue = "google")
    private String style;

    @Parameter private String importOrderStyle;

    @Parameter(defaultValue = "true")
    private boolean removeUnusedImports;

    @Parameter(defaultValue = "true")
    private boolean sortImports;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        List<File> dirs = new ArrayList<>();
        dirs.add(sourceDirectory);
        dirs.add(testSourceDirectory);
        if (additionalSourceDirectories != null) {
            dirs.addAll(asList(additionalSourceDirectories));
        }
        List<String> files = new ArrayList<>();
        for (File dir : dirs) {
            if (!dir.exists()) {
                continue;
            }
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(dir);
            scanner.setIncludes(includes);
            scanner.setExcludes(excludes);
            scanner.scan();
            for (String includedFile : scanner.getIncludedFiles()) {
                files.add(new File(dir, includedFile).toString());
            }
        }
        if (files.isEmpty()) {
            return;
        }

        FormatRequest request =
                FormatRequest.newBuilder()
                        .addAllFiles(files)
                        .setEncoding(sourceEncoding)
                        .setCheckOnly(isCheckOnly())
                        .setStyle(
                                JavaFormatterOptions.Style.valueOf(style.toUpperCase()).toString())
                        .setImportOrderStyle(
                                JavaFormatterOptions.Style.valueOf(
                                                (importOrderStyle != null
                                                                ? importOrderStyle
                                                                : style)
                                                        .toUpperCase())
                                        .toString())
                        .setRemoveUnusedImports(removeUnusedImports)
                        .setSortImports(sortImports)
                        .build();
        if (log.isDebugEnabled()) {
            log.debug("Request:\n" + request);
        }

        FormatResponse response;
        if (SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_16)) {
            Toolchain tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
            String jvm;
            if (tc != null) {
                jvm = tc.findTool("java");
            } else {
                jvm =
                        System.getProperty("java.home")
                                + File.separator
                                + "bin"
                                + File.separator
                                + "java";
            }
            List<String> classpath = new ArrayList<>();
            for (URL url : ((URLClassLoader) Main.class.getClassLoader()).getURLs()) {
                try {
                    classpath.add(new File(url.toURI()).toString());
                } catch (URISyntaxException ex) {
                    throw new MojoFailureException("Failed to build classpath", ex);
                }
            }
            List<String> cmdline = new ArrayList<>();
            cmdline.add(jvm);
            cmdline.add("-cp");
            cmdline.add(StringUtils.join(classpath.iterator(), File.pathSeparator));
            for (String module : asList("api", "file", "parser", "tree", "util")) {
                cmdline.add("--add-exports");
                cmdline.add(
                        String.format("jdk.compiler/com.sun.tools.javac.%s=ALL-UNNAMED", module));
            }
            cmdline.add(Main.class.getName());
            if (log.isDebugEnabled()) {
                log.debug("Java command line: " + String.join(" ", cmdline));
            }
            try {
                Process process =
                        Runtime.getRuntime().exec(cmdline.toArray(new String[cmdline.size()]));
                OutputStream out = process.getOutputStream();
                request.writeTo(out);
                out.close();
                response = FormatResponse.parseFrom(process.getInputStream());
            } catch (IOException ex) {
                throw new MojoFailureException("Failed to run formatter", ex);
            }
        } else {
            response = Main.run(request);
        }

        if (log.isDebugEnabled()) {
            log.info("Response:\n" + response);
        }
        if (response.hasError()) {
            log.error(response.getError());
            throw new MojoFailureException("The formatter failed");
        }
        if (response.getNonConformingFilesCount() > 0) {
            processNonConformingFiles(response.getNonConformingFilesList());
        }
    }

    protected abstract boolean isCheckOnly();

    protected abstract void processNonConformingFiles(List<String> files)
            throws MojoExecutionException, MojoFailureException;
}
