package tel.schich.javacan;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class NativeInterface {

    static class Errno {
        public static final int EAGAIN = 11;
    }

    public static native long resolveInterfaceName(@NonNull String interfaceName);
    public static native int createSocket();
    public static native int bindSocket(int sock, long interfaceId);
    public static native int close(int sock);
    public static native int errno();
    @NonNull
    public static native String errstr(int errno);
    public static native int setBlockingMode(int sock, boolean block);
    public static native int getBlockingMode(int sock);
    public static native int setTimeouts(int sock, long read, long write);
    @Nullable
    public static native CanFrame read(int sock);
    public static native int write(int sock, @NonNull CanFrame frame);
    public static native int setFilter(int sock, int[] id, int[] mask);
    public static native int setLoopback(int sock, boolean enable);
    public static native int getLoopback(int sock);
    public static native int setReceiveOwnMessages(int sock, boolean enable);
    public static native int getReceiveOwnMessages(int sock);
    public static native int setJoinFilters(int sock, boolean enable);
    public static native int getJoinFilters(int sock);
    public static native int setAllowFdFrames(int sock, boolean enable);
    public static native int getAllowFdFrames(int sock);
    public static native int setErrorFilter(int sock, int mask);
    public static native int getErrorFilter(int sock);

    private static boolean initialized = false;

    public synchronized static void initialize() {
        if (!initialized) {
            String libName = "JavaCAN";

            String archSuffix;
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("arm")) {
                archSuffix = "armv7";
            } else if (arch.contains("86") || arch.contains("amd")) {
                if (arch.contains("64")) {
                    archSuffix = "x86_64";
                } else {
                    archSuffix = "x86_32";
                }
            } else if (arch.contains("aarch64")) {
                archSuffix = "aarch64";
            } else {
                archSuffix = arch;
            }

            final String sourceLibPath = "/native/lib" + libName + "-" + archSuffix + ".so";
            try (InputStream libStream = NativeInterface.class.getResourceAsStream(sourceLibPath)) {
                if (libStream == null) {
                    throw new LinkageError("Failed to load the native library: " + sourceLibPath + " not found.");
                }
                final Path tempDirectory = Files.createTempDirectory(libName);
                final Path libPath = tempDirectory.resolve("lib" + libName + ".so");

                Files.copy(libStream, libPath, REPLACE_EXISTING);

                System.load(libPath.toString());
                libPath.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new LinkageError("Unable to load native library!", e);
            }

            initialized = true;
        }
    }

}
