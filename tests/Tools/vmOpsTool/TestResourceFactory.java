
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author rami.abughazaleh
 */
public class TestResourceFactory {
    
    private final String UserName;
    
    TestResourceFactory(String userName)
    {
        this.UserName = userName;
    }
    
    int GetOperationTimeoutValue()
    {
        try
        {
            Document document = GetTestResourcesDocument();
            Node node = GetNodeFromXPath(document, "/TestResources/TestResource[contains(@names, '" + UserName + "')]/OperationTimeout");
            if (node == null)
            {
                // default
                return 1200;
            }

            String content = node.getTextContent();

            return Integer.parseInt(content);
        }
        catch (IOException | NumberFormatException | ParserConfigurationException | XPathExpressionException | DOMException | SAXException ex)
        {
            // default
            return 1200;
        }
    }
    
    TestResource GetTestResource(String testMethodName) throws Exception
    {
        String errorMessage = "Test resource not found for user '" + UserName + "' and test method name '" + testMethodName + "'";
        TestResource testResource = new TestResource();
        
        Document document = GetTestResourcesDocument();
        Node node = GetNodeFromXPath(document, "/TestResources/TestResource[contains(@names, '" + UserName + "')]/TestMethod[@name='" + testMethodName + "']");
        if (node == null)
        {
            throw new Exception(errorMessage);
        }
        
        NodeList childNodes = node.getChildNodes();
        int length = childNodes.getLength();
        for (int i = 0; i < length; i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element)childNodes.item(i);
                testResource.Template = el.getElementsByTagName("Template").item(0).getTextContent();
                testResource.NewVmName = el.getElementsByTagName("NewVmName").item(0).getTextContent();
                testResource.TargetDC = el.getElementsByTagName("TargetDC").item(0).getTextContent();
                testResource.ComputeType = el.getElementsByTagName("ComputeType").item(0).getTextContent();
                testResource.ComputeName = el.getElementsByTagName("ComputeName").item(0).getTextContent();
                testResource.Datastore = el.getElementsByTagName("Datastore").item(0).getTextContent();
                if (el.getElementsByTagName("CustomizationSpec").getLength() != 0)
                {
                    testResource.CustomizationSpec = el.getElementsByTagName("CustomizationSpec").item(0).getTextContent();
                }
            }
        }
        
        return testResource;
    }
    
    TestVirtualMachine GetVirtualMachine(String testMethodName) throws Exception
    {
        String errorMessage = "Test virtual machine not found for user '" + UserName + "' and test method name '" + testMethodName + "'";
        
        TestVirtualMachine vm = new TestVirtualMachine();
        
        Document document = GetTestResourcesDocument();
        Node node = GetNodeFromXPath(document, "/TestResources/TestResource[contains(@names, '" + UserName + "')]/TestMethod[@name='" + testMethodName + "']");
        if (node == null)
        {
            throw new Exception(errorMessage);
        }
        
        NodeList childNodes = node.getChildNodes();
        int length = childNodes.getLength();
        for (int i = 0; i < length; i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element)childNodes.item(i);
                vm.Name = el.getElementsByTagName("Name").item(0).getTextContent();
                if (el.getElementsByTagName("Snapshot").getLength() != 0)
                {
                    vm.Snapshot = el.getElementsByTagName("Snapshot").item(0).getTextContent();
                }
                
                if (el.getElementsByTagName("NewSnapshot").getLength() != 0)
                {
                    vm.NewSnapshot = el.getElementsByTagName("NewSnapshot").item(0).getTextContent();
                }
                
                vm.TargetDC = el.getElementsByTagName("TargetDC").item(0).getTextContent();
            }
        }
        
        return vm;
    }
    
    ConnectionData GetConnectionData(String vCenterUrl, String testMethodName) throws Exception {
        
        String vCenterUserName = "";
        String vCenterPassword = "";
        String targetDC = "";

        Document document = GetTestResourcesDocument();
        Node node = GetNodeFromXPath(document, "/TestResources/TestResource[contains(@names, '" + UserName + "')]/Connection");
        if (node == null)
        {
            throw new Exception("Test connection data not found for user '" + UserName + "'");
        }
        
        Element el = (Element)node;
        vCenterUserName = el.getElementsByTagName("UserName").item(0).getTextContent();
        vCenterPassword = el.getElementsByTagName("Password").item(0).getTextContent();
        targetDC = el.getElementsByTagName("DataCenter").item(0).getTextContent();
        
        switch (testMethodName){
            case "connectShouldThrowForInvalidCredentials":
                return new ConnectionData(vCenterUrl, vCenterUserName, "InvalidPassword", targetDC, true);
            case "connectShouldThrowWithoutSkipCACheck":
                return new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, targetDC, false);
            default:
                return new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, targetDC, true);
        }
    }
    
    Document GetTestResourcesDocument() throws ParserConfigurationException, SAXException, IOException
    {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse("TestResources.xml");
    }
    
    Node GetNodeFromXPath(Document document, String expression) throws XPathExpressionException
    {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xPath = xPathfactory.newXPath();
        XPathExpression xPathExpression = xPath.compile(expression);
        return (Node)xPathExpression.evaluate(document, XPathConstants.NODE);
    }
}
