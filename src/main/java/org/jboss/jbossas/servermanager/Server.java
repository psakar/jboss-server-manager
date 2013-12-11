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

import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;

import org.jnp.interfaces.NamingContext;

import org.jboss.security.SecurityAssociation;
import org.jboss.security.SimplePrincipal;

// import org.jboss.system.server.ServerInfoMBean;
// import org.jboss.system.server.ServerImplMBean;

import org.jboss.logging.Logger;

/**
 * A Server.
 *
 * @author <a href="ryan.campbell@jboss.com">Ryan Campbell</a>
 * @version $Revision: 108801 $
 */
public class Server
{
   private static final Logger log = Logger.getLogger(Server.class);
   
   /** the directory where server config instances live **/
   public static final String JBOSS_SERVER_CONFIG_DIR_NAME = "server";

   /** the hot deployment directory **/
   public static final String JBOSS_SERVER_DEPLOY_DIR_NAME = "deploy";
   
   /** the handle for the server **/
   private String name;

   /** the config to start **/
   private String config;

   /** jmx username **/
   private String username = null;

   /** jmx password **/
   private String password = null;

   /** HA Partition name **/
   private String partition = "DefaultPartition";

   /** the arguments to pass to jboss **/
   private List<Argument> arguments = new ArrayList<Argument>();

   /** the server's process, if running **/
   private Process process;

   /** the arguments for the jvm **/
   private List<Argument> jvmArguments = new ArrayList<Argument>();

   /** system properties for the jvm **/
   private List<Property> sysProperties = new ArrayList<Property>();

   /** the port used to determine if jboss started **/
   private Integer httpPort = new Integer(8080);

   /** where to find the rmi port **/
   private Integer rmiPort = new Integer(1099);

   /** the name or IP address to bind to **/
   private String host = "localhost";

   /** used for global config info **/
   private ServerManager manager;

   /** the output log **/
   private PrintWriter outWriter;

   /** the error log **/
   private PrintWriter errorWriter;

   /** Is there a servlet engine? **/
   private boolean hasWebServer = true;

   /** the Naming Context */
   private Context namingContext;
   
   /** ObjectName of the Main Deployer **/
   public final static String DEPLOYER_NAME = "jboss.system:service=MainDeployer";

   /** the MBean Server Connection */
   private MBeanServerConnection serverConnection;

   /** the URL to use for connection */
   private String serverUrl = null;

   /** the InitialContext factory class name to use */
   private String initialContextFactoryClassName = null;

  /** the log threshold for the server */
   private String logThreshold = null;

   /** Property to set the server log Threshold **/
   public final static String SVR_LOG_PROP = "jboss.server.log.threshold";

   /** Property to set the boot log Threshold **/
   public final static String BOOT_LOG_PROP = "jboss.boot.server.log.level";

   /**
    * Get the name.
    *
    * @return the name.
    */
   public String getName()
   {
      return name;
   }

   /**
    * Set the name.
    *
    * @param name The name to set.
    */
   public void setName(String name)
   {
      this.name = name;
   }

   /**
    * Get username to pass to org.jboss.Shutdown using
    * the -u option.
    *
    * @return the server jmx username
    */
   public String getUsername()
   {
      return username;
   }

   /**
    * Set username to pass to org.jboss.Shutdown using
    * the -u option.
    *
    * @param username the server jmx username
    */
   public void setUsername(String username)
   {
      this.username=username;
   }

   /**
    * Get password to pass to org.jboss.Shutdown using
    * the -p option.
    *
    * @return the server jmx password
    */

   public String getPassword()
   {
      return password;
   }

   /**
    * Set password to pass to org.jboss.Shutdown using
    * the -p option.
    *
    * @param password the server jmx password
    */
   public void setPassword(String password)
   {
      this.password=password;
   }

   /**
    * Get the HA partition name of the Server
    *
    * @return the server's HA partition name
    */
   public String getPartition()
   {
      return partition;
   }

   /**
    * Set the HA partition name of the Server
    * 
    * same as -g in run.sh so set before start-up
    *
    * @param partition the HA partition name to use
    */
   public void setPartition(String partition)
   {
      this.partition=partition;
   }

   /**
    * Set the manager.
    * @param manager
    */
   protected void setManager(ServerManager manager)
   {
      this.manager = manager;
   }

   /**
    * Add an argument.
    *
    * @param arg
    */
   public void addArg(Argument arg)
   {
      arguments.add(arg);
   }

   /**
    * Get the arguments as a string for the command line.
    *
    * @return the arguments as a string
    */
   public String getArgs()
   {
      StringBuffer args = new StringBuffer();
      for (Iterator<Argument> iter = arguments.iterator(); iter.hasNext();)
      {
         Argument argument = iter.next();
         args.append(argument.getValue() + " ");
      }
      return args.toString();
   }

   /**
    * Add a jvm arg.
    *
    * @param arg
    */
   public void addJvmArg(Argument arg)
   {
      jvmArguments.add(arg);
   }

   /**
    * Get the JVM args for the command line.
    *
    * @return the arguments as a string
    */
   public String getJvmArgs()
   {
      StringBuffer args = new StringBuffer();
      for (Iterator<Argument> iter = jvmArguments.iterator(); iter.hasNext();)
      {
         Argument argument = iter.next();
         args.append(argument.getValue() + " ");
      }
      return args.toString();
   }

   /**
    * Add a system property.
    *
    * @param property
    */
   public void addSysProperty(Property property)
   {
      sysProperties.add(property);
   }

   /**
    * Get a single system property.
    *
    * @return a System property 
    */
   public String getSysProperty(String key)
   {
      for (Property property : sysProperties)
            if (key.equals(property.getKey()))
                return property.getValue();

      return null; 
   }

   /**
    * Get the system properties for the command line.
    *
    * @return the properties as a string
    */
   public String getSysProperties()
   {
      StringBuffer args = new StringBuffer();
      for (Iterator<Property> iter = sysProperties.iterator(); iter.hasNext();)
      {
         Property property = iter.next();
         args.append("-D" + property.getKey() + "=" + property.getValue() + " ");
      }
      return args.toString();
   }

   /**
    * Get the logging property to pass to the server
    * JBAS-6630 - default to DEBUG 
    *
    * @return the log threshold property 
    */
   public String getLoggingProperty()
   {
      String level = getLogThreshold();
      return "-D" + SVR_LOG_PROP + "=" + level + " " + "-D" + BOOT_LOG_PROP + "=" + level;
   }

   /**
    * The running process of this server.
    * @param process
    */
   public void setProcess(Process process)
   {
      this.process = process;
   }

   /**
    * Is the server actually running?
    *
    * @return whether the server is running
    */
   public boolean isRunning()
   {
      if (isStopped())
      {
         return false;
      }
      else
      {
         try
         {
            //exitValue() only returns if process has ended.
            process.exitValue();
            return false;
         }
         catch (IllegalThreadStateException e)
         {
            return true;
         }
      }
   }

   /**
    * Has the server been intentionally stopped?
    *
    * @return whether the server is stopped
    */
   public boolean isStopped()
   {
      return process == null;
   }

   /**
    * Get the process.
    *
    * @return the process
    */
   public Process getProcess()
   {
      return process;
   }

   /**
    * Where is the HTTP service listening?
    *
    * @return whether the service is listening
    * @throws MalformedURLException for a malformed url
    */
   public URL getHttpUrl() throws MalformedURLException
   {
      return new URL("http://" + getHostForURL() + ":" + httpPort);
   }

   /**
    * The URl for the RMI listener.
    *
    * @return the rmi url
    */
   public String getRmiUrl()
   {
      return "jnp://" + getHostForURL() + ":" + rmiPort;
   }

   /**
    * Get the config. Defaults to the server name.
    *
    * @return the config.
    */
   public String getConfig()
   {
      if (config != null)
      {
         return config;
      }
      else
      {
         return name;
      }
   }

   /**
    * Set the config.
    *
    * @param config The config to set.
    */
   public void setConfig(String config)
   {
      this.config = config;
   }

   /**
    * Get the host.
    *
    * @return the host.
    */
   public String getHost()
   {
      return host;
   }

   /**
    * Set the host.
    *
    * @param host The host to set.
    */
   public void setHost(String host)
   {
      this.host = host;
   }

   /**
    * Check host for IPv6 literal address and enclose in square brackets if required.
    * Used to embed hostnames in URL string to satisfy RFC 2732.
    * 
    * @return host suitable for embedding in URL string
    */
   private String getHostForURL()
   {
      if (host == null)
    	  return host ;
      
      if (host.indexOf(':') != -1) 
    	  return "[" + host + "]" ;
      else 
    	  return host ;
   }
   
   
   /**
    * Get the httpPort.
    *
    * @return the http port
    */
   public Integer getHttpPort()
   {
      return httpPort;
   }

   /**
    * Set the httpPort.
    *
    * @param httpPort The httpPort to set.
    */
   public void setHttpPort(Integer httpPort)
   {
      this.httpPort = httpPort;
   }

   /**
    * Set the rmiPort.
    *
    * @param rmiPort The rmiPort to set.
    */
   public void setRmiPort(Integer rmiPort)
   {
      this.rmiPort = rmiPort;
   }

   /**
    * Get the rmiPort
    *
    * @return the rmi port
    */
   public Integer getRmiPort()
   {
      return rmiPort;
   }
   /**
    * Where should the server's std err log go?
    *
    * @return the error log file
    */
   public File getErrorLog()
   {
      return new File(getLogDir(), "error.log");
   }

   /**
    * Where should the servers's std out go?
    *
    * @return the output log file
    */
   public File getOutputLog()
   {
      return new File(getLogDir(), "output.log");
   }

   /**
    * Where should the servers's trace dump go?
    *
    * @return the thread dump file
    */
   public File getDumpFile()
   {
      return new File(getLogDir(), "threadDump.log");
   }

   /**
    * The server's log directory
    *
    * @return the log directory
    */
   public File getLogDir()
   {
      return new File(getConfDir(), "log");
   }

   /**
    * The server's directory (ie, all, default)
    *
    * @return the configuration directory
    */
   protected File getConfDir()
   {
      return new File(manager.getJBossHome(), "server/" + getConfig());
   }

   /**
    * Set the output log's writer
    *
    * @param outlog the log writer
    */
   public void setOutWriter(PrintWriter outlog)
   {
      outWriter = outlog;
   }

   /**
    * The writer for the output log.
    *
    * @return the output writer
    */
   public PrintWriter getOutWriter()
   {
      return outWriter;
   }

   /**
    * The error log's writer.
    *
    * @return the log writer
    */
   public PrintWriter getErrorWriter()
   {
      return errorWriter;
   }

   /**
    * Set the error writer.
    * @param errorlog
    */
   public void setErrorWriter(PrintWriter errorlog)
   {
      errorWriter = errorlog;
   }

   /**
    * Get the hasWebServer.
    *
    * @return the hasWebServer.
    */
   public boolean hasWebServer()
   {
      return hasWebServer;
   }

   /**
    * Set the hasWebServer.
    *
    * @param hasWebServer The hasWebServer to set.
    */
   public void setHasWebServer(boolean hasWebServer)
   {
      this.hasWebServer = hasWebServer;
   }

   /**
    * Get the Naming Context.
    *
    * @return the namingContext.
    * @throws NamingException
    */
   public Context getNamingContext() throws NamingException
   {
      if (namingContext == null)
      {
         Properties properties = new Properties();
         properties.setProperty(Context.INITIAL_CONTEXT_FACTORY, getInitialContextFactoryClassName());
         properties.setProperty(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces");
         properties.setProperty(Context.PROVIDER_URL, getServerUrl());
         properties.setProperty("j2ee.clientName", "JBoss Server Manager");
         properties.setProperty(NamingContext.JNP_DISABLE_DISCOVERY, "true");

         if (username!=null)
         {
            SecurityAssociation.setPrincipal(new SimplePrincipal(username));
            SecurityAssociation.setCredential(password);
         }

         setNamingContext(new InitialContext(properties));
      }

      return namingContext; 
   }

   /**
    * Set the NamingContext
    *
    * @param namingContext
    */
   public void setNamingContext(Context namingContext)
   {
      this.namingContext = namingContext;
   }
   
   /**
    * Get server connection.
    *
    * @return the serverConnection
    * @throws NamingException
    */
   public MBeanServerConnection getServerConnection() throws NamingException
   {
      String adapterName = "jmx/rmi/RMIAdaptor";

      if ( serverConnection == null)
      {
         Object obj = getNamingContext().lookup(adapterName);
         if ( obj == null )
         {
            throw new NameNotFoundException("Object " + adapterName + " not found.");
         }

         setServerConnection((MBeanServerConnection) obj);
      }

      return serverConnection;
   }

   /**
    * Set server connection.
    *
    * @param serverConnection
    */
   public void setServerConnection(MBeanServerConnection serverConnection)
   {
      this.serverConnection = serverConnection;
   }

   /**
    * Get server thread dump.
    *
    * @return the thread dump
    * @throws NamingException for contect lookup
    * @throws InstanceNotFoundException MBeanServerConnection method invoke()
    * @throws MBeanException MBeanServerConnection method invoke()
    * @throws ReflectionException MBeanServerConnection method invoke()
    * @throws IOException MBeanServerConnection method invoke()
    */
   public String listThreadDump() throws NamingException, InstanceNotFoundException, MBeanException, ReflectionException, IOException
   {
      final ObjectName serverInfoName;
      try
      {
         serverInfoName = new ObjectName("jboss.system","type","ServerInfo");
         // serverInfoName = new ObjectName(ServerInfoMBean.OBJECT_NAME_STR);
      }
      catch (MalformedObjectNameException mone)
      {
         // should never happen anyways
         throw new RuntimeException("Bad object name, something is totally broken", mone);
      }

      return (String) getServerConnection().invoke(serverInfoName, "listThreadDump",null,null);
   }

   /**
    * Send server shutdown command.
    *
    * @throws NamingException for context lookup
    * @throws InstanceNotFoundException MBeanServerConnection method invoke()
    * @throws MBeanException MBeanServerConnection method invoke()
    * @throws ReflectionException MBeanServerConnection method invoke()
    * @throws IOException MBeanServerConnection method invoke()
    */
   public void doShutdown() throws NamingException, InstanceNotFoundException, MBeanException, ReflectionException, IOException
   {
      final ObjectName serverObjectName;

      try
      {
         serverObjectName = new ObjectName("jboss.system","type","Server");
      }
      catch (MalformedObjectNameException mone)
      {
         // should never happen anyways
         throw new RuntimeException("Bad object name, something is totally broken", mone);
      }
      // serverObjectName = ServerImplMBean.OBJECT_NAME;

      getServerConnection().invoke(serverObjectName, "shutdown",null,null);
   }

   /**
    *
    * Get the URL to connect server
    *
    */
   public String getServerUrl()
   {
      if (null == this.serverUrl ) 
         return getRmiUrl();

      return serverUrl;
   }

   /**
    * Set the URL to connect server
    *
    * @param serverUrl
    */
   public void setServerUrl(String serverUrl)
   {
      this.serverUrl = serverUrl;
   }

   /**
    *
    * Get the InitialContext factory class name
    *
    */
   public String getInitialContextFactoryClassName()
   {
      if (this.initialContextFactoryClassName != null) 
         return this.initialContextFactoryClassName;

      if (getServerUrl().startsWith("http"))
      {
         return "org.jboss.naming.HttpNamingContextFactory";
      }
      else
      {
         return "org.jboss.naming.NamingContextFactory";
      }
   }

   /**
    * Set the InitialContext factory class name
    *
    * @param initialContextFactoryClassName
    */
   public void setInitialContextFactoryClassName(String initialContextFactoryClassName)
   {
      this.initialContextFactoryClassName = initialContextFactoryClassName;
   }

  /**
    * Get the Log Level
    *
    * @return the logThreshold
    */

   public String getLogThreshold()
   {
      if (null == this.logThreshold)
        return "DEBUG";

      return logThreshold;
   }

   /**
    * Set the Log Level
    *
    * @param logThreshold log level
    */
   public void setLogThreshold (String logThreshold)
   {
      this.logThreshold = logThreshold;
   }


   /*
    * Code below this marker has been ported from jboss-test to supply 
    * the Server with invokable operations support
    */
   
   /**
    * Gets the Main Deployer Name attribute of the JBossTestCase object
    *
    * @return                                  The Main DeployerName value
    * @exception MalformedObjectNameException  Description of Exception
    */
   ObjectName getDeployerName() throws MalformedObjectNameException
   {
      return new ObjectName(DEPLOYER_NAME);
   }
   
   /**
    * Deploy a package with the main deployer. The supplied name is
    * interpreted as a url, or as a filename in jbosstest.deploy.lib or output/lib.
    *
    * @param name           filename/url of package to deploy.
    * @exception Exception  Description of Exception
    */
   public void deploy(File file) throws Exception
   {
      if (Boolean.getBoolean("jbosstest.nodeploy") == true)
      {
         log.debug("Skipping deployment of: " + name);
         return;
      }

      URL deployURL = file.toURL();
      log.debug("Deploying " + name + ", url=" + deployURL);
      invoke(getDeployerName(),
         "deploy",
         new Object[]{deployURL},
         new String[]{"java.net.URL"});
   }

   public void redeploy(File file) throws Exception
   {
      if (Boolean.getBoolean("jbosstest.nodeploy") == true)
      {
         log.debug("Skipping redeployment of: " + name);
         return;
      }

      URL deployURL = file.toURL();
      log.debug("Deploying " + name + ", url=" + deployURL);
      invoke(getDeployerName(),
         "redeploy",
         new Object[]{deployURL},
         new String[]{"java.net.URL"});
   }
   
   /**
    * Undeploy a package with the main deployer. The supplied name is
    * interpreted as a url, or as a filename in jbosstest.deploy.lib or output/lib.
    *
    * @param name           filename/url of package to undeploy.
    * @exception Exception  Description of Exception
    */
   public void undeploy(File file) throws Exception
   {
      if (Boolean.getBoolean("jbosstest.nodeploy") == true)
         return;
      URL deployURL = file.toURL();
      log.debug("Undeploying " + name + ", url=" + deployURL);
      Object[] args = {deployURL};
      String[] sig = {"java.net.URL"};
      invoke(getDeployerName(), "undeploy", args, sig);
   }
   
   /**
    * invoke wraps an invoke call to the mbean server in a lot of exception
    * unwrapping.
    *
    * @param name           ObjectName of the mbean to be called
    * @param method         mbean method to be called
    * @param args           Object[] of arguments for the mbean method.
    * @param sig            String[] of types for the mbean methods parameters.
    * @return               Object returned by mbean method invocation.
    * @exception Exception  Description of Exception
    */
   public Object invoke(ObjectName name, String method, Object[] args, String[] sig) throws Exception
   {
      return invoke(this.getServerConnection(), name, method, args, sig);
   }

   public Object invoke(MBeanServerConnection server, ObjectName name, String method, Object[] args, String[] sig)
      throws Exception
   {
      try
      {
         log.debug("Invoking " + name.getCanonicalName() + " method=" + method);
         if (args != null)
            log.debug("args=" + Arrays.asList(args));
         return server.invoke(name, method, args, sig);
      }
      catch (javax.management.MBeanException e)
      {
         log.error("MbeanException", e.getTargetException());
         throw e.getTargetException();
      }
      catch (javax.management.ReflectionException e)
      {
         log.error("ReflectionException", e.getTargetException());
         throw e.getTargetException();
      }
      catch (javax.management.RuntimeOperationsException e)
      {
         log.error("RuntimeOperationsException", e.getTargetException());
         throw e.getTargetException();
      }
      catch (javax.management.RuntimeMBeanException e)
      {
         log.error("RuntimeMbeanException", e.getTargetException());
         throw e.getTargetException();
      }
      catch (javax.management.RuntimeErrorException e)
      {
         log.error("RuntimeErrorException", e.getTargetError());
         throw e.getTargetError();
      }
   }
}
