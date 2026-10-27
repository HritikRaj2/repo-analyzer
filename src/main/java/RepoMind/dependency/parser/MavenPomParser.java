package RepoMind.dependency.parser;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parser for Maven {@code pom.xml} manifests.
 *
 * <p>Parses dependencies declared directly under {@code <project><dependencies>}.
 * Does not parse dependencies declared only within {@code <dependencyManagement>}.
 * Does not invent versions when versions are omitted or declared via properties.
 */
@Component
public class MavenPomParser implements DependencyParser {

    private static final String PACKAGE_MANAGER = "maven";

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.equals("pom.xml") || normalized.endsWith("/pom.xml") || normalized.endsWith("\\pom.xml");
    }

    @Override
    public String getPackageManager() {
        return PACKAGE_MANAGER;
    }

    @Override
    public List<ParsedDependency> parse(String content, String manifestPath) throws Exception {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // XXE and external entity hardening
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // Some parsers may not support all hardening flags
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(content)));
        Element projectElem = doc.getDocumentElement();

        if (projectElem == null || !"project".equalsIgnoreCase(projectElem.getTagName())) {
            return Collections.emptyList();
        }

        List<ParsedDependency> dependencies = new ArrayList<>();

        // Find direct <dependencies> child of <project>
        NodeList projectChildren = projectElem.getChildNodes();
        for (int i = 0; i < projectChildren.getLength(); i++) {
            Node child = projectChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "dependencies".equalsIgnoreCase(child.getNodeName())) {
                parseDependenciesBlock((Element) child, manifestPath, dependencies);
            }
        }

        return dependencies;
    }

    private void parseDependenciesBlock(Element dependenciesElem, String manifestPath, List<ParsedDependency> dependencies) {
        NodeList depNodes = dependenciesElem.getChildNodes();
        for (int i = 0; i < depNodes.getLength(); i++) {
            Node node = depNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "dependency".equalsIgnoreCase(node.getNodeName())) {
                Element depElem = (Element) node;

                String groupId = getDirectChildText(depElem, "groupId");
                String artifactId = getDirectChildText(depElem, "artifactId");

                if (artifactId == null || artifactId.isBlank()) {
                    continue;
                }

                String group = (groupId != null && !groupId.isBlank()) ? groupId.trim() : "";
                String artifact = artifactId.trim();
                String name = group.isEmpty() ? artifact : group + ":" + artifact;

                String rawVersion = getDirectChildText(depElem, "version");
                String version = null;
                boolean unresolved = false;

                if (rawVersion != null && !rawVersion.isBlank()) {
                    version = rawVersion.trim();
                    if (version.startsWith("${") && version.endsWith("}")) {
                        // Property-based version: kept as-is, flagged as unresolved statically
                        unresolved = true;
                    }
                } else {
                    // Missing version: inherited from parent or dependencyManagement
                    unresolved = true;
                }

                String rawScope = getDirectChildText(depElem, "scope");
                String scope = (rawScope != null && !rawScope.isBlank())
                        ? rawScope.trim().toLowerCase(Locale.ROOT)
                        : "compile";

                dependencies.add(new ParsedDependency(
                        name,
                        version,
                        PACKAGE_MANAGER,
                        scope,
                        true,
                        manifestPath,
                        unresolved
                ));
            }
        }
    }

    private String getDirectChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equalsIgnoreCase(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return null;
    }
}
