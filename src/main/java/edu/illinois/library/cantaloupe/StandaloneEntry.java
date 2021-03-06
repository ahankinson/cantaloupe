package edu.illinois.library.cantaloupe;

import edu.illinois.library.cantaloupe.config.Configuration;
import edu.illinois.library.cantaloupe.config.ConfigurationFactory;
import edu.illinois.library.cantaloupe.config.ConfigurationProvider;
import edu.illinois.library.cantaloupe.config.FileConfiguration;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * <p>Serves as the main application class in a standalone context.</p>
 *
 * <p>This class will be unavailable in a Servlet container, so it should not
 * be referred to externally. It should also have as few non-JDK dependencies
 * as possible. When the non-JDK dependencies are changed, the part of the POM
 * that controls how the application is packaged will need to be updated as
 * well.</p>
 */
public class StandaloneEntry {

    /**
     * <p>When provided (no value required), a list of available fonts will be
     * printed to stdout.</p>
     *
     * <p>The main reason this is a VM option and not a command-line argument
     * is that, due to the way the application is packaged, this class needs to
     * have as few dependencies as possible. All of the other would-be
     * arguments are VM options too, so let's preserve uniformity.</p>
     */
    static final String LIST_FONTS_VM_ARGUMENT = "cantaloupe.list_fonts";

    /**
     * When set to "true", calls to {@link System#exit} will be disabled,
     * in order to test output-to-console-followed-by-exit.
     */
    static final String TEST_VM_ARGUMENT = "cantaloupe.test";

    private static ApplicationServer appServer;

    static {
        // Suppress a Dock icon in macOS.
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Calls {@link System#exit(int)} unless {@link #isTesting()} returns
     * {@literal true}.
     *
     * @param status Process return status.
     */
    static void exitUnlessTesting(int status) {
        if (!isTesting()) {
            System.exit(status);
        }
    }

    /**
     * @return Whether the value of the {@link #TEST_VM_ARGUMENT} VM option is
     *         {@literal true}.
     */
    private static boolean isTesting() {
        return "true".equals(System.getProperty(TEST_VM_ARGUMENT));
    }

    /**
     * <p>Checks the configuration VM option and starts the embedded web
     * container. The following configuration options are available:</p>
     *
     * <dl>
     *     <dt><code>-Dcantaloupe.config</code></dt>
     *     <dd>Use the configuration file at the corresponding pathname.
     *     Required.</dd>
     *     <dt><code>-Dcantaloupe.test</code></dt>
     *     <dd>If set to <code>true</code>, calls to {@link System#exit(int)}
     *     will be disabled. Should only be supplied when testing.</dd>
     * </dl>
     *
     * @param args       Ignored.
     * @throws Exception if there is a problem starting the web server.
     */
    public static void main(String... args) throws Exception {
        final Configuration config = Configuration.getInstance();
        if (config == null) {
            printUsage();
            exitUnlessTesting(-1);
        } else {
            File configFile = getConfigFile();
            if (configFile == null) {
                printUsage();
                exitUnlessTesting(-1);
            } else if (!configFile.exists()) {
                System.out.println("Does not exist: " + configFile);
                printUsage();
                exitUnlessTesting(-1);
            } else if (!configFile.isFile()) {
                System.out.println("Not a file: " + configFile);
                printUsage();
                exitUnlessTesting(-1);
            } else if (!configFile.canRead()) {
                System.out.println("Not readable: " + configFile);
                printUsage();
                exitUnlessTesting(-1);
            }
        }
        getAppServer().start();
    }

    private static File getConfigFile() {
        File configFile = null;
        final Configuration config = Configuration.getInstance();
        final ConfigurationProvider provider = (ConfigurationProvider) config;
        final List<Configuration> wrappedConfigs = provider.getWrappedConfigurations();
        if (wrappedConfigs.size() > 1 &&
                wrappedConfigs.get(1) instanceof FileConfiguration) {
            configFile = ((FileConfiguration) wrappedConfigs.get(1)).getFile();
        }
        return configFile;
    }

    static File getWarFile() {
        ProtectionDomain protectionDomain =
                ApplicationServer.class.getProtectionDomain();
        URL location = protectionDomain.getCodeSource().getLocation();
        return new File(location.getFile());
    }

    /**
     * @return Application web server instance.
     */
    public static synchronized ApplicationServer getAppServer() {
        if (appServer == null) {
            appServer = new ApplicationServer(Configuration.getInstance());
        }
        return appServer;
    }

    /**
     * Prints program usage to {@link System#out}.
     */
    private static void printUsage() {
        System.out.println("\n" + usage());
    }

    /**
     * @return Program usage message.
     */
    static String usage() {
        return "Usage: java <VM options> -jar " + getWarFile().getName() +
                "\n\n" +
                "VM options:\n" +
                "-D" + ConfigurationFactory.CONFIG_VM_ARGUMENT + "=<config>" +
                "           Configuration file (REQUIRED)\n" +
                "-D" + LIST_FONTS_VM_ARGUMENT +
                "                List fonts\n";
    }

}
