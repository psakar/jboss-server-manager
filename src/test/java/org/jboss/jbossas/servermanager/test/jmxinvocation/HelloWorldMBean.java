package org.jboss.jbossas.servermanager.test.jmxinvocation;

/**
 * HelloWorldMBean
 * 
 * An interface defining a contract for a simple
 * "Hello World" test service
 *
 * @author <a href="mailto:alr@jboss.org">ALR</a>
 * @version $Revision: $
 */
public interface HelloWorldMBean
{
   /**
    * The value to be returned by the test method
    */
   String RETURN_VALUE = "Hello World";

   /**
    * Returns the contract-defined return value
    * 
    * @return
    */
   String sayHello();
}
