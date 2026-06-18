/**
 * Backend-independent facade for friend, Presence, hosting, and P2P join features.
 *
 * <p>Game-facing code should enter through {@link cool.muyucloud.netherlink.link.LinkServices},
 * provide environment capabilities through the {@code hook} package, and consume only models
 * from the {@code model} package. Authentication tokens and wire-protocol details belong to a
 * concrete {@link cool.muyucloud.netherlink.link.LinkService} implementation.</p>
 */
package cool.muyucloud.netherlink.link;
