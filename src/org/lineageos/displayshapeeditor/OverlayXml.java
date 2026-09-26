package org.lineageos.displayshapeeditor;

import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlSerializer;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

final class OverlayXml {
    private static void entry(XmlSerializer xml, String tag, String name, String value)
            throws java.io.IOException {
        xml.startTag(null, tag);
        xml.attribute(null, "name", name);
        if (tag.equals("string")) xml.attribute(null, "translatable", "false");
        xml.text(value);
        xml.endTag(null, tag);
    }

    static String write(ShapeConfig config) throws java.io.IOException {
        StringWriter writer = new StringWriter();
        XmlSerializer xml = Xml.newSerializer();
        xml.setOutput(writer);
        xml.startDocument("UTF-8", true);
        xml.startTag(null, "resources");
        entry(xml, "dimen", "rounded_corner_radius", config.radius);
        entry(xml, "dimen", "rounded_corner_radius_top", config.top);
        entry(xml, "dimen", "rounded_corner_radius_bottom", config.bottom);
        entry(xml, "dimen", "rounded_corner_radius_adjustment", config.adjustment);
        entry(xml, "dimen", "rounded_corner_radius_top_adjustment", config.topAdjustment);
        entry(xml, "dimen", "rounded_corner_radius_bottom_adjustment", config.bottomAdjustment);
        entry(xml, "string", "config_mainBuiltInDisplayCutout", config.cutout);
        if (!config.approximation.isEmpty()) {
            entry(xml, "string", "config_mainBuiltInDisplayCutoutRectApproximation",
                    config.approximation);
        }
        entry(xml, "bool", "config_fillMainBuiltInDisplayCutout", Boolean.toString(config.fill));
        entry(xml, "bool", "config_maskMainBuiltInDisplayCutout", Boolean.toString(config.mask));
        xml.endTag(null, "resources");
        xml.endDocument();
        return writer.toString();
    }

    static ShapeConfig read(InputStream stream, ShapeConfig previous) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(stream);
        if (!document.getDocumentElement().getTagName().equals("resources")) {
            throw new IllegalArgumentException("Expected a <resources> XML file");
        }
        Map<String, String> entries = new HashMap<>();
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element element = (Element) node;
            String tag = element.getTagName();
            if (tag.equals("string") || tag.equals("dimen") || tag.equals("bool")) {
                entries.put(tag + "/" + element.getAttribute("name"),
                        element.getTextContent().trim());
            }
        }
        ShapeConfig c = ShapeConfig.fromJson(previous.toJson().toString());
        c.radius = get(entries, "dimen/rounded_corner_radius", c.radius);
        c.top = get(entries, "dimen/rounded_corner_radius_top", c.top);
        c.bottom = get(entries, "dimen/rounded_corner_radius_bottom", c.bottom);
        c.adjustment = get(entries, "dimen/rounded_corner_radius_adjustment", c.adjustment);
        c.topAdjustment = get(entries, "dimen/rounded_corner_radius_top_adjustment", c.topAdjustment);
        c.bottomAdjustment = get(entries, "dimen/rounded_corner_radius_bottom_adjustment",
                c.bottomAdjustment);
        if (entries.containsKey("string/config_mainBuiltInDisplayCutout")) c.preset = 0;
        c.cutout = get(entries, "string/config_mainBuiltInDisplayCutout", c.cutout);
        String rect = entries.get("string/config_mainBuiltInDisplayCutoutRectApproximation");
        if (rect != null) {
            c.approximation = rect.equals("@string/config_mainBuiltInDisplayCutout") ? "" : rect;
        }
        c.fill = Boolean.parseBoolean(get(entries, "bool/config_fillMainBuiltInDisplayCutout",
                Boolean.toString(c.fill)));
        c.mask = Boolean.parseBoolean(get(entries, "bool/config_maskMainBuiltInDisplayCutout",
                Boolean.toString(c.mask)));
        c.validate();
        return c;
    }

    private static String get(Map<String, String> entries, String name, String fallback) {
        String value = entries.get(name);
        if (value != null && value.startsWith("@")) {
            value = entries.get(value.substring(1));
        }
        return value == null ? fallback : value;
    }

    private OverlayXml() {}
}
