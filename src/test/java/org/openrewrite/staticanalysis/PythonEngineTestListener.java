/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.openrewrite.python.rpc.PythonRewriteRpc;

import java.nio.file.Path;

/**
 * Configures the shared Python parsing engine before any {@code python()} test starts it. The engine
 * is an out-of-process RPC server backed by the {@code openrewrite} pip package, pinned to the exact
 * version of the resolved {@code rewrite-python} jar.
 * <p>
 * {@code pipPackagesPath} lets the engine install that version into a local directory (via
 * {@code pip install --target}) instead of requiring it on the interpreter, keeping installs out of
 * global site-packages. This is what makes CI work against published snapshots.
 * <p>
 * When testing against a locally-built {@code rewrite-python} (a {@code .dev0} version, which cannot
 * be resolved from PyPI), set {@code REWRITE_PYTHON_PATH=/path/to/rewrite-python/rewrite/.venv/bin/python}
 * to point at the matching editable install.
 */
public class PythonEngineTestListener implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        PythonRewriteRpc.Builder builder = PythonRewriteRpc.builder()
                .pipPackagesPath(Path.of(".rewrite-python"));

        String pythonPath = System.getenv("REWRITE_PYTHON_PATH");
        if (pythonPath != null && !pythonPath.isEmpty()) {
            builder.pythonPath(Path.of(pythonPath));
        }

        PythonRewriteRpc.setFactory(builder);
    }
}
