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

/**
 * A Property.
 * 
 * @author <a href="ryan.campbell@jboss.com">Ryan Campbell</a>
 * @version $Revision: 70297 $
 */
public class Property
{

   private String key;
   /**
    * Get the key.
    * 
    * @return the key.
    */
   public String getKey()
   {
      return key;
   }
   /**
    * Set the key.
    * 
    * @param key The key to set.
    */
   public void setKey(String key)
   {
      this.key = key;
   }
   /**
    * Get the value.
    * 
    * @return the value.
    */
   public String getValue()
   {
      return value;
   }
   /**
    * Set the value.
    * 
    * @param value The value to set.
    */
   public void setValue(String value)
   {
      this.value = value;
   }
   private String value;
   
   public Property()
   {

   }
   
}
