/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.jbossas.servermanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;

/**
 * Starts, stops, and (eventually) reboots server instances.
 *
 * @author <a href="ryan.campbell@jboss.com">Ryan Campbell</a>
 * @version $Revision: 82586 $
 */
public abstract class ServerController
{
   private static final String SHUTDOWN_CLASS = "org.jboss.Shutdown";

   private static final String MAIN = "org.jboss.Main";

   /**
    * Name of the system property denoting the location of endorsed libraries
    */
   private static final String SYS_PROP_JAVA_ENDORSED_DIRS = "java.endorsed.dirs";

   /**
    * Name of the system property denoting whether XB should allow unordered sequences
    */
   private static final String SYS_PROP_XB_UNORDERED = "xb.builder.useUnorderedSequence";

   /**
    * CLI switch denoting a system property will follow
    */
   private static final String SWITCH_SYSPROP = "-D";

   /**
    * Character for '='
    */
   private static final char EQUALS = '=';

   // delay (in ms) to guarantee that a destroyed process cannot respond with true
   // to ServerController.isServerStarted()
   private static final long PROCESS_DESTROY_DELAY = 45 * 1000 ;

   private ServerController()
   {
   }

   /**
    * Start the server and pump its output and error streams.
    *
    * @param server
    * @param manager
    * @throws IOException
    */
   public static void startServer(Server server, ServerManager manager) throws IOException
   {
      if (server.isRunning())
      {
         throw new IllegalArgumentException("The " + server.getName() + " server is already running.");
      }

      if (isServerStarted(server))
      {
         throw new IOException("Found a process already listening on:" + server.getHttpUrl() + " or "+ server.getRmiUrl());
      }

      // make sure these are initialized
      server.setNamingContext(null);
      server.setServerConnection(null);

      String execCmd = getStartCommandLine(server, manager);

      System.out.println("Starting server \"" + server.getName() + "\", with command (start timeout is " + manager.getStartupTimeout() + " seconds ): \n" + execCmd);

      File binDir = new File(manager.getJBossHome(), "/bin");
      final Process process = Runtime.getRuntime().exec(execCmd, null, binDir);

      new Thread(new ConsoleConsumer(process.getInputStream())).start();
      new Thread(new ConsoleConsumer(process.getErrorStream())).start();

      final BufferedReader errStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      final BufferedReader inStream = new BufferedReader(new InputStreamReader(process.getInputStream()));

      final File outFile = server.getOutputLog();
      initalizeLog(outFile);
      final PrintWriter outlog = new PrintWriter(new FileWriter(outFile));
      server.setOutWriter(outlog);

      Thread outPump = new OutputPumper(inStream, outlog);
      outPump.start();

      final File errorFile = server.getErrorLog();
      initalizeLog(errorFile);
      final PrintWriter errorlog = new PrintWriter(new FileWriter(errorFile));
      server.setErrorWriter(errorlog);

      Thread errorPump = new OutputPumper(errStream, errorlog);
      errorPump.start();

      /*
       * TODO: -TME This is a real problem.  If maintain reference
       * to the process, even for a short period of time, willl
       * cause the spawned process' threads to block when this process
       * blocks.  So if uncomment following line, then the ServerTestHarness
       * will block abnormally, thus causing the tests not to run correctly.
       *
       * Is this true for our environment? - rcampbell
       */
      server.setProcess(process);

      try
      {
         waitForServer(server, manager);
      }
      catch (IOException e)
      {
     	 // this affects the value of Server.isStopped()
         server.setProcess(null);
         throw e;
      }

      System.out.println("Server started.") ;
   }

   /**
    * Delete & create log files
    * @param logFile
    * @throws IOException
    */
   private static void initalizeLog(final File logFile) throws IOException
   {
      if (logFile.exists())
      {
         logFile.delete();
      }
      if (!logFile.getParentFile().exists())
      {
         logFile.getParentFile().mkdir();
      }

      try
      {
         logFile.createNewFile();
      }
      catch (final IOException ioe)
      {
         // Because the IOE tells you nothing about the file trying to be created
         throw new RuntimeException("Could not create new file: " + logFile.getAbsolutePath(), ioe);
      }
   }

   /**
    * Create the command line to execute
    *
    * @param server the server
    * @param manager the manager
    * @return the command line
    * @throws IOException for any error
    */
   private static String getStartCommandLine(Server server, ServerManager manager) throws IOException
   {
      String execCmd = manager.getJavaExecutable() + " -cp " + manager.getStartClasspath() + " ";
      execCmd = execCmd + server.getJvmArgs() + server.getSysProperties() + server.getLoggingProperty();
      execCmd = execCmd + " " + getEndorsedDirsProperty(manager);
      execCmd = execCmd + " " + getXbUnorderedSequenceProperty();
      execCmd = execCmd + " " + MAIN + " -c " + server.getConfig() + " -b " + server.getHost() + " -g " + server.getPartition();

      if (manager.getUdpGroup() != null && ! manager.getUdpGroup().equals(""))
      {
         execCmd = execCmd + " -u " + manager.getUdpGroup();
      }
      execCmd = execCmd + " " + server.getArgs();
      return execCmd;
   }

   /**
    * Obtains the full endorsed dirs property, (ie. "-Djava.endorsed.dirs=/path/to/place")
    *
    * @param manager The {@link ServerManager} to consult in obtaining the location
    *       relative to $JBOSS_HOME
    * @return
    */
   private static final String getEndorsedDirsProperty(final ServerManager manager)
   {
         return SWITCH_SYSPROP + SYS_PROP_JAVA_ENDORSED_DIRS + EQUALS +manager.getJavaEndorsedDirs();
   }

   /**
    * Obtains the unordered XB property, (ie. "-Dxb.builder.useUnorderedSequence=true")
    * @return
    */
   private static final String getXbUnorderedSequenceProperty()
   {
      return SWITCH_SYSPROP + SYS_PROP_XB_UNORDERED + EQUALS + "true";
   }

   /**
    * Get the server shutdown command line.
    *
    * @param server the server
    * @param manager the manager
    * @return the shutdown command
    * @throws IOException for any error
    */
   private static String getStopCommandLine(Server server, ServerManager manager) throws IOException
   {
      String strAuth="";
      String username = server.getUsername();
      String password = server.getPassword();
      if ( username != null && password != null )
      {
         strAuth = " -u " + username + " -p " + password;
      }

      String execCmd = manager.getJavaExecutable() + " -cp " + manager.getStopClasspath() + " ";
      execCmd = execCmd + SHUTDOWN_CLASS + strAuth +" --shutdown";
      return execCmd;
   }

   /**
    * Shutdown server with shutdown.jar
    *
    * @param server the server
    * @param manager the manager
    * @return the command output
    * @throws IOException for any error
    * @throws InterruptedException if interrupted while waiting for shutdown.jar
    */
   private static boolean stopServerCli(Server server, ServerManager manager, Writer log) throws IOException, InterruptedException
   {
      String shutdownCmd = getStopCommandLine(server, manager);
      System.out.println("Shutting down server: " + shutdownCmd);

      StringTokenizer cmdArrayTokenizer = new StringTokenizer(shutdownCmd);
      String[] cmdArray = new String[cmdArrayTokenizer.countTokens()];
      for (int i=0; i<cmdArray.length; i++)
      {
         cmdArray[i]=cmdArrayTokenizer.nextToken();
      }

      ProcessBuilder builder = new ProcessBuilder(cmdArray);
      builder.redirectErrorStream(true);
      Process proc = builder.start();

      try
      {
         proc.getOutputStream().close();
         BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
         PrintWriter output = new PrintWriter(log);

         OutputPumper pumper = new OutputPumper(stdout, output);
         pumper.start();

         // Wait 20.5 seconds for shutdown.jar to complete
         pumper.join(20000);
         Thread.sleep(500);

         if (proc.exitValue() != 0) {
            return false;
         }
      }
      catch (IllegalThreadStateException itse)
      {
         return false;
      }
      finally
      {
         proc.destroy();
         closeAllStreams(proc);
      }

      return true;
   }

   /**
    * Wait until the jboss instance is full initialized
    * @param server
    * @param manager
    * @throws IOException
    */
   private static void waitForServer(Server server, ServerManager manager) throws IOException
   {

      int tries = 0;
      while (tries++ < manager.getStartupTimeout())
      {
         if (!server.isRunning())
         {
         	// save output and error streams before raising exception (and terminating ant task)
         	closeAllStreams(server.getProcess()) ;
         	server.getErrorWriter().close() ;
         	server.getOutWriter().close() ;

            throw new IOException("Server failed to start; see logs. exit code: " + server.getProcess().exitValue());
         }

         try
         {
            Thread.sleep(1000);
         }
         catch (InterruptedException e)
         {
         }
         if (isServerStarted(server))
         {
            return;
         }
      }

      Process process = server.getProcess();

  	  // save output and error streams before raising exception (and terminating ant task)
  	  closeAllStreams(server.getProcess()) ;
  	  server.getErrorWriter().close() ;
  	  server.getOutWriter().close() ;

      System.err.println("Failed to start server \"" + server.getName()
            + "\" before timeout. Destroying the process.");
      process.destroy();

      throw new IOException("Server failed to start in time; see logs.");

   }

   /**
    * Check if the server is fully intialized by trying to
    * open a connection to tomcat.
    *
    * @param server the server
    * @return whether it is started
    * @throws IOException for any error
    */
   public static boolean isServerStarted(Server server) throws IOException
   {
      URL url = server.getHttpUrl();
      if (server.hasWebServer())
      {
         try
         {
            URLConnection conn = url.openConnection();
            if (conn instanceof HttpURLConnection)
            {
               HttpURLConnection http = (HttpURLConnection) conn;
               int responseCode = http.getResponseCode();

               if (responseCode > 0 && responseCode < 400)
               {
                  return true;
               }
            }
         }
         catch (IOException e)
         {
            return false;
         }
         return false;
      }
      else
      {
         //see if the rmi port is active
         Socket socket = null;
         try
         {
            socket = new Socket(server.getHost(), server.getRmiPort().intValue());
            return true;
         }
         catch (IOException e)
         {
            return false;
         }
         finally
         {
            if (socket != null)
            {
               socket.close();
            }
         }
      }
   }

   /**
    * Stop the server.
    * Get thread dump and Process.destroy() the server
    * if it fails to shutdown.
    *
    * @param server
    * @param manager
    * @throws IOException
    */
   public static void stopServer(Server server, ServerManager manager) throws IOException
   {
      boolean useShutdownJar = Boolean.getBoolean("sm.legacy.shutdown");
      StringWriter shutdownJarOutput = null;

      boolean cleanShutdown = true;
      Throwable shutdownException = null;

      if (!server.isRunning())
      {
         //throw new IllegalArgumentException("The " + server.getName() + " is not running; it cannot be stopped.");
         // JBASM-33
        System.err.println("The server " + server.getName() + " is not running; it cannot be stopped.");
      }
	else
      {
      	System.out.println("Shutting down server: " + server.getName());
      }

      /** Catch everything as we want the server killed unconditionally **/
      try
      {
         if (useShutdownJar)
         {
            shutdownJarOutput = new StringWriter(512);
            cleanShutdown = stopServerCli(server, manager, shutdownJarOutput);
         }
         else
         {
            server.doShutdown();
         }
      }
      catch (Throwable e)
      {
         shutdownException = e;
         cleanShutdown = false;
      }

      Process process = server.getProcess();
      if (cleanShutdown && !waitOnShutdown(server, manager))
      {
         cleanShutdown = false;
      }

      if (!cleanShutdown)
      {
         // try to provide some debug info
         try
         {
            if (useShutdownJar)
               System.err.println(shutdownJarOutput.toString());
            else
               writeServerDump(server);
         }
         catch (Throwable e)
         {
            e.printStackTrace();
         }

         System.err.println("Failed to shutdown server \"" + server.getName()
                 + "\"" + (shutdownException == null ? " before timeout." : ".")
                    + " Destroying the process.");

         // destroy process and print an error messsage
         process.destroy();

         // although the process has been destroyed, we need to wait for it to shutdown
         try {
       	  Thread.sleep(PROCESS_DESTROY_DELAY) ;
         }
         catch(InterruptedException e) {
         }
      }

      closeAllStreams(process);
      server.getErrorWriter().close();
      server.getOutWriter().close();

  	  // this affects the value of Server.isStopped()
      server.setProcess(null);

      if (!cleanShutdown)
      {
         throw (ServerShutdownException) new ServerShutdownException(
            "Failed to shutdown server"
            + (shutdownException == null ? " before timeout." : ".")
            + "Process was destroyed."
            ).initCause(shutdownException);
      }

      System.out.println("Server stopped.") ;
   }

   /**
    * Dump Server trace to file
    * @param server
    * @throws IOException on faled dump file write
    */
   private static void writeServerDump(final Server server) throws IOException
   {
         String threadDump = null;
         Exception dumpException = null;
         try
         {
            threadDump = server.listThreadDump();
         }
         catch (Exception e)
         {
            dumpException = e;
         }
         if ( threadDump == null )
         {
            threadDump = "Unable to get server thread dump: ";
            if ( dumpException == null )
            {
               dumpException = (Exception) new RuntimeException("threadDump and dumpException null - something broken").fillInStackTrace();

            }
            StringWriter dumpExceptionWriter = new StringWriter(512);
            dumpException.printStackTrace(new PrintWriter(dumpExceptionWriter));
            threadDump = threadDump + dumpExceptionWriter.toString();
         }
         File dumpFile = server.getDumpFile();
         System.out.println("Writing server thread dump to "
            + dumpFile.getAbsolutePath());

         try
         {
            FileWriter dumpFW = new FileWriter(dumpFile);
            dumpFW.write(threadDump);
            dumpFW.flush();
            dumpFW.close();
         }
         catch (Exception e)
         {
            System.err.println("Cannot write to "
               + dumpFile.getAbsolutePath());
            e.printStackTrace();
         }
   }

   /**
    * Wait for the server to shutdown.
    * @param server
    * @param manager
    * @return true if server process ends before timeout
    */
   private static boolean waitOnShutdown(Server server, ServerManager manager)
   {
      int shutdownTimeout = manager.getShutdownTimeout();
      System.out.println("shutdownTimeout will be="+shutdownTimeout);
      for (int tries = 0; tries < shutdownTimeout; tries++)
      {
         try
         {
            if (!server.isRunning())
            {
               return true;
            }
            Thread.sleep(1000);
         }
         catch (InterruptedException e)
         {
         }
      }

      return false;
   }

   /**
    * Close the streams of a process.
    *
    * @param process
    */
   private static void closeAllStreams(Process process)
   {
      try
      {
         process.getInputStream().close();
         process.getOutputStream().close();
         process.getErrorStream().close();
      }
      catch (IOException e)
      {
      }
   }

   /**
    * A OutputPumper.  Redirect std err & out to log files.
    *
    * @author <a href="ryan.campbell@jboss.com">Ryan Campbell</a>
    * @version $Revision: 82586 $
    */
   private static class OutputPumper extends Thread
   {
      private final BufferedReader outputReader;

      private final PrintWriter logWriter;

      public OutputPumper(BufferedReader outputReader, PrintWriter logWriter)
      {
         this.outputReader = outputReader;
         this.logWriter = logWriter;
      }

      @Override
      public void run()
      {
         try
         {
            String line = null;
            while ((line = outputReader.readLine()) != null)
            {
               logWriter.println(line);
            }
         }
         catch (IOException e)
         {
         }
      }
   }

   private static class ConsoleConsumer implements Runnable {
     private final InputStream stream;

    ConsoleConsumer(InputStream stream) {
        this.stream = stream;
     }

             @Override
             public void run() {
                 final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                 String line = null;
                 try {
                     while ((line = reader.readLine()) != null) {
                         System.out.println(line);
                     }
                 } catch (IOException e) {

                 }

             }
         }
}
