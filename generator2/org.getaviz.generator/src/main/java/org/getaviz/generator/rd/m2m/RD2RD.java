package org.getaviz.generator.rd.m2m;

import org.getaviz.generator.ColorGradient;
import org.getaviz.generator.SettingsConfiguration;
import org.getaviz.generator.Step;
import org.getaviz.generator.database.Labels;
import org.getaviz.generator.SettingsConfiguration.OutputFormat;

import java.util.ArrayList;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.getaviz.generator.database.DatabaseConnector;
import org.getaviz.generator.rd.s2m.Disk;
import org.getaviz.generator.rd.s2m.DiskSegment;
import org.neo4j.driver.v1.types.Node;

import java.util.List;

import org.neo4j.driver.v1.StatementResult;

public class RD2RD implements Step {
    private DatabaseConnector connector = DatabaseConnector.getInstance();
    private Log log = LogFactory.getLog(RD2RD.class);
    private List<String> NS_colors;
    private OutputFormat outputFormat;
    private double dataFactor;
    private int namespaceMaxLevel;
    private int diskMaxLevel;
    private static ArrayList<Disk> disksList = new ArrayList<>();
    private static ArrayList<Disk> rootDisksList = new ArrayList<>();
    private static ArrayList<DiskSegment> innerSegments = new ArrayList<>();
    private static ArrayList<DiskSegment> outerSegments = new ArrayList<>();

    public RD2RD(SettingsConfiguration config) {
        this.outputFormat = config.getOutputFormat();
        this.dataFactor = config.getRDDataFactor();
    }

    public void run() {
        log.info("RD2RD started");
        calculateNamespaceMaxLevel();
        calculateDiskMaxLevel();
        createLists();
        calculateLayoutData();
        writeToDatabase();
        log.info("RD2RD finished");
    }

    private String setNamespaceColor(int level) {
        return NS_colors.get(level - 1);
    }

    private void calculateAreaWithoutBorder(ArrayList<Disk> list) {
        list.forEach(disk -> {
            disk.calculateAreaWithoutBorder(dataFactor);
        });
    }

    private void calculateRadius(ArrayList<Disk> list) {
        list.forEach(Disk::calculateRadius);
    }

    private void calculateLayout(ArrayList<Disk> list) {
        ArrayList<CircleWithInnerCircles> nestedCircles = new ArrayList<>();
        list.forEach((disk) -> nestedCircles.add(new CircleWithInnerCircles(disk, false)));
        RDLayout.nestedLayout(nestedCircles);
        for (CircleWithInnerCircles circle : nestedCircles) {
            circle.updateDiskNode();
        }
    }

    private void postLayout(ArrayList<Disk> list) {
        list.forEach(Disk::setSegmentsArea);
    }

    private void calculateRings(ArrayList<Disk> list) {
        list.forEach(disk -> disk.calculateRings(outputFormat));
    }

    private void calculateNamespaceMaxLevel() {
        StatementResult length = connector.executeRead(
                "MATCH p=(n:Package)-[:CONTAINS*]->(m:Package) WHERE NOT (m)-[:CONTAINS]->(:Package) " +
                        "RETURN max(length(p)) AS length");
        namespaceMaxLevel = length.single().get("length").asInt() + 1;
    }

    private void calculateDiskMaxLevel() {
        StatementResult length = connector.executeRead(
                "MATCH p=(n:RD:Model)-[:CONTAINS*]->(m:RD:Disk) WHERE NOT (m)-[:CONTAINS]->(:RD:Disk) " +
                        "RETURN max(length(p)) AS length");
        diskMaxLevel = length.single().get("length").asInt() + 1;
    }

    private void createDisksList() {
        disksList.addAll(rootDisksList);
        StatementResult result = connector.executeRead("MATCH (p:Disk)-[:CONTAINS]->(d:Disk)-[:VISUALIZES]->(element) "
                + "RETURN d.grossArea AS areaWithBorder, d.netArea AS areaWithoutBorder, d.ringWidth AS ringWidth, d.height as height," +
                " d.size AS size, ID(d) AS id, ID(p) AS parentID, ID(element) AS visualizedID ORDER BY element.hash");
        result.forEachRemaining(d -> {
            long visualizedID = d.get("visualizedID").asLong();
            long parentID = d.get("parentID").asLong();
            long id = d.get("id").asLong();
            double areaWithBorder = d.get("areaWithBorder").asDouble(0.0);
            double areaWithoutBorder = d.get("areaWithoutBorder").asDouble(0.0);
            double ringWidth = d.get("ringWidth").asDouble();
            double height = d.get("height").asDouble();
            Disk disk = new Disk(visualizedID, parentID, id, areaWithBorder, areaWithoutBorder, ringWidth, height);
            disk.setMaxLevel(diskMaxLevel);
            disksList.add(disk);
            addPosition(disk);
        });
    }

    private void createRootDisksList() {
        StatementResult result = connector.executeRead("MATCH (n:RD:Model)-[:CONTAINS]->(d:Disk)-[:VISUALIZES]->(element)" +
                " RETURN d.grossArea AS areaWithBorder,d.netArea AS areaWithoutBorder, d.ringWidth AS ringWidth, d.height AS height," +
                " d.size AS size, ID(d) as id, ID(n) AS parentID, ID(element) AS visualizedID ORDER BY element.hash");
        result.forEachRemaining(d -> {
            long visualizedID = d.get("visualizedID").asLong();
            long parentID = d.get("parentID").asLong();
            long id = d.get("id").asLong();
            double areaWithBorder = d.get("areaWithBorder").asDouble(0.0);
            double areaWithoutBorder = d.get("areaWithoutBorder").asDouble(0.0);
            double ringWidth = d.get("ringWidth").asDouble();
            double height = d.get("height").asDouble();
            Disk disk = new Disk(visualizedID, parentID, id, areaWithBorder, areaWithoutBorder, ringWidth, height);
            disk.setMaxLevel(diskMaxLevel);
            rootDisksList.add(disk);
            addPosition(disk);
        });
    }

    private void createInnerSegmentsList() {
        StatementResult result = connector.executeRead(
                "MATCH (n)-[:CONTAINS]->(d:DiskSegment)-[:VISUALIZES]->(field:Field) " +
                        "RETURN d.size AS size, ID(d) as id, ID(n) as parentID ORDER BY field.hash");
        result.forEachRemaining(f -> {
            long parentID = f.get("parentID").asLong();
            long id = f.get("id").asLong();
            double size = f.get("size").asDouble();
            DiskSegment innerSegment = new DiskSegment(parentID, id, size);
            innerSegments.add(innerSegment);
        });
    }

    private void createOuterSegmentsList() {
        StatementResult result = connector.executeRead(
                "MATCH (n)-[:CONTAINS]->(d:DiskSegment)-[:VISUALIZES]->(method:Method) " +
                        "RETURN d.size AS size, ID(d) as id, ID(n) as parentID ORDER BY method.hash");
        result.forEachRemaining(f -> {
            long parentID = f.get("parentID").asLong();
            long id = f.get("id").asLong();
            double size = f.get("size").asDouble();
            DiskSegment outerSegment = new DiskSegment(parentID, id, size);
            outerSegments.add(outerSegment);
        });
    }

    private void addPosition(Disk disk) {
        StatementResult position = connector
                .executeRead("MATCH (n)-[:HAS]->(p:Position) WHERE ID(n) = " + disk.getId() + " RETURN p");
        if (!position.list().isEmpty()) {
            Node node = position.single().get("p").asNode();
            disk.setPosition(node.get("x").asDouble(), node.get("y").asDouble(), node.get("z").asDouble());
        }
    }

    private void addColor() {
        String NS_colorStart = "#969696";
        String NS_colorEnd = "#F0F0F0";
        NS_colors = ColorGradient.createColorGradient(NS_colorStart, NS_colorEnd, namespaceMaxLevel);
        connector.executeRead(
                "MATCH p = (n:Model:RD)-[:CONTAINS*]->(d:Disk)-[:VISUALIZES]->(e) RETURN e, length(p)-1 AS length," +
                        " ID(d) AS id")
                .forEachRemaining((result) -> {
                    if (result.get("e").asNode().hasLabel(Labels.Package.name())) {
                        String color = "\'" + setNamespaceColor(result.get("length").asInt()) + "\'";
                        disksList.forEach(disk -> {
                            long diskID = disk.getId();
                            if (result.get("id").asLong() == diskID) {
                                disk.setColor(color);
                            }
                        });
                    }
                });
    }

    private static void addSubDisks() {
        disksList.forEach(disk -> {
            ArrayList<Disk> list = new ArrayList<>();
            long id = disk.getId();
            disksList.forEach(d -> {
                long parentID = d.getParentID();
                if (id == parentID) {
                    list.add(d);
                }
            });
            disk.setSubDisksList(list);
        });
    }

    private void addSegmentsToDisk() {
        disksList.forEach(disk -> {
            long id = disk.getId();
            ArrayList<DiskSegment> innerSegmentsList = createDiskSegmentList(id, innerSegments);
            ArrayList<DiskSegment> outerSegmentsList = createDiskSegmentList(id, outerSegments);
            disk.setInnerSegmentsList(innerSegmentsList);
            disk.setOuterSegmentsList(outerSegmentsList);
        });
    }

    private static ArrayList<DiskSegment> createDiskSegmentList(long id, ArrayList<DiskSegment> diskSegments) {
        ArrayList<DiskSegment> list = new ArrayList<>();
        diskSegments.forEach(d -> {
            long parentID = d.getParentID();
            if (id == parentID) {
                list.add(d);
            }
        });
        return list;
    }

    private void createLists() {
        createInnerSegmentsList();
        createOuterSegmentsList();
        createRootDisksList();
        createDisksList();
        addColor();
        addSubDisks();
        addSegmentsToDisk();
    }

    private void calculateLayoutData() {
        calculateAreaWithoutBorder(disksList);
        calculateRadius(disksList);
        calculateLayout(rootDisksList);
        postLayout(disksList);
        calculateRings(disksList);
    }

    private void writeToDatabase() {
        innerSegments.forEach(segment -> segment.writeToDatabase(connector, true));
        outerSegments.forEach(segment -> segment.writeToDatabase(connector, true));
        disksList.forEach(disk -> disk.writeToDatabase(connector, true));
    }
}
