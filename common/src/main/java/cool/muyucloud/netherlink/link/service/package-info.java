/**
 * Service-provider interfaces implemented by a Link backend.
 *
 * <p>Methods identify game runtimes by an opaque {@code runtimeKey}. Implementations resolve the
 * corresponding account and world integration capabilities through {@code LinkContextHooks};
 * callers must not pass backend tokens or protocol-specific values through these interfaces.</p>
 */
package cool.muyucloud.netherlink.link.service;
