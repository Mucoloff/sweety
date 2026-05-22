package dev.sweety.launcher;

/**
 * @deprecated Moved to {@link dev.sweety.launcher.adapter.in.cli.LauncherMain}.
 *             This class is kept as a deprecated entry-point alias for backward compatibility.
 */
@Deprecated
public final class MainLauncher {

    private MainLauncher() {}

    /** @deprecated Use {@link dev.sweety.launcher.adapter.in.cli.LauncherMain#main(String[])} */
    @Deprecated
    public static void main(String[] args) throws Exception {
        dev.sweety.launcher.adapter.in.cli.LauncherMain.main(args);
    }
}
