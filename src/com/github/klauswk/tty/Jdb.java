package com.github.klauswk.tty;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import com.sun.jdi.connect.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.*;

import java.nio.file.Paths;
import java.nio.file.Path;

import org.jline.reader.*;
import org.jline.reader.impl.completer.*;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Jdb {
  private static final String progname = "kjb";

  public static void main(String argv[]) throws MissingResourceException {
    String cmdLine = "";
    String javaArgs = "";
    int traceFlags = VirtualMachine.TRACE_NONE;
    boolean trackVthreads = false;
    boolean launchImmediately = false;
    String connectSpec = null;

    MessageOutput.textResources = ResourceBundle.getBundle
      ("com.github.klauswk.tty.TTYResources",
       Locale.getDefault());

    for (int i = 0; i < argv.length; i++) {
      String token = argv[i];
      if (token.equals("-dbgtrace")) {
        if ((i == argv.length - 1) ||
            ! Character.isDigit(argv[i+1].charAt(0))) {
          traceFlags = VirtualMachine.TRACE_ALL;
        } else {
          String flagStr = "";
          try {
            flagStr = argv[++i];
            traceFlags = Integer.decode(flagStr).intValue();
          } catch (NumberFormatException nfe) {
            usageError("dbgtrace flag value must be an integer:",
                flagStr);
            return;
          }
        }
      } else if (token.equals("-trackallthreads")) {
        trackVthreads = true;
      } else if (token.equals("-X")) {
        usageError("Use java minus X to see");
        return;
      } else if (
          // Standard VM options passed on
          token.startsWith("-verbose") ||                  // -verbose[:...]
          token.startsWith("-D") ||
          // -classpath handled below
          // NonStandard options passed on
          token.startsWith("-X") ||
          // Old-style options (These should remain in place as long as
          //  the standard VM accepts them)
          token.equals("-verify") ||
          token.equals("-verifyremote") ||
          token.equals("-verbosegc")) {

        javaArgs = addArgument(javaArgs, token);
      } else if (token.startsWith("-R")) {
        javaArgs = addArgument(javaArgs, token.substring(2));
      } else if (token.equals("-tclassic")) {
        usageError("Classic VM no longer supported.");
        return;
      } else if (token.equals("-tclient")) {
        // -client must be the first one
        javaArgs = "-client " + javaArgs;
      } else if (token.equals("-tserver")) {
        // -server must be the first one
        javaArgs = "-server " + javaArgs;
      } else if (token.equals("-sourcepath") || token.equals("-sp")) {
        if (i == (argv.length - 1)) {
          usageError("No sourcepath specified.");
          return;
        }
        Env.setSourcePath(argv[++i]);
      } else if (token.equals("-classpath") || token.equals("-cp")) {
        if (i == (argv.length - 1)) {
          usageError("No classpath specified.");
          return;
        }
        javaArgs = addArgument(javaArgs, token);
        javaArgs = addArgument(javaArgs, argv[++i]);
      } else if (token.equals("-attach")) {
        if (connectSpec != null) {
          usageError("cannot redefine existing connection", token);
          return;
        }
        if (i == (argv.length - 1)) {
          usageError("No attach address specified.");
          return;
        }
        String address = argv[++i];

        /*
         * -attach is shorthand for one of the reference implementation's
         * attaching connectors. Use the shared memory attach if it's
         * available; otherwise, use sockets. Build a connect
         * specification string based on this decision.
         */
        if (supportsSharedMemory()) {
          connectSpec = "com.sun.jdi.SharedMemoryAttach:name=" +
            address;
        } else {
          String suboptions = addressToSocketArgs(address);
          connectSpec = "com.sun.jdi.SocketAttach:" + suboptions;
        }
      } else if (token.equals("-listen") || token.equals("-listenany")) {
        if (connectSpec != null) {
          usageError("cannot redefine existing connection", token);
          return;
        }
        String address = null;
        if (token.equals("-listen")) {
          if (i == (argv.length - 1)) {
            usageError("No attach address specified.");
            return;
          }
          address = argv[++i];
        }

        /*
         * -listen[any] is shorthand for one of the reference implementation's
         * listening connectors. Use the shared memory listen if it's
         * available; otherwise, use sockets. Build a connect
         * specification string based on this decision.
         */
        if (supportsSharedMemory()) {
          connectSpec = "com.sun.jdi.SharedMemoryListen:";
          if (address != null) {
            connectSpec += ("name=" + address);
          }
        } else {
          connectSpec = "com.sun.jdi.SocketListen:";
          if (address != null) {
            connectSpec += addressToSocketArgs(address);
          }
        }
      } else if (token.equals("-launch")) {
        launchImmediately = true;
      } else if (token.equals("-listconnectors")) {
        Commands evaluator = new Commands();
        evaluator.commandConnectors(Bootstrap.virtualMachineManager());
        return;
      } else if (token.equals("-connect")) {
        /*
         * -connect allows the user to pick the connector
         * used in bringing up the target VM. This allows
         * use of connectors other than those in the reference
         * implementation.
         */
        if (connectSpec != null) {
          usageError("cannot redefine existing connection", token);
          return;
        }
        if (i == (argv.length - 1)) {
          usageError("No connect specification.");
          return;
        }
        connectSpec = argv[++i];
      } else if (token.equals("-?") ||
          token.equals("-h") ||
          token.equals("--help") ||
          // -help: legacy.
          token.equals("-help")) {
        usage();
      } else if (token.equals("-version")) {
        Commands evaluator = new Commands();
        evaluator.commandVersion(progname,
            Bootstrap.virtualMachineManager());
        System.exit(0);
      } else if (token.startsWith("-")) {
        usageError("invalid option", token);
        return;
      } else {
        // Everything from here is part of the command line
        cmdLine = addArgument("", token);
        for (i++; i < argv.length; i++) {
          cmdLine = addArgument(cmdLine, argv[i]);
        }
        break;
      }
    }

    /*
     * Unless otherwise specified, set the default connect spec.
     */

    /*
     * Here are examples of jdb command lines and how the options
     * are interpreted as arguments to the program being debugged.
     *                     arg1       arg2
     *                     ----       ----
     * jdb hello a b       a          b
     * jdb hello "a b"     a b
     * jdb hello a,b       a,b
     * jdb hello a, b      a,         b
     * jdb hello "a, b"    a, b
     * jdb -connect "com.sun.jdi.CommandLineLaunch:main=hello  a,b"   illegal
     * jdb -connect  com.sun.jdi.CommandLineLaunch:main=hello "a,b"   illegal
     * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a,b"'  arg1 = a,b
     * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a b"'  arg1 = a b
     * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello  a b'   arg1 = a  arg2 = b
     * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a," b' arg1 = a, arg2 = b
     */
    if (connectSpec == null) {
      connectSpec = "com.sun.jdi.CommandLineLaunch:";
    } else if (!connectSpec.endsWith(",") && !connectSpec.endsWith(":")) {
      connectSpec += ","; // (Bug ID 4285874)
    }

    cmdLine = cmdLine.trim();
    javaArgs = javaArgs.trim();
    Path sourcePath = Path.of("").toAbsolutePath();

    if (cmdLine.endsWith(".jar")) {
      javaArgs = addArgument(javaArgs, "-cp");
      javaArgs = addArgument(javaArgs, cmdLine);
      System.out.println("javaArgs: " + javaArgs);
      try { 
        JarFile jarFile = new JarFile(cmdLine);
        sourcePath = Path.of(cmdLine).toAbsolutePath().getParent();
        for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
          JarEntry entry = it.next();
          String realName = entry.getRealName();
          String manifestFile = "MANIFEST.MF";
          if (realName.endsWith(manifestFile)) {
            try (InputStream s = jarFile.getInputStream(entry)) {
              BufferedReader br = new BufferedReader(new InputStreamReader(s));
              String line = null;
              while((line = br.readLine()) != null) {
                if (line.startsWith("Main-Class: ")) {
                  String mainClassJar = line.substring(line.indexOf(":") + 2);
                  System.out.println("MainClass: " + mainClassJar);
                  cmdLine = mainClassJar;
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      } catch (IOException e) {
        System.err.println(e.getMessage());
      }
    } else if (cmdLine.endsWith(".java")) {
      sourcePath = Path.of(cmdLine).toAbsolutePath().getParent();
    }

    if (cmdLine.length() > 0) {
      if (!connectSpec.startsWith("com.sun.jdi.CommandLineLaunch:")) {
        usageError("Cannot specify command line with connector:",
            connectSpec);
        return;
      }
      connectSpec += "main=" + cmdLine + ",";
    }
    if (javaArgs.length() > 0) {
      if (!connectSpec.startsWith("com.sun.jdi.CommandLineLaunch:")) {
        usageError("Cannot specify target vm arguments with connector:",
            connectSpec);
        return;
      }
    }

    if (connectSpec.startsWith("com.sun.jdi.CommandLineLaunch:") && trackVthreads) {
      connectSpec += "includevirtualthreads=y,";
    }

    try {
      Env.init(connectSpec, launchImmediately, traceFlags, trackVthreads, javaArgs, cmdLine, sourcePath);
      var ttyScreen = new TTY();
      ttyScreen.run();
    } catch(Exception e) {
      MessageOutput.printException("Internal exception:", e);
    }
  }
  private static void usage() {
    MessageOutput.println("zz usage text", new Object [] {progname,
        File.pathSeparator});
    System.exit(0);
  }

  static void usageError(String messageKey) {
    MessageOutput.println(messageKey);
    MessageOutput.println();
    usage();
  }

  static void usageError(String messageKey, String argument) {
    MessageOutput.println(messageKey, argument);
    MessageOutput.println();
    usage();
  }

  private static boolean supportsSharedMemory() {
    for (Connector connector :
        Bootstrap.virtualMachineManager().allConnectors()) {
      if (connector.transport() == null) {
        continue;
      }
      if ("dt_shmem".equals(connector.transport().name())) {
        return true;
      }
    }
    return false;
  }

  private static String addArgument(String string, String argument) {
    if (hasWhitespace(argument) || argument.indexOf(',') != -1) {
      // Quotes were stripped out for this argument, add 'em back.
      StringBuilder sb = new StringBuilder(string);
      sb.append('"');
      for (int i = 0; i < argument.length(); i++) {
        char c = argument.charAt(i);
        if (c == '"') {
          sb.append('\\');
        }
        sb.append(c);
      }
      sb.append("\" ");
      return sb.toString();
    } else {
      return string + argument + ' ';
    }
  }

  private static String addressToSocketArgs(String address) {
    int index = address.indexOf(':');
    if (index != -1) {
      String hostString = address.substring(0, index);
      String portString = address.substring(index + 1);
      return "hostname=" + hostString + ",port=" + portString;
    } else {
      return "port=" + address;
    }
  }

  private static boolean hasWhitespace(String string) {
    int length = string.length();
    for (int i = 0; i < length; i++) {
      if (Character.isWhitespace(string.charAt(i))) {
        return true;
      }
    }
    return false;
  }

}
