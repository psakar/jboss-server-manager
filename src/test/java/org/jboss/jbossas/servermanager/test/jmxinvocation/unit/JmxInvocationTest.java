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
package org.jboss.jbossas.servermanager.test.jmxinvocation.unit;

import java.io.File;

import javax.management.ObjectName;

import junit.framework.TestCase;

import org.jboss.jbossas.servermanager.Server;
import org.jboss.jbossas.servermanager.ServerController;
import org.jboss.jbossas.servermanager.ServerManager;
import org.jboss.jbossas.servermanager.test.common.AsLifecycleDelegate;
import org.jboss.jbossas.servermanager.test.jmxinvocation.HelloWorldMBean;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JmxInvocationTest
 * 
 * Tests that generic invocations over the JMX Bus
 * succeed
 *
 * @author <a href="mailto:alr@jboss.org">ALR</a>
 * @version $Revision: $
 */
public class JmxInvocationTest
{
   //----------------------------------------------------------------------------------||
   // Class Members -------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   public static final String SERVER_NAME = AsLifecycleDelegate.SERVER_NAME_DEFAULT;

   public static final String INVOCATION_OBJECT_NAME = "org.jboss.jbossas.servermanager.test.jmxinvocation:service=HelloWorld";

   public static final String INVOCATION_METHOD_NAME = "sayHello";

   /**
    * Name of the system property denoting the base directory..
    * 
    * HACK! This binds the Test to Maven
    */
   private static final String SYSTEM_PROP_NAME_BASEDIR = "basedir";

   private static final String FILENAME_JMXINVOCATION_TEST = "jmxinvocation";

   /**
    * AS Lifecycle (Start/Stop) Delegate
    */
   private static AsLifecycleDelegate delegate;

   //----------------------------------------------------------------------------------||
   // Tests ---------------------------------------------------------------------------||
   //----------------------------------------------------------------------------------||

   /**
    * Deploys a test SAR into the running AS instance and invokes upon it, 
    * testing for expected result
    */
   @Test
   public void testJmxInvocation() throws Exception
   {
      // Get the Server
      Server server = getDelegate().getServerManager().getServer(SERVER_NAME);

      /*
       * Deploy the test SAR into the Server
       */

      // Construct the deployable path name
      //TODO This whole section is Hacky as Maven doesn't let you define
      // an explicit name for your assembly, so we search for the right file by name
      String baseDirName = this.getBaseDirName();
      File buildDir = new File(baseDirName + "/target");
      assert (buildDir != null && buildDir.exists() && buildDir.isDirectory()) : baseDirName
            + " must be a valid directory";
      File[] files = buildDir.listFiles();
      File deployable = null;
      // For each file in the build directory
      for (File file : files)
      {
         // Look for the deployable one we want by name
         if (file.getName().contains(FILENAME_JMXINVOCATION_TEST))
         {
            deployable = file;
            break;
         }
      }
      assert deployable != null : "Deployable file could not be found";

      // Deploy
      server.deploy(deployable);

      /*
       * Invoke upon the Server
       */

      // Construct the ObjectName
      ObjectName name = new ObjectName(INVOCATION_OBJECT_NAME);

      // Invoke
      Object result = server.invoke(name, INVOCATION_METHOD_NAME, new Object[]
      {}, new String[]
      {});

      /*
       * Test the invocation result
       */
      TestCase.assertNotNull(result);
      TestCase.assertEquals(HelloWorldMBean.RETURN_VALUE, result);
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
      // Obtain the server
      ServerManager manager = delegate.getServerManager();
      Server server = manager.getServer(SERVER_NAME);

      // If started/running
      if (ServerController.isServerStarted(server))
      {
         getDelegate().stopJbossAs(SERVER_NAME);
      }
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

   private String getBaseDirName()
   {
      return this.getSystemProperty(SYSTEM_PROP_NAME_BASEDIR);
   }

   private String getSystemProperty(String property)
   {
      String baseDir = System.getProperty(property);
      assert baseDir != null && baseDir.length() > 0 : "System property \"" + property
            + "\" must be specified for this test to complete normally - "
            + "it is populated by Maven automatically in mvn environments";
      return baseDir;
   }

}
