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
import org.jboss.jbossas.servermanager.test.common.AsLifecycleDelegate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * StartStopTest
 * 
 * Simple tests to check that the server may be started and stopped, 
 * and further that the ServerController may query as to the state
 * of AS
 *
 * @author <a href="mailto:alr@jboss.org">ALR</a>
 * @version $Revision: $
 */
public class StartStopTest
{

   //----------------------------------------------------------------------------------||
   // Class Members -------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   public static final String SERVER_NAME = AsLifecycleDelegate.SERVER_NAME_DEFAULT;

   /**
    * AS Lifecycle (Start/Stop) Delegate
    */
   private static AsLifecycleDelegate delegate;

   //----------------------------------------------------------------------------------||
   // Tests ---------------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   /**
    * Tests that the server has successfully started, and that
    * the ServerController can properly query its state
    */
   @Test
   public void testStart() throws Throwable
   {
      // Obtain the server
      ServerManager manager = getDelegate().getServerManager();
      Server server = manager.getServer(SERVER_NAME);

      // Ensure we read that it's up
      TestCase.assertTrue("The server has not been started.", ServerController.isServerStarted(server));
   }

   /**
    * Tests a full roundtrip of started > stopped > restarted, 
    * with proper state queries along the way
    * 
    * @throws Throwable
    */
   @Test
   public void testRestart() throws Throwable
   {
      // Obtain the server
      ServerManager manager = getDelegate().getServerManager();
      Server server = manager.getServer(SERVER_NAME);

      // Ensure we read that it's up
      TestCase.assertTrue("The server has not been started.", ServerController.isServerStarted(server));

      // Bring the server down
      ServerController.stopServer(server, manager);

      // Ensure we read it's down
      TestCase.assertTrue("The server should have been shutdown", !ServerController.isServerStarted(server));

      // Restart
      ServerController.startServer(server, manager);

      // Ensure we read that it's up
      TestCase.assertTrue("The server has not been restarted.", ServerController.isServerStarted(server));
   }

   //----------------------------------------------------------------------------------||
   // LifeCycle Methods ---------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   /**
    * Lifecycle Start
    * 
    * Starts JBossAS
    * 
    * @throws Throwable
    */
   @BeforeClass
   public static void beforeClass() throws Throwable
   {
      getDelegate().startJbossAs(SERVER_NAME);
   }

   /**
    * Lifecycle Stop
    * 
    * Stops JBossAS
    * 
    * @throws Throwable
    */
   @AfterClass
   public static void afterClass() throws Throwable
   {
         getDelegate().stopJbossAs(SERVER_NAME);
   }

   //----------------------------------------------------------------------------------||
   // Internal Helper Methods ---------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   private synchronized static AsLifecycleDelegate getDelegate()
   {
      if (delegate == null)
      {
         delegate = new AsLifecycleDelegate();
      }
      return delegate;
   }

}
