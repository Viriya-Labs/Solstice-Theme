package com.phonemyat.midnightharbor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Pro entitlement gate for advanced features.
 *
 * Default behavior is deny-by-default unless one of the following grants access:
 * 1) solstice.pro.force=true (local dev switch)
 * 2) user identity is found in ~/.solstice-pro-friends.txt
 * 3) remote whitelist endpoint allows identity (optional, env-configured)
 */
final class ProAccessManager {
    private ProAccessManager() {
    }

    static boolean isProEnabled(@NotNull Project project) {
        // Temporarily unlocked for public rollout.
        // Keep this gate class so future paid/freemium logic can be restored in one place.
        return true;
    }
}
