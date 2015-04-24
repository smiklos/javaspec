package info.javaspec;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

/**
 * Command-line interface for JavaSpec.  <em>For diagnostic purposes only</em>.
 * <p>
 * See JavaSpecRunner for details on running tests.
 */
public final class JavaSpec {
  private static final PrintStream DEFAULT_CONSOLE = System.out;
  private static PrintStream _console = DEFAULT_CONSOLE;
  
  private static final ExitHandler DEFAULT_SYSTEM = System::exit;
  private static ExitHandler _system = DEFAULT_SYSTEM;
  
  /* Environment */
  
  public static void setEnvironment() {
    setEnvironment(DEFAULT_CONSOLE, DEFAULT_SYSTEM);
  }
  
  public static void setEnvironment(PrintStream console, ExitHandler system) {
    _console = console;
    _system = system;
  }
  
  @FunctionalInterface
  public interface ExitHandler {
    void exit(int code);
  }
  
  /* Command line interface */
  
  public static void main(String... args) {
    if(isHelpCommand(args))
      printUsage(0);
    else if(isVersionCommand(args))
      printVersion();
    else
      printUsage(1);
  }
  
  private static boolean isHelpCommand(String... args) {
    return args.length == 0 || (args.length == 1 && "--help".equals(args[0]));
  }
  
  private static void printUsage(int exitCode) {
    _console.println(String.format("Usage: java %s --version", JavaSpec.class.getName()));
    _console.println("--version: Show the version");
    _system.exit(exitCode);
  }
  
  private static boolean isVersionCommand(String... args) {
    return args.length == 1 && "--version".equals(args[0]);
  }
  
  private static void printVersion() {
    Properties properties = new Properties();
    try {
      InputStream propertiesStream = JavaSpec.class.getResourceAsStream("/javaspec.properties");
      properties.load(propertiesStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    _console.println(properties.getProperty("javaspec.version"));
    _system.exit(0);
  }
}