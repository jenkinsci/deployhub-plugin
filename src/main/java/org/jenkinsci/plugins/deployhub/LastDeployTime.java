package org.jenkinsci.plugins.deployhub;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;

public class LastDeployTime extends ListViewColumn {
    private boolean displayName;
    private boolean trim;
    private int displayLength; //numbers of lines to display
    private int columnWidth;
    private boolean forceWidth;

    @DataBoundConstructor
    public LastDeployTime(boolean displayName, boolean trim, int displayLength, int columnWidth, boolean forceWidth) {
        super();
        this.displayName = displayName;
        this.trim = trim;
        this.displayLength = displayLength;
        this.columnWidth = columnWidth;
        this.forceWidth = forceWidth;
    }

    public LastDeployTime() {
        this(false, false, 1, 80, false);
    }
    
    public boolean isDisplayName() {
        return displayName;
    }

    public boolean isTrim() {
        return trim;
    }

    public int getDisplayLength() {
        return displayLength;
    }

    public int getColumnWidth() {
        return columnWidth;
    }
    
    public boolean isForceWidth() {
        return forceWidth;
    }

    public String getToolTip(AbstractItem job) {
        return formatDescription(job, false);
    }
    
    public String getDescription(AbstractItem job){
        return formatDescription(job, isTrim());
    }
    
 private String formatDescription(AbstractItem job, boolean trimIt)
 {
  String deploymentid = "N/A";
  Document doc = null;
  
  if (job == null)
  {
   return null;
  }

  String rootDir = job.getRootDir().getAbsolutePath();
  File t = new File(rootDir, "DeployHub.xml");
  if (t != null && t.exists())
  {
    // Parse XML to DOM
    String xml = "";
    try
    {
     xml = readFile(t.getPath(), StandardCharsets.UTF_8);
    }
    catch (IOException e)
    {
     e.printStackTrace();
    }

    try
    {
     DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
     DocumentBuilder builder = null;
     builder = factory.newDocumentBuilder();

     doc = builder.parse(new ByteArrayInputStream(xml.getBytes("utf-8")));
    }
    catch (SAXException | IOException | ParserConfigurationException e)
    {
     e.printStackTrace();
    }
    XPathFactory xPathfactory = XPathFactory.newInstance();
    XPath xpath = xPathfactory.newXPath();

    XPathExpression expr;
    try
    {
     expr = xpath.compile("/net.sf.json.JSONObject/properties/org.apache.commons.collections.map.ListOrderedMap/map/entry[*]/int");
     deploymentid = (String) expr.evaluate(doc, XPathConstants.STRING);
     if (deploymentid != null)
      return deploymentid;
    }
    catch (XPathExpressionException e)
    {
     e.printStackTrace();
    }

    try
    {
     expr = xpath.compile("/properties/DeploymentID");
     deploymentid = (String) expr.evaluate(doc, XPathConstants.STRING);
     
     if (deploymentid != null)
      return deploymentid;
    } 
    catch (XPathExpressionException e)
    {
     e.printStackTrace();
    } 
  }
  return deploymentid;
 }
    
    static String readFile(String path, Charset encoding) 
      throws IOException 
    {
      byte[] encoded = Files.readAllBytes(Paths.get(path));
      return new String(encoded, encoding);
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        @Override
        public boolean shownByDefault() {
            return true;
        }

        @Override
        public String getDisplayName() {
	    return "Last Deployment";
        }

        @Override
        public String getHelpFile() {
            return "/plugins/DeployHub/help-description-column.html";
        }
    }
}
