/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.jbossas.servermanager.test.startstop.unit;

import junit.framework.TestCase;

import org.jboss.jbossas.servermanager.Server;
import org.jboss.jbossas.servermanager.ServerController;
import org.jboss.jbossas.servermanager.ServerManager;
import org.jboss.jbossas.servermanager.ServerShutdownException;
import org.jboss.jbossas.servermanager.test.common.AsLifecycleDelegate;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;

/**
 * FailStartStopTest
 * 
 * Test failure conditions trying to start/stop JBoss AS server.
 *
 * @author <a href="mailto:akostadinov@jboss.org">Aleksandar Kostadinov</a>
 * @version $Revision: $
 */
public class FailStartStopTest
{

   //----------------------------------------------------------------------------------||
   // Class Members -------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   /*
    * Configuration
    */

   private static final String SERVER_NAME = AsLifecycleDelegate.SERVER_NAME_DEFAULT;

   /**
    * The ServerManager for all Servers used in testing
    */
   private static ServerManager serverManager;

   // record original timeout value so we initialize after tests
   private static int initTimeout;

   //----------------------------------------------------------------------------------||
   // Tests ---------------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   /**
    * Tests that process is destroyed on server startup issue.
    */
   @Test
   public void testFailStart() throws Throwable
   {
      // Create test server
      Server server = new IgnoreStopServer();
      server.setName(SERVER_NAME);

      // Add the Server to the Manager
      TimeoutConfigurableMananager manager = (TimeoutConfigurableMananager) getServerManager();
      AsLifecycleDelegate.applyServerDefaults(server, manager);

      boolean timedOut = false;

      manager.setStartupTimeout(-1);

      // Start server
      try
      {
         ServerController.startServer(server, manager);
      }
      catch (IOException e)
      {
         if (e.toString().matches(".*start in time.*"))
         {
            timedOut = true;
         }
         else
         {
            throw e;
         }
      }

      // check server timed out and is destroyed
      TestCase.assertTrue("Server started successfully, but should not", timedOut);

      try
      {
         // exitValue() only returns if process has ended.
         server.getProcess().exitValue();
         // the process has ended, that's fine
      }
      catch (IllegalThreadStateException e)
      {
         TestCase.assertTrue("Server process was not terminated", false);
      }
      catch (NullPointerException npe)
      {
         // process is null, that's fine
      }

      // Ensure we read that it's down
      TestCase.assertTrue("Server is operational, but should not", !ServerController.isServerStarted(server));
   }

   /**
    * Tests server process is destroyed on shutdown failure and
    * thread dump is logged.
    * 
    * @throws Throwable
    */
   @Test
   public void testTimeoutStop() throws Throwable
   {
      boolean stopFailed = false;

      // Create test server
      IgnoreStopServer server = new IgnoreStopServer();
      server.setName(SERVER_NAME);

      // Add the Server to the Manager
      ServerManager manager = getServerManager();
      AsLifecycleDelegate.applyServerDefaults(server, manager);

      // Start server
      ServerController.startServer(server, manager);

      // Ensure we read that it's up
      TestCase.assertTrue("The server has not been restarted.", ServerController.isServerStarted(server));

      // Bring the server down
      try
      {
         ServerController.stopServer(server, manager);
      }
      catch (ServerShutdownException e)
      {
         stopFailed = true;
         TestCase.assertTrue("Issue should have been timeout", e.toString().matches(".*timeout.*"));
      }

      // Ensure we had issues shutting down.
      TestCase.assertTrue("Shutdown should have failed", stopFailed);

      // Ensure we read it's down
      TestCase.assertTrue("The server should have been killed", !ServerController.isServerStarted(server));

      // Check if we have actually created a thread dump
      File td = server.getDumpFile();
      BufferedReader tdReader;
      String tdLine = "";

      try
      {
         tdReader = new BufferedReader(new FileReader(td));
         tdLine = tdReader.readLine();
      }
      catch(Exception e)
      {
         throw new IOException("Can't read server thread dump").initCause(e);
      }
      TestCase.assertTrue("threadDump.log doesn't look like a thread dump", tdLine.startsWith("<b>Total Threads:</b>"));
   }

   /**
    * Preparation
    * 
    * Pre-configures the JBossAS test Configuration 
    * 
    * @throws Throwable
    */
   @BeforeClass
   public static void beforeClass() throws Throwable
   {
      // Create ServerManager
      TimeoutConfigurableMananager serverManager = new TimeoutConfigurableMananager();
      AsLifecycleDelegate.applyServerManagerDefaults(serverManager);

      setServerManager(serverManager);
      initTimeout = serverManager.getStartupTimeout();
   }

   /**
    * Clean up
    * 
    * Stops the JBossAS Configuration in case of troubles
    * 
    * @throws Throwable
    */
   @After
   public void after() throws Throwable
   {
      // Obtain the server
      TimeoutConfigurableMananager manager = (TimeoutConfigurableMananager) getServerManager();
      Server server = manager.getServer(SERVER_NAME);

      // If started/running
      if (ServerController.isServerStarted(server))
      {
         // Stop
         ServerController.stopServer(server, manager);
      }

      manager.delServer(SERVER_NAME);
      manager.setStartupTimeout(initTimeout);
   }

   //----------------------------------------------------------------------------------||
   // Internal Helper Methods ---------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   private static ServerManager getServerManager()
   {
      return serverManager;
   }

   private static void setServerManager(ServerManager serverManager)
   {
      FailStartStopTest.serverManager = serverManager;
   }

   /**
    * IgnoreStopServer
    * 
    * Server that ignores shutdown.
    *
    * @author <a href="mailto:akostadinov@jboss.org">Aleksandar Kostadinov</a>
    * @version $Revision: $
    */
   private class IgnoreStopServer extends Server
   {
      @Override
      public void doShutdown() {
         // ignore
      }
   }   /**

    * TimeoutConfigurableMananager
    * 
    * ServerManager that can have timeout set.
    *
    * @author <a href="mailto:akostadinov@jboss.org">Aleksandar Kostadinov</a>
    * @version $Revision: $
    */
   private static class TimeoutConfigurableMananager extends ServerManager
   {
      private int timeout = super.getStartupTimeout();

      @Override
      public int getStartupTimeout() {
         return timeout;
      }
      public void setStartupTimeout(int timeout) {
         this.timeout = timeout;
      }
   }

}

