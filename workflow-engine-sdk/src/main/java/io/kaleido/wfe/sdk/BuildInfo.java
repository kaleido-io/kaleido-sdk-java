// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk;

import java.io.IOException;
import java.util.Properties;

/**
 * Provides the SDK version at runtime, loaded from the
 * {@code version.properties} resource generated during the build.
 */
public final class BuildInfo {
    private BuildInfo() {}

    private static final String VERSION;

    static {
        var props = new Properties();
        try (var is = BuildInfo.class.getResourceAsStream("/META-INF/kaleido-wfe-sdk/version.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ignored) {
        }
        VERSION = props.getProperty("version", "unknown");
    }

    public static String version() {
        return VERSION;
    }
}
