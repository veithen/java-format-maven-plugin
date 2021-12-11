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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.ImportOrderer;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import com.google.googlejavaformat.java.RemoveUnusedImports;

public class Main {
    public static FormatResponse run(FormatRequest request) {
        try {
            FormatResponse.Builder responseBuilder = FormatResponse.newBuilder();
            Formatter formatter =
                    new Formatter(
                            JavaFormatterOptions.builder()
                                    .style(JavaFormatterOptions.Style.valueOf(request.getStyle()))
                                    .build());
            Charset cs = Charset.forName(request.getEncoding());
            for (String file : request.getFilesList()) {
                Path path = Path.of(file);
                String original = Files.readString(path, cs);
                String formatted = formatter.formatSource(original);
                if (request.getRemoveUnusedImports()) {
                    formatted = RemoveUnusedImports.removeUnusedImports(formatted);
                }
                if (request.getSortImports()) {
                    formatted =
                            ImportOrderer.reorderImports(
                                    formatted,
                                    JavaFormatterOptions.Style.valueOf(
                                            request.getImportOrderStyle()));
                }
                if (formatted.equals(original)) {
                    continue;
                }
                responseBuilder.addNonConformingFiles(file);
                if (!request.getCheckOnly()) {
                    Files.writeString(path, formatted, cs);
                }
            }
            return responseBuilder.build();
        } catch (Throwable ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, false);
            ex.printStackTrace(new PrintWriter(pw));
            pw.flush();
            return FormatResponse.newBuilder().setError(sw.toString()).build();
        }
    }

    public static void main(String[] args) throws IOException {
        run(FormatRequest.parseFrom(System.in)).writeTo(System.out);
        System.out.close();
    }
}
