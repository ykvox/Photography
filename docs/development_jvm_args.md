# Development JVM args

Recommended launcher/runtime JVM arg for Java 25 + LWJGL development runs:

```text
--enable-native-access=ALL-UNNAMED
```

Photography's Gradle `runClient` and `runServer` development runs add this through Loom run configuration JVM args. This belongs in launcher/runtime configuration only, not in the mod jar manifest.

The dev runs also add this Java 25 warning-cleanup arg:

```text
--sun-misc-unsafe-memory-access=allow
```

It was accepted by the local Java 25 runtime. Remove it if using a Java runtime that does not support the flag.

IntelliJ note: a direct Application run configuration that launches
`net.fabricmc.devlaunchinjector.Main.main()` can bypass Loom's generated
`runClient`/`runServer` JVM args. If IntelliJ still logs LWJGL native-access or
`sun.misc.Unsafe` warnings, add both lines above manually to that run
configuration's VM options.
