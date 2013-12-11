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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
 
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * StartStopShutdownJarTest
 * 
 * Simple tests to check that the server may be started and stopped, 
 * and further that the ServerController may query as to the state
 * of AS; by this tests the legacy shutdown.jar server stop method is
 * used
 *
 * @author <a href="mailto:akostadinov@jboss.org">Aleksandar Kostadinov</a>
 * @version $Revision: $
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
   StartStopTest.class
})public class StartStopShutdownJarTest
{
   public static final String SHUTDOWN_METHOD_PROPERTY = "sm.legacy.shutdown";

   @BeforeClass
   public static void beforeClass()
   {
      System.setProperty(SHUTDOWN_METHOD_PROPERTY, "true");
   }

   @AfterClass
   public static void afterClass()
   {
      System.clearProperty(SHUTDOWN_METHOD_PROPERTY);
   }
}
