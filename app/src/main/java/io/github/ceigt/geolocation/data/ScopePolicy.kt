package io.github.ceigt.geolocation.data

/** Packages that need application-process hooks in addition to the system-service hooks. */
internal fun applicationHookTargets(scope: Collection<String>): Set<String> =
    scope.asSequence()
        .filterNot(SYSTEM_HOOK_PACKAGES::contains)
        .filterNot { it == MANAGER_APP_PACKAGE_NAME }
        .filter(String::isNotBlank)
        .toSet()
