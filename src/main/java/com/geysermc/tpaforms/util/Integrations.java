package com.geysermc.tpaforms.util;

/**
 * Class-presence probes for every optional integration.
 *
 * <p>All of these plugins used to be hard {@code depend:} entries in plugin.yml, which meant the
 * plugin refused to load at all when one was missing. They are soft now, so every entry point that
 * touches a third-party class has to check first - otherwise the first touch throws
 * {@link NoClassDefFoundError} on a class-loading boundary instead of failing gracefully.
 */
public final class Integrations {

    private Integrations() {
    }

    public static final boolean FLOODGATE_API =
            present("org.geysermc.floodgate.api.FloodgateApi");

    public static final boolean GEYSER_MENU_API =
            present("com.geysermenu.companion.api.GeyserMenuAPI");

    /** EssentialsX's TPA event. NOT provided by the Folia fork "EssentialsC". */
    public static final boolean ESSENTIALS_TPA_EVENT =
            present("net.ess3.api.events.TPARequestEvent");

    public static final boolean SKINSRESTORER_API =
            present("net.skinsrestorer.api.SkinsRestorerProvider");

    private static boolean present(String className) {
        try {
            Class.forName(className, false, Integrations.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
