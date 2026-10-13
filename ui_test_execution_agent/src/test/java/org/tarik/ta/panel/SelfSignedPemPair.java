/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/** A throw-away self-signed certificate for 127.0.0.1 as PEM strings, made with the JDK's keytool. */
record SelfSignedPemPair(String certificate, String privateKey) {
    private static final String ALIAS = "panel";
    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    // keytool has no PEM export of a private key, so the key is taken from the PKCS12 store it writes
    static SelfSignedPemPair create(@NotNull Path directory) throws Exception {
        var keystore = directory.resolve("panel.p12");
        var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var process = new ProcessBuilder(List.of(keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", keystore.toString(), "-storepass", "changeit", "-keypass", "changeit"))
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: %s".formatted(output));
        }
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keystore)) {
            store.load(input, STORE_PASSWORD);
        }
        var certificate = store.getCertificate(ALIAS).getEncoded();
        var privateKey = store.getKey(ALIAS, STORE_PASSWORD).getEncoded();
        return new SelfSignedPemPair(pem("CERTIFICATE", certificate), pem("PRIVATE KEY", privateKey));
    }

    private static String pem(String type, byte[] der) {
        var base64 = Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(der);
        return "-----BEGIN %s-----\n%s\n-----END %s-----\n".formatted(type, base64, type);
    }
}
