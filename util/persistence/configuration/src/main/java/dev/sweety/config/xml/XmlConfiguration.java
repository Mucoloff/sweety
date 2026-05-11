package dev.sweety.config.xml;

import dev.sweety.config.common.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlConfiguration extends Configuration {

    private static final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    private static final TransformerFactory tFactory = TransformerFactory.newInstance();

    public XmlConfiguration() {
        super("xml");
    }

    public XmlConfiguration(String extension) {
        super(extension);
    }

    @Override
    protected void dumpToStream(Map<String, Object> map, OutputStream out) throws IOException {
        try {
            Document doc = dbFactory.newDocumentBuilder().newDocument();
            Element root = doc.createElement("config");
            doc.appendChild(root);

            mapToXml(doc, root, map);

            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        } catch (Exception e) {
            throw new IOException("Failed to write XML", e);
        }
    }

    @Override
    protected Map<String, Object> loadFromStream(InputStream in) throws IOException {
        try {
            Document doc = dbFactory.newDocumentBuilder().parse(in);
            Element root = doc.getDocumentElement();
            return xmlToMap(root);
        } catch (Exception e) {
            throw new IOException("Failed to read XML", e);
        }
    }

    private void mapToXml(Document doc, Element parent, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            Element elem = doc.createElement(sanitizeTagName(key));

            switch (value) {
                case null -> elem.setAttribute("type", "null");
                case Map<?,?> mp -> mapToXml(doc, elem, castMap(mp));
                case List<?> list -> listToXml(doc, elem, list);
                default -> addTypedValue(elem, value);
            }

            parent.appendChild(elem);
        }
    }

    private void listToXml(Document doc, Element parent, List<?> list) {
        for (Object item : list) {
            Element elem = doc.createElement("item");

            switch (item) {
                case null -> elem.setAttribute("type", "null");
                case Map<?, ?> map -> mapToXml(doc, elem, castMap(map));
                case List<?> objects -> listToXml(doc, elem, objects);
                default -> addTypedValue(elem, item);
            }

            parent.appendChild(elem);
        }
    }

    private void addTypedValue(Element elem, Object value) {
        if (value instanceof String s) {
            elem.setTextContent(s);
        } else if (value instanceof Integer) {
            elem.setAttribute("type", "int");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Long) {
            elem.setAttribute("type", "long");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Double) {
            elem.setAttribute("type", "double");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Float) {
            elem.setAttribute("type", "float");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Boolean) {
            elem.setAttribute("type", "boolean");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Byte) {
            elem.setAttribute("type", "byte");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Short) {
            elem.setAttribute("type", "short");
            elem.setTextContent(String.valueOf(value));
        } else if (value instanceof Character) {
            elem.setAttribute("type", "char");
            elem.setTextContent(String.valueOf((int) (Character) value));
        } else {
            elem.setTextContent(String.valueOf(value));
        }
    }

    private Map<String, Object> xmlToMap(Element element) {
        Map<String, Object> map = new LinkedHashMap<>();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element child = (Element) node;
            String key = child.getTagName();
            Object value = parseElement(child);

            if (map.containsKey(key)) {
                // Handle duplicate keys by converting to list
                Object existing = map.get(key);
                if (existing instanceof List<?> ls) {
                    //noinspection unchecked
                    ((List<Object>) ls).add(value);
                } else {
                    List<Object> list = new java.util.ArrayList<>();
                    list.add(existing);
                    list.add(value);
                    map.put(key, list);
                }
            } else {
                map.put(key, value);
            }
        }

        return map;
    }

    private Object parseElement(Element element) {
        String type = element.getAttribute("type");
        
        if ("null".equals(type)) {
            return null;
        }

        NodeList children = element.getChildNodes();
        int elementChildren = 0;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                elementChildren++;
            }
        }

        if (elementChildren == 0) {
            String text = element.getTextContent();
            if (text.isEmpty()) return null;
            
            // Use explicit type if provided
            if (!type.isEmpty()) {
                try {
                    return switch (type) {
                        case "int" -> Integer.parseInt(text);
                        case "long" -> Long.parseLong(text);
                        case "double" -> Double.parseDouble(text);
                        case "float" -> Float.parseFloat(text);
                        case "boolean" -> Boolean.parseBoolean(text);
                        case "byte" -> Byte.parseByte(text);
                        case "short" -> Short.parseShort(text);
                        case "char" -> (char) Integer.parseInt(text);
                        default -> text;
                    };
                } catch (NumberFormatException ignored) {
                    return text;
                }
            }
            
            // Otherwise detect and convert types
            return parseLegacyScalar(text);
        }

        // Check if all children are "item" elements (list)
        boolean isList = true;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (!node.getNodeName().equals("item")) {
                    isList = false;
                    break;
                }
            }
        }

        if (isList) {
            List<Object> list = new java.util.ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    list.add(parseElement((Element) node));
                }
            }
            return list;
        } else {
            return xmlToMap(element);
        }
    }

    private Object parseLegacyScalar(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }

        return value;
    }

    private String sanitizeTagName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            casted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return casted;
    }
}
