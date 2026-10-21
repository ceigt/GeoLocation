package io.github.ceigt.geolocation.xposed.hooks

/** Empty native results need fresh authorization; existing results retain native validation. */
internal inline fun <T : Any> replaceSingleFix(original: T?, mayCreate: () -> Boolean, replace: (T?) -> T?): T? =
    if (original == null && !mayCreate()) null else replace(original)
