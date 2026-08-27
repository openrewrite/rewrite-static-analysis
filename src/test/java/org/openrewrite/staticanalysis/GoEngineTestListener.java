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

import org.jspecify.annotations.Nullable;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.openrewrite.golang.rpc.GoRewriteRpc;
import org.openrewrite.golang.rpc.GoRpcSourceExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Configures the shared Go parsing engine before any {@code go()} test starts it. The engine is an
 * out-of-process RPC server, {@code rewrite-go-rpc}. When the Moderne CLI has already produced that
 * binary under {@code $MODERNE_CLI_HOME} (default {@code ~/.moderne/cli}) it is reused; otherwise it is
 * built from the {@code go} sources bundled in the {@code rewrite-go} jar, which vendors its
 * dependencies and so builds offline against a host {@code go} toolchain.
 * <p>
 * When neither a prebuilt binary nor a {@code go} toolchain is available, {@link #isAvailable()}
 * returns {@code false} and {@code go()} tests skip instead of failing.
 */
public class GoEngineTestListener implements LauncherSessionListener {

    private static boolean available;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Path binary = resolveBinary();
        if (binary != null) {
            available = true;
            GoRewriteRpc.setFactory(GoRewriteRpc.builder().goBinaryPath(binary));
        }
    }

    static boolean isAvailable() {
        return available;
    }

    private static @Nullable Path resolveBinary() {
        String executableName = System.getProperty("os.name", "").toLowerCase().contains("win") ?
                "rewrite-go-rpc.exe" : "rewrite-go-rpc";

        String cliHome = System.getenv("MODERNE_CLI_HOME");
        Path cliBinary = (cliHome != null && !cliHome.isEmpty() ? Path.of(cliHome) :
                Path.of(System.getProperty("user.home"), ".moderne", "cli"))
                .resolve("recipes").resolve("go").resolve(executableName);
        if (Files.isRegularFile(cliBinary)) {
            return cliBinary;
        }

        try {
            Path work = Files.createTempDirectory("rewrite-go-rpc");
            Path source = work.resolve("source");
            GoRpcSourceExtractor.extractTo(source);
            Path binary = work.resolve(executableName);

            Process process = new ProcessBuilder("go", "build", "-o", binary.toString(), "./cmd/rpc")
                    .directory(source.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0 && Files.isExecutable(binary)) {
                return binary;
            }
            System.err.println("Could not build rewrite-go-rpc; go() tests will be skipped.\n" + output);
        } catch (Exception e) {
            System.err.println("Could not build rewrite-go-rpc (" + e.getMessage() + "); go() tests will be skipped.");
        }
        return null;
    }
}
