package org.netxms.client;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Set;
import java.util.regex.Pattern;
import org.netxms.base.Glob;
import org.netxms.client.objects.AbstractNode;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.objects.AccessPoint;
import org.netxms.client.objects.Cluster;
import org.netxms.client.objects.Interface;
import org.netxms.client.objects.Sensor;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class created to store menu filter
 */
@Root(name="objectMenuFilter")
public class ObjectMenuFilter
{
   public static final int REQUIRES_SNMP                    = 0x00000001;
   public static final int REQUIRES_AGENT                   = 0x00000002;
   public static final int REQUIRES_OID_MATCH               = 0x00000004;
   public static final int REQUIRES_NODE_OS_MATCH           = 0x00000008;
   public static final int REQUIRES_TEMPLATE_MATCH          = 0x00000010;
   public static final int REQUIRES_WORKSTATION_OS_MATCH    = 0x00000020;
   public static final int REQUIRES_CUSTOM_ATTRIBUTE_MATCH  = 0x00000040;
   public static final int REQUIRES_SSH                     = 0x00000080;
   public static final int REQUIRES_ETHERNET_IP             = 0x00000100;
   public static final int REQUIRES_MODBUS_TCP              = 0x00000200;
   
   private static Logger logger = LoggerFactory.getLogger(ObjectMenuFilter.class);

   @Element(required=false, name="toolOS")
   public String toolNodeOS;

   @Element(required=false)
   public String toolWorkstationOS;
   
   @Element(required=false)
   public String toolTemplate;
   
   @Element(required=false)
   public String toolCustomAttributes;
   
   @Element(required=false)
   public String snmpOid;

   @Element(required=false)
   public int flags;

   /**
    * Create empty filter
    */
   public ObjectMenuFilter()
   {
      toolNodeOS = "";
      toolWorkstationOS = "";
      toolTemplate = "";
      toolCustomAttributes = "";
      snmpOid = "";
   }
   
   /**
    * Create XML from configuration.
    * 
    * @return XML document
    */
   public String createXml()
   {
      try
      {
         Serializer serializer = new Persister();
         Writer writer = new StringWriter();
         serializer.write(this, writer);
         return writer.toString();
      }
      catch(Exception e)
      {
         logger.warn("Exception during object menu filter serialization", e);
         return "";
      }
   }
   
   /**
    * Check if tool is applicable for given object.
    * For an interface, node-capability checks (SNMP/agent/SSH/EtherNet-IP/Modbus/OID/OS)
    * are evaluated against the parent node. Objects without an associated node
    * (sensor, access point, container) do not satisfy node-capability filters.
    *
    * @param object The object to test
    * @return true if tool is applicable for given object
    */
   public boolean isApplicableForObject(AbstractObject object)
   {
      AbstractNode node = null;
      if (object instanceof AbstractNode)
         node = (AbstractNode)object;
      else if (object instanceof Interface)
         node = ((Interface)object).getParentNode();

      if (node != null)
      {
         if (((flags & REQUIRES_SNMP) != 0) && (node.getCapabilities() & AbstractNode.NC_IS_SNMP) == 0)
            return false; // Node does not support SNMP

         if (((flags & REQUIRES_AGENT) != 0) && (node.getCapabilities() & AbstractNode.NC_IS_NATIVE_AGENT) == 0)
            return false; // Node does not have NetXMS agent

         if (((flags & REQUIRES_SSH) != 0) && (node.getCapabilities() & AbstractNode.NC_IS_SSH) == 0)
            return false; // Node does not support SSH

         if (((flags & REQUIRES_ETHERNET_IP) != 0) && (node.getCapabilities() & AbstractNode.NC_IS_ETHERNET_IP) == 0)
            return false; // Node does not support EtherNet/IP

         if (((flags & REQUIRES_MODBUS_TCP) != 0) && (node.getCapabilities() & AbstractNode.NC_IS_MODBUS_TCP) == 0)
            return false; // Node does not support Modbus TCP

         if ((flags & REQUIRES_OID_MATCH) != 0)
         {
            if (!Glob.matchIgnoreCase(snmpOid, node.getSnmpOID()))
               return false; // OID does not match
         }

         if ((flags & REQUIRES_NODE_OS_MATCH) != 0)
         {
            boolean match = false;
            String[] substrings = toolNodeOS.split(",");
            for(int i = 0; i < substrings.length; i++)
            {
               if (Pattern.matches(substrings[i], node.getPlatformName()))
               {
                  match = true;
                  break;
               }
            }
            if (!match)
               return false; // Not correct type of OS
         }

         if ((flags & REQUIRES_WORKSTATION_OS_MATCH) != 0)
         {
            boolean match = false;
            String[] substrings = toolWorkstationOS.split(",");
            for(int i = 0; i < substrings.length; i++)
            {
               if (Pattern.matches(substrings[i], System.getProperty("os.name")))
               {
                  match = true;
                  break;
               }
            }
            if (!match)
               return false; // Not correct type of OS
         }
      }
      else
      {
         if ((flags & (REQUIRES_SNMP | REQUIRES_AGENT | REQUIRES_SSH | REQUIRES_ETHERNET_IP | REQUIRES_MODBUS_TCP | REQUIRES_OID_MATCH | REQUIRES_NODE_OS_MATCH)) != 0)
            return false;
      }

	   if ((flags & REQUIRES_TEMPLATE_MATCH) != 0)
      {
         // For interface, templates are inspected on the parent node. Nodes,
         // clusters, sensors and access points carry templates themselves.
         AbstractObject templateHolder;
         if (object instanceof Interface)
         {
            if (node == null)
               return false;
            templateHolder = node;
         }
         else if ((object instanceof AbstractNode) || (object instanceof Cluster) ||
                  (object instanceof Sensor) || (object instanceof AccessPoint))
         {
            templateHolder = object;
         }
         else
         {
            return false;
         }

         boolean match = false;
         String[] substrings = toolTemplate.split(",");
         Set<AbstractObject> parents = templateHolder.getAllParents(AbstractObject.OBJECT_TEMPLATE);
         for(AbstractObject parent : parents)
         {
            for(int i = 0; i < substrings.length; i++)
            {
               if (Pattern.matches(substrings[i].trim(), parent.getObjectName().trim()))
               {
                  match = true;
                  break;
               }
            }
            if (match)
               break;
         }
         if (!match)
            return false;  // Does not belong to those templates
      }

      if ((flags & REQUIRES_CUSTOM_ATTRIBUTE_MATCH) != 0)
      {
         boolean match = false;
         String[] substrings = toolCustomAttributes.split(",");
         for(String attr : object.getCustomAttributes().keySet())
         {
            for(int i = 0; i < substrings.length; i++)
            {
               if (Pattern.matches(substrings[i].trim(), attr.trim()))
               {
                  match = true;
                  break;
               }
            }
            if (match)
               break;
         }
         if (!match)
            return false;
      }

      return true;
   }
   
   /**
    * Set filter
    * 
    * @param filterText The filter text
    * @param filterType The filter type
    */
   public void setFilter(String filterText, int filterType)
   {
      switch(filterType)
      {
         case ObjectMenuFilter.REQUIRES_CUSTOM_ATTRIBUTE_MATCH:
            toolCustomAttributes = filterText;
            break;
         case ObjectMenuFilter.REQUIRES_NODE_OS_MATCH:
            toolNodeOS = filterText;
            break;
         case ObjectMenuFilter.REQUIRES_OID_MATCH:
            snmpOid = filterText;
            break;
         case ObjectMenuFilter.REQUIRES_TEMPLATE_MATCH:
            toolTemplate = filterText;
            break;
         case ObjectMenuFilter.REQUIRES_WORKSTATION_OS_MATCH:
            toolWorkstationOS = filterText;
            break;
         default:
            break;
      }
   }
}
